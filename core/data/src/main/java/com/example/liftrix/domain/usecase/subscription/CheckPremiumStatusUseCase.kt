package com.example.liftrix.domain.usecase.subscription

import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for checking if the current user has active premium subscription status.
 * Handles user authentication state and provides reactive premium status updates.
 */
class CheckPremiumStatusUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {
    
    /**
     * Check if the current authenticated user has active premium subscription.
     * 
     * @return Flow<Boolean> - true if user has active premium subscription, false otherwise
     */
    suspend operator fun invoke(): Flow<Boolean> {
        return try {
            // Get current user ID and check their subscription status
            val currentUserId = authQueryUseCase(waitForAuth = false).getOrNull()
            
            if (currentUserId == null) {
                Timber.d("No authenticated user found - returning false for premium status")
                return flowOf(false)
            }

            subscriptionRepository.getSubscriptionStatus(currentUserId.value)
                .map { subscription ->
                    val hasActive = subscription?.isActive == true
                    Timber.d("Premium status check for user ${currentUserId.value}: $hasActive")
                    hasActive
                }
        } catch (e: Exception) {
            Timber.e(e, "Error checking premium status")
            flowOf(false)
        }
    }
    
    /**
     * Check premium status for a specific user ID.
     * 
     * @param userId The user ID to check premium status for
     * @return Flow<Boolean> - true if user has active premium subscription, false otherwise
     */
    fun checkForUser(userId: String): Flow<Boolean> {
        return try {
            if (userId.isBlank()) {
                Timber.w("Empty user ID provided for premium status check")
                return flowOf(false)
            }
            
            subscriptionRepository.getSubscriptionStatus(userId)
                .map { subscription ->
                    val hasActive = subscription?.isActive == true
                    Timber.d("Premium status check for user $userId: $hasActive")
                    hasActive
                }
        } catch (e: Exception) {
            Timber.e(e, "Error checking premium status for user: $userId")
            flowOf(false)
        }
    }
    
    /**
     * Get detailed subscription information for the current user.
     * 
     * @return Flow<Subscription?> - subscription details or null if no subscription
     */
    suspend fun getSubscriptionDetails(): Flow<Subscription?> {
        return try {
            val currentUserId = authQueryUseCase(waitForAuth = false).getOrNull()
            
            if (currentUserId == null) {
                Timber.d("No authenticated user found - returning null for subscription details")
                return flowOf(null)
            }

            subscriptionRepository.getSubscriptionStatus(currentUserId.value)
        } catch (e: Exception) {
            Timber.e(e, "Error getting subscription details")
            flowOf(null)
        }
    }
    
    /**
     * Get detailed subscription information for a specific user.
     * 
     * @param userId The user ID to get subscription details for
     * @return Flow<Subscription?> - subscription details or null if no subscription
     */
    fun getSubscriptionDetailsForUser(userId: String): Flow<Subscription?> {
        return try {
            if (userId.isBlank()) {
                Timber.w("Empty user ID provided for subscription details")
                return flowOf(null)
            }
            
            subscriptionRepository.getSubscriptionStatus(userId)
        } catch (e: Exception) {
            Timber.e(e, "Error getting subscription details for user: $userId")
            flowOf(null)
        }
    }
}