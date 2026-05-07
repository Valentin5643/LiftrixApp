package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.analytics.WorkoutData
import kotlinx.datetime.LocalDate

typealias ExercisePerformanceData = com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData

interface WorkoutAnalyticsDataRepository {
    suspend fun getExercisePerformanceData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): LiftrixResult<List<ExercisePerformanceData>>

    suspend fun getWorkoutStats(userId: String): LiftrixResult<WorkoutStats>

    suspend fun getWorkoutsInDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WorkoutData>
}
