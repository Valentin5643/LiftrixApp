package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.common.LiftrixResult

interface WorkoutSyncStatusRepository {
    suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int>

    suspend fun getUnsyncedCountForUser(userId: String): LiftrixResult<Int>

    suspend fun queueSync(workoutId: WorkoutId, userId: String): LiftrixResult<Unit>

    suspend fun syncNow(userId: String): LiftrixResult<Unit>

    suspend fun syncNowForUser(userId: String): LiftrixResult<Unit>
}
