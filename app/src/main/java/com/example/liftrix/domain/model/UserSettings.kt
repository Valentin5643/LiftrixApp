package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing user settings and preferences.
 * 
 * This model encapsulates all user-configurable settings including theme preferences,
 * notification settings, and other user configurations. It provides a clean abstraction
 * over the data layer's SettingsEntity.
 * 
 * @property userId The unique identifier for the user
 * @property darkMode Whether dark mode is enabled
 * @property notificationsEnabled Whether notifications are enabled
 * @property weightUnit The user's preferred weight unit (kg or lbs)
 * @property updatedAt The timestamp of the last update
 */
data class UserSettings(
    val userId: String,
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.getSystemDefault(),
    val terminologyPreference: String = "NEW", // NEW or LEGACY
    val migrationCompleted: Boolean = false,
    val migrationExplanationSeen: Boolean = false,
    val updatedAt: Instant = Instant.now()
) {
    companion object {
        /**
         * Creates default settings for a new user.
         * 
         * @param userId The user's unique identifier
         * @return UserSettings with default values
         */
        fun createDefault(userId: String): UserSettings = UserSettings(
            userId = userId,
            darkMode = false,
            notificationsEnabled = true,
            weightUnit = WeightUnit.getSystemDefault(),
            terminologyPreference = "NEW",
            migrationCompleted = false,
            migrationExplanationSeen = false,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Validates that the settings are in a valid state.
     * 
     * @throws IllegalArgumentException if the settings are invalid
     */
    fun validate() {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(updatedAt.isBefore(Instant.now().plusSeconds(60))) { 
            "Updated timestamp cannot be in the future" 
        }
    }
    
    /**
     * Creates a copy of the settings with an updated timestamp.
     * 
     * @return UserSettings with current timestamp
     */
    fun withUpdatedTimestamp(): UserSettings = copy(updatedAt = Instant.now())
}