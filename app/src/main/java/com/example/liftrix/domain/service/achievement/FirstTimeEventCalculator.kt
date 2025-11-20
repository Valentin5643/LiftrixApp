package com.example.liftrix.domain.service.achievement

import com.example.liftrix.domain.model.AchievementType
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserAchievement
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Calculates first-time event achievements (e.g., first workout).
 */
class FirstTimeEventCalculator @Inject constructor() : AchievementCalculator {

    override fun calculate(
        userId: String,
        streakData: StreakData,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement> {
        val newAchievements = mutableListOf<UserAchievement>()
        val currentTime = LocalDateTime.now()

        // First workout achievement
        if (streakData.totalWorkouts >= 1) {
            val exists = existingAchievements.any {
                it.achievementType == AchievementType.FIRST_TIME_EVENTS &&
                it.title == "Welcome to Liftrix"
            }

            if (!exists) {
                val achievement = UserAchievement(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    achievementType = AchievementType.FIRST_TIME_EVENTS,
                    title = "Welcome to Liftrix",
                    description = "Completed your first workout",
                    unlockedAt = currentTime,
                    isDisplayed = true
                )
                newAchievements.add(achievement)
            }
        }

        return newAchievements
    }
}
