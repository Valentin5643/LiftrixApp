package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.FCMTokenDao
import com.example.liftrix.data.local.entity.FCMTokenEntity
import com.example.liftrix.data.mapper.FCMTokenMapper
import com.example.liftrix.domain.model.FCMToken
import com.example.liftrix.domain.repository.FCMTokenRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FCMTokenRepository with Room database integration.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Security: All operations are user-scoped to prevent data leakage.
 */
@Singleton
class FCMTokenRepositoryImpl @Inject constructor(
    private val fcmTokenDao: FCMTokenDao,
    private val mapper: FCMTokenMapper
) : FCMTokenRepository {

    override fun observeActiveTokensForUser(userId: String): Flow<List<FCMToken>> {
        return fcmTokenDao.observeActiveTokensForUser(userId)
            .map { entities -> entities.map(mapper::toDomain) }
    }

    override suspend fun getActiveTokensForUser(userId: String): LiftrixResult<List<FCMToken>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get active FCM tokens",
                    operation = "GET_ACTIVE_TOKENS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val entities = fcmTokenDao.getActiveTokensForUser(userId)
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun getActiveTokenForUser(userId: String): LiftrixResult<String?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get active FCM token",
                    operation = "GET_ACTIVE_TOKEN",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            fcmTokenDao.getActiveTokenForUser(userId)
        }
    }

    override suspend fun updateToken(
        userId: String,
        token: String,
        deviceId: String,
        platform: String,
        appVersion: String?,
        deviceName: String?
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update FCM token",
                    operation = "UPDATE_TOKEN",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "device_id" to deviceId,
                        "platform" to platform
                    )
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            
            // Check if token already exists for this device
            val existingToken = fcmTokenDao.getTokenForDevice(userId, deviceId)
            
            if (existingToken != null) {
                // Update existing token
                fcmTokenDao.updateTokenForDevice(userId, deviceId, token, currentTime)
                fcmTokenDao.updateActiveStatus(userId, deviceId, true, currentTime)
                fcmTokenDao.updateLastUsed(userId, deviceId, currentTime)
            } else {
                // Create new token
                val tokenEntity = FCMTokenEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    token = token,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    platform = platform,
                    appVersion = appVersion,
                    isActive = true,
                    lastUsed = currentTime,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    isSynced = false
                )
                fcmTokenDao.insertToken(tokenEntity)
            }
            
            Timber.d("FCM token updated for user $userId on device $deviceId")
        }
    }

    override suspend fun updateTokenActiveStatus(
        userId: String,
        deviceId: String,
        isActive: Boolean
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update token active status",
                    operation = "UPDATE_ACTIVE_STATUS",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "device_id" to deviceId,
                        "is_active" to isActive.toString()
                    )
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            fcmTokenDao.updateActiveStatus(userId, deviceId, isActive, currentTime)
        }
    }

    override suspend fun updateTokenLastUsed(
        userId: String,
        deviceId: String,
        lastUsed: Long
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update token last used",
                    operation = "UPDATE_LAST_USED",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "device_id" to deviceId
                    )
                )
            }
        ) {
            fcmTokenDao.updateLastUsed(userId, deviceId, lastUsed)
        }
    }

    override suspend fun deactivateOtherDeviceTokens(
        userId: String,
        currentDeviceId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to deactivate other device tokens",
                    operation = "DEACTIVATE_OTHER_TOKENS",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "current_device_id" to currentDeviceId
                    )
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            val deactivatedCount = fcmTokenDao.deactivateOtherDeviceTokens(userId, currentDeviceId, currentTime)
            Timber.d("Deactivated $deactivatedCount FCM tokens for user $userId")
        }
    }

    override suspend fun deleteTokenForDevice(
        userId: String,
        deviceId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete FCM token",
                    operation = "DELETE_TOKEN",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "device_id" to deviceId
                    )
                )
            }
        ) {
            fcmTokenDao.deleteTokenForDevice(userId, deviceId)
            Timber.d("Deleted FCM token for user $userId device $deviceId")
        }
    }

    override suspend fun cleanupOldTokens(
        userId: String,
        olderThanMillis: Long
    ): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to cleanup old FCM tokens",
                    operation = "CLEANUP_OLD_TOKENS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val cutoffTime = System.currentTimeMillis() - olderThanMillis
            val deletedCount = fcmTokenDao.deleteInactiveTokensOlderThan(userId, cutoffTime)
            Timber.d("Cleaned up $deletedCount old FCM tokens for user $userId")
            deletedCount
        }
    }

    override suspend fun getUnsyncedTokensForUser(userId: String): LiftrixResult<List<FCMToken>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get unsynced FCM tokens",
                    operation = "GET_UNSYNCED_TOKENS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val entities = fcmTokenDao.getUnsyncedTokensForUser(userId)
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun markTokensAsSynced(
        userId: String,
        tokenIds: List<String>
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to mark FCM tokens as synced",
                    operation = "MARK_SYNCED",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "token_count" to tokenIds.size.toString()
                    )
                )
            }
        ) {
            tokenIds.forEach { tokenId ->
                fcmTokenDao.updateSyncStatus(userId, tokenId, true)
            }
            Timber.d("Marked ${tokenIds.size} FCM tokens as synced for user $userId")
        }
    }

    override suspend fun hasActiveTokens(userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check for active FCM tokens",
                    operation = "HAS_ACTIVE_TOKENS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            fcmTokenDao.getActiveTokenCount(userId) > 0
        }
    }

    override suspend fun getTokenStatistics(userId: String): LiftrixResult<FCMTokenRepository.TokenStatistics> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get FCM token statistics",
                    operation = "GET_TOKEN_STATISTICS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeTokens = fcmTokenDao.getActiveTokenCount(userId)
            val platformBreakdown = fcmTokenDao.getTokenCountByPlatform(userId)
            
            // Get most recent token update time
            val recentTokens = fcmTokenDao.getActiveTokensForUser(userId)
            val lastUpdated = recentTokens.maxOfOrNull { it.updatedAt }
            
            FCMTokenRepository.TokenStatistics(
                totalTokens = platformBreakdown.values.sum(),
                activeTokens = activeTokens,
                platformBreakdown = platformBreakdown,
                lastUpdated = lastUpdated
            )
        }
    }
}