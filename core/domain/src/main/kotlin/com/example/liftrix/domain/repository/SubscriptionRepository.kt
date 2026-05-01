package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for subscription management with Google Play Billing integration.
 * Handles subscription status tracking, billing operations, and local caching.
 * Works with domain models to maintain Clean Architecture principles.
 */
interface SubscriptionRepository {

    /**
     * Get subscription status for a specific user
     * @param userId The user ID to get subscription for
     * @return Flow of subscription domain model or null if no subscription
     */
    fun getSubscriptionStatus(userId: String): Flow<Subscription?>

    /**
     * Check if user has active premium subscription
     * @param userId The user ID to check
     * @return Flow of boolean indicating premium status
     */
    fun hasActivePremiumSubscription(userId: String): Flow<Boolean>

    /**
     * Update subscription data in local storage
     * @param subscription The subscription domain model to update
     * @return Result indicating success or failure
     */
    suspend fun updateSubscription(subscription: Subscription): Result<Unit>

    /**
     * Insert new subscription data
     * @param subscription The subscription domain model to insert
     * @return Result indicating success or failure
     */
    suspend fun insertSubscription(subscription: Subscription): Result<Unit>

    /**
     * Sync subscription status with Google Play Billing
     * @param userId The user ID to sync subscription for
     * @return Result indicating sync success or failure
     */
    suspend fun syncWithGooglePlay(userId: String): Result<Unit>

    /**
     * Get subscription count for user (should be 0 or 1)
     * @param userId The user ID to check
     * @return Count of subscriptions for user
     */
    suspend fun getSubscriptionCount(userId: String): Int

    /**
     * Delete subscription for a user
     * @param userId The user ID to delete subscription for
     * @return Result indicating success or failure
     */
    suspend fun deleteSubscription(userId: String): Result<Unit>

    /**
     * Get all active subscriptions (for admin/sync purposes)
     * @return List of all active subscriptions as domain models
     */
    suspend fun getActiveSubscriptions(): List<Subscription>

    /**
     * Get subscriptions that need sync
     * @return List of unsynced subscriptions as domain models
     */
    suspend fun getUnsyncedSubscriptions(): List<Subscription>

    /**
     * Mark subscription as synced
     * @param userId The user ID
     * @param syncVersion The sync version
     * @return Result indicating success or failure
     */
    suspend fun markAsSynced(userId: String, syncVersion: Long): Result<Unit>

    /**
     * Create subscription from Google Play purchase
     * @param userId The user ID
     * @param tier The subscription tier
     * @param productId The Google Play product ID
     * @param subscriptionId The Google Play subscription ID
     * @return Result with created subscription domain model
     */
    suspend fun createSubscriptionFromGooglePlay(
        userId: String,
        tier: SubscriptionTier,
        productId: String,
        subscriptionId: String
    ): Result<Subscription>
}