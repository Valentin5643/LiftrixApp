package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

interface WorkoutHistoryRepository {
    suspend fun getUserWorkoutHistory(
        userId: String,
        limit: Int = 20,
        offset: Int = 0
    ): LiftrixResult<List<WorkoutSummary>>

    suspend fun getWorkoutHistoryCount(userId: String): LiftrixResult<Int>

    fun getAllWorkoutsForUser(userId: String): Flow<List<Workout>>
}
