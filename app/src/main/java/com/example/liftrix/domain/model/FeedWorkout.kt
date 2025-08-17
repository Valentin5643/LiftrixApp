package com.example.liftrix.domain.model

import java.io.Serializable

/**
 * Domain model representing a workout instance for display in social feed
 * 
 * Combines a workout session with user context to support unified display
 * of both personal workouts and friends' workouts in chronological feed.
 * 
 * @property workout The workout session data
 * @property isPersonal True if this is the current user's workout, false for friends' workouts
 * @property user User information for friends' workouts, null for personal workouts
 * @property mediaUrls List of media URLs associated with this workout post
 * @property mediaThumbnails List of thumbnail URLs for the media
 */
data class FeedWorkout(
    val workout: Workout,
    val isPersonal: Boolean,
    val user: User? = null,
    val mediaUrls: List<String> = emptyList(),
    val mediaThumbnails: List<String> = emptyList()
) : Serializable {
    
    init {
        // Validate that friends' workouts have user information
        if (!isPersonal) {
            requireNotNull(user) { "Friends' workouts must include user information" }
        }
        
        // Validate that personal workouts don't have redundant user info
        if (isPersonal && user != null) {
            require(user.uid == workout.userId) { 
                "Personal workout user info must match workout user ID" 
            }
        }
    }
    
    /**
     * Gets the display title for the workout with user context
     * 
     * For personal workouts: uses the workout name directly
     * For friends' workouts: includes the friend's display name
     */
    val displayTitle: String
        get() = if (isPersonal) {
            workout.name
        } else {
            "${user?.displayName ?: "Unknown User"}'s ${workout.name}"
        }
    
    /**
     * Gets the user to display for this workout entry
     * 
     * Returns null for personal workouts (no user display needed)
     * Returns the friend's user info for friends' workouts
     */
    val displayUser: User?
        get() = if (isPersonal) null else user
    
    /**
     * Checks if the workout is completed and suitable for feed display
     */
    fun isDisplayable(): Boolean = workout.status == WorkoutStatus.COMPLETED
    
    /**
     * Gets a summary of the workout for quick display
     */
    fun getSummary(): String = buildString {
        workout.getDuration()?.let { duration ->
            val minutes = duration.toMinutes()
            append("${minutes}m • ")
        }
        append("${workout.exercises.size} exercises")
        
        val completedSets = workout.getCompletedSets()
        if (completedSets > 0) {
            append(" • $completedSets sets")
        }
    }
    
    /**
     * Creates a copy with updated workout data
     */
    fun withUpdatedWorkout(updatedWorkout: Workout): FeedWorkout = copy(
        workout = updatedWorkout
    )
    
    /**
     * Gets the workout's completion time for chronological ordering
     */
    fun getCompletionTime() = workout.endTime
    
    companion object {
        private const val serialVersionUID: Long = 1L
        
        /**
         * Creates a FeedWorkout for the current user's workout
         */
        fun forPersonalWorkout(
            workout: Workout,
            mediaUrls: List<String> = emptyList(),
            mediaThumbnails: List<String> = emptyList()
        ): FeedWorkout = FeedWorkout(
            workout = workout,
            isPersonal = true,
            user = null,
            mediaUrls = mediaUrls,
            mediaThumbnails = mediaThumbnails
        )
        
        /**
         * Creates a FeedWorkout for a friend's workout
         */
        fun forFriendWorkout(
            workout: Workout,
            friendUser: User,
            mediaUrls: List<String> = emptyList(),
            mediaThumbnails: List<String> = emptyList()
        ): FeedWorkout = FeedWorkout(
            workout = workout,
            isPersonal = false,
            user = friendUser,
            mediaUrls = mediaUrls,
            mediaThumbnails = mediaThumbnails
        )
    }
}