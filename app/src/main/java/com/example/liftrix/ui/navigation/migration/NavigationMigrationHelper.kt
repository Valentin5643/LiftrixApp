package com.example.liftrix.ui.navigation.migration

import androidx.navigation.NavController
import com.example.liftrix.ui.navigation.LiftrixRoute
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navigation Migration Helper
 * 
 * Provides backward compatibility layer for gradual migration from string-based navigation
 * to type-safe LiftrixRoute sealed classes. This helper enables existing string navigation
 * calls to be gradually converted without breaking functionality.
 * 
 * Key features:
 * - String route to LiftrixRoute conversion
 * - Legacy navigation support with deprecation warnings
 * - Migration progress tracking and analytics
 * - Clean removal path for future
 * 
 * Usage:
 * ```
 * // Legacy (will show deprecation warning)
 * migrationHelper.navigateViaString("home")
 * 
 * // New (recommended)
 * navController.navigate(LiftrixRoute.Home)
 * ```
 */
@Singleton
class NavigationMigrationHelper @Inject constructor() {
    
    private var migrationStatsEnabled = true
    private val migrationStats = mutableMapOf<String, Int>()
    
    /**
     * Converts string-based routes to type-safe LiftrixRoute objects
     * 
     * This function handles the mapping from legacy string routes to their
     * corresponding LiftrixRoute sealed class variants. It supports both
     * simple routes and parameterized routes.
     * 
     * @param route The legacy string route to convert
     * @return LiftrixRoute object if conversion successful, null if unsupported
     */
    fun migrateStringRoute(route: String): LiftrixRoute? {
        return when {
            // Main navigation routes
            route == "home" -> LiftrixRoute.Home
            route == "workout" -> LiftrixRoute.Workout
            route == "progress" -> LiftrixRoute.Progress
            route == "coach" -> LiftrixRoute.Coach
            route == "friends" -> LiftrixRoute.Friends
            
            // Settings and onboarding
            route == "settings" -> LiftrixRoute.Settings
            route == "onboarding" -> LiftrixRoute.Onboarding
            
            // Template creation
            route == "template_creation" -> LiftrixRoute.TemplateCreation()
            
            // Exercise selection routes
            route == "exercise_selection" -> LiftrixRoute.ExerciseSelection()
            route == "exercise_selection_for_template" -> LiftrixRoute.ExerciseSelection(isForTemplate = true)
            route.startsWith("exercise_selection") -> parseExerciseSelectionRoute(route)
            
            // Parameterized routes - extract parameters from URL-style strings
            route.startsWith("unified_active_workout") -> parseActiveWorkoutRoute(route)
            route.startsWith("active_session") -> parseActiveSessionRoute(route)
            route.startsWith("workout_details") -> parseWorkoutDetailsRoute(route)
            route.startsWith("exercise_details") -> parseExerciseDetailsRoute(route)
            
            // Legacy routes that need special handling
            route == "blank_workout_session" -> LiftrixRoute.ActiveWorkout(isBlankWorkout = true)
            
            // Legacy home routes
            route.startsWith("home/") -> parseLegacyHomeRoute(route)
            
            // Legacy workout routes
            route.startsWith("workout_") -> parseLegacyWorkoutRoute(route)
            
            else -> {
                logUnknownRoute(route)
                null
            }
        }
    }
    
    /**
     * Provides backward compatibility navigation with deprecation warnings
     * 
     * @deprecated Use type-safe LiftrixRoute navigation instead
     * This method will be removed in a future version. Migrate to:
     * navController.navigate(LiftrixRoute.TargetScreen)
     */
    @Deprecated(
        message = "Use type-safe LiftrixRoute navigation instead",
        replaceWith = ReplaceWith(
            "navController.navigate(LiftrixRoute.TargetScreen)",
            "com.example.liftrix.ui.navigation.LiftrixRoute"
        ),
        level = DeprecationLevel.WARNING
    )
    fun navigateViaString(navController: NavController, route: String) {
        timber.log.Timber.w("🔄 MIGRATION: Legacy string navigation used: '$route'. Please migrate to LiftrixRoute.")
        
        val liftrixRoute = migrateStringRoute(route)
        if (liftrixRoute != null) {
            timber.log.Timber.i("🔄 MIGRATION: Successfully converted '$route' to ${liftrixRoute::class.simpleName}")
            navController.navigate(liftrixRoute)
            logMigrationUsage(route, success = true)
        } else {
            timber.log.Timber.e("🔄 MIGRATION: Failed to convert '$route' - falling back to legacy navigation")
            fallbackToLegacy(navController, route)
            logMigrationUsage(route, success = false)
        }
    }
    
    /**
     * Checks if legacy navigation support is currently enabled
     * 
     * This can be used to conditionally provide legacy support during migration
     * and can be disabled in future versions to force migration completion.
     */
    fun provideLegacySupport(): Boolean {
        return true // Currently enabled during migration period
    }
    
    /**
     * Logs migration progress and provides analytics data
     * 
     * This method tracks which routes are still using legacy navigation
     * and provides insights for migration completion tracking.
     */
    fun logMigrationProgress() {
        if (!migrationStatsEnabled) return
        
        val totalUsages = migrationStats.values.sum()
        if (totalUsages > 0) {
            timber.log.Timber.i("🔄 MIGRATION PROGRESS: $totalUsages legacy navigation calls tracked")
            timber.log.Timber.i("🔄 MIGRATION ROUTES: ${migrationStats.keys.joinToString(", ")}")
            
            // Log top legacy routes that need migration
            val topRoutes = migrationStats.entries.sortedByDescending { it.value }.take(5)
            topRoutes.forEach { (route, count) ->
                timber.log.Timber.w("🔄 MIGRATION NEEDED: '$route' used $count times")
            }
        }
    }
    
