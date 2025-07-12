package com.example.liftrix.data.repository

import com.example.liftrix.billing.BillingRepository
import com.example.liftrix.data.local.dao.SubscriptionDao
import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.SubscriptionTier as DataSubscriptionTier
import com.example.liftrix.data.mapper.SubscriptionMapper
import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SubscriptionRepository handling Google Play Billing integration
 * and local subscription data management with proper error handling and logging.
 * Uses domain models and mappers to maintain Clean Architecture principles.
 */
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val subscriptionMapper: SubscriptionMapper,
    private val billingRepository: BillingRepository
) : SubscriptionRepository {

    override fun getSubscriptionStatus(userId: String): Flow<Subscription?> {
        return subscriptionDao.getSubscriptionFlow(userId).map { entity ->
            entity?.let { subscriptionMapper.toDomain(it) }
        }
    }

    override fun hasActivePremiumSubscription(userId: String): Flow<Boolean> {
        return subscriptionDao.getSubscriptionFlow(userId).map { entity ->
            entity?.let { subscriptionMapper.toDomain(it) }?.isActive == true
        }
    }

    override suspend fun updateSubscription(subscription: Subscription): Result<Unit> {
        return try {
            // Get existing entity to preserve sync metadata
            val existingEntity = subscriptionDao.getSubscription(subscription.userId)
            if (existingEntity == null) {
                Timber.w("No existing subscription found for user: ${subscription.userId}")
                return Result.failure(IllegalStateException("No existing subscription found for user"))
            }
            
            val updatedEntity = subscriptionMapper.updateEntity(existingEntity, subscription, false)
            subscriptionDao.updateSubscription(updatedEntity)
            Timber.d("Updated subscription for user: ${subscription.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update subscription for user: ${subscription.userId}")
            Result.failure(e)
        }
    }

    override suspend fun insertSubscription(subscription: Subscription): Result<Unit> {
        return try {
            // Check if subscription already exists
            val existingCount = subscriptionDao.getSubscriptionCount(subscription.userId)
            if (existingCount > 0) {
                Timber.w("Subscription already exists for user: ${subscription.userId}")
                return Result.failure(IllegalStateException("Subscription already exists for user"))
            }

            val newEntity = subscriptionMapper.toEntity(subscription, false, 1L)
            subscriptionDao.insertSubscription(newEntity)
            Timber.d("Inserted new subscription for user: ${subscription.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert subscription for user: ${subscription.userId}")
            Result.failure(e)
        }
    }

    override suspend fun syncWithGooglePlay(userId: String): Result<Unit> {
        return try {
            Timber.d("Starting Google Play sync for user: $userId")
            
            // Initialize billing client if not already connected
            billingRepository.initialize().getOrElse { error ->
                Timber.e("Failed to initialize billing client: ${error.message}")
                return Result.failure(error)
            }
            
            // Query current purchases from Google Play
            val purchases = billingRepository.queryPurchases().getOrElse { error ->
                Timber.e("Failed to query purchases: ${error.message}")
                return Result.failure(error)
            }
            
            // Process each purchase
            purchases.forEach { purchase ->
                if (billingRepository.validatePurchase(purchase)) {
                    // Convert purchase to subscription domain model
                    val subscription = billingRepository.convertPurchaseToSubscription(purchase, userId)
                    
                    if (subscription != null) {
                        // Update or insert subscription in local database
                        val existingCount = subscriptionDao.getSubscriptionCount(userId)
                        if (existingCount > 0) {
                            updateSubscription(subscription)
                        } else {
                            insertSubscription(subscription)
                        }
                        
                        // Acknowledge purchase if not already acknowledged
                        if (!purchase.isAcknowledged) {
                            billingRepository.acknowledgePurchase(purchase.purchaseToken)
                                .onFailure { error ->
                                    Timber.e("Failed to acknowledge purchase: ${error.message}")
                                }
                        }
                    } else {
                        Timber.w("Failed to convert purchase to subscription: ${purchase.purchaseToken}")
                    }
                } else {
                    Timber.w("Purchase validation failed: ${purchase.purchaseToken}")
                }
            }
            
            Timber.d("Google Play sync completed for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync with Google Play for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun getSubscriptionCount(userId: String): Int {
        return try {
            subscriptionDao.getSubscriptionCount(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get subscription count for user: $userId")
            0
        }
    }

    override suspend fun deleteSubscription(userId: String): Result<Unit> {
        return try {
            subscriptionDao.deleteSubscription(userId)
            Timber.d("Deleted subscription for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete subscription for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun getActiveSubscriptions(): List<Subscription> {
        return try {
            subscriptionDao.getActiveSubscriptions().map { entity ->
                subscriptionMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get active subscriptions")
            emptyList()
        }
    }

    override suspend fun getUnsyncedSubscriptions(): List<Subscription> {
        return try {
            subscriptionDao.getUnsyncedSubscriptions().map { entity ->
                subscriptionMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced subscriptions")
            emptyList()
        }
    }

    override suspend fun markAsSynced(userId: String, syncVersion: Long): Result<Unit> {
        return try {
            subscriptionDao.markAsSynced(userId, syncVersion)
            Timber.d("Marked subscription as synced for user: $userId, version: $syncVersion")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark subscription as synced for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun createSubscriptionFromGooglePlay(
        userId: String,
        tier: SubscriptionTier,
        productId: String,
        subscriptionId: String
    ): Result<Subscription> {
        return try {
            // Check if subscription already exists
            val existingCount = subscriptionDao.getSubscriptionCount(userId)
            if (existingCount > 0) {
                Timber.w("Subscription already exists for user: $userId")
                return Result.failure(IllegalStateException("Subscription already exists for user"))
            }

            val now = Instant.now()
            val subscription = Subscription(
                userId = userId,
                tier = tier,
                status = SubscriptionStatus.ACTIVE,
                provider = SubscriptionProvider.GOOGLE_PLAY,
                productId = productId,
                subscriptionId = subscriptionId,
                startedAt = now,
                expiresAt = null, // Will be updated with actual expiration from Google Play
                autoRenew = true,
                currency = "USD"
            )

            val entity = subscriptionMapper.toEntity(subscription, false, 1L)
            subscriptionDao.insertSubscription(entity)
            Timber.d("Created subscription from Google Play for user: $userId, tier: $tier")
            Result.success(subscription)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create subscription from Google Play for user: $userId")
            Result.failure(e)
        }
    }
}