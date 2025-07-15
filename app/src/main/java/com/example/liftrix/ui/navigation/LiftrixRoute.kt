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
     * Workout screen - workout templates and session management
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
    
    /**
     * Workout details screen with specific workout ID
     * 
     * @param workoutId Unique identifier for the workout to display
     */
    @Serializable
    data class WorkoutDetails(val workoutId: String) : LiftrixRoute()
    
    /**
     * Exercise selection screen for adding exercises to workout or template
     * 
     * @param templateId Optional template ID when selecting exercises for template creation
     * @param isForTemplate Whether the selection is for template creation or active workout
     */
    @Serializable
    data class ExerciseSelection(
        val templateId: String? = null,
        val isForTemplate: Boolean = false
    ) : LiftrixRoute()
    
    /**
     * Active workout session screen
     * 
     * @param templateId Optional template ID to start workout from template
     * @param isBlankWorkout Whether to start a blank workout without template
     */
    @Serializable
    data class ActiveWorkout(
        val templateId: String? = null,
        val isBlankWorkout: Boolean = false
    ) : LiftrixRoute()
    
    /**
     * Template creation screen for creating new workout templates
     */
    @Serializable
    data object TemplateCreation : LiftrixRoute()
    
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
     * Onboarding flow for new user setup
     */
    @Serializable
    data object Onboarding : LiftrixRoute()
}