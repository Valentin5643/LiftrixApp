package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.FriendEntity
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.UserPresence
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between Friend domain model and FriendEntity
 * Follows established codebase patterns for entity-domain conversion
 */
@Singleton
class FriendMapper @Inject constructor() {

    /**
     * Convert FriendEntity to Friend domain model
     * Note: Requires additional user profile data for complete Friend object
     */
    fun toDomain(
        entity: FriendEntity,
        displayName: String,
        email: String? = null,
        avatarUrl: String? = null,
        presence: UserPresence? = null,
        isMutual: Boolean = false
    ): Friend {
        return Friend(
            userId = entity.friendUserId,
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            status = FriendEntity.toFriendStatus(entity.status),
            presence = presence,
            friendSince = entity.createdAt,
            isMutual = isMutual
        )
    }

    /**
     * Convert Friend domain model to FriendEntity
     */
    fun toEntity(friend: Friend, currentUserId: String, isSynced: Boolean = false): FriendEntity {
        return FriendEntity(
            userId = currentUserId,
            friendUserId = friend.userId,
            status = FriendEntity.fromFriendStatus(friend.status),
            createdAt = friend.friendSince,
            updatedAt = Instant.now(),
            isSynced = isSynced
        )
    }

    /**
     * Create a new friend request entity
     */
    fun createFriendRequest(
        currentUserId: String,
        friendUserId: String,
        isSynced: Boolean = false
    ): FriendEntity {
        val now = Instant.now()
        return FriendEntity(
            userId = currentUserId,
            friendUserId = friendUserId,
            status = FriendEntity.fromFriendStatus(FriendStatus.PENDING),
            createdAt = now,
            updatedAt = now,
            isSynced = isSynced
        )
    }

    /**
     * Update entity sync status
     */
    fun updateSyncStatus(entity: FriendEntity, isSynced: Boolean): FriendEntity {
        return entity.copy(
            isSynced = isSynced,
            updatedAt = Instant.now()
        )
    }
} 