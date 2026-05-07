package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for workout duration (progress chart) widget analytics.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 457-470
 *
 * Provides duration analysis including:
 * - Average workout duration
 * - Total time spent training
 * - Efficiency score (comparing actual vs ideal duration)
 * - Chart data points for duration visualization
 *
 * **Data Keys**:
 * - `averageDuration`: Int - Average workout duration in minutes
 * - `totalTime`: Int - Total training time in minutes
 * - `efficiency`: Float - Efficiency score (0.0 to 1.0+)
 * - `chartData`: List<Int> - Duration data points for chart
 */
@Singleton
class WorkoutDurationCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val durationData = progressStatsRepository.getWorkoutDurationData(userId, startDate, endDate).first()
        val averageDuration = if (durationData.isNotEmpty()) {
            durationData.map { it.durationMinutes }.average().toInt()
        } else 0
        val totalTime = durationData.sumOf { it.durationMinutes }

        mapOf(
            "averageDuration" to averageDuration,
            "totalTime" to totalTime,
            "efficiency" to if (totalTime > 0) (totalTime.toFloat() / (durationData.size * 90)) else 0f, // Assume 90min ideal
            "chartData" to durationData.map { it.durationMinutes }
        )
    }
}
