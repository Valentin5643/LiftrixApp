package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Tracks guest user session state and workout limits
 * 
 * This model manages the guest user experience by tracking workout counts,
 * nudge interactions, and session limits to encourage account creation.
 */
data class GuestSession(
    val userId: String,
    val sessionId: String = generateSessionId(),
    val workoutCount: Int = 0,
    val maxWorkouts: Int = 3,
    val lastNudgeShown: Instant? = null,
    val nudgeCount: Int = 0,
    val significantInteractionCount: Int = 0,
    val sessionStartedAt: Instant = Instant.now(),
    val lastActivityAt: Instant = Instant.now(),
    val hasSeenLimitWarning: Boolean = false,
    val isLimitReached: Boolean = false
) {
    
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(workoutCount >= 0) { "Workout count cannot be negative" }
        require(maxWorkouts > 0) { "Max workouts must be positive" }
        require(nudgeCount >= 0) { "Nudge count cannot be negative" }
        require(significantInteractionCount >= 0) { "Interaction count cannot be negative" }
    }

    companion object {
        private fun generateSessionId(): String = "guest_${System.currentTimeMillis()}_${(1000..9999).random()}"
        
        const val DEFAULT_MAX_WORKOUTS = 3
        const val NUDGE_COOLDOWN_MINUTES = 15
        const val SIGNIFICANT_INTERACTION_THRESHOLD = 5
        
        /**
         * Creates a new guest session for an anonymous user
         */
        fun create(userId: String): GuestSession {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            return GuestSession(userId = userId)
        }
    }

    /**
     * Records a completed workout and updates session state
     */
    fun recordWorkout(): GuestSession {
        val newWorkoutCount = workoutCount + 1
        return copy(
            workoutCount = newWorkoutCount,
            lastActivityAt = Instant.now(),
            isLimitReached = newWorkoutCount >= maxWorkouts
        )
    }

    /**
     * Records a significant user interaction (set completion, tab switch, etc.)
     */
    fun recordSignificantInteraction(): GuestSession {
        return copy(
            significantInteractionCount = significantInteractionCount + 1,
            lastActivityAt = Instant.now()
        )
    }

    /**
     * Records that a nudge was shown to the user
     */
    fun recordNudgeShown(): GuestSession {
        return copy(
            lastNudgeShown = Instant.now(),
            nudgeCount = nudgeCount + 1
        )
    }

    /**
     * Records that the user has seen the limit warning
     */
    fun markLimitWarningSeen(): GuestSession {
        return copy(hasSeenLimitWarning = true)
    }

    /**
     * Checks if a nudge should be shown based on interactions and cooldown
     */
    fun shouldShowNudge(): Boolean {
        if (isLimitReached) return false
        
        // Don't show nudges too frequently
        lastNudgeShown?.let { lastNudge ->
            val minutesSinceLastNudge = (Instant.now().epochSecond - lastNudge.epochSecond) / 60
            if (minutesSinceLastNudge < NUDGE_COOLDOWN_MINUTES) return false
        }
        
        // Show nudge after significant interactions
        return significantInteractionCount > 0 && 
               significantInteractionCount % SIGNIFICANT_INTERACTION_THRESHOLD == 0
    }

    /**
     * Checks if the workout limit warning should be shown
     */
    fun shouldShowLimitWarning(): Boolean {
        return workoutCount == maxWorkouts - 1 && !hasSeenLimitWarning
    }

    /**
     * Gets the number of workouts remaining
     */
    fun getWorkoutsRemaining(): Int = (maxWorkouts - workoutCount).coerceAtLeast(0)

    /**
     * Checks if the session is still active (within limits)
     */
    fun isActive(): Boolean = !isLimitReached

    /**
     * Gets an appropriate nudge message based on session state
     */
    fun getNudgeMessage(): String {
        return when {
            workoutCount == 0 && significantInteractionCount >= SIGNIFICANT_INTERACTION_THRESHOLD -> 
                "Enjoying the app? Create a free account to save your progress."
            workoutCount == 1 -> 
                "You're doing great! Let's make it official with a quick sign-up."
            workoutCount == 2 -> 
                "One more workout left! Sign up to continue your fitness journey."
            significantInteractionCount >= SIGNIFICANT_INTERACTION_THRESHOLD * 2 -> 
                "Don't lose your progress! Create an account to save everything."
            else -> 
                "Create a free account to unlock unlimited workouts."
        }
    }

    /**
     * Gets session statistics
     */
    fun getSessionStats(): GuestSessionStats {
        val sessionDurationMinutes = (Instant.now().epochSecond - sessionStartedAt.epochSecond) / 60
        return GuestSessionStats(
            workoutCount = workoutCount,
            workoutsRemaining = getWorkoutsRemaining(),
            nudgeCount = nudgeCount,
            significantInteractionCount = significantInteractionCount,
            sessionDurationMinutes = sessionDurationMinutes,
            isLimitReached = isLimitReached
        )
    }
}

/**
 * Statistics about the guest session
 */
data class GuestSessionStats(
    val workoutCount: Int,
    val workoutsRemaining: Int,
    val nudgeCount: Int,
    val significantInteractionCount: Int,
    val sessionDurationMinutes: Long,
    val isLimitReached: Boolean
)

/**
 * Types of significant interactions that trigger nudges
 */
enum class SignificantInteraction {
    SET_COMPLETED,
    WORKOUT_COMPLETED,
    TAB_SWITCHED,
    EXERCISE_ADDED,
    TEMPLATE_VIEWED,
    PROGRESS_VIEWED,
    SESSION_PAUSED,
    SESSION_RESUMED
}