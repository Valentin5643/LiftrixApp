package com.example.liftrix.domain.service.achievement

import com.example.liftrix.domain.model.AchievementType
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserAchievement
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Calculates workout milestone achievements (1, 5, 10, 25, 50, 100, etc. workouts).
 */
class WorkoutMilestoneCalculator @Inject constructor() : AchievementCalculator {

    private val milestones = listOf(1, 5, 10, 25, 50, 100, 250, 500, 1000)

    override fun calculate(
        userId: String,
        streakData: StreakData,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement> {
        val newAchievements = mutableListOf<UserAchievement>()
        val currentTime = LocalDateTime.now()

        for (milestone in milestones) {
            if (streakData.totalWorkouts >= milestone) {
                val achievementTitle = getMilestoneTitle(milestone)

                // Check if this achievement already exists
                val exists = existingAchievements.any {
                    it.achievementType == AchievementType.WORKOUT_MILESTONE &&
                    it.title == achievementTitle
                }

                if (!exists) {
                    val achievement = UserAchievement(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        achievementType = AchievementType.WORKOUT_MILESTONE,
                        title = achievementTitle,
                        description = "Completed $milestone workout${if (milestone > 1) "s" else ""}",
                        unlockedAt = currentTime,
                        isDisplayed = true
                    )
                    newAchievements.add(achievement)
                }
            }
        }

        return newAchievements
    }

    private fun getMilestoneTitle(milestone: Int): String = when (milestone) {
        1 -> "First Workout"
        5 -> "Getting Started"
        10 -> "Perfect Ten"
        25 -> "Quarter Century"
        50 -> "Half Century"
        100 -> "Century Club"
        250 -> "Quarter Thousand"
        500 -> "Elite Athlete"
        1000 -> "Workout Legend"
        else -> "$milestone Workouts"
    }
}
