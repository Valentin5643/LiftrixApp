package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for saving user widget preferences.
 * 
 * This use case handles the business logic for persisting widget preferences,
 * including validation, error handling, and logging. It ensures that preferences
 * are valid before saving and provides detailed error information on failure.
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class SaveWidgetPreferencesUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Saves widget preferences for a user with validation.
     * 
     * This method validates the preferences before saving and ensures that
     * the configuration is valid and consistent. It handles all business
     * rules related to widget preferences.
     * 
     * @param preferences The widget preferences to save
     * @return LiftrixResult indicating success or failure with detailed error information
     */
    suspend operator fun invoke(preferences: WidgetPreferences): LiftrixResult<Unit> {
        return try {
            // Repair any consistency issues in preferences before validation
            val repairedPreferences = preferences.repairConsistency()
            
            // Log for debugging
            Timber.d("Original preferences - Visible: ${preferences.visibleWidgets.joinToString(", ")}, Order: ${preferences.widgetOrder.joinToString(", ")}")
            Timber.d("Repaired preferences - Visible: ${repairedPreferences.visibleWidgets.joinToString(", ")}, Order: ${repairedPreferences.widgetOrder.joinToString(", ")}")
            
            // Validate repaired preferences before saving
            repairedPreferences.validate()
            
            // Log the save operation for debugging
            Timber.d("Saving widget preferences for user: ${repairedPreferences.userId}")
            Timber.d("Visible widgets: ${repairedPreferences.visibleWidgets}")
            Timber.d("Dashboard layout: ${repairedPreferences.dashboardLayout}")
            Timber.d("User level: ${repairedPreferences.userLevel}")
            
            // Perform the save operation with repaired preferences
            val result = widgetPreferencesRepository.saveWidgetPreferences(repairedPreferences)
            
            result.fold(
                onSuccess = {
                    Timber.i("Widget preferences saved successfully for user: ${repairedPreferences.userId}")
                },
                onFailure = { error ->
                    Timber.e("Failed to save widget preferences for user: ${repairedPreferences.userId} - ${error.message}")
                }
            )
            result
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid widget preferences for user: ${preferences.userId} - ${e.message}")
            Result.failure(LiftrixError.ValidationError(field = "preferences", violations = listOf(e.message ?: "Invalid preferences")))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error saving widget preferences for user: ${preferences.userId}")
            Result.failure(LiftrixError.UnknownError("Failed to save widget preferences: ${e.message}"))
        }
    }
}