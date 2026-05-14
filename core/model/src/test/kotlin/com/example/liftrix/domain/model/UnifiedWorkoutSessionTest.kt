package com.example.liftrix.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UnifiedWorkoutSessionTest {

    @Test
    fun `toCompletedWorkout preserves accumulated elapsed duration`() {
        val completedAt = Instant.parse("2026-05-13T10:20:00Z")
        val session = UnifiedWorkoutSession(
            id = WorkoutSessionId("session-1"),
            userId = "user-1",
            name = "Workout",
            exercises = emptyList(),
            sessionStatus = UnifiedWorkoutSession.SessionStatus.COMPLETED,
            startedAt = completedAt.minusSeconds(30),
            endedAt = completedAt,
            elapsedTimeSeconds = 5 * 60,
            lastModified = completedAt
        )

        val workout = session.toCompletedWorkout()

        assertEquals(completedAt.minusSeconds(5 * 60), workout.startTime)
        assertEquals(completedAt.toLocalDate(), workout.date)
        assertEquals(5L, Duration.between(workout.startTime!!, workout.endTime!!).toMinutes())
    }

    @Test
    fun `toCompletedWorkout keeps wall clock duration when no accumulated elapsed time exists`() {
        val startedAt = Instant.parse("2026-05-13T10:00:00Z")
        val completedAt = Instant.parse("2026-05-13T10:03:12Z")
        val session = UnifiedWorkoutSession(
            id = WorkoutSessionId("session-2"),
            userId = "user-1",
            name = "Workout",
            exercises = emptyList(),
            sessionStatus = UnifiedWorkoutSession.SessionStatus.COMPLETED,
            startedAt = startedAt,
            endedAt = completedAt,
            elapsedTimeSeconds = 0,
            lastModified = completedAt
        )

        val workout = session.toCompletedWorkout()

        assertEquals(3L, Duration.between(workout.startTime!!, workout.endTime!!).toMinutes())
    }

    private fun Instant.toLocalDate(): java.time.LocalDate =
        atZone(java.time.ZoneId.systemDefault()).toLocalDate()
}
