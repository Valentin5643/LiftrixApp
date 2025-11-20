package com.example.liftrix.domain.service.achievement

import com.example.liftrix.domain.model.AchievementType
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserAchievement
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Calculates streak achievements (3, 7, 14, 30, 60, 100 day streaks).
 */
class StreakAchievementCalculator @Inject constructor() : AchievementCalculator {

    private val streakMilestones = listOf(3, 7, 14, 30, 60, 100)

    override fun calculate(
        userId: String,
        streakData: StreakData,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement> {
        val newAchievements = mutableListOf<UserAchievement>()
        val currentTime = LocalDateTime.now()

        for (streak in streakMilestones) {
            if (streakData.longestStreak >= streak) {
                val achievementTitle = getStreakTitle(streak)

                // Check if this achievement already exists
                val exists = existingAchievements.any {
                    it.achievementType == AchievementType.STREAK_ACHIEVEMENT &&
                    it.title == achievementTitle
                }

                if (!exists) {
                    val achievement = UserAchievement(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        achievementType = AchievementType.STREAK_ACHIEVEMENT,
                        title = achievementTitle,
                        description = "Maintained a $streak day workout streak",
                        unlockedAt = currentTime,
                        isDisplayed = true
                    )
                    newAchievements.add(achievement)
                }
            }
        }

        return newAchievements
    }

    private fun getStreakTitle(streak: Int): String = when (streak) {
        3 -> "Three Day Streak"
        7 -> "Week Warrior"
        14 -> "Two Week Champion"
        30 -> "Monthly Streak Master"
        60 -> "Two Month Legend"
        100 -> "Streak Superstar"
        else -> "$streak Day Streak"
    }
}
