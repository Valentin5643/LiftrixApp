package com.example.liftrix.service

import com.example.liftrix.core.cache.CacheKeyGenerator
import com.example.liftrix.core.cache.EnhancedCacheManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.LocalDate
import timber.log.Timber

class CacheInvalidationServiceImpl(
    private val cacheManager: EnhancedCacheManager,
    private val keyGenerator: CacheKeyGenerator
) : CacheInvalidationService {
    private val _invalidationEvents = MutableSharedFlow<InvalidationEvent>(extraBufferCapacity = 64)
    override val invalidationEvents: Flow<InvalidationEvent> = _invalidationEvents.asSharedFlow()

    override suspend fun invalidateWorkoutData(
        userId: String,
        workoutDate: LocalDate,
        exerciseIds: List<String>,
        workoutDuration: Int
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CacheError(
                errorMessage = "Failed to invalidate workout cache",
                analyticsContext = mapOf("operation" to "INVALIDATE_WORKOUT_CACHE")
            )
        }
    ) {
        val datePrefix = "${workoutDate.year}-${workoutDate.monthNumber.toString().padStart(2, '0')}"
        val patterns = listOf(
            keyGenerator.userPattern(userId),
            keyGenerator.exercisePattern(userId, exerciseIds),
            keyGenerator.timePattern(userId, datePrefix)
        )
        patterns.forEach { pattern ->
            cacheManager.invalidatePattern(pattern)
        }
        _invalidationEvents.emit(InvalidationEvent.WorkoutCompleted(patterns))
        Timber.d(
            "Invalidated workout cache for user=$userId date=$workoutDate " +
                "exercises=${exerciseIds.size} duration=$workoutDuration"
        )
    }
}
