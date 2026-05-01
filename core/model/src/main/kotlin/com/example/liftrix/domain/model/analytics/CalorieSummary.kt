package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.Instant

/**
 * Data class representing a comprehensive summary of calorie burn analytics.
 * 
 * This class encapsulates all calorie-related metrics and insights for a user,
 * including daily averages, weekly totals, streaks, and goal progress tracking.
 * 
 * Key Features:
 * - Daily and weekly calorie burn tracking
 * - Goal progress monitoring with percentage calculation
 * - Streak tracking for consistency measurement
 * - Highest day tracking for peak performance insights
 * - Timestamp tracking for data freshness
 * 
 * Usage:
 * ```kotlin
 * val summary = CalorieSummary(
 *     averageDailyCalories = 350,
 *     totalCaloriesThisWeek = 2450,
 *     goalProgress = 0.87f,
 *     currentStreak = 5,
 *     highestDayCalories = 580
 * )
 * ```
 * 
 * @param averageDailyCalories Average calories burned per day over the tracked period
 * @param totalCaloriesThisWeek Total calories burned in the current week
 * @param goalProgress Progress towards weekly calorie goal (0.0 to 1.0+)
 * @param currentStreak Current streak of consecutive days with workout activity
 * @param highestDayCalories Highest single-day calorie burn in the tracked period
 * @param lastUpdated Timestamp when this summary was last calculated
 */
data class CalorieSummary(
    val averageDailyCalories: Int,
    val totalCaloriesThisWeek: Int,
    val goalProgress: Float,
    val currentStreak: Int,
    val highestDayCalories: Int,
    val lastUpdated: Instant = kotlinx.datetime.Clock.System.now()
)