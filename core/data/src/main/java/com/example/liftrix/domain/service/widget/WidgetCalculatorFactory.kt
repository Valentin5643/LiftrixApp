package com.example.liftrix.domain.service.widget

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for selecting appropriate widget calculator based on widget type.
 *
 * Implements the Strategy pattern to eliminate large when/switch statements
 * and reduce cyclomatic complexity in GetWidgetDataUseCase and AnalyticsQueryUseCase.
 *
 * **Design Philosophy**:
 * - Strategy Pattern: Encapsulates calculator selection logic
 * - Open/Closed: New calculators can be added without modifying this factory
 * - Single Responsibility: Only responsible for calculator selection
 * - Dependency Injection: All calculators injected via constructor
 *
 * **Usage**:
 * ```kotlin
 * val calculator = widgetCalculatorFactory.getCalculator(AnalyticsWidget.VolumeChart)
 * val data = calculator.calculate(userId, startDate, endDate)
 * ```
 *
 * **Complexity Reduction**:
 * - Before: Cyclomatic complexity ~18 in use case
 * - After: Cyclomatic complexity ~13 in factory, <5 in use case
 *
 * @property volumeChartCalculator Calculator for volume chart widgets
 * @property frequencyChartCalculator Calculator for frequency chart widgets
 * @property strengthProgressCalculator Calculator for strength progress widgets
 * @property workoutDurationCalculator Calculator for workout duration widgets
 * @property personalRecordsCalculator Calculator for personal records widgets
 * @property muscleGroupChartCalculator Calculator for muscle group distribution widgets
 * @property consistencyScoreCalculator Calculator for consistency/streak widgets
 * @property oneRmProgressionCalculator Calculator for 1RM progression widgets
 * @property recoveryMetricsCalculator Calculator for recovery metrics widgets
 * @property exerciseRankingCalculator Calculator for exercise ranking widgets (placeholder)
 * @property overtrainingRiskCalculator Calculator for overtraining risk widgets (placeholder)
 * @property progressiveOverloadCalculator Calculator for progressive overload widgets (placeholder)
 * @property defaultCalculator Fallback calculator for unrecognized widgets
 */
@Singleton
class WidgetCalculatorFactory @Inject constructor(
    private val volumeChartCalculator: VolumeChartCalculator,
    private val frequencyChartCalculator: FrequencyChartCalculator,
    private val strengthProgressCalculator: StrengthProgressCalculator,
    private val workoutDurationCalculator: WorkoutDurationCalculator,
    private val personalRecordsCalculator: PersonalRecordsCalculator,
    private val muscleGroupChartCalculator: MuscleGroupChartCalculator,
    private val consistencyScoreCalculator: ConsistencyScoreCalculator,
    private val oneRmProgressionCalculator: OneRmProgressionCalculator,
    private val recoveryMetricsCalculator: RecoveryMetricsCalculator,
    private val exerciseRankingCalculator: ExerciseRankingCalculator,
    private val overtrainingRiskCalculator: OvertrainingRiskCalculator,
    private val progressiveOverloadCalculator: ProgressiveOverloadCalculator,
    private val defaultCalculator: DefaultWidgetCalculator
) {

    /**
     * Selects the appropriate calculator for the given widget type.
     *
     * **Widget to Calculator Mapping**:
     * - VolumeChart, VolumeAnalytics → VolumeChartCalculator
     * - FrequencyChart → FrequencyChartCalculator
     * - StrengthProgress, StrengthAnalytics → StrengthProgressCalculator
     * - ProgressChart → WorkoutDurationCalculator
     * - PersonalRecords → PersonalRecordsCalculator
     * - MuscleGroupDistribution → MuscleGroupChartCalculator
     * - WorkoutStreak, AverageDuration → ConsistencyScoreCalculator
     * - OneRMProgression → OneRmProgressionCalculator
     * - RecoveryMetrics → RecoveryMetricsCalculator
     * - MonthlySummary, others → DefaultCalculator
     *
     * @param widgetType The widget type requesting data
     * @return Appropriate WidgetCalculator instance for the widget type
     */
    fun getCalculator(widgetType: AnalyticsWidget): WidgetCalculator {
        return when (widgetType) {
            // Volume widgets - consolidated and deprecated
            AnalyticsWidget.VolumeChart,
            AnalyticsWidget.VolumeAnalytics,
            AnalyticsWidget.VolumeTrends,
            AnalyticsWidget.VolumeLoadProgression -> volumeChartCalculator

            // Frequency widgets
            AnalyticsWidget.FrequencyChart -> frequencyChartCalculator

            // Strength widgets - consolidated and deprecated
            AnalyticsWidget.StrengthProgress,
            AnalyticsWidget.StrengthAnalytics -> strengthProgressCalculator

            // Duration widgets
            AnalyticsWidget.ProgressChart -> workoutDurationCalculator

            // Personal records widgets
            AnalyticsWidget.PersonalRecords -> personalRecordsCalculator

            // Muscle group widgets
            AnalyticsWidget.MuscleGroupDistribution -> muscleGroupChartCalculator

            // Consistency/streak widgets (deprecated but kept for compatibility)
            AnalyticsWidget.WorkoutStreak,
            AnalyticsWidget.AverageDuration -> consistencyScoreCalculator

            // 1RM progression widgets
            AnalyticsWidget.OneRMProgression -> oneRmProgressionCalculator

            // Recovery widgets
            AnalyticsWidget.RecoveryMetrics -> recoveryMetricsCalculator

            // Hidden/deprecated widgets that still need basic data
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.VolumeCalendar -> consistencyScoreCalculator

            // Default for MonthlySummary and any future widgets
            AnalyticsWidget.MonthlySummary -> defaultCalculator
            else -> defaultCalculator
        }
    }

    /**
     * Gets all available calculators for testing or diagnostic purposes.
     *
     * @return List of all calculator instances
     */
    fun getAllCalculators(): List<WidgetCalculator> {
        return listOf(
            volumeChartCalculator,
            frequencyChartCalculator,
            strengthProgressCalculator,
            workoutDurationCalculator,
            personalRecordsCalculator,
            muscleGroupChartCalculator,
            consistencyScoreCalculator,
            oneRmProgressionCalculator,
            recoveryMetricsCalculator,
            exerciseRankingCalculator,
            overtrainingRiskCalculator,
            progressiveOverloadCalculator,
            defaultCalculator
        )
    }

    /**
     * Gets the calculator class name for a given widget type (for logging/debugging).
     *
     * @param widgetType The widget type
     * @return Simple class name of the calculator that would be used
     */
    fun getCalculatorName(widgetType: AnalyticsWidget): String {
        return getCalculator(widgetType)::class.simpleName ?: "Unknown"
    }
}
