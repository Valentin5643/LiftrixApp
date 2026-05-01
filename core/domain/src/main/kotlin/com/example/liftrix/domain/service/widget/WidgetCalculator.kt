package com.example.liftrix.domain.service.widget

import kotlinx.datetime.LocalDate

/**
 * Strategy interface for widget-specific analytics calculations.
 *
 * Implements the Strategy pattern to reduce cyclomatic complexity in GetWidgetDataUseCase
 * by extracting widget calculation logic into dedicated, testable classes.
 *
 * **Design Philosophy**:
 * - Single Responsibility: Each calculator handles one widget type
 * - Open/Closed: New widget types can be added without modifying existing code
 * - Dependency Inversion: Use case depends on abstraction, not concrete implementations
 *
 * **Usage**:
 * ```kotlin
 * class VolumeChartCalculator @Inject constructor(
 *     private val progressStatsRepository: ProgressStatsRepository
 * ) : WidgetCalculator {
 *     override suspend fun calculate(userId: String, startDate: LocalDate, endDate: LocalDate): Map<String, Any> {
 *         // Volume calculation logic
 *     }
 * }
 * ```
 *
 * @see WidgetCalculatorFactory for calculator selection strategy
 */
interface WidgetCalculator {

    /**
     * Calculates widget-specific analytics data for the given user and time range.
     *
     * All implementations must:
     * - Enforce user scoping via repository methods
     * - Use withContext(Dispatchers.IO) for database operations
     * - Return data as Map<String, Any> for flexible UI consumption
     * - Handle edge cases (empty data, insufficient data points)
     *
     * @param userId User identifier (must not be blank)
     * @param startDate Start date of analysis period (inclusive)
     * @param endDate End date of analysis period (inclusive)
     * @return Map containing widget-specific data keys and values
     * @throws IllegalArgumentException if userId is blank or date range is invalid
     */
    suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any>
}
