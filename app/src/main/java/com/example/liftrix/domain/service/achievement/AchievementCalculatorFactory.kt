package com.example.liftrix.domain.service.achievement

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory providing all achievement calculator strategies.
 * Reduces cyclomatic complexity in CalculateAchievementsUseCase by delegating
 * calculation logic to specialized strategy classes.
 */
@Singleton
class AchievementCalculatorFactory @Inject constructor(
    private val workoutMilestoneCalculator: WorkoutMilestoneCalculator,
    private val streakAchievementCalculator: StreakAchievementCalculator,
    private val firstTimeEventCalculator: FirstTimeEventCalculator
) {
    /**
     * Returns all available achievement calculators.
     */
    fun getAllCalculators(): List<AchievementCalculator> = listOf(
        workoutMilestoneCalculator,
        streakAchievementCalculator,
        firstTimeEventCalculator
    )
}
