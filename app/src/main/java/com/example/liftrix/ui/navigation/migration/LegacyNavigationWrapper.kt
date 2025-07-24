package com.example.liftrix.ui.navigation.migration

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import com.example.liftrix.ui.navigation.LiftrixRoute
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy Navigation Wrapper
 * 
 * Provides a compatibility wrapper that intercepts legacy navigation calls
 * and automatically converts them to type-safe LiftrixRoute navigation.
 * This wrapper maintains the same API as existing navigation code but
 * adds migration capabilities behind the scenes.
 * 
 * Key features:
 * - Transparent migration for existing navigation code
 * - Deprecation warnings for gradual migration guidance
 * - Fallback support for unsupported routes
 * - Analytics and progress tracking
 * - Zero-breaking-change transition path
 * 
 * Usage:
 * ```
 * // Instead of direct NavController usage in legacy code:
 * navController.navigate("home")
 * 
 * // Use wrapper (can be injected into existing components):
 * legacyWrapper.navigate("home", navController)
 * ```
 */
@Singleton
class LegacyNavigationWrapper @Inject constructor(
    private val migrationHelper: NavigationMigrationHelper
) {
    
    /**
     * Navigate using legacy string route with automatic migration
     * 
     * This method provides a drop-in replacement for legacy navigation calls.
     * It attempts to convert string routes to type-safe LiftrixRoute objects
     * and falls back to legacy navigation if conversion fails.
     * 
     * @param route The legacy string route to navigate to
     * @param navController The NavController to use for navigation
     * @param builder Optional NavOptionsBuilder for navigation options
     * 
     * @deprecated Use type-safe LiftrixRoute navigation instead
     */
    @Deprecated(
        message = "Use type-safe LiftrixRoute navigation instead. " +
                "Replace with: navController.navigate(LiftrixRoute.TargetScreen)",
        replaceWith = ReplaceWith(
            "navController.navigate(LiftrixRoute.TargetScreen)",
            "com.example.liftrix.ui.navigation.LiftrixRoute"
        ),
        level = DeprecationLevel.WARNING
    )
    fun navigate(
        route: String,
        navController: NavController,
        builder: (NavOptionsBuilder.() -> Unit)? = null
    ) {
        timber.log.Timber.w("🔄 LEGACY-WRAPPER: Legacy navigation call for route: '$route'")
        
        val liftrixRoute = migrationHelper.migrateStringRoute(route)
        
        if (liftrixRoute != null) {
            timber.log.Timber.i("🔄 LEGACY-WRAPPER: Successfully migrated '$route' to ${liftrixRoute::class.simpleName}")
            
            // Use type-safe navigation with original options
            if (builder != null) {
                navController.navigate(liftrixRoute, builder)
            } else {
                navController.navigate(liftrixRoute)
            }
        } else {
            timber.log.Timber.w("🔄 LEGACY-WRAPPER: Could not migrate '$route', using legacy navigation")
            
            // Fallback to legacy string navigation
            if (builder != null) {
                navController.navigate(route, builder)
            } else {
                navController.navigate(route)
            }
        }
    }
    
    /**
     * Navigate with single top behavior using legacy route
     * 
     * @deprecated Use navController.navigate(LiftrixRoute.TargetScreen) { launchSingleTop = true }
     */
    @Deprecated(
        message = "Use type-safe LiftrixRoute navigation with launchSingleTop = true",
        level = DeprecationLevel.WARNING
    )
    fun navigateSingleTop(route: String, navController: NavController) {
        navigate(route, navController) {
            launchSingleTop = true
        }
    }
    
    /**
     * Navigate and replace current screen using legacy route
     * 
     * @deprecated Use navController.navigate(LiftrixRoute.TargetScreen) with appropriate popUpTo configuration
     */
    @Deprecated(
        message = "Use type-safe LiftrixRoute navigation with popUpTo configuration",
        level = DeprecationLevel.WARNING
    )
    fun navigateAndReplace(route: String, navController: NavController) {
        navigate(route, navController) {
            popUpTo(navController.currentBackStackEntry?.destination?.route ?: "") {
                inclusive = true
            }
        }
    }
    
    /**
     * Navigate to main tabs with proper back stack management
     * 
     * This method handles the common pattern of main tab navigation with
     * proper state saving and restoration.
     * 
     * @deprecated Use specific navigation extension functions instead
     */
    @Deprecated(
        message = "Use specific navigation extension functions like navigateToHome(), navigateToWorkout(), etc.",
        level = DeprecationLevel.WARNING
    )
    fun navigateToMainTab(route: String, navController: NavController) {
        navigate(route, navController) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    /**
     * Provides compatibility check for legacy routes
     * 
     * This method can be used to validate whether a legacy route
     * can be successfully migrated before attempting navigation.
     * 
     * @param route The legacy string route to check
     * @return true if the route can be migrated, false if fallback required
     */
    fun canMigrateRoute(route: String): Boolean {
        return migrationHelper.migrateStringRoute(route) != null
    }
    
    /**
     * Gets migration suggestions for legacy routes
     * 
     * This method provides guidance on how to migrate specific legacy routes
     * to their type-safe LiftrixRoute equivalents.
     * 
     * @param route The legacy string route
     * @return Migration suggestion string or null if no specific guidance available
     */
    fun getMigrationSuggestion(route: String): String? {
        val liftrixRoute = migrationHelper.migrateStringRoute(route)
        return if (liftrixRoute != null) {
            when (liftrixRoute) {
                is LiftrixRoute.Home -> "Replace with: navController.navigate(LiftrixRoute.Home)"
                is LiftrixRoute.Workout -> "Replace with: navController.navigate(LiftrixRoute.Workout)"
                is LiftrixRoute.Progress -> "Replace with: navController.navigate(LiftrixRoute.Progress)"
                is LiftrixRoute.Coach -> "Replace with: navController.navigate(LiftrixRoute.Coach)"
                is LiftrixRoute.Friends -> "Replace with: navController.navigate(LiftrixRoute.Friends)"
                is LiftrixRoute.Settings -> "Replace with: navController.navigate(LiftrixRoute.Settings)"
                is LiftrixRoute.WidgetSettings -> "Replace with: navController.navigate(LiftrixRoute.WidgetSettings)"
                is LiftrixRoute.DashboardCustomization -> "Replace with: navController.navigate(LiftrixRoute.DashboardCustomization)"
                is LiftrixRoute.Onboarding -> "Replace with: navController.navigate(LiftrixRoute.Onboarding)"
                is LiftrixRoute.TemplateCreation -> "Replace with: navController.navigate(LiftrixRoute.TemplateCreation)"
                is LiftrixRoute.WorkoutDetails -> "Replace with: navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))"
                is LiftrixRoute.ExerciseSelection -> "Replace with: navController.navigate(LiftrixRoute.ExerciseSelection(...))"
                is LiftrixRoute.ActiveWorkout -> "Replace with: navController.navigate(LiftrixRoute.ActiveWorkout(...))"
                is LiftrixRoute.ExerciseDetails -> "Replace with: navController.navigate(LiftrixRoute.ExerciseDetails(exerciseId))"
                is LiftrixRoute.AnomalyDashboard -> "Replace with: navController.navigate(LiftrixRoute.AnomalyDashboard)"
                is LiftrixRoute.AnomalySettings -> "Replace with: navController.navigate(LiftrixRoute.AnomalySettings)"
                is LiftrixRoute.CalorieAnalytics -> "Replace with: navController.navigate(LiftrixRoute.CalorieAnalytics)"
                is LiftrixRoute.CalorieGoalSettings -> "Replace with: navController.navigate(LiftrixRoute.CalorieGoalSettings)"
                is LiftrixRoute.CalorieHistory -> "Replace with: navController.navigate(LiftrixRoute.CalorieHistory(...))"
                is LiftrixRoute.GuestModeSelection -> "Replace with: navController.navigate(LiftrixRoute.GuestModeSelection)"
                is LiftrixRoute.GuestDashboard -> "Replace with: navController.navigate(LiftrixRoute.GuestDashboard)"
                is LiftrixRoute.GuestConversion -> "Replace with: navController.navigate(LiftrixRoute.GuestConversion(...))"
                is LiftrixRoute.AuthSignUp -> "Replace with: navController.navigate(LiftrixRoute.AuthSignUp)"
                is LiftrixRoute.AuthSignIn -> "Replace with: navController.navigate(LiftrixRoute.AuthSignIn)"
                is LiftrixRoute.CreateWorkout -> "Replace with: navController.navigate(LiftrixRoute.CreateWorkout)"
                is LiftrixRoute.EditWorkout -> "Replace with: navController.navigate(LiftrixRoute.EditWorkout(workoutId))"
                is LiftrixRoute.EditSession -> "Replace with: navController.navigate(LiftrixRoute.EditSession(sessionId))"
            }
        } else {
            null
        }
    }
    
    /**
     * Logs comprehensive migration report
     * 
     * This method provides detailed migration analytics and suggestions
     * for completing the transition to type-safe navigation.
     */
    fun logMigrationReport() {
        timber.log.Timber.i("🔄 LEGACY-WRAPPER: Generating migration report...")
        
        val stats = migrationHelper.getMigrationStats()
        if (stats.isEmpty()) {
            timber.log.Timber.i("🔄 LEGACY-WRAPPER: No legacy navigation usage detected")
            return
        }
        
        timber.log.Timber.i("🔄 LEGACY-WRAPPER: Legacy navigation usage summary:")
        stats.entries.sortedByDescending { it.value }.forEach { (route, count) ->
            val suggestion = getMigrationSuggestion(route.replace("_FAILED", ""))
            val status = if (route.endsWith("_FAILED")) "NEEDS MAPPING" else "CONVERTIBLE"
            
            timber.log.Timber.i("🔄   Route: '$route' | Count: $count | Status: $status")
            if (suggestion != null) {
                timber.log.Timber.i("🔄   Suggestion: $suggestion")
            }
        }
        
        migrationHelper.logMigrationProgress()
    }
    
    /**
     * Provides migration status for the wrapper
     * 
     * @return true if legacy support is enabled, false if migration is complete
     */
    fun isLegacySupportEnabled(): Boolean {
        return migrationHelper.provideLegacySupport()
    }
}

/**
 * Extension functions to make legacy wrapper usage more convenient
 * 
 * These extensions can be temporarily added to existing classes that use
 * legacy navigation to provide an easy migration path.
 */

/**
 * Extension for NavController to use legacy wrapper
 * 
 * @deprecated Use type-safe LiftrixRoute navigation instead
 */
@Deprecated(
    message = "Use type-safe LiftrixRoute navigation instead",
    level = DeprecationLevel.WARNING
)
fun NavController.navigateWithLegacySupport(
    route: String,
    wrapper: LegacyNavigationWrapper,
    builder: (NavOptionsBuilder.() -> Unit)? = null
) {
    wrapper.navigate(route, this, builder)
}

/**
 * Utility class for managing migration state and providing guidance
 */
object MigrationGuidance {
    
    /**
     * Provides guidance for migrating from specific legacy navigation patterns
     */
    fun getPatternMigrationGuide(): Map<String, String> {
        return mapOf(
            "String route navigation" to "Use LiftrixRoute sealed classes with compile-time safety",
            "navController.navigate(\"route\")" to "navController.navigate(LiftrixRoute.Target)",
            "Parameterized string routes" to "Use data classes like LiftrixRoute.WorkoutDetails(id)",
            "Deep linking with strings" to "Use @Serializable LiftrixRoute with kotlinx.serialization",
            "Route constants" to "Replace with LiftrixRoute sealed class variants"
        )
    }
    
    /**
     * Provides common migration scenarios and solutions
     */
    fun getCommonMigrationScenarios(): Map<String, String> {
        return mapOf(
            "Main tab navigation" to "Use specific extension functions: navigateToHome(), navigateToWorkout(), etc.",
            "Parameterized navigation" to "Use type-safe data classes: LiftrixRoute.WorkoutDetails(workoutId)",
            "Conditional navigation" to "Use navigateIf() extension with LiftrixRoute",
            "Back stack management" to "Use clearBackStackAndNavigate() with LiftrixRoute",
            "Deep linking" to "Configure @Serializable routes with proper URI patterns"
        )
    }
}