package com.example.liftrix.data.repository.notifications

import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.local.entity.NotificationMuteEntity
import com.example.liftrix.domain.repository.notifications.NotificationMuteRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of NotificationMuteRepository using Room database.
 * 
 * Manages user muting for notification privacy including:
 * - User-scoped mute/unmute operations
 * - Efficient count queries without loading full data
 * - Bulk operations for performance
 * - Proper security with userId filtering
 */
@Singleton
class NotificationMuteRepositoryImpl @Inject constructor(
    private val notificationMuteDao: NotificationMuteDao
) : NotificationMuteRepository {

    override suspend fun getMutedUsersCount(userId: String): Int {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        Timber.d("Getting muted users count for user: $userId")
        
        val count = notificationMuteDao.getMutedUsersCount(userId)
        
        Timber.d("Muted users count for user $userId: $count")
        return count
    }

    override suspend fun getMutedUsers(userId: String): List<String> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        Timber.d("Getting muted users list for user: $userId")
        
        val mutedUsers = notificationMuteDao.getMutedUsers(userId)
        
        Timber.d("Found ${mutedUsers.size} muted users for user: $userId")
        return mutedUsers
    }

    override suspend fun isUserMuted(userId: String, mutedUserId: String): Boolean {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(mutedUserId.isNotBlank()) { "Muted user ID cannot be blank" }
        
        return notificationMuteDao.isUserMuted(userId, mutedUserId)
    }

    override suspend fun muteUser(userId: String, mutedUserId: String) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(mutedUserId.isNotBlank()) { "Muted user ID cannot be blank" }
        require(userId != mutedUserId) { "User cannot mute themselves" }
        
        Timber.d("Muting user $mutedUserId for user: $userId")
        
        val muteEntity = NotificationMuteEntity(
            id = "${userId}_user_${mutedUserId}_${System.currentTimeMillis()}",
            userId = userId,
            muteType = "USER",
            mutedUserId = mutedUserId,
            mutedCategory = null,
            mutedUntil = null,
            createdAt = System.currentTimeMillis()
        )
        
        notificationMuteDao.insertMute(muteEntity)
        
        Timber.d("Successfully muted user $mutedUserId for user: $userId")
    }

    override suspend fun unmuteUser(userId: String, mutedUserId: String) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(mutedUserId.isNotBlank()) { "Muted user ID cannot be blank" }
        
        Timber.d("Unmuting user $mutedUserId for user: $userId")
        
        notificationMuteDao.deleteMute(userId, mutedUserId)
        
        Timber.d("Successfully unmuted user $mutedUserId for user: $userId")
    }

    override suspend fun clearAllMutedUsers(userId: String) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        Timber.d("Clearing all muted users for user: $userId")
        
        notificationMuteDao.deleteAllMutesForUser(userId)
        
        Timber.d("Successfully cleared all muted users for user: $userId")
    }
}