    /**
     * Enables or disables migration statistics tracking
     */
    fun setMigrationStatsEnabled(enabled: Boolean) {
        migrationStatsEnabled = enabled
    }
    
    /**
     * Gets current migration statistics
     * 
     * @return Map of route strings to usage counts
     */
    fun getMigrationStats(): Map<String, Int> {
        return migrationStats.toMap()
    }
    
    /**
     * Clears migration statistics
     */
    fun clearMigrationStats() {
        migrationStats.clear()
    }
    
    // MARK: - Private Helper Functions
    
    private fun parseActiveWorkoutRoute(route: String): LiftrixRoute.ActiveWorkout {
        val templateId = extractParameter(route, "templateId")
        val isBlankWorkout = extractBooleanParameter(route, "isBlankWorkout") ?: false
        
        return LiftrixRoute.ActiveWorkout(
            templateId = templateId?.takeIf { it.isNotBlank() && it != "null" },
            isBlankWorkout = isBlankWorkout
        )
    }
    
    private fun parseActiveSessionRoute(route: String): LiftrixRoute.ActiveWorkout {
        val templateId = extractParameter(route, "templateId")
        return LiftrixRoute.ActiveWorkout(
            templateId = templateId?.takeIf { it.isNotBlank() && it != "null" },
            isBlankWorkout = templateId == null
        )
    }
    
    private fun parseWorkoutDetailsRoute(route: String): LiftrixRoute.WorkoutDetails? {
        val workoutId = extractParameter(route, "workoutId")
        return if (workoutId != null) {
            LiftrixRoute.WorkoutDetails(workoutId)
        } else null
    }
    
    private fun parseExerciseDetailsRoute(route: String): LiftrixRoute.ExerciseDetails? {
        val exerciseId = extractParameter(route, "exerciseId")
        return if (exerciseId != null) {
            LiftrixRoute.ExerciseDetails(exerciseId)
        } else null
    }
    
    private fun parseLegacyHomeRoute(route: String): LiftrixRoute? {
        return when (route) {
            "home/main" -> LiftrixRoute.Home
            "home/friends" -> LiftrixRoute.Friends
            "home/settings" -> LiftrixRoute.Settings
            else -> null
        }
    }
    
    private fun parseLegacyWorkoutRoute(route: String): LiftrixRoute? {
        return when (route) {
            "workout_main" -> LiftrixRoute.Workout
            else -> null
        }
    }
    
    private fun parseExerciseSelectionRoute(route: String): LiftrixRoute.ExerciseSelection {
        val templateId = extractParameter(route, "templateId")
        val isForTemplate = extractBooleanParameter(route, "isForTemplate") ?: false
        
        return LiftrixRoute.ExerciseSelection(
            templateId = templateId?.takeIf { it.isNotBlank() && it != "null" },
            isForTemplate = isForTemplate
        )
    }
    
    private fun extractParameter(route: String, paramName: String): String? {
        // Handle URL parameter extraction for routes like "route?param=value"
        val paramPattern = "$paramName=([^&]*)"
        val regex = paramPattern.toRegex()
        val matchResult = regex.find(route)
        return matchResult?.groupValues?.get(1)?.let { value ->
            if (value == "null" || value.isBlank()) null else value
        }
    }
    
    private fun extractBooleanParameter(route: String, paramName: String): Boolean? {
        return extractParameter(route, paramName)?.toBooleanStrictOrNull()
    }
    
    private fun fallbackToLegacy(navController: NavController, route: String) {
        timber.log.Timber.w("🔄 MIGRATION: Using legacy navigation for unsupported route: '$route'")
        try {
            // Attempt legacy navigation as fallback
            navController.navigate(route)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "🔄 MIGRATION: Legacy navigation failed for route: '$route'")
        }
    }
    
    private fun logMigrationUsage(route: String, success: Boolean) {
        if (!migrationStatsEnabled) return
        
        val key = if (success) route else "${route}_FAILED"
        migrationStats[key] = migrationStats.getOrDefault(key, 0) + 1
        
        timber.log.Timber.d("🔄 MIGRATION STATS: Route '$route' ${if (success) "converted" else "failed"}")
    }
    
    private fun logUnknownRoute(route: String) {
        timber.log.Timber.w("🔄 MIGRATION: Unknown route '$route' - migration mapping needed")
        logMigrationUsage(route, success = false)
    }
}

/**
 * Extension function to provide convenient legacy navigation with deprecation warning
 * 
 * @deprecated Use type-safe LiftrixRoute navigation instead
 */
@Deprecated(
    message = "Use type-safe LiftrixRoute navigation instead",
    replaceWith = ReplaceWith(
        "navigate(LiftrixRoute.TargetScreen)",
        "com.example.liftrix.ui.navigation.LiftrixRoute"
    ),
    level = DeprecationLevel.WARNING
)
fun NavController.navigateWithMigration(
    route: String,
    migrationHelper: NavigationMigrationHelper
) {
    migrationHelper.navigateViaString(this, route)
}