package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for migrating existing users to widget preferences system.
 * 
 * This use case handles the migration of existing users who don't have widget
 * preferences configured yet. It analyzes their usage patterns and creates
 * appropriate default configurations based on their experience level.
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class MigrateWidgetPreferencesUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Migrates existing user to widget preferences system.
     * 
     * This method analyzes user characteristics and creates appropriate
     * widget preferences. It's designed to be called once for each existing
     * user when they first encounter the new preferences system.
     * 
     * @param userId The unique identifier for the user
     * @param totalWorkouts Total number of workouts completed by user
     * @param daysSinceFirstWorkout Days since user's first workout
     * @param legacyPreferences Any existing preference data to consider
     * @return LiftrixResult indicating success or failure
     */
    suspend operator fun invoke(
        userId: String,
        totalWorkouts: Int = 0,
        daysSinceFirstWorkout: Int = 0,
        legacyPreferences: Map<String, Any>? = null
    ): LiftrixResult<Unit> {
        return try {
            // Validate input parameters
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(totalWorkouts >= 0) { "Total workouts cannot be negative" }
            require(daysSinceFirstWorkout >= 0) { "Days since first workout cannot be negative" }
            
            Timber.d("Migrating widget preferences for user $userId (workouts: $totalWorkouts, days: $daysSinceFirstWorkout)")
            
            // Determine appropriate user level based on experience
            val userLevel = determineUserLevel(totalWorkouts, daysSinceFirstWorkout, legacyPreferences)
            
            // Create migration-specific preferences
            val migratedPreferences = createMigratedPreferences(
                userId = userId,
                userLevel = userLevel,
                legacyPreferences = legacyPreferences
            )
            
            // Save migrated preferences
            val result = widgetPreferencesRepository.saveWidgetPreferences(migratedPreferences)
            
            result.fold(
                onSuccess = {
                    Timber.i("Widget preferences migrated successfully for user $userId with level: $userLevel")
                },
                onFailure = { error ->
                    Timber.e("Failed to migrate widget preferences for user $userId: ${error.message}")
                }
            )
            result
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid parameters for widget preferences migration")
            Result.failure(LiftrixError.ValidationError(field = "migration", violations = listOf(e.message ?: "Invalid parameters")))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error migrating widget preferences for user: $userId")
            Result.failure(LiftrixError.UnknownError("Failed to migrate widget preferences: ${e.message}"))
        }
    }
    
    /**
     * Determines appropriate user level based on usage history.
     */
    private fun determineUserLevel(
        totalWorkouts: Int,
        daysSinceFirstWorkout: Int,
        legacyPreferences: Map<String, Any>?
    ): UserLevel {
        // Check for explicit level in legacy preferences
        legacyPreferences?.get("userLevel")?.let { level ->
            when (level.toString().lowercase()) {
                "intermediate" -> return UserLevel.INTERMEDIATE
                "advanced" -> return UserLevel.ADVANCED
                "beginner" -> return UserLevel.BEGINNER
            }
        }
        
        // Determine level based on usage patterns
        return when {
            // Advanced users: Long-term consistent users
            totalWorkouts >= 100 && daysSinceFirstWorkout >= 180 -> UserLevel.ADVANCED
            
            // Intermediate users: Regular users with good consistency
            totalWorkouts >= 30 && daysSinceFirstWorkout >= 60 -> UserLevel.INTERMEDIATE
            
            // Intermediate users: High frequency users regardless of total time
            totalWorkouts >= 50 -> UserLevel.INTERMEDIATE
            
            // Everyone else starts as beginner
            else -> UserLevel.BEGINNER
        }
    }
    
    /**
     * Creates migrated preferences with intelligent defaults.
     */
    private fun createMigratedPreferences(
        userId: String,
        userLevel: UserLevel,
        legacyPreferences: Map<String, Any>?
    ): WidgetPreferences {
        // Start with standard defaults for the user level
        val basePreferences = WidgetPreferences.createDefault(userId, userLevel)
        
        // Apply any legacy preference overrides
        return legacyPreferences?.let { legacy ->
            applyLegacyPreferences(basePreferences, legacy)
        } ?: basePreferences
    }
    
    /**
     * Applies legacy preferences to new preference structure.
     */
    private fun applyLegacyPreferences(
        basePreferences: WidgetPreferences,
        legacyPreferences: Map<String, Any>
    ): WidgetPreferences {
        var preferences = basePreferences
        
        // Apply legacy widget visibility if available
        legacyPreferences["visibleWidgets"]?.let { widgets ->
            if (widgets is List<*>) {
                val widgetNames = widgets.mapNotNull { it as? String }.toSet()
                if (widgetNames.isNotEmpty()) {
                    preferences = preferences.updateVisibleWidgets(widgetNames)
                }
            }
        }
        
        // Apply legacy layout mode if available
        legacyPreferences["dashboardLayout"]?.let { layout ->
            val layoutMode = when (layout.toString().lowercase()) {
                "grid" -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.AUTO
                "list" -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.COMPACT
                "custom" -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.CUSTOM
                else -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.AUTO
            }
            preferences = preferences.updateLayout(layoutMode)
        }
        
        // Apply legacy refresh settings if available
        legacyPreferences["autoRefresh"]?.let { enabled ->
            if (enabled is Boolean) {
                val interval = (legacyPreferences["refreshInterval"] as? Number)?.toInt() ?: 5
                preferences = preferences.updateAutoRefresh(enabled, interval)
            }
        }
        
        return preferences
    }
    
    /**
     * Checks if user needs migration (doesn't have preferences yet).
     * 
     * @param userId The user identifier to check
     * @return Boolean indicating if migration is needed
     */
    suspend fun needsMigration(userId: String): Boolean {
        return try {
            // This would typically check if preferences exist
            // For now, we'll assume migration is needed if this method is called
            true
        } catch (e: Exception) {
            Timber.e(e, "Error checking migration status for user: $userId")
            true // Err on the side of attempting migration
        }
    }
}