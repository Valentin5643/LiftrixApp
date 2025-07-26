package com.example.liftrix.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe route definitions for Liftrix navigation
 * 
 * This sealed class hierarchy replaces string-based navigation with compile-time
 * type safety and kotlinx.serialization support for deep linking and state restoration.
 * 
 * Key features:
 * - Compile-time route validation prevents runtime navigation errors
 * - Type-safe parameters eliminate string manipulation for navigation arguments
 * - Automatic serialization/deserialization for deep linking support
 * - Clear route hierarchy enables easy navigation maintenance
 */
@Serializable
sealed class LiftrixRoute {
    
    /**
     * Home screen - main dashboard with workout feed and discovery
     */
    @Serializable
    data object Home : LiftrixRoute()
    
    /**
     * Workout screen - workout routines and session management
     */
    @Serializable
    data object Workout : LiftrixRoute()
    
    /**
     * Progress dashboard - analytics and workout history
     */
    @Serializable
    data object Progress : LiftrixRoute()
    
    /**
     * Coach screen - AI guidance and workout recommendations
     */
    @Serializable
    data object Coach : LiftrixRoute()
    
    /**
     * Friends/Social screen - social features and sharing
     */
    @Serializable
    data object Friends : LiftrixRoute()
    
    // Social Discovery Routes
    
    /**
     * User search screen for discovering and connecting with other users
     */
    @Serializable
    data object UserSearch : LiftrixRoute()
    
    /**
     * Public profile display screen showing privacy-aware user information
     * 
     * @param userId Unique identifier for the user whose profile to display
     */
    @Serializable
    data class PublicProfile(val userId: String) : LiftrixRoute()
    
    /**
     * QR code display screen for profile sharing
     * 
     * @param userId Unique identifier for the user whose QR code to generate (defaults to current user)
     */
    @Serializable
    data class QRCodeDisplay(val userId: String? = null) : LiftrixRoute()
    
    /**
     * Workout details screen with specific workout ID
     * 
     * @param workoutId Unique identifier for the workout to display
     */
    @Serializable
    data class WorkoutDetails(val workoutId: String) : LiftrixRoute()
    
    /**
     * Exercise selection screen for adding exercises to workout or routine
     * 
     * @param templateId Optional workout routine ID when selecting exercises for routine creation (backend compatibility)
     * @param isForTemplate Whether the selection is for routine creation or active workout (backend compatibility)
     */
    @Serializable
    data class ExerciseSelection(
        val templateId: String? = null,
        val isForTemplate: Boolean = false
    ) : LiftrixRoute()
    
    /**
     * Active workout session screen
     * 
     * @param templateId Optional workout routine ID to start workout from saved routine (backend compatibility)
     * @param isBlankWorkout Whether to start a blank workout without saved routine
     */
    @Serializable
    data class ActiveWorkout(
        val templateId: String? = null,
        val isBlankWorkout: Boolean = false
    ) : LiftrixRoute()
    
    /**
     * Template creation screen for creating new workout routines
     * @deprecated Use CreateWorkout instead for user-friendly workflow terminology
     */
    @Deprecated("Use CreateWorkout instead", ReplaceWith("LiftrixRoute.CreateWorkout"))
    @Serializable
    data object TemplateCreation : LiftrixRoute()
    
    /**
     * Workout creation screen for creating new workout routines (user-friendly "Creating a workout")
     */
    @Serializable
    data object CreateWorkout : LiftrixRoute()
    
    /**
     * Exercise details screen showing specific exercise information
     * 
     * @param exerciseId Unique identifier for the exercise to display
     */
    @Serializable
    data class ExerciseDetails(val exerciseId: String) : LiftrixRoute()
    
    /**
     * Settings screen for user preferences and app configuration
     */
    @Serializable
    data object Settings : LiftrixRoute()
    
    /**
     * Widget settings screen for customizing dashboard widgets
     */
    @Serializable
    data object WidgetSettings : LiftrixRoute()
    
    /**
     * Dashboard customization screen - enhanced widget management interface
     */
    @Serializable
    data object DashboardCustomization : LiftrixRoute()
    
    /**
     * Onboarding flow for new user setup
     */
    @Serializable
    data object Onboarding : LiftrixRoute()
    
    /**
     * Anomaly detection dashboard - view and manage workout anomalies
     */
    @Serializable
    data object AnomalyDashboard : LiftrixRoute()
    
    /**
     * Anomaly detection settings - configure detection sensitivity
     */
    @Serializable
    data object AnomalySettings : LiftrixRoute()
    
    /**
     * Calorie analytics dashboard - detailed calorie insights and goal tracking
     */
    @Serializable
    data object CalorieAnalytics : LiftrixRoute()
    
    /**
     * Calorie goal settings screen for setting and managing daily calorie goals
     */
    @Serializable
    data object CalorieGoalSettings : LiftrixRoute()
    
    /**
     * Detailed calorie history screen showing historical calorie burn data
     * 
     * @param timePeriod Optional time period filter (week, month, quarter, year)
     */
    @Serializable
    data class CalorieHistory(
        val timePeriod: String? = null
    ) : LiftrixRoute()
    
    /**
     * Edit workout routine screen for modifying saved workout routines
     * 
     * @param workoutId Unique identifier for the workout routine to edit
     */
    @Serializable
    data class EditWorkout(val workoutId: String) : LiftrixRoute()
    
    /**
     * Edit workout session screen for modifying completed workout sessions
     * 
     * @param sessionId Unique identifier for the workout session to edit
     */
    @Serializable
    data class EditSession(val sessionId: String) : LiftrixRoute()
    
    // Guest Mode Routes
    
    /**
     * Guest mode selection screen shown during onboarding
     */
    @Serializable
    data object GuestModeSelection : LiftrixRoute()
    
    /**
     * Guest session dashboard showing current limitations and conversion prompts
     */
    @Serializable
    data object GuestDashboard : LiftrixRoute()
    
    /**
     * Guest-to-registered conversion flow screen
     * 
     * @param source The source screen that triggered the conversion (e.g., "limit_reached", "nudge", "manual")
     * @param returnTo Optional route to return to after successful conversion
     */
    @Serializable
    data class GuestConversion(
        val source: String = "manual",
        val returnTo: String? = null
    ) : LiftrixRoute()
    
    // Authentication Routes
    
    /**
     * Sign-up screen for new user registration
     */
    @Serializable
    data object AuthSignUp : LiftrixRoute()
    
    /**
     * Sign-in screen for existing user authentication
     */
    @Serializable
    data object AuthSignIn : LiftrixRoute()
    
    /**
     * Profile screen - main profile display with achievements and settings
     * 
     * @param userId Optional user ID for viewing other users' profiles (defaults to current user)
     */
    @Serializable
    data class Profile(val userId: String? = null) : LiftrixRoute()
    
    /**
     * Profile edit screen - comprehensive profile editing interface
     */
    @Serializable
    data object ProfileEdit : LiftrixRoute()
    
    /**
     * Image crop screen for profile picture editing
     * 
     * @param imageUri URI of the image to crop (serialized as string)
     */
    @Serializable
    data class ImageCrop(val imageUri: String) : LiftrixRoute()
}