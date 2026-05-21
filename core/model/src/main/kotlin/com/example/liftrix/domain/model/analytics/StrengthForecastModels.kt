package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.LocalDate

data class StrengthForecastResult(
    val exercises: List<StrengthExerciseForecast>,
    val selectedExerciseId: String? = exercises.firstOrNull { it.status == StrengthForecastStatus.READY }?.exerciseId
) {
    val isEmpty: Boolean = exercises.isEmpty()
}

data class StrengthExerciseForecast(
    val exerciseId: String,
    val exerciseName: String,
    val historicalPoints: List<StrengthForecastPoint>,
    val forecastPoints: List<StrengthForecastPoint>,
    val regression: StrengthForecastRegression?,
    val summary: StrengthForecastSummary,
    val status: StrengthForecastStatus
) {
    val allPoints: List<StrengthForecastPoint> = historicalPoints + forecastPoints
}

data class StrengthForecastPoint(
    val date: LocalDate,
    val estimatedOneRmKg: Double,
    val type: StrengthForecastPointType
)

enum class StrengthForecastPointType {
    HISTORICAL,
    FORECAST
}

data class StrengthForecastRegression(
    val slopeKgPerDay: Double,
    val interceptKg: Double,
    val rSquared: Double,
    val residualStandardErrorKg: Double
)

enum class StrengthForecastTrend {
    IMPROVING,
    PLATEAU,
    DECLINING,
    INCONSISTENT,
    INACTIVE,
    INSUFFICIENT_DATA
}

enum class StrengthForecastStatus {
    READY,
    NO_DATA,
    INSUFFICIENT_DATA,
    INACTIVE,
    INCONSISTENT
}

data class StrengthForecastSummary(
    val trend: StrengthForecastTrend,
    val message: String,
    val latestEstimatedOneRmKg: Double?,
    val projectedEstimatedOneRmKg: Double?,
    val projectedChangeKg: Double?,
    val historyDays: Int,
    val forecastDays: Int
)
