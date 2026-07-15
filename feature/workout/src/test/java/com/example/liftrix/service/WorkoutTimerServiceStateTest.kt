package com.example.liftrix.service

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTimerServiceStateTest {

    @Test
    fun `pause and resume projections preserve the original session start`() {
        val originalStart = Instant.fromEpochSeconds(1_784_116_800L)
        val running = WorkoutTimerService.TimerState.SessionRunning(
            startTime = originalStart,
            elapsedSeconds = 600L
        )

        val paused = WorkoutTimerService.TimerState.SessionPaused(
            startTime = running.startTime,
            pausedAtSeconds = running.elapsedSeconds
        )
        val resumed = WorkoutTimerService.TimerState.SessionRunning(
            startTime = paused.startTime,
            elapsedSeconds = paused.pausedAtSeconds
        )

        assertEquals(originalStart, paused.startTime)
        assertEquals(originalStart, resumed.startTime)
        assertEquals(600L, resumed.elapsedSeconds)
    }
}
