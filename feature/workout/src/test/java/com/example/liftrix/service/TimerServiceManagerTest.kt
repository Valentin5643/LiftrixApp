package com.example.liftrix.service

import android.content.ContextWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimerServiceManagerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reconnectCancelsOldCollectorAndUsesCurrentServiceState() = runTest(dispatcher) {
        val manager = TimerServiceManager(ContextWrapper(null))
        val oldServiceState = MutableStateFlow(sessionState(elapsedSeconds = 0))
        val newServiceState = MutableStateFlow(sessionState(elapsedSeconds = 0))
        var oldServiceCurrent = true
        var newServiceCurrent = true

        manager.collectServiceStateForTest(
            serviceState = oldServiceState,
            isCurrentService = { oldServiceCurrent }
        )
        oldServiceState.value = sessionState(elapsedSeconds = 10)
        advanceUntilIdle()

        assertEquals(10, manager.timerState.value.sessionDurationSeconds)

        oldServiceCurrent = false
        manager.collectServiceStateForTest(
            serviceState = newServiceState,
            isCurrentService = { newServiceCurrent }
        )

        oldServiceState.value = sessionState(elapsedSeconds = 20)
        newServiceState.value = sessionState(elapsedSeconds = 30)
        advanceUntilIdle()

        assertEquals(30, manager.timerState.value.sessionDurationSeconds)
    }

    @Test
    fun canceledCollectorDoesNotRelayStaleServiceUpdates() = runTest(dispatcher) {
        val manager = TimerServiceManager(ContextWrapper(null))
        val oldServiceState = MutableStateFlow(sessionState(elapsedSeconds = 0))

        manager.collectServiceStateForTest(serviceState = oldServiceState)
        oldServiceState.value = sessionState(elapsedSeconds = 15)
        advanceUntilIdle()

        assertEquals(15, manager.timerState.value.sessionDurationSeconds)

        manager.cancelServiceStateCollectionForTest()
        oldServiceState.value = sessionState(elapsedSeconds = 45)
        advanceUntilIdle()

        assertEquals(15, manager.timerState.value.sessionDurationSeconds)
    }

    @Test
    fun staleOverlappingEmissionCannotOverwriteCurrentServiceState() = runTest(dispatcher) {
        val manager = TimerServiceManager(ContextWrapper(null))
        val oldServiceState = MutableStateFlow(sessionState(elapsedSeconds = 0))
        val newServiceState = MutableStateFlow(sessionState(elapsedSeconds = 0))
        var currentGeneration = 1

        manager.collectServiceStateForTest(
            serviceState = oldServiceState,
            isCurrentService = { currentGeneration == 1 }
        )
        oldServiceState.value = sessionState(elapsedSeconds = 5)
        advanceUntilIdle()

        currentGeneration = 2
        manager.collectServiceStateForTest(
            serviceState = newServiceState,
            isCurrentService = { currentGeneration == 2 }
        )

        newServiceState.value = sessionState(elapsedSeconds = 60)
        oldServiceState.value = sessionState(elapsedSeconds = 6)
        advanceUntilIdle()

        assertEquals(60, manager.timerState.value.sessionDurationSeconds)
    }

    private fun sessionState(elapsedSeconds: Long): WorkoutTimerService.TimerServiceState {
        val startTime = Instant.fromEpochSeconds(1_000L)
        return WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionRunning(
                startTime = startTime,
                elapsedSeconds = elapsedSeconds
            ),
            isRunning = true,
            sessionDurationSeconds = elapsedSeconds
        )
    }
}
