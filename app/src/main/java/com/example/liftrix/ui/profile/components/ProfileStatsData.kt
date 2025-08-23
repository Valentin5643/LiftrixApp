package com.example.liftrix.ui.profile.components

/**
 * Statistics types for profile interaction
 */
enum class StatType {
    WORKOUTS,
    FOLLOWERS,
    FOLLOWING
}

/**
 * Profile statistics data for the modern header component
 */
data class ProfileStatsData(
    val workoutCount: Int,
    val followersCount: Int,
    val followingCount: Int
)