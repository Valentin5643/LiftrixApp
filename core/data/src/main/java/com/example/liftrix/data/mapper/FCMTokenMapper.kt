package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.FCMTokenEntity
import com.example.liftrix.domain.model.FCMToken
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper between FCMTokenEntity and FCMToken domain model.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
@Singleton
class FCMTokenMapper @Inject constructor() {

    fun toDomain(entity: FCMTokenEntity): FCMToken {
        return FCMToken(
            id = entity.id,
            userId = entity.userId,
            token = entity.token,
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            platform = FCMToken.Platform.fromString(entity.platform),
            appVersion = entity.appVersion,
            isActive = entity.isActive,
            lastUsed = entity.lastUsed,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isSynced = entity.isSynced
        )
    }

    fun toEntity(domain: FCMToken): FCMTokenEntity {
        return FCMTokenEntity(
            id = domain.id,
            userId = domain.userId,
            token = domain.token,
            deviceId = domain.deviceId,
            deviceName = domain.deviceName,
            platform = domain.platform.value,
            appVersion = domain.appVersion,
            isActive = domain.isActive,
            lastUsed = domain.lastUsed,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isSynced = domain.isSynced
        )
    }

    fun toDomainList(entities: List<FCMTokenEntity>): List<FCMToken> {
        return entities.map { toDomain(it) }
    }

    fun toEntityList(domains: List<FCMToken>): List<FCMTokenEntity> {
        return domains.map { toEntity(it) }
    }
}