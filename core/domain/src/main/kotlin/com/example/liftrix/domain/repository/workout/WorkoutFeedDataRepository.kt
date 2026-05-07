package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

interface WorkoutFeedDataRepository {
    suspend fun getFeedWorkouts(userId: String, limit: Int = 10): LiftrixResult<List<FeedWorkout>>

    fun getFeedWorkoutsReactive(userId: String, limit: Int = 10): Flow<LiftrixResult<List<FeedWorkout>>>

    fun getRecentActivityFeed(
        userId: String,
        includeOthers: Boolean = true,
        limit: Int = 20
    ): Flow<LiftrixResult<List<FeedWorkout>>>
}
