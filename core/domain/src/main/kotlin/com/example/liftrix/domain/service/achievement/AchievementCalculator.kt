package com.example.liftrix.domain.service.achievement

import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserAchievement

/**
 * Strategy interface for calculating specific types of achievements.
 * Reduces cyclomatic complexity by extracting calculation logic into separate strategies.
 */
interface AchievementCalculator {
    /**
     * Calculates new achievements for a user based on streak data.
     *
     * @param userId The user ID
     * @param streakData Workout streak and count data
     * @param existingAchievements Previously unlocked achievements to avoid duplicates
     * @return List of newly unlocked achievements
     */
    fun calculate(
        userId: String,
        streakData: StreakData,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement>
}
