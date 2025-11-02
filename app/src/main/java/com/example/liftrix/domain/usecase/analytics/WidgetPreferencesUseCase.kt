package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.service.WidgetOperationsService
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all widget preference operations.
 *
 * This use case consolidates functionality from:
 * - GetWidgetPreferencesUseCase (query operation)
 * - SaveWidgetPreferencesUseCase (save with validation)
 * - UpdateWidgetVisibilityUseCase (granular visibility control)
 * - ResetWidgetPreferencesUseCase (reset to defaults)
 *
 * **Pattern**: CQRS-lite with query (invoke) and command (save, updateVisibility, reset) separation.
 *
 * **Key Features**:
 * - Reactive widget preferences via Flow for real-time updates
 * - Automatic preference repair and validation via WidgetOperationsService
 * - Atomic operations with proper error handling
 * - User-scoped data access for privacy
 *
 * **Usage Example**:
 * ```kotlin
 * // Query preferences
 * widgetPreferencesUseCase(userId).collect { result ->
 *     result.fold(
 *         onSuccess = { preferences -> updateUI(preferences) },
 *         onFailure = { error -> handleError(error) }
 *     )
 * }
 *
 * // Save preferences
 * val result = widgetPreferencesUseCase.save(updatedPreferences)
 *
 * // Update visibility
 * widgetPreferencesUseCase.updateVisibility(userId, "volume_chart", visible = false)
 *
 * // Reset to defaults
 * widgetPreferencesUseCase.reset(userId, UserLevel.INTERMEDIATE)
 * ```
 *
 * @property repository Repository for widget preferences persistence
 * @property widgetOperationsService Service for widget business logic (repair, validation, migration)
 */
@Singleton
class WidgetPreferencesUseCase @Inject constructor(
    private val repository: WidgetPreferencesRepository,
    private val widgetOperationsService: WidgetOperationsService
) {

    /**
     * Gets widget preferences for a specific user as a reactive Flow.
     *
     * This method provides real-time updates of widget preferences, automatically
     * handling preference changes and validation. The returned Flow emits new
     * values whenever preferences are updated.
     *
     * **Query Operation**: Primary method for reading preferences (operator fun invoke).
     *
     * @param userId The unique identifier for the user
     * @return Flow emitting LiftrixResult with WidgetPreferences or error
     * @throws IllegalArgumentException if userId is blank
     */
    operator fun invoke(userId: String): Flow<LiftrixResult<WidgetPreferences>> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        Timber.d("Getting widget preferences for user: $userId")
        return repository.getWidgetPreferences(userId)
    }

    /**
     * Saves widget preferences with automatic repair and validation.
     *
     * This method:
     * 1. Repairs any consistency issues in preferences
     * 2. Validates the repaired preferences
     * 3. Persists to the repository
     *
     * **Command Operation**: Modifies preferences with full validation.
     *
     * @param preferences The widget preferences to save
     * @return LiftrixResult indicating success or failure with detailed error information
     */
    suspend fun save(preferences: WidgetPreferences): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SAVE_PREFERENCES_FAILED",
                errorMessage = "Failed to save widget preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "saveWidgetPreferences",
                    "userId" to preferences.userId
                )
            )
        }
    ) {
        Timber.d("Saving widget preferences for user: ${preferences.userId}")

        // Log original state for debugging
        Timber.d("Original - Visible: ${preferences.visibleWidgets.joinToString(", ")}, Order: ${preferences.widgetOrder.joinToString(", ")}")

        // Step 1: Repair any consistency issues
        val repaired = widgetOperationsService.repairPreferenceConsistency(preferences)

        // Log repaired state
        Timber.d("Repaired - Visible: ${repaired.visibleWidgets.joinToString(", ")}, Order: ${repaired.widgetOrder.joinToString(", ")}")

        // Step 2: Validate repaired preferences
        widgetOperationsService.validateWidgetConfiguration(repaired).getOrThrow()

        // Step 3: Log save details
        Timber.d("Dashboard layout: ${repaired.dashboardLayout}")
        Timber.d("User level: ${repaired.userLevel}")

        // Step 4: Perform the save operation
        repository.saveWidgetPreferences(repaired).getOrThrow()

        Timber.i("Widget preferences saved successfully for user: ${repaired.userId}")
    }

    /**
     * Updates the visibility of a specific widget for a user.
     *
     * This method toggles widget visibility while ensuring that at least one
     * widget remains visible on the dashboard. It provides immediate feedback
     * for user interface updates.
     *
     * **Command Operation**: Granular visibility control.
     *
     * @param userId The unique identifier for the user
     * @param widgetName Name of the widget to update
     * @param visible Whether the widget should be visible
     * @return LiftrixResult indicating success or failure
     * @throws IllegalArgumentException if userId or widgetName is blank
     */
    suspend fun updateVisibility(
        userId: String,
        widgetName: String,
        visible: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_VISIBILITY_FAILED",
                errorMessage = "Failed to update widget visibility: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "updateWidgetVisibility",
                    "userId" to userId,
                    "widgetName" to widgetName,
                    "visible" to visible.toString()
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(widgetName.isNotBlank()) { "Widget name cannot be blank" }

        Timber.d("Updating widget visibility for user $userId: $widgetName = $visible")

        // Update widget visibility through repository
        repository.updateWidgetVisibility(userId, widgetName, visible).getOrThrow()

        Timber.i("Widget visibility updated successfully for user $userId: $widgetName = $visible")
    }

    /**
     * Resets widget preferences to default values for a user.
     *
     * This method clears all current preferences and applies system defaults
     * based on the specified user level. If no user level is provided,
     * it defaults to BEGINNER configuration.
     *
     * **Command Operation**: Resets to defaults with optional level override.
     *
     * @param userId The unique identifier for the user
     * @param userLevel Optional user level to determine default configuration
     * @return LiftrixResult indicating success or failure
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun reset(
        userId: String,
        userLevel: UserLevel? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RESET_PREFERENCES_FAILED",
                errorMessage = "Failed to reset widget preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "resetWidgetPreferences",
                    "userId" to userId,
                    "userLevel" to (userLevel?.name ?: "DEFAULT")
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val targetLevel = userLevel ?: UserLevel.BEGINNER

        Timber.d("Resetting widget preferences to defaults for user $userId with level: $targetLevel")

        // Reset preferences through repository
        repository.resetToDefaults(userId, targetLevel).getOrThrow()

        Timber.i("Widget preferences reset to defaults successfully for user $userId")
    }
}
