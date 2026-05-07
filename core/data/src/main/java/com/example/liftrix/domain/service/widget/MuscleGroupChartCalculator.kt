package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculator for muscle group distribution widget analytics.
 *
 * **Extracted from**: AnalyticsQueryUseCase.getWidgetData() lines 481-507
 *
 * Provides muscle group distribution analysis including:
 * - Percentage distribution across major muscle groups
 * - Empty state handling for users with no workouts
 *
 * **Note**: Uses sample distribution data until exercise-based analysis is implemented.
 *
 * **Data Keys**:
 * - `chest`: String - Chest training percentage (e.g., "25.5")
 * - `back`: String - Back training percentage
 * - `legs`: String - Legs training percentage
 * - `shoulders`: String - Shoulders training percentage
 * - `arms`: String - Arms training percentage
 * - `core`: String - Core training percentage
 */
@Singleton
class MuscleGroupChartCalculator @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        // Generate sample muscle group distribution data for the pie chart
        // In a real implementation, this would come from exercise analysis
        val progressSummary = progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()
        val hasWorkouts = progressSummary.totalWorkouts > 0

        if (hasWorkouts) {
            mapOf(
                "chest" to "25.5",
                "back" to "22.3",
                "legs" to "20.1",
                "shoulders" to "15.7",
                "arms" to "12.2",
                "core" to "4.2"
            )
        } else {
            // Empty state - show zero distribution
            mapOf(
                "chest" to "0",
                "back" to "0",
                "legs" to "0",
                "shoulders" to "0",
                "arms" to "0",
                "core" to "0"
            )
        }
    }
}
