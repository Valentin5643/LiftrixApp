package com.example.liftrix.domain.service.analytics

import com.example.liftrix.data.local.dao.StrengthForecastSetSampleResult
import com.example.liftrix.domain.model.analytics.StrengthExerciseForecast
import com.example.liftrix.domain.model.analytics.StrengthForecastPoint
import com.example.liftrix.domain.model.analytics.StrengthForecastPointType
import com.example.liftrix.domain.model.analytics.StrengthForecastRegression
import com.example.liftrix.domain.model.analytics.StrengthForecastResult
import com.example.liftrix.domain.model.analytics.StrengthForecastStatus
import com.example.liftrix.domain.model.analytics.StrengthForecastSummary
import com.example.liftrix.domain.model.analytics.StrengthForecastTrend
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@Singleton
class StrengthForecastService @Inject constructor() {

    fun buildForecast(
        samples: List<StrengthForecastSetSampleResult>,
        today: LocalDate,
        selectedExerciseId: String? = null,
        historyDays: Int = DEFAULT_HISTORY_DAYS,
        forecastDays: Int = DEFAULT_FORECAST_DAYS
    ): StrengthForecastResult {
        val clampedForecastDays = forecastDays.coerceIn(MIN_FORECAST_DAYS, MAX_FORECAST_DAYS)
        val filtered = samples
            .filter { sample -> selectedExerciseId == null || sample.exercise_library_id == selectedExerciseId }
            .filter { it.weight_kg > 0f && it.reps in 1..36 }

        val forecasts = filtered
            .groupBy { it.exercise_library_id }
            .map { (exerciseId, exerciseSamples) ->
                buildExerciseForecast(exerciseId, exerciseSamples, today, historyDays, clampedForecastDays)
            }
            .sortedWith(
                compareByDescending<StrengthExerciseForecast> { it.status == StrengthForecastStatus.READY }
                    .thenBy { it.exerciseName }
            )

        return StrengthForecastResult(
            exercises = forecasts,
            selectedExerciseId = forecasts.firstOrNull { it.status == StrengthForecastStatus.READY }?.exerciseId
                ?: forecasts.firstOrNull()?.exerciseId
        )
    }

    fun estimateBrzyckiOneRmKg(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0.0 || reps !in 1..36) return null
        val estimate = weightKg * (36.0 / (37.0 - reps))
        return estimate.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun buildExerciseForecast(
        exerciseId: String,
        samples: List<StrengthForecastSetSampleResult>,
        today: LocalDate,
        historyDays: Int,
        forecastDays: Int
    ): StrengthExerciseForecast {
        val exerciseName = samples.firstOrNull()?.exercise_name?.takeIf { it.isNotBlank() } ?: "Unknown Exercise"
        val historical = selectWorkoutBests(samples)
        if (historical.isEmpty()) {
            return emptyForecast(exerciseId, exerciseName, StrengthForecastStatus.NO_DATA, historyDays, forecastDays)
        }
        if (historical.size < 2) {
            return emptyForecast(exerciseId, exerciseName, StrengthForecastStatus.INSUFFICIENT_DATA, historyDays, forecastDays, historical)
        }

        val latestDate = historical.maxOf { it.date }
        if (latestDate.daysUntil(today) > INACTIVE_AFTER_DAYS) {
            return emptyForecast(exerciseId, exerciseName, StrengthForecastStatus.INACTIVE, historyDays, forecastDays, historical)
        }
        if (timelineSpanDays(historical) <= 0.0) {
            return emptyForecast(exerciseId, exerciseName, StrengthForecastStatus.INSUFFICIENT_DATA, historyDays, forecastDays, historical)
        }

        val regression = calculateRegression(historical)
            ?: return emptyForecast(exerciseId, exerciseName, StrengthForecastStatus.INSUFFICIENT_DATA, historyDays, forecastDays, historical)
        val latestPoint = historical.maxWith(compareBy<StrengthForecastPoint> { it.date }.thenBy { it.timelineDay ?: 0.0 })
        val firstDate = historical.minOf { it.date }
        val forecast = (1..forecastDays).mapNotNull { offset ->
            val date = latestDate.plus(DatePeriod(days = offset))
            val x = (latestPoint.timelineDay ?: firstDate.daysUntil(latestDate).toDouble()) + offset.toDouble()
            val predicted = (regression.slopeKgPerDay * x + regression.interceptKg).coerceAtLeast(0.0)
            predicted.takeIf { it.isFinite() }?.let {
                StrengthForecastPoint(date, it, StrengthForecastPointType.FORECAST, x)
            }
        }
        val projected = forecast.lastOrNull()?.estimatedOneRmKg
        val change = projected?.minus(latestPoint.estimatedOneRmKg)
        val trend = classifyTrend(change, regression)
        val status = when (trend) {
            StrengthForecastTrend.INCONSISTENT -> StrengthForecastStatus.INCONSISTENT
            else -> StrengthForecastStatus.READY
        }

        return StrengthExerciseForecast(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            historicalPoints = historical,
            forecastPoints = forecast,
            regression = regression,
            summary = StrengthForecastSummary(
                trend = trend,
                message = trend.message(change),
                latestEstimatedOneRmKg = latestPoint.estimatedOneRmKg,
                projectedEstimatedOneRmKg = projected,
                projectedChangeKg = change,
                historyDays = historyDays,
                forecastDays = forecastDays
            ),
            status = status
        )
    }

