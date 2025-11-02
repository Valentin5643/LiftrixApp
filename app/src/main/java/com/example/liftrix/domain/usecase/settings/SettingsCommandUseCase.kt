package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetLayoutMode
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.service.PreferencesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated command use case for settings mutation operations.
 *
 * Replaces:
 * - UpdateSettingsUseCase.kt
 * - UpdateUserPreferencesUseCase.kt
 * - UpdateWeightUnitPreferenceUseCase.kt
 *
 * Provides methods for updating various user settings including:
 * - Basic settings (dark mode, notifications)
 * - Weight unit preference
 * - Widget layout and preferences
 * - User experience level
 * - Widget visibility and order
 * - Auto-refresh settings
 * - Dashboard sections
 *
 * All operations include proper validation and error handling.
 *
 * @property settingsRepository Repository for settings data operations
 * @property preferencesService Service for user preference management
 */
@Singleton
class SettingsCommandUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val preferencesService: PreferencesService
) {

    // ===== Basic Settings Operations (from UpdateSettingsUseCase) =====

    /**
     * Updates the dark mode setting for a user.
     * Replaces UpdateSettingsUseCase.updateDarkMode()
     *
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether dark mode should be enabled
     * @return Result<Unit> indicating success or failure
     */
    suspend fun updateDarkMode(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to update dark mode with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Updating dark mode for user $userId to: $enabled")

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
     * Replaces UpdateSettingsUseCase.updateNotifications()
     *
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether notifications should be enabled
     * @return Result<Unit> indicating success or failure
     */
    suspend fun updateNotifications(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to update notifications with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Updating notifications for user $userId to: $enabled")

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
     * Replaces UpdateSettingsUseCase.updateMultipleSettings()
     *
     * @param userId The ID of the user whose settings to update
     * @param darkMode The dark mode setting to apply, null to skip
     * @param notifications The notification setting to apply, null to skip
     * @return Result<Unit> indicating success or failure
     */
    suspend fun updateMultipleSettings(
        userId: String,
        darkMode: Boolean? = null,
        notifications: Boolean? = null
    ): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to update multiple settings with blank user ID")
                return Result.failure(error)
            }

            if (darkMode == null && notifications == null) {
                val error = IllegalArgumentException("At least one setting must be provided for update")
                Timber.e("Attempted to update multiple settings with no values for user: $userId")
                return Result.failure(error)
            }

            Timber.d("Updating multiple settings for user $userId: darkMode=$darkMode, notifications=$notifications")

            darkMode?.let { enabled ->
                val darkModeResult = settingsRepository.updateDarkMode(userId, enabled)
                if (darkModeResult.isFailure) {
                    Timber.e("Failed to update dark mode during multiple settings update for user: $userId")
                    return darkModeResult
                }
            }

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

    // ===== Weight Unit Operations (from UpdateWeightUnitPreferenceUseCase) =====

    /**
     * Updates the user's weight unit preference.
     * Replaces UpdateWeightUnitPreferenceUseCase.invoke()
     *
     * @param userId The ID of the user
     * @param weightUnit The new weight unit preference
     * @return Result<Unit> indicating success or failure
     */
    suspend fun updateWeightUnit(userId: String, weightUnit: WeightUnit): Result<Unit> {
        return try {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            settingsRepository.updateWeightUnit(userId, weightUnit)

        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(RuntimeException("Failed to update weight unit preference", e))
        }
    }

    // ===== User Preferences Operations (from UpdateUserPreferencesUseCase) =====

    /**
     * Updates the widget layout mode for the specified user.
     * Replaces UpdateUserPreferencesUseCase.updateLayoutMode()
     *
     * @param userId The unique identifier for the user
     * @param mode The new widget layout mode
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateLayoutMode(
        userId: String,
        mode: WidgetLayoutMode
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "layoutMode",
                    errorMessage = "Failed to update layout mode for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating layout mode for user: $userId to mode: $mode")

            val result = preferencesService.updateLayoutMode(userId, mode)
            result.getOrThrow()
        }
    }

    /**
     * Updates the user experience level and recalculates widget configurations.
     * Replaces UpdateUserPreferencesUseCase.updateUserLevel()
     *
     * @param userId The unique identifier for the user
     * @param level The new user experience level
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateUserLevel(
        userId: String,
        level: UserLevel
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "userLevel",
                    errorMessage = "Failed to update user level for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating user level for user: $userId to level: $level")

            val result = preferencesService.updateUserLevel(userId, level)
            result.getOrThrow()
        }
    }

    /**
     * Updates widget visibility for a specific widget.
     * Replaces UpdateUserPreferencesUseCase.updateWidgetVisibility()
     *
     * @param userId The unique identifier for the user
     * @param widgetName The name of the widget to update
     * @param visible Whether the widget should be visible
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetVisibility(
        userId: String,
        widgetName: String,
        visible: Boolean
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "widgetVisibility",
                    errorMessage = "Failed to update widget visibility for user $userId, widget $widgetName: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating widget visibility for user: $userId, widget: $widgetName, visible: $visible")

            val result = preferencesService.updateWidgetVisibility(userId, widgetName, visible)
            result.getOrThrow()
        }
    }

    /**
     * Updates the order of widgets in the dashboard.
     * Replaces UpdateUserPreferencesUseCase.updateWidgetOrder()
     *
     * @param userId The unique identifier for the user
     * @param widgetOrder List of widget names in the desired order
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetOrder(
        userId: String,
        widgetOrder: List<String>
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "widgetOrder",
                    errorMessage = "Failed to update widget order for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating widget order for user: $userId with ${widgetOrder.size} widgets")

            val result = preferencesService.updateWidgetOrder(userId, widgetOrder)
            result.getOrThrow()
        }
    }

    /**
     * Updates auto-refresh settings for widgets.
     * Replaces UpdateUserPreferencesUseCase.updateAutoRefreshSettings()
     *
     * @param userId The unique identifier for the user
     * @param enabled Whether auto-refresh should be enabled
     * @param intervalMinutes Refresh interval in minutes (1-60)
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateAutoRefreshSettings(
        userId: String,
        enabled: Boolean,
        intervalMinutes: Int
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "autoRefresh",
                    errorMessage = "Failed to update auto-refresh settings for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating auto-refresh settings for user: $userId, enabled: $enabled, interval: $intervalMinutes")

            if (intervalMinutes !in 1..60) {
                throw IllegalArgumentException("Refresh interval must be between 1 and 60 minutes")
            }

            val result = preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
            result.getOrThrow()
        }
    }

    /**
     * Toggles the collapsed state of a dashboard section.
     * Replaces UpdateUserPreferencesUseCase.toggleSection()
     *
     * @param userId The unique identifier for the user
     * @param sectionName The name of the section to toggle
     * @return LiftrixResult indicating success or failure
     */
    suspend fun toggleSection(
        userId: String,
        sectionName: String
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "sectionToggle",
                    errorMessage = "Failed to toggle section for user $userId, section $sectionName: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Toggling section for user: $userId, section: $sectionName")

            val result = preferencesService.toggleSection(userId, sectionName)
            result.getOrThrow()
        }
    }

    /**
     * Resets all user preferences to default values.
     * Replaces UpdateUserPreferencesUseCase.resetToDefaults()
     *
     * @param userId The unique identifier for the user
     * @return LiftrixResult indicating success or failure
     */
    suspend fun resetToDefaults(userId: String): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "resetDefaults",
                    errorMessage = "Failed to reset preferences to defaults for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Resetting preferences to defaults for user: $userId")

            val result = preferencesService.resetToDefaults(userId)
            result.getOrThrow()
        }
    }

    /**
     * Bulk updates multiple preference settings in a single atomic operation.
     * Replaces UpdateUserPreferencesUseCase.bulkUpdatePreferences()
     *
     * @param userId The unique identifier for the user
     * @param updates Map of preference updates to apply
     * @return LiftrixResult indicating success or failure
     */
    suspend fun bulkUpdatePreferences(
        userId: String,
        updates: Map<PreferenceType, Any>
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "bulkUpdate",
                    errorMessage = "Failed to bulk update preferences for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Bulk updating preferences for user: $userId with ${updates.size} updates")

            updates.forEach { (type, value) ->
                when (type) {
                    PreferenceType.LAYOUT_MODE -> {
                        if (value is WidgetLayoutMode) {
                            updateLayoutMode(userId, value).getOrThrow()
                        }
                    }
                    PreferenceType.USER_LEVEL -> {
                        if (value is UserLevel) {
                            updateUserLevel(userId, value).getOrThrow()
                        }
                    }
                    PreferenceType.WIDGET_ORDER -> {
                        if (value is List<*>) {
                            @Suppress("UNCHECKED_CAST")
                            updateWidgetOrder(userId, value as List<String>).getOrThrow()
                        }
                    }
                    PreferenceType.AUTO_REFRESH -> {
                        if (value is AutoRefreshSettings) {
                            updateAutoRefreshSettings(userId, value.enabled, value.intervalMinutes).getOrThrow()
                        }
                    }
                }
            }
        }
    }

    /**
     * Enum representing different types of user preferences.
     */
    enum class PreferenceType {
        LAYOUT_MODE,
        USER_LEVEL,
        WIDGET_ORDER,
        AUTO_REFRESH
    }

    /**
     * Data class for auto-refresh settings updates.
     */
    data class AutoRefreshSettings(
        val enabled: Boolean,
        val intervalMinutes: Int
    )
}
