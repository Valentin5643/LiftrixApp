package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for volume chart widget analytics.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 413-429
 *
 * Provides volume analysis including:
 * - Total volume over time period
 * - Weekly average volume
 * - Volume trend direction (up/down/stable)
 * - Chart data points for visualization
 *
 * **Data Keys**:
 * - `totalVolume`: Int - Sum of all volume in period
 * - `weeklyAverage`: Int - Average volume per week
 * - `trend`: String - "up", "down", or "stable"
 * - `chartData`: List<Int> - Volume data points for chart
 */
@Singleton
class VolumeChartCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val volumeData = progressStatsRepository.getWorkoutVolumeData(userId, startDate, endDate).first()
        val totalVolume = volumeData.sumOf { it.totalVolume.toDouble() }.toFloat()
        val weeklyAverage = if (volumeData.isNotEmpty()) totalVolume / 4 else 0f // Rough 4-week average

        val trend = if (volumeData.size >= 2) {
            val recent = volumeData.takeLast(7).sumOf { it.totalVolume.toDouble() }
            val previous = volumeData.dropLast(7).takeLast(7).sumOf { it.totalVolume.toDouble() }
            if (recent > previous) "up" else if (recent < previous) "down" else "stable"
        } else "stable"

        mapOf(
            "totalVolume" to totalVolume.toInt(),
            "weeklyAverage" to weeklyAverage.toInt(),
            "trend" to trend,
            "chartData" to volumeData.map { it.totalVolume.toInt() }
        )
    }
}
