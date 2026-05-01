package com.example.liftrix.domain.repository.workout

import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.common.LiftrixResult

interface PreviousSetRepository {
    suspend fun getLastCompletedWorkoutsWithExercise(
        userId: String,
        exerciseId: String,
        limit: Int = 5,
        excludeWorkoutId: String? = null
    ): LiftrixResult<List<WorkoutEntity>>
}