    private fun selectWorkoutBests(samples: List<StrengthForecastSetSampleResult>): List<StrengthForecastPoint> {
        val candidates = samples.mapNotNull { sample ->
            val estimate = estimateBrzyckiOneRmKg(sample.weight_kg.toDouble(), sample.reps) ?: return@mapNotNull null
            val date = parseDate(sample.activity_date) ?: parseCompletedDate(sample.completed_at) ?: return@mapNotNull null
            WorkoutCandidate(sample, date, sample.completed_at.takeIf { it > 0L }, estimate)
        }
            .groupBy { it.workoutKey }
            .map { (_, candidates) ->
                candidates.maxWith(
                    compareBy<WorkoutCandidate> { it.estimatedOneRmKg }
                        .thenBy { it.sample.weight_kg }
                        .thenBy { it.sample.completed_at }
                )
            }
            .sortedWith(compareBy<WorkoutCandidate> { it.date }.thenBy { it.completedAtMillis ?: 0L })
            .recordHighsOnly()

        val timelineDays = workoutTimelineDays(candidates)
        return candidates.zip(timelineDays).map { (candidate, timelineDay) ->
            StrengthForecastPoint(
                date = candidate.date,
                estimatedOneRmKg = candidate.estimatedOneRmKg,
                type = StrengthForecastPointType.HISTORICAL,
                timelineDay = timelineDay
            )
        }
    }

    private fun List<WorkoutCandidate>.recordHighsOnly(): List<WorkoutCandidate> {
        var bestEstimate = 0.0
        return mapNotNull { candidate ->
            if (candidate.estimatedOneRmKg < bestEstimate) {
                null
            } else {
                bestEstimate = candidate.estimatedOneRmKg
                candidate
            }
        }
    }

    private fun workoutTimelineDays(candidates: List<WorkoutCandidate>): List<Double> {
        if (candidates.isEmpty()) return emptyList()

        val firstCompletedAt = candidates.mapNotNull { it.completedAtMillis }.minOrNull()
        val firstDate = candidates.minOfOrNull { it.date }
        val rawTimeline = candidates.map { candidate ->
            when {
                firstCompletedAt != null && candidate.completedAtMillis != null ->
                    (candidate.completedAtMillis - firstCompletedAt).toDouble() / MILLIS_PER_DAY
                firstDate != null -> firstDate.daysUntil(candidate.date).toDouble()
                else -> 0.0
            }
        }
        val rawSpan = (rawTimeline.maxOrNull() ?: 0.0) - (rawTimeline.minOrNull() ?: 0.0)
        return if (rawSpan < MIN_REAL_DAY_TIMELINE_SPAN) {
            candidates.indices.map { it.toDouble() }
        } else {
            val adjustedTimeline = mutableListOf<Double>()
            rawTimeline.forEach { current ->
                val previous = adjustedTimeline.lastOrNull()
                adjustedTimeline += if (previous != null && current <= previous) {
                    previous + MIN_TIMELINE_INCREMENT_DAYS
                } else {
                    current
                }
            }
            adjustedTimeline
        }
    }

    private fun calculateRegression(points: List<StrengthForecastPoint>): StrengthForecastRegression? {
        val firstDate = points.minOf { it.date }
        val xy = points.map { (it.timelineDay ?: firstDate.daysUntil(it.date).toDouble()) to it.estimatedOneRmKg }
        val n = xy.size.toDouble()
        val sumX = xy.sumOf { it.first }
        val sumY = xy.sumOf { it.second }
        val sumXY = xy.sumOf { it.first * it.second }
        val sumX2 = xy.sumOf { it.first * it.first }
        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n
        val meanY = sumY / n
        val residuals = xy.map { (x, y) -> y - (slope * x + intercept) }
        val ssResidual = residuals.sumOf { it * it }
        val ssTotal = xy.sumOf { (_, y) -> (y - meanY).pow(2) }
        val rSquared = if (ssTotal == 0.0) 1.0 else (1.0 - ssResidual / ssTotal).coerceIn(0.0, 1.0)
        val residualError = sqrt(ssResidual / (xy.size - 2).coerceAtLeast(1))
        return StrengthForecastRegression(slope, intercept, rSquared, residualError)
    }

