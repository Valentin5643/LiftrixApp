package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for one rep max (1RM) progression widget analytics.
 *
 * **Widget**: OneRMProgression (deprecated, replaced by StrengthAnalytics)
 *
 * Provides 1RM progression analysis including:
 * - Estimated 1RM values over time
 * - Progression trend
 * - Best lifts by exercise
 *
 * **Note**: Basic implementation using progress summary. Full 1RM tracking
 * should be implemented via AnalyticsQueryUseCase.getOneRmProgression().
 *
 * **Data Keys**:
 * - `totalWorkouts`: Int - Number of workouts in period
 * - `totalVolume`: Int - Total training volume
 * - `strengthScore`: Int - Calculated strength metric
 * - `progressionTrend`: String - Trend direction
 */
@Singleton
class OneRmProgressionCalculator @Inject constructor(
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
            "totalWorkouts" to progressSummary.totalWorkouts,
            "totalVolume" to progressSummary.totalVolume.toInt(),
            "strengthScore" to (progressSummary.totalVolume * 0.1).toInt(),
            "progressionTrend" to "stable"
        )
    }
}
