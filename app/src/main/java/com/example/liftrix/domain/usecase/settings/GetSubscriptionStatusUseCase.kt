package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving subscription status with Google Play Billing validation.
 * 
 * This use case provides reactive access to subscription data through Flow streams,
 * handling Google Play Billing integration, caching for offline access, and proper
 * error handling for billing failures.
 * 
 * The use case follows the single responsibility principle by focusing solely on
 * subscription status retrieval while delegating billing and persistence logic
 * to the repository layer.
 */
class GetSubscriptionStatusUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) {

    /**
     * Retrieves subscription status as a reactive stream wrapped in Result.
     * 
     * This method provides continuous updates to subscription changes, with proper error
     * handling that wraps exceptions in Result.failure() for graceful degradation.
     * Maps Subscription from data layer to SubscriptionStatus domain model.
     * 
     * @param userId The ID of the user whose subscription status to retrieve
     * @return Flow<Result<SubscriptionStatus>> that emits subscription updates or errors
     */
    suspend operator fun invoke(userId: String): Flow<Result<SubscriptionStatus>> {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to get subscription status with blank user ID")
                throw IllegalArgumentException("User ID cannot be blank")
            }

            Timber.d("Getting subscription status for user: $userId")
            
            subscriptionRepository.getSubscriptionStatus(userId)
                .map { subscription ->
                    if (subscription != null) {
                        Timber.d("Subscription retrieved successfully for user: $userId")
                        Result.success(subscription.status)
                    } else {
                        Timber.d("No subscription found for user: $userId, returning default tier")
                        Result.success(SubscriptionStatus.default())
                    }
                }
                .catch { exception ->
                    Timber.e(exception, "Error retrieving subscription status for user: $userId")
                    emit(Result.failure(exception))
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception while setting up subscription status retrieval for user: $userId")
            throw e
        }
    }

    /**
     * Checks if user has active premium subscription.
     * 
     * This method provides a quick way to determine if a user has premium access
     * without retrieving the full subscription object.
     * 
     * @param userId The ID of the user to check
     * @return Flow<Result<Boolean>> indicating premium status
     */
    suspend fun hasActivePremiumSubscription(userId: String): Flow<Result<Boolean>> {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to check premium status with blank user ID")
                throw IllegalArgumentException("User ID cannot be blank")
            }

            Timber.d("Checking premium status for user: $userId")
            
            subscriptionRepository.hasActivePremiumSubscription(userId)
                .map { hasPremium ->
                    Timber.d("Premium status for user $userId: $hasPremium")
                    Result.success(hasPremium)
                }
                .catch { exception ->
                    Timber.e(exception, "Error checking premium status for user: $userId")
                    emit(Result.failure(exception))
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception while checking premium status for user: $userId")
            throw e
        }
    }

    /**
     * Forces synchronization of subscription status with Google Play Billing.
     * 
     * This method ensures that the local cache is up-to-date with the latest
     * subscription state from Google Play, useful for resolving discrepancies
     * or after purchase completion.
     * 
     * @param userId The ID of the user whose subscription to sync
     * @return Result<Unit> indicating success or failure of the sync operation
     */
    suspend fun syncSubscriptionStatus(userId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to sync subscription with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Syncing subscription status for user: $userId")
            val result = subscriptionRepository.syncWithGooglePlay(userId)
            
            if (result.isSuccess) {
                Timber.d("Subscription synced successfully for user: $userId")
            } else {
                Timber.e("Failed to sync subscription for user: $userId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while syncing subscription for user: $userId")
            Result.failure(e)
        }
    }

}