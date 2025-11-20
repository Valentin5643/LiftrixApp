package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for strength progress widget analytics.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 445-456
 *
 * Provides strength analysis including:
 * - Total workouts completed
 * - Total training volume
 * - Current workout streak
 * - Calculated strength score (volume-based metric)
 *
 * **Data Keys**:
 * - `totalWorkouts`: Int - Number of workouts in period
 * - `totalVolume`: Int - Total training volume
 * - `currentStreak`: Int - Current consecutive workout days
 * - `strengthScore`: Int - Calculated strength metric (volume * 0.1)
 */
@Singleton
class StrengthProgressCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val progressSummary = progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()
        val monthRange = TimeRange.lastMonth()
        val progressMetrics = progressStatsRepository.getProgressMetrics(userId, monthRange).first()

        mapOf(
            "totalWorkouts" to progressSummary.totalWorkouts,
            "totalVolume" to progressSummary.totalVolume.toInt(),
            "currentStreak" to progressSummary.currentStreak,
            "strengthScore" to (progressSummary.totalVolume * 0.1).toInt() // Simple strength score calculation
        )
    }
}
