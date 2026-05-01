package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AchievementRepository
import com.example.liftrix.domain.repository.workout.WorkoutHistoryRepository
import com.example.liftrix.domain.service.achievement.AchievementCalculatorFactory
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for calculating and assigning user achievements based on workout data.
 * Detects milestones, streaks, consistency badges, and first-time events.
 *
 * Refactored to use Strategy Pattern for reduced cyclomatic complexity:
 * - Delegates calculations to specialized AchievementCalculator strategies
 * - Factory pattern provides all calculators
 * - Complexity reduced from 22 to <15
 */
class CalculateAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val calculatorFactory: AchievementCalculatorFactory
) {

    suspend operator fun invoke(userId: String): LiftrixResult<List<UserAchievement>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ACHIEVEMENT_CALCULATION_FAILED",
                errorMessage = "Failed to calculate achievements: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CALCULATE_ACHIEVEMENTS",
                    "user_id" to userId
                )
            )
        }
    ) {
        val existingAchievements = achievementRepository.getUserAchievements(userId).getOrElse {
            throw LiftrixError.BusinessLogicError(
                code = "ACHIEVEMENT_LOAD_FAILED",
                errorMessage = "Failed to load existing achievements: ${it.message}"
            )
        }

        val streakData = calculateStreakData(userId).getOrElse {
            throw LiftrixError.BusinessLogicError(
                code = "STREAK_DATA_CALCULATION_FAILED",
                errorMessage = "Failed to calculate streak data: ${it.message}"
            )
        }

        // Use strategy pattern: delegate to specialized calculators
        val newAchievements = calculatorFactory.getAllCalculators().flatMap { calculator ->
            calculator.calculate(userId, streakData, existingAchievements)
        }

        // Save new achievements
        if (newAchievements.isNotEmpty()) {
            achievementRepository.saveAchievements(newAchievements).getOrElse {
                throw LiftrixError.BusinessLogicError(
                    code = "ACHIEVEMENT_SAVE_FAILED",
                    errorMessage = "Failed to save achievements: ${it.message}"
                )
            }
        }

        // Return all achievements (existing + new)
        existingAchievements + newAchievements
    }

    private suspend fun calculateStreakData(userId: String): LiftrixResult<StreakData> {
        return try {
            // Get the first emission from the flow
            val allWorkouts = workoutHistoryRepository.getAllWorkoutsForUser(userId).first()
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

}
