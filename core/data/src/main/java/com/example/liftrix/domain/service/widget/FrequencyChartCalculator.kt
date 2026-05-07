package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for workout frequency chart widget analytics.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 430-444
 *
 * Provides frequency analysis including:
 * - Weekly workout frequency
 * - Consistency score (percentage of days with workouts)
 * - Total workouts in period
 * - Chart data points for frequency visualization
 *
 * **Data Keys**:
 * - `weeklyFrequency`: Int - Average workouts per week
 * - `consistency`: String - Consistency score (0.00 to 1.00 formatted)
 * - `totalWorkouts`: Int - Total number of workouts
 * - `chartData`: List<Int> - Workout count per day for chart
 */
@Singleton
class FrequencyChartCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val frequencyData = progressStatsRepository.getWorkoutFrequencyData(userId, startDate, endDate).first()
        val totalWorkouts = frequencyData.sumOf { it.workoutCount }
        val weeklyFrequency = totalWorkouts / 4f // 4 weeks

        val consistency = if (frequencyData.isNotEmpty()) {
            frequencyData.count { it.workoutCount > 0 }.toFloat() / frequencyData.size
        } else 0f

        mapOf(
            "weeklyFrequency" to weeklyFrequency.toInt(),
            "consistency" to String.format("%.2f", consistency),
            "totalWorkouts" to totalWorkouts,
            "chartData" to frequencyData.map { it.workoutCount }
        )
    }
}
