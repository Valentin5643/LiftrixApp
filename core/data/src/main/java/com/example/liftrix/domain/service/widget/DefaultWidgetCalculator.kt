package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default fallback calculator for unrecognized or unsupported widget types.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 521-530 (else branch)
 *
 * Provides generic widget data when no specific calculator is available.
 * Returns basic workout summary data that can be displayed by any widget.
 *
 * **Data Keys**:
 * - `value`: String - Total workouts as string
 * - `subtitle`: String - Generic widget description
 * - `trend`: String - Always "stable" (no trend analysis)
 * - `lastUpdated`: Long - Current timestamp
 */
@Singleton
class DefaultWidgetCalculator @Inject constructor(
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
            "value" to progressSummary.totalWorkouts.toString(),
            "subtitle" to "Widget data",
            "trend" to "stable",
            "lastUpdated" to System.currentTimeMillis()
        )
    }
}
