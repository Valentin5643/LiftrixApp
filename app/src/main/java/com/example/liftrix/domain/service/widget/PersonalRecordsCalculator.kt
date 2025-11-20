package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for personal records widget analytics.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 471-480
 *
 * Provides personal records analysis including:
 * - Total PRs achieved (estimated at 30% of workouts)
 * - Recent PRs (estimated at 10% of workouts)
 * - PRs this month (estimated at 15% of workouts)
 *
 * **Note**: Uses estimation algorithms until dedicated PR tracking is implemented.
 *
 * **Data Keys**:
 * - `totalPRs`: Int - Estimated total personal records
 * - `recentPRs`: Int - Estimated recent personal records
 * - `thisMonth`: Int - Estimated PRs this month
 */
@Singleton
class PersonalRecordsCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        // Get recent workout data to find personal records (simplified)
        val progressSummary = progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()

        mapOf(
            "totalPRs" to (progressSummary.totalWorkouts * 0.3).toInt(), // Estimate 30% of workouts have PRs
            "recentPRs" to (progressSummary.totalWorkouts * 0.1).toInt(), // 10% recent PRs
            "thisMonth" to (progressSummary.totalWorkouts * 0.15).toInt() // 15% this month
        )
    }
}
