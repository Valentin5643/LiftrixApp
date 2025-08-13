package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.FCMToken
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing FCM tokens.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Security Note: All operations are user-scoped to prevent data leakage.
 */
interface FCMTokenRepository {

    /**
     * Observes active FCM tokens for the specified user
     */
    fun observeActiveTokensForUser(userId: String): Flow<List<FCMToken>>

    /**
     * Gets all active FCM tokens for a user
     */
    suspend fun getActiveTokensForUser(userId: String): LiftrixResult<List<FCMToken>>

    /**
     * Gets the most recent active token for a user
     */
    suspend fun getActiveTokenForUser(userId: String): LiftrixResult<String?>

    /**
     * Updates or creates an FCM token for a user and device
     */
    suspend fun updateToken(
        userId: String,
        token: String,
        deviceId: String,
        platform: String,
        appVersion: String? = null,
        deviceName: String? = null
    ): LiftrixResult<Unit>

    /**
     * Marks a token as active/inactive
     */
    suspend fun updateTokenActiveStatus(
        userId: String,
        deviceId: String,
        isActive: Boolean
    ): LiftrixResult<Unit>

    /**
     * Updates the last used timestamp for a token
     */
    suspend fun updateTokenLastUsed(
        userId: String,
        deviceId: String,
        lastUsed: Long = System.currentTimeMillis()
    ): LiftrixResult<Unit>

    /**
     * Deactivates all tokens for other devices (keeping only current device active)
     */
    suspend fun deactivateOtherDeviceTokens(
        userId: String,
        currentDeviceId: String
    ): LiftrixResult<Unit>

    /**
     * Removes a token for a specific device
     */
    suspend fun deleteTokenForDevice(
        userId: String,
        deviceId: String
    ): LiftrixResult<Unit>

    /**
     * Cleans up old inactive tokens
     */
    suspend fun cleanupOldTokens(
        userId: String,
        olderThanMillis: Long = 30L * 24 * 60 * 60 * 1000 // 30 days
    ): LiftrixResult<Int>

    /**
     * Gets tokens that need to be synced to the backend
     */
    suspend fun getUnsyncedTokensForUser(userId: String): LiftrixResult<List<FCMToken>>

    /**
     * Marks tokens as synced after successful backend sync
     */
    suspend fun markTokensAsSynced(userId: String, tokenIds: List<String>): LiftrixResult<Unit>

    /**
     * Checks if a user has any active tokens
     */
    suspend fun hasActiveTokens(userId: String): LiftrixResult<Boolean>

    /**
     * Gets token statistics for analytics
     */
    suspend fun getTokenStatistics(userId: String): LiftrixResult<TokenStatistics>

    data class TokenStatistics(
        val totalTokens: Int,
        val activeTokens: Int,
        val platformBreakdown: Map<String, Int>,
        val lastUpdated: Long?
    )
}