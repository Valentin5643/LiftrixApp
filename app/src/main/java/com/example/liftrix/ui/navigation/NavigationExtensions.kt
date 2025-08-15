package com.example.liftrix.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import com.example.liftrix.ui.animations.TransitionType

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
 * - Animated transitions with 300ms timing (Task ANIM-002)
 * - Athletic design principles with smooth screen transitions
 */

// MARK: - Core Navigation Functions

/**
 * Navigate to workout details screen with specific workout ID
 * 
 * @param workoutId The unique identifier for the workout
 * @param transitionType Animation transition type for screen change
 */
fun NavController.navigateToWorkoutDetails(
    workoutId: String,
    transitionType: TransitionType = TransitionType.VERTICAL
) {
    navigate(LiftrixRoute.WorkoutDetails(workoutId))
}

/**
 * Navigate to exercise selection screen
 * 
 * @param templateId Optional template ID when selecting exercises for template creation
 * @param isForTemplate Whether the selection is for template creation or active workout
 * @param replaceExerciseIndex Optional index of exercise to replace, null means add new
 */
fun NavController.navigateToExerciseSelection(
    templateId: String? = null,
    isForTemplate: Boolean = false,
    replaceExerciseIndex: Int? = null
) {
    navigate(LiftrixRoute.ExerciseSelection(templateId, isForTemplate, replaceExerciseIndex))
}

/**
 * Navigate to active workout session screen
 * 
 * @param templateId Optional template ID to start workout from template
 * @param isBlankWorkout Whether to start a blank workout without template
 */
fun NavController.navigateToActiveWorkout(
    templateId: String? = null,
    isBlankWorkout: Boolean = false,
    transitionType: TransitionType = TransitionType.ATHLETIC
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

// MARK: - Social Discovery Navigation

/**
 * Navigate to user search screen for discovering other users
 */
fun NavController.navigateToUserSearch() {
    navigate(LiftrixRoute.UserSearch)
}

/**
 * Navigate to public profile screen for viewing user profiles
 * 
 * @param userId Unique identifier for the user whose profile to display
 */
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(userId))
}

/**
 * Navigate to QR code display screen for profile sharing
 * 
 * @param userId Optional user ID for QR code generation (defaults to current user)
 */
fun NavController.navigateToQRCodeDisplay(userId: String? = null) {
    navigate(LiftrixRoute.QRCodeDisplay(userId))
}

/**
 * Navigate to profile screen - main profile display with achievements and settings
 * 
 * @param userId Optional user ID for viewing other users' profiles (defaults to current user)
 */
fun NavController.navigateToProfile(userId: String? = null) {
    navigate(LiftrixRoute.Profile(userId))
}

/**
 * Navigate to profile edit screen - comprehensive profile editing interface
 */
fun NavController.navigateToProfileEdit() {
    navigate(LiftrixRoute.ProfileEdit)
}

/**
 * Navigate to image crop screen for profile picture editing
 * 
 * @param imageUri URI of the image to crop (serialized as string)
 */
fun NavController.navigateToImageCrop(imageUri: String) {
    navigate(LiftrixRoute.ImageCrop(imageUri))
}

// MARK: - Social System Navigation Functions (Added for social system completion)

/**
 * Navigate to share workout screen for sharing workout sessions and routines
 * 
 * @param workoutId Unique identifier for the workout to share
 */
fun NavController.navigateToShareWorkout(workoutId: String) {
    navigate(LiftrixRoute.ShareWorkout(workoutId))
}

/**
 * Navigate to progress comparison screen for comparing workout progress between users
 * 
 * @param comparisonId Unique identifier for the comparison to display
 * @param shareMode Whether this comparison is being viewed in share mode (default: false)
 */
fun NavController.navigateToProgressComparison(
    comparisonId: String, 
    shareMode: Boolean = false
) {
    navigate(LiftrixRoute.ProgressComparison(comparisonId, shareMode))
}

/**
 * Navigate to social feed screen showing workout posts and social interactions
 * 
 * @param initialTab Initial tab to display in the feed (default: "HOME")
 */
