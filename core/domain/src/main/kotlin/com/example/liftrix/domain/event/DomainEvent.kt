package com.example.liftrix.domain.event

import com.example.liftrix.domain.cache.CacheInvalidationSignal
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

sealed class DomainEvent {
    abstract val userId: String
    abstract val occurredAt: Instant
    abstract val invalidationSignals: Set<CacheInvalidationSignal>

    data class WorkoutChanged(
        override val userId: String,
        val workoutId: String? = null,
        val workoutDate: LocalDate? = null,
        val exerciseIds: List<String> = emptyList(),
        override val occurredAt: Instant = Clock.System.now()
    ) : DomainEvent() {
        override val invalidationSignals = setOf(CacheInvalidationSignal.WORKOUT_CHANGED)
    }

    data class ProfileChanged(
        override val userId: String,
        val profileUserId: String = userId,
        override val occurredAt: Instant = Clock.System.now()
    ) : DomainEvent() {
        override val invalidationSignals = setOf(CacheInvalidationSignal.PROFILE_CHANGED)
    }

    data class ExerciseLibraryChanged(
        override val userId: String,
        val exerciseIds: List<String> = emptyList(),
        override val occurredAt: Instant = Clock.System.now()
    ) : DomainEvent() {
        override val invalidationSignals = setOf(CacheInvalidationSignal.EXERCISE_LIBRARY_CHANGED)
    }

    data class SocialFeedChanged(
        override val userId: String,
        val postId: String? = null,
        override val occurredAt: Instant = Clock.System.now()
    ) : DomainEvent() {
        override val invalidationSignals = setOf(CacheInvalidationSignal.SOCIAL_FEED_CHANGED)
    }

    data class SyncCompleted(
        override val userId: String,
        val entityTypes: Set<String> = emptySet(),
        override val occurredAt: Instant = Clock.System.now()
    ) : DomainEvent() {
        override val invalidationSignals = setOf(CacheInvalidationSignal.SYNC_COMPLETED)
    }

    data class UserDataReset(
        override val userId: String,
        override val occurredAt: Instant = Clock.System.now()
    ) : DomainEvent() {
        override val invalidationSignals = setOf(CacheInvalidationSignal.USER_DATA_RESET)
    }
}
