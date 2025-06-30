package com.example.liftrix.domain.model

import java.time.Duration

/**
 * Domain model representing workout statistics and progress insights
 * Used for progress dashboard and home screen workout analytics
 */
data class WorkoutStats(
    val totalWorkouts: Int,
    val currentStreak: Int,
    val weeklyVolume: Duration,
    val averageWorkoutDuration: Duration
) {
    init {
        require(totalWorkouts >= 0) { "Total workouts cannot be negative: $totalWorkouts" }
        require(currentStreak >= 0) { "Current streak cannot be negative: $currentStreak" }
        require(!weeklyVolume.isNegative) { "Weekly volume cannot be negative: $weeklyVolume" }
        require(!averageWorkoutDuration.isNegative) { "Average workout duration cannot be negative: $averageWorkoutDuration" }
    }
    
    companion object {
        /**
         * Empty stats for new users or when no data is available
         */
        val EMPTY = WorkoutStats(
            totalWorkouts = 0,
            currentStreak = 0,
            weeklyVolume = Duration.ZERO,
            averageWorkoutDuration = Duration.ZERO
        )
    }
    
    /**
     * Calculates the weekly volume (total workout time for the current week)
     * @return Duration representing total time spent working out this week
     */
    fun calculateWeeklyVolume(): Duration = weeklyVolume
    
    /**
     * Calculates the current workout streak (consecutive days with workouts)
     * @return Int representing the number of consecutive days with workouts
     */
    fun calculateStreak(): Int = currentStreak
    
    /**
     * Checks if the user has any workout data
     * @return true if the user has completed at least one workout
     */
    fun hasWorkoutData(): Boolean = totalWorkouts > 0
    
    /**
     * Gets a formatted string representation of the weekly volume
     * @return String representation of weekly volume (e.g., "2h 30m")
     */
    fun getFormattedWeeklyVolume(): String {
        if (weeklyVolume.isZero) return "0m"
        
        val hours = weeklyVolume.toHours()
        val minutes = weeklyVolume.toMinutesPart()
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Gets a formatted string representation of the average workout duration
     * @return String representation of average duration (e.g., "45m")
     */
    fun getFormattedAverageDuration(): String {
        if (averageWorkoutDuration.isZero) return "0m"
        
        val hours = averageWorkoutDuration.toHours()
        val minutes = averageWorkoutDuration.toMinutesPart()
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Gets the streak description for display purposes
     * @return String describing the current streak (e.g., "5 day streak")
     */
    fun getStreakDescription(): String {
        return when (currentStreak) {
            0 -> "No current streak"
            1 -> "1 day streak"
            else -> "$currentStreak day streak"
        }
    }
    
    /**
     * Checks if the current streak is considered significant (7+ days)
     * @return true if the streak is 7 days or longer
     */
    fun hasSignificantStreak(): Boolean = currentStreak >= 7
}