fun NavController.navigateToSocialFeed(initialTab: String = "HOME") {
    navigate(LiftrixRoute.SocialFeed(initialTab))
}

/**
 * Navigate to notification settings screen for managing social notifications
 */
fun NavController.navigateToNotificationSettings() {
    navigate(LiftrixRoute.NotificationSettings)
}

/**
 * Navigate to gym buddy screen for QR code pairing and gym partner connections
 */
fun NavController.navigateToGymBuddy() {
    navigate(LiftrixRoute.GymBuddy)
}

/**
 * Navigate to post creation screen for creating and sharing workout posts
 * 
 * @param workoutId Unique identifier for the workout to create a post from
 */
fun NavController.navigateToPostCreation(workoutId: String) {
    navigate(LiftrixRoute.PostCreation(workoutId))
}

/**
 * Navigate to post comments screen for viewing and managing post comments
 * 
 * @param postId Unique identifier for the post to view comments for
 */
fun NavController.navigateToPostComments(postId: String) {
    navigate(LiftrixRoute.PostComments(postId))
}

// MARK: - Feature Navigation

/**
 * Navigate to template creation screen
 */
fun NavController.navigateToTemplateCreation() {
    navigate(LiftrixRoute.TemplateCreation)
}

/**
 * Navigate to create workout screen (updated terminology from template creation)
 * 
 * @param transitionType Animation transition type for screen change
 */
fun NavController.navigateToCreateWorkout(
    folderId: String? = null,
    transitionType: TransitionType = TransitionType.MODAL
) {
    navigate(LiftrixRoute.CreateWorkout(folderId = folderId))
}

/**
 * Navigate to edit workout routine screen
 * 
 * @param workoutId Unique identifier for the workout routine to edit
 */
fun NavController.navigateToEditWorkout(workoutId: String) {
    timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: Navigation - navigateToEditWorkout called with workoutId: '$workoutId'")
    timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: Navigation - workoutId length: ${workoutId.length}, isBlank: ${workoutId.isBlank()}")
    navigate(LiftrixRoute.EditWorkout(workoutId))
}

/**
 * Navigate to edit workout session screen  
 * 
 * @param sessionId Unique identifier for the workout session to edit
 */
