package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.AchievementType
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.AchievementRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for calculating and assigning user achievements based on workout data.
 * Detects milestones, streaks, consistency badges, and first-time events.
 */
class CalculateAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val workoutRepository: WorkoutRepository
) {

    suspend operator fun invoke(userId: String): LiftrixResult<List<UserAchievement>> {
        return try {
            val existingAchievements = achievementRepository.getUserAchievements(userId).getOrElse {
                return LiftrixResult.failure(Exception("Failed to load existing achievements: ${it.message}"))
            }
            
            val streakData = calculateStreakData(userId).getOrElse {
                return LiftrixResult.failure(Exception("Failed to calculate streak data: ${it.message}"))
            }
            
            val newAchievements = mutableListOf<UserAchievement>()
            
            // Calculate workout milestone achievements
            newAchievements.addAll(calculateWorkoutMilestones(userId, streakData, existingAchievements))
            
            // Calculate streak achievements
            newAchievements.addAll(calculateStreakAchievements(userId, streakData, existingAchievements))
            
            // Calculate consistency badges
            newAchievements.addAll(calculateConsistencyBadges(userId, existingAchievements))
            
            // Calculate first-time achievements
            newAchievements.addAll(calculateFirstTimeAchievements(userId, streakData, existingAchievements))
            
            // Save new achievements
            if (newAchievements.isNotEmpty()) {
                achievementRepository.saveAchievements(newAchievements).getOrElse {
                    return LiftrixResult.failure(Exception("Failed to save achievements: ${it.message}"))
                }
            }
            
            // Return all achievements (existing + new)
            val allAchievements = existingAchievements + newAchievements
            LiftrixResult.success(allAchievements)
            
        } catch (e: Exception) {
            LiftrixResult.failure(Exception("Failed to calculate achievements: ${e.message}"))
        }
    }

    private suspend fun calculateStreakData(userId: String): LiftrixResult<StreakData> {
        return try {
            // Get the first emission from the flow
            val allWorkouts = workoutRepository.getAllWorkoutsForUser(userId).first()
            val workouts = allWorkouts.filter { it.status == WorkoutStatus.COMPLETED && it.endTime != null }
            
            if (workouts.isEmpty()) {
                return LiftrixResult.success(StreakData(0, 0, 0, null))
            }
            
            val sortedWorkouts = workouts.sortedByDescending { it.endTime }
            val totalWorkouts = workouts.size
            val lastWorkoutDate = sortedWorkouts.firstOrNull()?.endTime
            
            // Calculate current streak
            var currentStreak = 0
            var longestStreak = 0
            var tempStreak = 1
            
            // Group workouts by date to handle multiple workouts per day
            val workoutsByDate = sortedWorkouts.groupBy { workout -> 
                workout.endTime?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() 
            }
            val uniqueDates = workoutsByDate.keys.filterNotNull().sorted().reversed()
            
            if (uniqueDates.isNotEmpty()) {
                // Calculate current streak
                val today = LocalDateTime.now().toLocalDate()
                val lastWorkoutDateLocal = uniqueDates.first()
                
                if (lastWorkoutDateLocal == today || lastWorkoutDateLocal == today.minusDays(1)) {
                    currentStreak = 1
                    for (i in 1 until uniqueDates.size) {
                        val currentDate = uniqueDates[i]
                        val previousDate = uniqueDates[i - 1]
                        
                        if (previousDate.minusDays(1) == currentDate) {
                            currentStreak++
                        } else {
                            break
                        }
                    }
                }
                
                // Calculate longest streak
                tempStreak = 1
                longestStreak = 1
                
                for (i in 1 until uniqueDates.size) {
                    val currentDate = uniqueDates[i]
                    val previousDate = uniqueDates[i - 1]
                    
                    if (previousDate.minusDays(1) == currentDate) {
                        tempStreak++
                        longestStreak = maxOf(longestStreak, tempStreak)
                    } else {
                        tempStreak = 1
                    }
                }
            }
            
            val lastWorkoutDateTime = lastWorkoutDate?.atZone(java.time.ZoneId.systemDefault())?.toLocalDateTime()
            LiftrixResult.success(StreakData(currentStreak, longestStreak, totalWorkouts, lastWorkoutDateTime))
            
        } catch (e: Exception) {
            LiftrixResult.failure(Exception("Failed to calculate streak data: ${e.message}"))
        }
    }

    private fun calculateWorkoutMilestones(
        userId: String,
        streakData: StreakData,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement> {
        val milestones = listOf(1, 5, 10, 25, 50, 100, 250, 500, 1000)
        val newAchievements = mutableListOf<UserAchievement>()
        val currentTime = LocalDateTime.now()
        
        for (milestone in milestones) {
            if (streakData.totalWorkouts >= milestone) {
                val achievementTitle = when (milestone) {
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

    private fun calculateStreakAchievements(
        userId: String,
        streakData: StreakData,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement> {
        val streakMilestones = listOf(3, 7, 14, 30, 60, 100)
        val newAchievements = mutableListOf<UserAchievement>()
        val currentTime = LocalDateTime.now()
        
        for (streak in streakMilestones) {
            if (streakData.longestStreak >= streak) {
                val achievementTitle = when (streak) {
                    3 -> "Three Day Streak"
                    7 -> "Week Warrior"
                    14 -> "Two Week Champion"
                    30 -> "Monthly Streak Master"
                    60 -> "Two Month Legend"
                    100 -> "Streak Superstar"
                    else -> "$streak Day Streak"
                }
                
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

    private suspend fun calculateConsistencyBadges(
        userId: String,
        existingAchievements: List<UserAchievement>
    ): List<UserAchievement> {
        // This would require more complex workout analysis
        // For now, return empty list - can be enhanced later
        return emptyList()
    }

    private fun calculateFirstTimeAchievements(
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