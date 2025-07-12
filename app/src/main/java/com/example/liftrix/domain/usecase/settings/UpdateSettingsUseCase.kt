package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.repository.SettingsRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for updating user settings with validation and persistence.
 * 
 * This use case handles individual setting updates with proper input validation,
 * business rules enforcement, and error handling. It provides atomic operations
 * for specific settings to prevent partial state corruption.
 * 
 * The use case follows the single responsibility principle by focusing solely on
 * settings updates while delegating persistence logic to the repository layer.
 */
class UpdateSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    /**
     * Updates the dark mode setting for a user.
     * 
     * This method validates the input parameters and delegates to the repository
     * for immediate persistence to DataStore and background sync to Room.
     * 
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether dark mode should be enabled
     * @return Result<Unit> indicating success or failure with error details
     */
    suspend fun updateDarkMode(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            // Validate user ID presence
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to update dark mode with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Updating dark mode for user $userId to: $enabled")
            
            // Delegate to repository for persistence
            val result = settingsRepository.updateDarkMode(userId, enabled)
            
            if (result.isSuccess) {
                Timber.d("Dark mode updated successfully for user: $userId")
            } else {
                Timber.e("Failed to update dark mode for user: $userId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while updating dark mode for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Updates the notification setting for a user.
     * 
     * This method validates the input parameters and delegates to the repository
     * for immediate persistence to DataStore and background sync to Room.
     * 
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether notifications should be enabled
     * @return Result<Unit> indicating success or failure with error details
     */
    suspend fun updateNotifications(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            // Validate user ID presence
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to update notifications with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Updating notifications for user $userId to: $enabled")
            
            // Delegate to repository for persistence
            val result = settingsRepository.updateNotifications(userId, enabled)
            
            if (result.isSuccess) {
                Timber.d("Notifications updated successfully for user: $userId")
            } else {
                Timber.e("Failed to update notifications for user: $userId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while updating notifications for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Updates multiple settings atomically for a user.
     * 
     * This method provides a way to update multiple settings in a single operation
     * while maintaining data consistency and preventing partial updates.
     * 
     * @param userId The ID of the user whose settings to update
     * @param darkMode The dark mode setting to apply, null to skip
     * @param notifications The notification setting to apply, null to skip
     * @return Result<Unit> indicating success or failure with error details
     */
    suspend fun updateMultipleSettings(
        userId: String,
        darkMode: Boolean? = null,
        notifications: Boolean? = null
    ): Result<Unit> {
        return try {
            // Validate user ID presence
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to update multiple settings with blank user ID")
                return Result.failure(error)
            }

            // Validate at least one setting is provided
            if (darkMode == null && notifications == null) {
                val error = IllegalArgumentException("At least one setting must be provided for update")
                Timber.e("Attempted to update multiple settings with no values for user: $userId")
                return Result.failure(error)
            }

            Timber.d("Updating multiple settings for user $userId: darkMode=$darkMode, notifications=$notifications")
            
            // Update dark mode if provided
            darkMode?.let { enabled ->
                val darkModeResult = settingsRepository.updateDarkMode(userId, enabled)
                if (darkModeResult.isFailure) {
                    Timber.e("Failed to update dark mode during multiple settings update for user: $userId")
                    return darkModeResult
                }
            }
            
            // Update notifications if provided
            notifications?.let { enabled ->
                val notificationsResult = settingsRepository.updateNotifications(userId, enabled)
                if (notificationsResult.isFailure) {
                    Timber.e("Failed to update notifications during multiple settings update for user: $userId")
                    return notificationsResult
                }
            }
            
            Timber.d("Multiple settings updated successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Exception while updating multiple settings for user: $userId")
            Result.failure(e)
        }
    }
}