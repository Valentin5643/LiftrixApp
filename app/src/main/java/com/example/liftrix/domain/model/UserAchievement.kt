package com.example.liftrix.domain.model

import java.time.LocalDateTime

/**
 * Domain model representing a user's fitness achievement.
 * Tracks milestone accomplishments, streaks, and other fitness achievements.
 */
data class UserAchievement(
    val id: String,
    val userId: String,
    val achievementType: AchievementType,
    val title: String,
    val description: String,
    val unlockedAt: LocalDateTime,
    val isDisplayed: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "Achievement ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(title.isNotBlank()) { "Achievement title cannot be blank" }
        require(description.isNotBlank()) { "Achievement description cannot be blank" }
    }
}

/**
 * Types of achievements that can be unlocked by users.
 */
enum class AchievementType {
    WORKOUT_MILESTONE,    // 10, 50, 100, 500 workouts
    STREAK_ACHIEVEMENT,   // 7, 30, 100 day streaks
    CONSISTENCY_BADGE,    // Weekly/monthly consistency
    FIRST_TIME_EVENTS     // First workout, first month
}

/**
 * Data class for calculating streak information.
 */
data class StreakData(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalWorkouts: Int,
    val lastWorkoutDate: LocalDateTime?
)