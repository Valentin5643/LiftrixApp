package com.example.liftrix.domain.repository.notifications

import com.example.liftrix.domain.model.notifications.NotificationPreferences

/**
 * Repository interface for managing notification preferences.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Provides access to user notification settings including:
 * - Master notification controls
 * - Category-specific preferences
 * - Delivery timing and frequency
 * - Quiet hours configuration
 * - Sound and vibration settings
 * 
 * All operations are user-scoped for security.
 */
interface NotificationPreferencesRepository {
    
    /**
     * Gets notification preferences for a specific user.
     * Returns default preferences if none exist.
     * 
     * @param userId User ID to get preferences for
     * @return NotificationPreferences for the user
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun getNotificationPreferences(userId: String): NotificationPreferences
    
    /**
     * Updates notification preferences for a user.
     * Creates new preferences if they don't exist.
     * 
     * @param preferences Updated notification preferences
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun updateNotificationPreferences(preferences: NotificationPreferences)
    
    /**
     * Resets notification preferences to default values for a user.
     * 
     * @param userId User ID to reset preferences for
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun resetNotificationPreferences(userId: String)
    
    /**
     * Checks if notifications are enabled for a specific category and user.
     * 
     * @param userId User ID to check
     * @param category Notification category (social, workout, achievement)
     * @return true if notifications are enabled for the category, false otherwise
     */
    suspend fun isCategoryEnabled(userId: String, category: String): Boolean
    
    /**
     * Gets the count of users who have disabled all notifications.
     * Used for analytics and monitoring.
     * 
     * @return Number of users with notifications disabled
     */
    suspend fun getDisabledNotificationsCount(): Int
}