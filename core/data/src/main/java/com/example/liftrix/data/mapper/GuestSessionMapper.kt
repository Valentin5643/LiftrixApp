package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.GuestSessionEntity
import com.example.liftrix.domain.model.GuestSession

/**
 * Mapper for converting between GuestSession domain model and GuestSessionEntity
 */
object GuestSessionMapper {

    /**
     * Converts a GuestSession domain model to GuestSessionEntity for database storage
     */
    fun GuestSession.toEntity(): GuestSessionEntity {
        return GuestSessionEntity(
            sessionId = sessionId,
            userId = userId,
            workoutCount = workoutCount,
            maxWorkouts = maxWorkouts,
            lastNudgeShown = lastNudgeShown,
            nudgeCount = nudgeCount,
            significantInteractionCount = significantInteractionCount,
            sessionStartedAt = sessionStartedAt,
            lastActivityAt = lastActivityAt,
            hasSeenLimitWarning = hasSeenLimitWarning,
            isLimitReached = isLimitReached
        )
    }

    /**
     * Converts a GuestSessionEntity from database to GuestSession domain model
     */
    fun GuestSessionEntity.toDomain(): GuestSession {
        return GuestSession(
            userId = userId,
            sessionId = sessionId,
            workoutCount = workoutCount,
            maxWorkouts = maxWorkouts,
            lastNudgeShown = lastNudgeShown,
            nudgeCount = nudgeCount,
            significantInteractionCount = significantInteractionCount,
            sessionStartedAt = sessionStartedAt,
            lastActivityAt = lastActivityAt,
            hasSeenLimitWarning = hasSeenLimitWarning,
            isLimitReached = isLimitReached
        )
    }

    /**
     * Converts a list of GuestSessionEntity to list of GuestSession domain models
     */
    fun List<GuestSessionEntity>.toDomain(): List<GuestSession> {
        return map { it.toDomain() }
    }
}