fun NavController.navigateToEditSession(sessionId: String) {
    navigate(LiftrixRoute.EditSession(sessionId))
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

// MARK: - Analytics Detail Navigation

/**
 * Navigate to volume analysis detail screen
 */
fun NavController.navigateToVolumeAnalysisDetail() {
    navigate(LiftrixRoute.VolumeAnalysisDetail)
}

/**
 * Navigate to one rep max detail screen
 */
fun NavController.navigateToOneRmDetail() {
    navigate(LiftrixRoute.OneRmDetail)
}

/**
 * Navigate to muscle group detail screen
 */
fun NavController.navigateToMuscleGroupDetail() {
    navigate(LiftrixRoute.MuscleGroupDetail)
}

/**
 * Navigate to workout frequency detail screen
 */
fun NavController.navigateToWorkoutFrequencyDetail() {
    navigate(LiftrixRoute.WorkoutFrequencyDetail)
}

/**
 * Navigate to exercise ranking detail screen
 */
fun NavController.navigateToExerciseRankingDetail() {
    navigate(LiftrixRoute.ExerciseRankingDetail)
}

// MARK: - Folder Management Navigation


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

// MARK: - Animated Navigation Extensions (Task ANIM-002)

/**
 * Navigate with animated transitions using specified transition type
 * 
 * @param route The target route to navigate to
 * @param transitionType Animation transition type for screen change
 * @param clearStack Whether to clear the back stack
 */
fun NavController.navigateAnimated(
    route: LiftrixRoute,
    transitionType: TransitionType = TransitionType.VERTICAL,
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

/**
 * Navigate to workout details with card transition animation
 * Provides smooth card-to-detail transition experience
 */
fun NavController.navigateToWorkoutDetailsAnimated(workoutId: String) {
    navigateAnimated(
        route = LiftrixRoute.WorkoutDetails(workoutId),
        transitionType = TransitionType.CARD
    )
}

/**
 * Navigate to active workout with athletic transition
 * Enhanced animation for workout start experience
 */
fun NavController.navigateToActiveWorkoutAnimated(
    templateId: String? = null,
    isBlankWorkout: Boolean = false
) {
    navigateAnimated(
        route = LiftrixRoute.ActiveWorkout(templateId, isBlankWorkout),
        transitionType = TransitionType.ATHLETIC
    )
}

/**
 * Navigate to create workout with modal transition
 * Bottom-up modal animation for creation flow
 */
fun NavController.navigateToCreateWorkoutAnimated(folderId: String? = null) {
    navigateAnimated(
        route = LiftrixRoute.CreateWorkout(folderId = folderId),
        transitionType = TransitionType.MODAL
    )
}

/**
 * Navigate to profile screen with card transition animation
 * Enhanced animation for profile viewing experience
 * 
 * @param userId Optional user ID for viewing other users' profiles
 */
fun NavController.navigateToProfileAnimated(userId: String? = null) {
    navigateAnimated(
        route = LiftrixRoute.Profile(userId),
        transitionType = TransitionType.CARD
    )
}

/**
 * Navigate to profile edit screen with vertical transition
 * Smooth upward animation for editing flow
 */
fun NavController.navigateToProfileEditAnimated() {
    navigateAnimated(
        route = LiftrixRoute.ProfileEdit,
        transitionType = TransitionType.VERTICAL
    )
}

/**
 * Navigate to image crop screen with modal transition
 * Modal-style animation for image editing workflow
 * 
 * @param imageUri URI of the image to crop
 */
fun NavController.navigateToImageCropAnimated(imageUri: String) {
    navigateAnimated(
        route = LiftrixRoute.ImageCrop(imageUri),
        transitionType = TransitionType.MODAL
    )
}

/**
 * Navigate with horizontal forward transition
 * For progressive navigation flows
 */
fun NavController.navigateForwardAnimated(route: LiftrixRoute) {
    navigateAnimated(
        route = route,
        transitionType = TransitionType.HORIZONTAL_FORWARD
    )
}

/**
 * Navigate with horizontal back transition
 * For return navigation flows
 */
fun NavController.navigateBackAnimated(route: LiftrixRoute) {
    navigateAnimated(
        route = route,
        transitionType = TransitionType.HORIZONTAL_BACK
    )
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
        isCurrentRoute(LiftrixRoute.TemplateCreation::class) -> LiftrixRoute.TemplateCreation()
        isCurrentRoute(LiftrixRoute.CreateWorkout::class) -> LiftrixRoute.CreateWorkout()
        isCurrentRoute(LiftrixRoute.Settings::class) -> LiftrixRoute.Settings
        isCurrentRoute(LiftrixRoute.Onboarding::class) -> LiftrixRoute.Onboarding
        isCurrentRoute(LiftrixRoute.DashboardCustomization::class) -> LiftrixRoute.DashboardCustomization
        isCurrentRoute(LiftrixRoute.AnomalyDashboard::class) -> LiftrixRoute.AnomalyDashboard
        isCurrentRoute(LiftrixRoute.AnomalySettings::class) -> LiftrixRoute.AnomalySettings
        isCurrentRoute(LiftrixRoute.GuestModeSelection::class) -> LiftrixRoute.GuestModeSelection
        isCurrentRoute(LiftrixRoute.GuestDashboard::class) -> LiftrixRoute.GuestDashboard
        isCurrentRoute(LiftrixRoute.AuthSignUp::class) -> LiftrixRoute.AuthSignUp
        isCurrentRoute(LiftrixRoute.AuthSignIn::class) -> LiftrixRoute.AuthSignIn
        isCurrentRoute(LiftrixRoute.ProfileEdit::class) -> LiftrixRoute.ProfileEdit
        else -> null // For parameterized routes like Profile(userId), EditWorkout(workoutId), EditSession(sessionId), would need more complex logic
    } as LiftrixRoute?
}