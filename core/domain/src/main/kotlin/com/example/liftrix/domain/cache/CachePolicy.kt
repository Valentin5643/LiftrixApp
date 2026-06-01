package com.example.liftrix.domain.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Single source of truth for cache ownership in Liftrix.
 */
sealed class CachePolicy {
    abstract val tier: CacheTier
    abstract val userScoped: Boolean
    abstract val ttl: Duration?
    abstract val invalidatesOn: Set<CacheInvalidationSignal>

    data object RoomSourceOfTruth : CachePolicy() {
        override val tier = CacheTier.ROOM
        override val userScoped = true
        override val ttl: Duration? = null
        override val invalidatesOn = CacheInvalidationSignal.roomBackedSignals
    }

    data class Memory(
        override val userScoped: Boolean,
        override val ttl: Duration = 5.minutes,
        override val invalidatesOn: Set<CacheInvalidationSignal> = emptySet()
    ) : CachePolicy() {
        override val tier = CacheTier.MEMORY
    }

    data object ViewModelState : CachePolicy() {
        override val tier = CacheTier.VIEW_MODEL_STATE
        override val userScoped = true
        override val ttl: Duration? = null
        override val invalidatesOn = emptySet<CacheInvalidationSignal>()
    }

    data object DataStorePreferences : CachePolicy() {
        override val tier = CacheTier.DATASTORE
        override val userScoped = true
        override val ttl: Duration? = null
        override val invalidatesOn = setOf(CacheInvalidationSignal.PROFILE_CHANGED)
    }

    companion object {
        fun requireUserScope(policy: CachePolicy, owner: String) {
            require(policy.userScoped) {
                "$owner stores user-specific data and must be user-scoped"
            }
        }
    }
}

enum class CacheTier {
    ROOM,
    MEMORY,
    VIEW_MODEL_STATE,
    DATASTORE
}

enum class CacheInvalidationSignal {
    WORKOUT_CHANGED,
    PROFILE_CHANGED,
    EXERCISE_LIBRARY_CHANGED,
    SOCIAL_FEED_CHANGED,
    SYNC_COMPLETED,
    USER_DATA_RESET;

    companion object {
        val roomBackedSignals = setOf(
            WORKOUT_CHANGED,
            PROFILE_CHANGED,
            EXERCISE_LIBRARY_CHANGED,
            SOCIAL_FEED_CHANGED,
            SYNC_COMPLETED,
            USER_DATA_RESET
        )
    }
}
