package com.example.liftrix.domain.progress

import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.AnomalyValue
import com.example.liftrix.domain.model.ExerciseHistory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.UserAnomalyAction
import com.example.liftrix.domain.model.WorkoutAnomaly
import com.example.liftrix.domain.model.common.LiftrixResult

interface ProgressAnomalyPort {
    suspend fun detectWeightAnomaly(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentWeight: Double,
        previousWeight: Double? = null
    ): LiftrixResult<WorkoutAnomaly?>

    suspend fun detectRepsAnomaly(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentReps: Int,
        previousReps: Int? = null
    ): LiftrixResult<WorkoutAnomaly?>

    suspend fun detectDurationAnomaly(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentDuration: Long,
        previousDuration: Long? = null
    ): LiftrixResult<WorkoutAnomaly?>

    suspend fun resolveAnomaly(
        anomalyId: String,
        userAction: UserAnomalyAction,
        correctedValue: AnomalyValue? = null
    ): LiftrixResult<WorkoutAnomaly>

    suspend fun updateExerciseHistory(
        userId: String,
        exerciseId: ExerciseId,
        weight: Double? = null,
        reps: Int? = null,
        duration: Long? = null
    ): LiftrixResult<Unit>

    suspend fun getDetectionSettings(userId: String): AnomalyDetectionSettings
    suspend fun getExerciseHistory(userId: String, exerciseId: ExerciseId): ExerciseHistory
    suspend fun getUserAnomalyFeedback(userId: String): LiftrixResult<Pair<Int, Int>>
}
