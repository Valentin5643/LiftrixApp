package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for consistency-related widget analytics (workout streak, average duration).
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 509-519
 *
 * Provides consistency metrics including:
 * - Average workout duration
 * - Current workout streak (consecutive days)
 * - Longest workout streak
 * - Streak type classification
 *
 * **Used for widgets**: WorkoutStreak, AverageDuration (both deprecated but kept for compatibility)
 *
 * **Data Keys**:
 * - `averageDuration`: Int - Average workout duration in minutes
 * - `currentStreak`: Int - Current consecutive workout days
 * - `longestStreak`: Int - Longest consecutive workout days achieved
 * - `streakType`: String - Always "days"
 */
@Singleton
class ConsistencyScoreCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val progressSummary = progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()

        mapOf(
            "averageDuration" to progressSummary.averageDuration,
            "currentStreak" to progressSummary.currentStreak,
            "longestStreak" to progressSummary.longestStreak,
            "streakType" to "days"
        )
    }
}