    private fun timelineSpanDays(points: List<StrengthForecastPoint>): Double {
        val firstDate = points.minOf { it.date }
        val timelineValues = points.map { it.timelineDay ?: firstDate.daysUntil(it.date).toDouble() }
        return (timelineValues.maxOrNull() ?: 0.0) - (timelineValues.minOrNull() ?: 0.0)
    }

    private fun classifyTrend(projectedChangeKg: Double?, regression: StrengthForecastRegression): StrengthForecastTrend {
        val change = projectedChangeKg ?: return StrengthForecastTrend.INSUFFICIENT_DATA
        if (regression.rSquared < MIN_R_SQUARED || regression.residualStandardErrorKg > HIGH_RESIDUAL_ERROR_KG) {
            return StrengthForecastTrend.INCONSISTENT
        }
        return when {
            change >= TREND_THRESHOLD_KG -> StrengthForecastTrend.IMPROVING
            change <= -TREND_THRESHOLD_KG -> StrengthForecastTrend.DECLINING
            abs(change) < TREND_THRESHOLD_KG -> StrengthForecastTrend.PLATEAU
            else -> StrengthForecastTrend.INCONSISTENT
        }
    }

    private fun emptyForecast(
        exerciseId: String,
        exerciseName: String,
        status: StrengthForecastStatus,
        historyDays: Int,
        forecastDays: Int,
        historical: List<StrengthForecastPoint> = emptyList()
    ) = StrengthExerciseForecast(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        historicalPoints = historical,
        forecastPoints = emptyList(),
        regression = null,
        summary = StrengthForecastSummary(
            trend = when (status) {
                StrengthForecastStatus.INACTIVE -> StrengthForecastTrend.INACTIVE
                StrengthForecastStatus.INCONSISTENT -> StrengthForecastTrend.INCONSISTENT
                else -> StrengthForecastTrend.INSUFFICIENT_DATA
            },
            message = when (status) {
                StrengthForecastStatus.INACTIVE -> "Recent strength data is stale. Log this lift again to refresh the forecast."
                else -> "Not enough data to generate forecast"
            },
            latestEstimatedOneRmKg = historical.lastOrNull()?.estimatedOneRmKg,
            projectedEstimatedOneRmKg = null,
            projectedChangeKg = null,
            historyDays = historyDays,
            forecastDays = forecastDays
        ),
        status = status
    )

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun parseCompletedDate(value: Long): LocalDate? =
        value.takeIf { it > 0L }
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date }

    private fun StrengthForecastTrend.message(change: Double?): String = when (this) {
        StrengthForecastTrend.IMPROVING -> "Projected to improve by ${change?.formatKg().orEmpty()} over 14 days"
        StrengthForecastTrend.PLATEAU -> "Projected strength is holding steady"
        StrengthForecastTrend.DECLINING -> "Projected to decline by ${abs(change ?: 0.0).formatKg()} over 14 days"
        StrengthForecastTrend.INCONSISTENT -> "Recent results are inconsistent; treat the projection cautiously"
        StrengthForecastTrend.INACTIVE -> "Recent strength data is stale. Log this lift again to refresh the forecast."
        StrengthForecastTrend.INSUFFICIENT_DATA -> "Not enough data to generate forecast"
    }

    private fun Double.formatKg(): String = "${"%.1f".format(this)} kg"

    private data class WorkoutCandidate(
        val sample: StrengthForecastSetSampleResult,
        val date: LocalDate,
        val completedAtMillis: Long?,
        val estimatedOneRmKg: Double
    ) {
        val workoutKey: String = sample.workout_id.takeIf { it.isNotBlank() }
            ?: "${date}_${sample.completed_at}"
    }

    companion object {
        const val DEFAULT_HISTORY_DAYS = 30
        const val DEFAULT_FORECAST_DAYS = 14
        private const val MILLIS_PER_DAY = 86_400_000.0
        private const val MIN_FORECAST_DAYS = 7
        private const val MAX_FORECAST_DAYS = 30
        private const val MIN_REAL_DAY_TIMELINE_SPAN = 1.0
        private const val MIN_TIMELINE_INCREMENT_DAYS = 0.01
        private const val TREND_THRESHOLD_KG = 1.0
        private const val MIN_R_SQUARED = 0.15
        private const val HIGH_RESIDUAL_ERROR_KG = 12.5
        private const val INACTIVE_AFTER_DAYS = 14
    }
}
