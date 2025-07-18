package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for updating individual widget visibility settings.
 * 
 * This use case provides granular control over widget visibility, allowing
 * users to show or hide specific widgets without affecting other preferences.
 * It includes validation to ensure at least one widget remains visible.
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class UpdateWidgetVisibilityUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Updates the visibility of a specific widget for a user.
     * 
     * This method toggles widget visibility while ensuring that at least one
     * widget remains visible on the dashboard. It provides immediate feedback
     * for user interface updates.
     * 
     * @param userId The unique identifier for the user
     * @param widgetName Name of the widget to update
     * @param visible Whether the widget should be visible
     * @return LiftrixResult indicating success or failure
     */
    suspend operator fun invoke(
        userId: String,
        widgetName: String,
        visible: Boolean
    ): LiftrixResult<Unit> {
        return try {
            // Validate input parameters
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(widgetName.isNotBlank()) { "Widget name cannot be blank" }
            
            Timber.d("Updating widget visibility for user $userId: $widgetName = $visible")
            
            // Update widget visibility through repository
            val result = widgetPreferencesRepository.updateWidgetVisibility(userId, widgetName, visible)
            
            result.fold(
                onSuccess = {
                    Timber.i("Widget visibility updated successfully for user $userId: $widgetName = $visible")
                },
                onFailure = { error ->
                    Timber.e("Failed to update widget visibility for user $userId: ${error.message}")
                }
            )
            result
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid parameters for widget visibility update")
            Result.failure(LiftrixError.ValidationError(field = "widgetVisibility", violations = listOf(e.message ?: "Invalid parameters")))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error updating widget visibility for user: $userId")
            Result.failure(LiftrixError.UnknownError("Failed to update widget visibility: ${e.message}"))
        }
    }
}