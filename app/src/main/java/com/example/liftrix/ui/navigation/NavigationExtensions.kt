package com.example.liftrix.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder

/**
 * Type-Safe Navigation Extension Functions
 * 
 * This file provides clean, type-safe extension functions for NavController to simplify
 * navigation throughout the Liftrix application using LiftrixRoute sealed classes.
 * 
 * Key features:
 * - Type-safe navigation with compile-time validation
 * - Consistent navigation patterns across the app
 * - Safe back stack management
 * - Support for deep linking and external navigation
 * - Error prevention through type safety
 */

// MARK: - Core Navigation Functions

/**
 * Navigate to workout details screen with specific workout ID
 */
fun NavController.navigateToWorkoutDetails(workoutId: String) {
    navigate(LiftrixRoute.WorkoutDetails(workoutId))
}

/**
 * Navigate to exercise selection screen
 * 
 * @param templateId Optional template ID when selecting exercises for template creation
 * @param isForTemplate Whether the selection is for template creation or active workout
 */
fun NavController.navigateToExerciseSelection(
    templateId: String? = null,
    isForTemplate: Boolean = false
) {
    navigate(LiftrixRoute.ExerciseSelection(templateId, isForTemplate))
}

/**
 * Navigate to active workout session screen
 * 
 * @param templateId Optional template ID to start workout from template
 * @param isBlankWorkout Whether to start a blank workout without template
 */
fun NavController.navigateToActiveWorkout(
    templateId: String? = null,
    isBlankWorkout: Boolean = false
) {
    navigate(LiftrixRoute.ActiveWorkout(templateId, isBlankWorkout))
}

/**
 * Navigate to exercise details screen
 */
fun NavController.navigateToExerciseDetails(exerciseId: String) {
    navigate(LiftrixRoute.ExerciseDetails(exerciseId))
}

// MARK: - Main Tab Navigation

/**
 * Navigate to home screen
 */
