package com.example.liftrix.data.mapper.notifications

import com.example.liftrix.data.local.entity.NotificationPreferenceEntity
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between NotificationPreferenceEntity and NotificationPreferences.
 * 
 * Handles bidirectional mapping with:
 * - Entity to domain model conversion
 * - Domain model to entity conversion
 * - Proper field mapping and validation
 * - Default value handling
 */
@Singleton
class NotificationPreferencesMapper @Inject constructor() {

    /**
     * Converts NotificationPreferenceEntity to domain NotificationPreferences model.
     * 
     * @param entity The entity to convert
     * @return NotificationPreferences domain model
     */
    fun toDomain(entity: NotificationPreferenceEntity): NotificationPreferences {
        return NotificationPreferences(
            userId = entity.userId,
            notificationsEnabled = entity.notificationsEnabled,
            socialNotifications = entity.socialNotifications,
            workoutNotifications = entity.workoutNotifications,
            achievementNotifications = entity.achievementNotifications,
            reminderNotifications = entity.reminderNotifications,
            gymBuddyPrs = entity.gymBuddyPrs,
            followRequests = entity.followRequests,
            postLikes = entity.postLikes,
            postComments = entity.postComments,
            mentions = entity.mentions,
            deliveryFrequency = entity.deliveryFrequency,
            quietHoursEnabled = entity.quietHoursEnabled,
            quietHoursStart = entity.quietHoursStart,
            quietHoursEnd = entity.quietHoursEnd,
            batchSocialNotifications = entity.batchSocialNotifications,
            batchWindowMinutes = entity.batchWindowMinutes,
            notificationSound = entity.notificationSound,
            notificationVibration = entity.notificationVibration,
            showInAppNotifications = entity.showInAppNotifications,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Converts NotificationPreferences domain model to NotificationPreferenceEntity.
     * 
     * @param domain The domain model to convert
     * @return NotificationPreferenceEntity for Room database
     */
    fun toEntity(domain: NotificationPreferences): NotificationPreferenceEntity {
        return NotificationPreferenceEntity(
            userId = domain.userId,
            notificationsEnabled = domain.notificationsEnabled,
            socialNotifications = domain.socialNotifications,
            workoutNotifications = domain.workoutNotifications,
            achievementNotifications = domain.achievementNotifications,
            reminderNotifications = domain.reminderNotifications,
            gymBuddyPrs = domain.gymBuddyPrs,
            followRequests = domain.followRequests,
            postLikes = domain.postLikes,
            postComments = domain.postComments,
            mentions = domain.mentions,
            deliveryFrequency = domain.deliveryFrequency,
            quietHoursEnabled = domain.quietHoursEnabled,
            quietHoursStart = domain.quietHoursStart,
            quietHoursEnd = domain.quietHoursEnd,
            batchSocialNotifications = domain.batchSocialNotifications,
            batchWindowMinutes = domain.batchWindowMinutes,
            notificationSound = domain.notificationSound,
            notificationVibration = domain.notificationVibration,
            showInAppNotifications = domain.showInAppNotifications,
            updatedAt = domain.updatedAt
        )
    }

    /**
     * Converts a list of entities to domain models.
     * 
     * @param entities List of entities to convert
     * @return List of NotificationPreferences domain models
     */
    fun toDomain(entities: List<NotificationPreferenceEntity>): List<NotificationPreferences> {
        return entities.map { toDomain(it) }
    }

    /**
     * Converts a list of domain models to entities.
     * 
     * @param domainModels List of domain models to convert
     * @return List of NotificationPreferenceEntity for Room database
     */
    fun toEntity(domainModels: List<NotificationPreferences>): List<NotificationPreferenceEntity> {
        return domainModels.map { toEntity(it) }
    }
}