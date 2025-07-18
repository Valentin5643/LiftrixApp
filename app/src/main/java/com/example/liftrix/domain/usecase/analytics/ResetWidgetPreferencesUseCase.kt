package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for resetting widget preferences to default values.
 * 
 * This use case handles resetting user widget preferences to system defaults,
 * typically used when users want to start fresh or when migrating from
 * legacy configurations. It ensures proper default values based on user level.
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class ResetWidgetPreferencesUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Resets widget preferences to default values for a user.
     * 
     * This method clears all current preferences and applies system defaults
     * based on the specified user level. If no user level is provided,
     * it defaults to BEGINNER configuration.
     * 
     * @param userId The unique identifier for the user
     * @param userLevel Optional user level to determine default configuration
     * @return LiftrixResult indicating success or failure
     */
    suspend operator fun invoke(
        userId: String,
        userLevel: UserLevel? = null
    ): LiftrixResult<Unit> {
        return try {
            // Validate input parameters
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            val targetLevel = userLevel ?: UserLevel.BEGINNER
            
            Timber.d("Resetting widget preferences to defaults for user $userId with level: $targetLevel")
            
            // Reset preferences through repository
            val result = widgetPreferencesRepository.resetToDefaults(userId, targetLevel)
            
            result.fold(
                onSuccess = {
                    Timber.i("Widget preferences reset to defaults successfully for user $userId")
                },
                onFailure = { error ->
                    Timber.e("Failed to reset widget preferences for user $userId: ${error.message}")
                }
            )
            result
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid parameters for widget preferences reset")
            Result.failure(LiftrixError.ValidationError(field = "resetPreferences", violations = listOf(e.message ?: "Invalid parameters")))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error resetting widget preferences for user: $userId")
            Result.failure(LiftrixError.UnknownError("Failed to reset widget preferences: ${e.message}"))
        }
    }
}