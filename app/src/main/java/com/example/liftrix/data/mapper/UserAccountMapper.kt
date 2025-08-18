package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.UserAccountEntity
import com.example.liftrix.domain.model.UserAccount
import java.time.LocalDateTime

/**
 * Mapper for converting between UserAccount domain model and UserAccountEntity.
 */
object UserAccountMapper {
    
    /**
     * Converts UserAccountEntity to UserAccount domain model
     */
    fun toDomain(entity: UserAccountEntity): UserAccount {
        return UserAccount(
            userId = entity.userId,
            email = entity.email,
            username = entity.username,
            emailVerified = entity.emailVerified,
            displayName = entity.displayName,
            lastPasswordChange = entity.lastPasswordChange,
            accountCreatedAt = entity.accountCreatedAt,
            lastEmailUpdate = entity.lastEmailUpdate,
            deletionRequestedAt = entity.deletionRequestedAt
        )
    }
    
    /**
     * Converts UserAccount domain model to UserAccountEntity
     */
    fun toEntity(domain: UserAccount, isSynced: Boolean = false, syncVersion: Long = 1L): UserAccountEntity {
        return UserAccountEntity(
            userId = domain.userId,
            email = domain.email,
            username = domain.username,
            emailVerified = domain.emailVerified,
            displayName = domain.displayName,
            lastPasswordChange = domain.lastPasswordChange,
            accountCreatedAt = domain.accountCreatedAt,
            lastEmailUpdate = domain.lastEmailUpdate,
            deletionRequestedAt = domain.deletionRequestedAt,
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }
    
    /**
     * Creates a UserAccount from basic user information
     */
    fun fromFirebaseUser(
        userId: String,
        email: String,
        displayName: String? = null,
        emailVerified: Boolean = false
    ): UserAccount {
        return UserAccount.create(
            userId = userId,
            email = email,
            displayName = displayName,
            username = null
        ).withEmailVerified(emailVerified)
    }
    
    /**
     * Updates an existing entity with new domain data while preserving sync metadata
     */
    fun updateEntity(
        entity: UserAccountEntity,
        domain: UserAccount,
        markAsUnsynced: Boolean = true
    ): UserAccountEntity {
        return entity.copy(
            email = domain.email,
            username = domain.username,
            emailVerified = domain.emailVerified,
            displayName = domain.displayName,
            lastPasswordChange = domain.lastPasswordChange,
            lastEmailUpdate = domain.lastEmailUpdate,
            deletionRequestedAt = domain.deletionRequestedAt,
            isSynced = if (markAsUnsynced) false else entity.isSynced,
            syncVersion = if (markAsUnsynced) entity.syncVersion + 1 else entity.syncVersion
        )
    }
}