fun NavController.navigateToHome() {
    navigate(LiftrixRoute.Home) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigate to workout screen
 */
fun NavController.navigateToWorkout() {
    navigate(LiftrixRoute.Workout) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigate to progress dashboard screen
 */
fun NavController.navigateToProgress() {
    navigate(LiftrixRoute.Progress) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigate to coach screen
 */
fun NavController.navigateToCoach() {
    navigate(LiftrixRoute.Coach) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigate to friends/social screen
 */
fun NavController.navigateToFriends() {
    navigate(LiftrixRoute.Friends)
}

// MARK: - Feature Navigation

/**
 * Navigate to template creation screen
 */
fun NavController.navigateToTemplateCreation() {
    navigate(LiftrixRoute.TemplateCreation)
}

/**
 * Navigate to settings screen
 */
fun NavController.navigateToSettings() {
    navigate(LiftrixRoute.Settings)
}

/**
 * Navigate to onboarding screen
 */
fun NavController.navigateToOnboarding() {
    navigate(LiftrixRoute.Onboarding)
}

/**
 * Navigate to dashboard customization screen
 */
fun NavController.navigateToDashboardCustomization() {
    navigate(LiftrixRoute.DashboardCustomization)
}

/**
 * Navigate to anomaly detection dashboard
 */
fun NavController.navigateToAnomalyDashboard() {
    navigate(LiftrixRoute.AnomalyDashboard)
}

/**
 * Navigate to anomaly detection settings
 */
fun NavController.navigateToAnomalySettings() {
    navigate(LiftrixRoute.AnomalySettings)
}

/**
 * Navigate to guest mode selection screen
 */
fun NavController.navigateToGuestModeSelection() {
    navigate(LiftrixRoute.GuestModeSelection)
}

/**
 * Navigate to guest dashboard
 */
fun NavController.navigateToGuestDashboard() {
    navigate(LiftrixRoute.GuestDashboard)
}

/**
 * Navigate to guest conversion screen
 * 
 * @param source The source that triggered the conversion
 * @param returnTo Optional route to return to after conversion
 */
fun NavController.navigateToGuestConversion(
    source: String = "manual",
    returnTo: String? = null
) {
    navigate(LiftrixRoute.GuestConversion(source, returnTo))
}

/**
 * Navigate to sign-up screen
 */
fun NavController.navigateToAuthSignUp() {
    navigate(LiftrixRoute.AuthSignUp)
}

/**
 * Navigate to sign-in screen
 */
fun NavController.navigateToAuthSignIn() {
    navigate(LiftrixRoute.AuthSignIn)
}

// MARK: - Navigation Utilities

/**
 * Safely pop back stack with null check
 * 
 * This function prevents crashes when trying to pop an empty back stack
 * and provides a consistent way to handle back navigation throughout the app.
 */
fun NavController.popBackStackSafely(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

/**
 * Clear entire back stack and navigate to a specific route
 * 
 * This is useful for scenarios like logout, onboarding completion,
 * or when you want to reset the navigation state completely.
 * 
 * @param route The target route to navigate to after clearing back stack
 */
fun NavController.clearBackStackAndNavigate(route: LiftrixRoute) {
    navigate(route) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

/**
 * Navigate with single top behavior
 * 
 * Prevents multiple instances of the same screen in the back stack.
 * Useful for navigation from notifications or deep links.
 * 
 * @param route The target route to navigate to
 */
fun NavController.navigateSingleTop(route: LiftrixRoute) {
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * Navigate and replace current screen
 * 
 * This removes the current screen from the back stack and navigates to the new route.
 * Useful for flows where you don't want users to return to the previous screen.
 * 
 * @param route The target route to navigate to
 */
fun NavController.navigateAndReplace(route: LiftrixRoute) {
    navigate(route) {
        popUpTo(currentBackStackEntry?.destination?.route ?: "") {
            inclusive = true
        }
    }
}

// MARK: - Deep Linking Support

/**
 * Navigate from external deep link or intent
 * 
 * This function handles navigation from external sources like notifications,
 * widgets, or other apps. It ensures proper back stack management for deep links.
 * 
 * @param route The target route from deep link
 * @param clearStack Whether to clear the back stack (default: false)
 */
fun NavController.navigateFromDeepLink(
    route: LiftrixRoute,
    clearStack: Boolean = false
) {
    navigate(route) {
        if (clearStack) {
            popUpTo(0) {
                inclusive = true
            }
        }
        launchSingleTop = true
    }
}

// MARK: - Conditional Navigation

/**
 * Navigate with condition check
 * 
 * Only navigates if a condition is met. Useful for navigation that depends
 * on authentication state, permissions, or other app state.
 * 
 * @param route The target route to navigate to
 * @param condition The condition that must be true to navigate
 * @param onConditionFailed Optional callback when condition fails
 */
fun NavController.navigateIf(
    route: LiftrixRoute,
    condition: Boolean,
    onConditionFailed: (() -> Unit)? = null
) {
    if (condition) {
        navigate(route)
    } else {
        onConditionFailed?.invoke()
    }
}

/**
 * Navigate to route if not already there
 * 
 * Prevents unnecessary navigation to the same screen. Checks if the current
 * destination is already the target route before navigating.
 * 
 * @param route The target route to navigate to
 */
fun NavController.navigateIfNotCurrent(route: LiftrixRoute) {
    val currentRoute = currentBackStackEntry?.destination?.route
    val targetRoute = route::class.simpleName
    
    if (currentRoute?.contains(targetRoute.orEmpty()) != true) {
        navigate(route)
    }
}

// MARK: - Navigation State Helpers

/**
 * Check if currently on a specific route type
 * 
 * @param routeClass The route class to check against
 * @return true if currently on the specified route type
 */
fun NavController.isCurrentRoute(routeClass: kotlin.reflect.KClass<out LiftrixRoute>): Boolean {
    val currentRoute = currentBackStackEntry?.destination?.route
    return currentRoute?.contains(routeClass.simpleName.orEmpty()) == true
}

/**
 * Check if back navigation is available
 * 
 * @return true if there are screens in the back stack to navigate to
 */
fun NavController.canNavigateUp(): Boolean {
    return previousBackStackEntry != null
}

/**
 * Get current route as LiftrixRoute if possible
 * 
 * This is a utility function to get the current route in a type-safe way.
 * Returns null if the current route cannot be determined or converted.
 * 
 * Note: This is a simplified implementation. In a real app, you might want
 * to use more sophisticated route tracking mechanisms.
 */
fun NavController.getCurrentLiftrixRoute(): LiftrixRoute? {
    // This is a simplified implementation - in practice you might want
    // to implement more sophisticated current route tracking
    return when {
        isCurrentRoute(LiftrixRoute.Home::class) -> LiftrixRoute.Home
        isCurrentRoute(LiftrixRoute.Workout::class) -> LiftrixRoute.Workout
        isCurrentRoute(LiftrixRoute.Progress::class) -> LiftrixRoute.Progress
        isCurrentRoute(LiftrixRoute.Coach::class) -> LiftrixRoute.Coach
        isCurrentRoute(LiftrixRoute.Friends::class) -> LiftrixRoute.Friends
        isCurrentRoute(LiftrixRoute.TemplateCreation::class) -> LiftrixRoute.TemplateCreation
        isCurrentRoute(LiftrixRoute.Settings::class) -> LiftrixRoute.Settings
        isCurrentRoute(LiftrixRoute.Onboarding::class) -> LiftrixRoute.Onboarding
        isCurrentRoute(LiftrixRoute.DashboardCustomization::class) -> LiftrixRoute.DashboardCustomization
        isCurrentRoute(LiftrixRoute.AnomalyDashboard::class) -> LiftrixRoute.AnomalyDashboard
        isCurrentRoute(LiftrixRoute.AnomalySettings::class) -> LiftrixRoute.AnomalySettings
        isCurrentRoute(LiftrixRoute.GuestModeSelection::class) -> LiftrixRoute.GuestModeSelection
        isCurrentRoute(LiftrixRoute.GuestDashboard::class) -> LiftrixRoute.GuestDashboard
        isCurrentRoute(LiftrixRoute.AuthSignUp::class) -> LiftrixRoute.AuthSignUp
        isCurrentRoute(LiftrixRoute.AuthSignIn::class) -> LiftrixRoute.AuthSignIn
        else -> null // For parameterized routes, would need more complex logic
    }
}