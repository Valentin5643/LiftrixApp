package com.example.liftrix.domain.interactor.workout

import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.analytics.LogWorkoutEventUseCase
import com.example.liftrix.domain.usecase.workout.CreateWorkoutRequest
import com.example.liftrix.domain.usecase.workout.CreateWorkoutUseCase
import com.example.liftrix.domain.usecase.workout.CreateWorkoutWithExercisesRequest
import com.example.liftrix.domain.usecase.workout.PreviousSetData
import com.example.liftrix.domain.usecase.workout.PreviousSetDataResponse
import com.example.liftrix.domain.usecase.workout.WorkoutCommandUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutSessionEditingData
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class WorkoutInteractor @Inject constructor(
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val workoutCommandUseCase: WorkoutCommandUseCase,
    private val createWorkoutUseCase: CreateWorkoutUseCase,
    private val logWorkoutEventUseCase: LogWorkoutEventUseCase
) {
    suspend fun getById(workoutId: WorkoutId, userId: String): LiftrixResult<Workout?> =
        workoutQueryUseCase.getById(workoutId, userId)

    suspend fun getPreviousWorkoutData(
        userId: String,
        exerciseLibraryIds: List<String>,
        excludeWorkoutId: String? = null
    ): LiftrixResult<Map<String, List<PreviousSetData>>> =
        workoutQueryUseCase.getPreviousWorkoutData(userId, exerciseLibraryIds, excludeWorkoutId)

    suspend fun getPreviousSetData(
        userId: String,
        exerciseId: String,
        exerciseName: String? = null,
        setNumber: Int,
        excludeWorkoutId: String? = null
    ): LiftrixResult<PreviousSetDataResponse> =
        workoutQueryUseCase.getPreviousSetData(userId, exerciseId, exerciseName, setNumber, excludeWorkoutId)

    suspend fun estimateDuration(template: WorkoutTemplate): LiftrixResult<Duration> =
        workoutQueryUseCase.estimateDuration(template)

    suspend fun getSessionForEditing(
        sessionId: WorkoutId,
        userId: String,
        allowCrossUserEditing: Boolean = false
    ): LiftrixResult<WorkoutSessionEditingData> =
        workoutQueryUseCase.getSessionForEditing(sessionId, userId, allowCrossUserEditing)

    suspend fun updateSession(
        updatedSession: Workout,
        originalCreatedAt: Instant? = null
    ): LiftrixResult<Workout> =
        workoutCommandUseCase.updateSession(updatedSession, originalCreatedAt)

    suspend fun addExercise(
        workoutId: WorkoutId,
        exerciseLibraryId: String,
        initialSets: Int = 3
    ): LiftrixResult<Workout> =
        workoutCommandUseCase.addExercise(workoutId, exerciseLibraryId, initialSets)

    suspend fun addCustomExercise(
        workoutId: WorkoutId,
        exerciseName: String,
        muscleGroup: ExerciseCategory,
        initialSets: Int = 3
    ): LiftrixResult<Workout> =
        workoutCommandUseCase.addCustomExercise(workoutId, exerciseName, muscleGroup, initialSets)

    suspend fun saveWorkout(workout: Workout): LiftrixResult<Unit> =
        workoutCommandUseCase.saveWorkout(workout)

    suspend fun createWorkout(request: CreateWorkoutRequest): LiftrixResult<Workout> =
        workoutCommandUseCase.createWorkout(request)

    suspend fun createWithExercises(request: CreateWorkoutWithExercisesRequest): LiftrixResult<Workout> =
        workoutCommandUseCase.createWithExercises(request)

    suspend fun create(workout: Workout): LiftrixResult<Workout> =
        createWorkoutUseCase(workout)

    suspend fun logWorkoutStatusChange(workout: Workout, previousStatus: WorkoutStatus): Result<Unit> =
        logWorkoutEventUseCase.logWorkoutStatusChange(workout, previousStatus)
}
