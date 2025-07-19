package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for fixing widget preference migration issues.
 * 
 * This use case specifically addresses the issue where widget preferences contain
 * legacy display names like "Progress summary" instead of proper enum names like "ProgressChart".
 * It applies the migration mapping and saves the corrected preferences permanently.
 * 
 * This is different from MigrateWidgetPreferencesUseCase which handles initial migration
 * for new users. This use case fixes existing preferences that have migration issues.
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class FixWidgetPreferenceMigrationUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Fixes widget preference migration for a user by converting old display names
     * to proper enum names and saving the corrected preferences.
     * 
     * This method:
     * 1. Loads current preferences (which applies migration automatically)
     * 2. Forces a repair to ensure consistency
     * 3. Saves the migrated preferences back to storage permanently
     * 
     * Example of fixes applied:
     * - "Progress summary" → "ProgressChart"
     * - "Total volume" → "TotalVolume"
     * - "Active time" → "AverageDuration"
     * - "Best streak" → "ConsistencyStreak"
     * - etc.
     * 
     * @param userId The unique identifier for the user whose preferences need fixing
     * @return LiftrixResult indicating success or failure of the migration fix
     */
    suspend operator fun invoke(userId: String): LiftrixResult<Unit> {
        return try {
            // Validate input
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            Timber.i("Applying widget preference migration fix for user: $userId")
            
            // Call the repository method to apply the migration fix
            val result = widgetPreferencesRepository.fixWidgetPreferenceMigration(userId)
            
            result.fold(
                onSuccess = {
                    Timber.i("Widget preference migration fix applied successfully for user: $userId")
                },
                onFailure = { error ->
                    Timber.e("Failed to apply widget preference migration fix for user $userId: ${error.message}")
                }
            )
            
            result
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid parameters for widget preference migration fix")
            Result.failure(LiftrixError.ValidationError(
                field = "userId", 
                violations = listOf(e.message ?: "Invalid user ID")
            ))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error fixing widget preference migration for user: $userId")
            Result.failure(LiftrixError.UnknownError(
                "Failed to fix widget preference migration: ${e.message}"
            ))
        }
    }
    
    /**
     * Checks if a user needs the migration fix by examining their preferences.
     * 
     * This method can be used to proactively detect users who have the migration issue
     * before they encounter problems in the UI.
     * 
     * @param userId The user identifier to check
     * @return Boolean indicating if the migration fix is needed
     */
    suspend fun needsMigrationFix(userId: String): Boolean {
        return try {
            var needsMigration = false
            
            // Get the current preferences
            widgetPreferencesRepository.getWidgetPreferences(userId)
                .collect { result ->
                    needsMigration = result.fold(
                        onSuccess = { preferences ->
                            // Check if any visible widgets contain legacy display names
                            val legacyNames = setOf(
                                "Progress summary", "Total volume", "Active time", "Best streak",
                                "Consistency", "Today's calories", "Calories burned", "Weekly calories",
                                "Volume trend", "Workout duration", "Frequency calendar"
                            )
                            
                            val hasLegacyNames = preferences.visibleWidgets.any { it in legacyNames }
                            if (hasLegacyNames) {
                                Timber.d("User $userId has legacy widget names that need migration")
                            }
                            hasLegacyNames
                        },
                        onFailure = { error ->
                            Timber.w("Could not check migration status for user $userId: ${error.message}")
                            true // Assume migration is needed if we can't check
                        }
                    )
                }
            
            needsMigration
        } catch (e: Exception) {
            Timber.e(e, "Error checking migration fix status for user: $userId")
            true // Err on the side of attempting migration
        }
    }
}