package com.example.liftrix.data.repository.notifications

import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.local.entity.NotificationPreferenceEntity
import com.example.liftrix.data.mapper.notifications.NotificationPreferencesMapper
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import com.example.liftrix.domain.repository.notifications.NotificationPreferencesRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of NotificationPreferencesRepository using Room database.
 * 
 * Handles persistence of user notification preferences with:
 * - Default preferences for new users
 * - User-scoped security for all operations
 * - Entity-to-domain model mapping
 * - Proper error handling and logging
 * 
 * All database operations use userId filtering for security.
 */
@Singleton
class NotificationPreferencesRepositoryImpl @Inject constructor(
    private val notificationPreferenceDao: NotificationPreferenceDao,
    private val notificationPreferencesMapper: NotificationPreferencesMapper
) : NotificationPreferencesRepository {

    override suspend fun getNotificationPreferences(userId: String): NotificationPreferences {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        Timber.d("Getting notification preferences for user: $userId")
        
        val entity = notificationPreferenceDao.getNotificationPreferences(userId)
        
        return if (entity != null) {
            Timber.d("Found existing preferences for user: $userId")
            notificationPreferencesMapper.toDomain(entity)
        } else {
            Timber.d("No preferences found for user: $userId, returning defaults")
            createDefaultPreferences(userId)
        }
    }

    override suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
        require(preferences.userId.isNotBlank()) { "User ID cannot be blank" }
        
        Timber.d("Updating notification preferences for user: ${preferences.userId}")
        
        val entity = notificationPreferencesMapper.toEntity(preferences)
        notificationPreferenceDao.upsertNotificationPreferences(entity)
        
        Timber.d("Successfully updated notification preferences for user: ${preferences.userId}")
    }

    override suspend fun resetNotificationPreferences(userId: String) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        Timber.d("Resetting notification preferences for user: $userId")
        
        val defaultPreferences = createDefaultPreferences(userId)
        val entity = notificationPreferencesMapper.toEntity(defaultPreferences)
        
        notificationPreferenceDao.upsertNotificationPreferences(entity)
        
        Timber.d("Successfully reset notification preferences for user: $userId")
    }

    override suspend fun isCategoryEnabled(userId: String, category: String): Boolean {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        val preferences = getNotificationPreferences(userId)
        
        return when (category.lowercase()) {
            "social" -> preferences.notificationsEnabled && preferences.socialNotifications
            "workout" -> preferences.notificationsEnabled && preferences.workoutNotifications
            "achievement" -> preferences.notificationsEnabled && preferences.achievementNotifications
            "reminder" -> preferences.notificationsEnabled && preferences.reminderNotifications
            else -> {
                Timber.w("Unknown notification category: $category")
                false
            }
        }
    }

    override suspend fun getDisabledNotificationsCount(): Int {
        return notificationPreferenceDao.getDisabledNotificationsCount()
    }

    /**
     * Creates default notification preferences for a new user.
     * 
     * @param userId User ID to create defaults for
     * @return NotificationPreferences with default values
     */
    private fun createDefaultPreferences(userId: String): NotificationPreferences {
        return NotificationPreferences(
            userId = userId,
            notificationsEnabled = true,
            socialNotifications = true,
            workoutNotifications = true,
            achievementNotifications = true,
            reminderNotifications = true,
            gymBuddyPrs = true,
            followRequests = true,
            postLikes = true,
            postComments = true,
            mentions = true,
            deliveryFrequency = "IMMEDIATE",
            quietHoursEnabled = true,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            batchSocialNotifications = true,
            batchWindowMinutes = 60,
            notificationSound = true,
            notificationVibration = true,
            showInAppNotifications = true,
            updatedAt = System.currentTimeMillis()
        )
    }
}