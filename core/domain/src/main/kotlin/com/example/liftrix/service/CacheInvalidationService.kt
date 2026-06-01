package com.example.liftrix.service

import com.example.liftrix.domain.event.DomainEvent
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface CacheInvalidationService {
    val invalidationEvents: Flow<DomainEvent>

    suspend fun publish(event: DomainEvent): LiftrixResult<Unit>

    suspend fun invalidateWorkoutData(
        userId: String,
        workoutDate: LocalDate,
        exerciseIds: List<String>,
        workoutDuration: Int
    ): LiftrixResult<Unit>
}
