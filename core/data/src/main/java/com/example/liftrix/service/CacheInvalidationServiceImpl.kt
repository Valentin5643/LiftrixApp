package com.example.liftrix.service

import com.example.liftrix.domain.event.DomainEvent
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.analytics.AnalyticsQueryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.LocalDate
import timber.log.Timber

class CacheInvalidationServiceImpl(
    private val analyticsQueryUseCase: AnalyticsQueryUseCase
) : CacheInvalidationService {
    private val _invalidationEvents = MutableSharedFlow<DomainEvent>(extraBufferCapacity = 64)
    override val invalidationEvents: Flow<DomainEvent> = _invalidationEvents.asSharedFlow()

    override suspend fun publish(event: DomainEvent): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CacheError(
                errorMessage = "Failed to publish cache invalidation event: ${throwable.message}",
                operation = "publishCacheInvalidation"
            )
        }
    ) {
        when (event) {
            is DomainEvent.WorkoutChanged,
            is DomainEvent.ExerciseLibraryChanged,
            is DomainEvent.SyncCompleted,
            is DomainEvent.UserDataReset -> analyticsQueryUseCase.invalidateCacheForUser(event.userId)
            is DomainEvent.ProfileChanged,
            is DomainEvent.SocialFeedChanged -> Unit
        }

        _invalidationEvents.emit(event)
        Timber.d(
            "Published cache invalidation event type=${event::class.simpleName} " +
                "userId=${event.userId} signals=${event.invalidationSignals}"
        )
    }

    override suspend fun invalidateWorkoutData(
        userId: String,
        workoutDate: LocalDate,
        exerciseIds: List<String>,
        workoutDuration: Int
    ): LiftrixResult<Unit> = publish(
        DomainEvent.WorkoutChanged(
            userId = userId,
            workoutDate = workoutDate,
            exerciseIds = exerciseIds
        )
    )
}
