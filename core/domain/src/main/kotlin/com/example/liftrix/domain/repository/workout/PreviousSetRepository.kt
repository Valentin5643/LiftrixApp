package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.common.LiftrixResult

interface PreviousSetRepository {
    suspend fun getLastCompletedWorkoutsWithExercise(
        userId: String,
        exerciseId: String,
        exerciseName: String? = null,
        limit: Int = 5,
        excludeWorkoutId: String? = null
    ): LiftrixResult<List<Workout>>
}
