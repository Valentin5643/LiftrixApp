package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface CacheInvalidationService {
    val invalidationEvents: Flow<InvalidationEvent>

    suspend fun invalidateWorkoutData(
        userId: String,
        workoutDate: LocalDate,
        exerciseIds: List<String>,
        workoutDuration: Int
    ): LiftrixResult<Unit>
}

sealed class InvalidationEvent {
    abstract val invalidatedPatterns: List<String>

    data class WorkoutCompleted(
        override val invalidatedPatterns: List<String>
    ) : InvalidationEvent()

    data class ExerciseUpdated(
        override val invalidatedPatterns: List<String>
    ) : InvalidationEvent()

    data class PreferenceUpdated(
        override val invalidatedPatterns: List<String>
    ) : InvalidationEvent()

    data class UserDataReset(
        val userId: String
    ) : InvalidationEvent() {
        override val invalidatedPatterns: List<String> = listOf("*user:$userId*")
    }
}
