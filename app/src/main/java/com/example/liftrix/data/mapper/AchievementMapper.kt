package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.UserAchievementEntity
import com.example.liftrix.domain.model.AchievementType
import com.example.liftrix.domain.model.UserAchievement
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between UserAchievement domain model and UserAchievementEntity.
 * Handles bidirectional mapping with proper type conversions.
 */
@Singleton
class AchievementMapper @Inject constructor() {

    /**
     * Converts UserAchievementEntity to UserAchievement domain model.
     */
    fun toDomain(entity: UserAchievementEntity): UserAchievement {
        return UserAchievement(
            id = entity.id,
            userId = entity.userId,
            achievementType = AchievementType.valueOf(entity.achievementType),
            title = entity.achievementTitle,
            description = entity.achievementDescription,
            unlockedAt = entity.unlockedAt,
            isDisplayed = entity.isDisplayed
        )
    }

    /**
     * Converts UserAchievement domain model to UserAchievementEntity.
     */
    fun toEntity(
        achievement: UserAchievement,
        isSynced: Boolean = false,
        syncVersion: Long = 1L
    ): UserAchievementEntity {
        val currentTime = LocalDateTime.now()
        
        return UserAchievementEntity(
            id = achievement.id,
            userId = achievement.userId,
            achievementType = achievement.achievementType.name,
            achievementTitle = achievement.title,
            achievementDescription = achievement.description,
            unlockedAt = achievement.unlockedAt,
            isDisplayed = achievement.isDisplayed,
            createdAt = currentTime,
            updatedAt = currentTime,
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }

    /**
     * Converts list of entities to domain models.
     */
    fun toDomainList(entities: List<UserAchievementEntity>): List<UserAchievement> {
        return entities.map { toDomain(it) }
    }

    /**
     * Converts list of domain models to entities.
     */
    fun toEntityList(
        achievements: List<UserAchievement>,
        isSynced: Boolean = false,
        syncVersion: Long = 1L
    ): List<UserAchievementEntity> {
        return achievements.map { toEntity(it, isSynced, syncVersion) }
    }

    /**
     * Creates a Firestore-compatible map from the achievement.
     * Used for Firebase synchronization.
     */
    fun toFirestoreMap(achievement: UserAchievement): Map<String, Any> {
        return mapOf(
            "id" to achievement.id,
            "userId" to achievement.userId,
            "achievementType" to achievement.achievementType.name,
            "title" to achievement.title,
            "description" to achievement.description,
            "unlockedAt" to achievement.unlockedAt.toString(),
            "isDisplayed" to achievement.isDisplayed,
            "createdAt" to LocalDateTime.now().toString(),
            "updatedAt" to LocalDateTime.now().toString()
        )
    }

    /**
     * Creates a UserAchievement from a Firestore document map.
     * Used when syncing data from Firebase.
     */
    fun fromFirestoreMap(data: Map<String, Any>): UserAchievement? {
        return try {
            UserAchievement(
                id = data["id"] as String,
                userId = data["userId"] as String,
                achievementType = AchievementType.valueOf(data["achievementType"] as String),
                title = data["title"] as String,
                description = data["description"] as String,
                unlockedAt = LocalDateTime.parse(data["unlockedAt"] as String),
                isDisplayed = data["isDisplayed"] as? Boolean ?: true
            )
        } catch (e: Exception) {
            null // Return null for invalid data
        }
    }
}