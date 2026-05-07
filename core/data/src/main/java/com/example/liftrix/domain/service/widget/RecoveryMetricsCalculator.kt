package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for recovery metrics widget analytics.
 *
 * **Widget**: RecoveryMetrics (active widget)
 *
 * Provides recovery pattern analysis including:
 * - Average rest days between workouts
 * - Recovery quality score
 * - Overtraining risk indicators
 * - Rest day recommendations
 *
 * **Note**: Basic implementation using workout frequency data. Advanced recovery
 * tracking (heart rate variability, sleep, etc.) to be implemented later.
 *
 * **Data Keys**:
 * - `averageRestDays`: Float - Average days between workouts
 * - `recoveryScore`: Float - Recovery quality (0.0 to 1.0)
 * - `overtrainingRisk`: String - "low", "moderate", or "high"
 * - `recommendedRestDays`: Int - Suggested rest days
 */
@Singleton
class RecoveryMetricsCalculator @Inject constructor(
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
        val totalDays = frequencyData.size

        // Calculate basic recovery metrics
        val averageRestDays = if (totalWorkouts > 0) {
            (totalDays - totalWorkouts).toFloat() / totalWorkouts
        } else 0f

        val workoutFrequency = totalWorkouts.toFloat() / (totalDays / 7f) // Workouts per week

        val overtrainingRisk = when {
            workoutFrequency > 6f -> "high"
            workoutFrequency > 4f -> "moderate"
            else -> "low"
        }

        val recoveryScore = when {
            averageRestDays >= 1f -> 1.0f
            averageRestDays >= 0.5f -> 0.7f
            else -> 0.4f
        }

        mapOf(
            "averageRestDays" to averageRestDays,
            "recoveryScore" to recoveryScore,
            "overtrainingRisk" to overtrainingRisk,
            "recommendedRestDays" to if (workoutFrequency > 5f) 2 else 1
        )
    }
}
