package com.example.liftrix.service

import android.content.ContextWrapper
import com.example.liftrix.domain.model.RestTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        val oldServiceState = MutableStateFlow(restState(remainingSeconds = 0))
        val newServiceState = MutableStateFlow(restState(remainingSeconds = 0))
        var oldServiceCurrent = true
        var newServiceCurrent = true

        manager.collectServiceStateForTest(
            serviceState = oldServiceState,
            isCurrentService = { oldServiceCurrent }
        )
        oldServiceState.value = restState(remainingSeconds = 10)
        advanceUntilIdle()

        assertEquals(10, manager.timerState.value.restRemainingSeconds)

        oldServiceCurrent = false
        manager.collectServiceStateForTest(
            serviceState = newServiceState,
            isCurrentService = { newServiceCurrent }
        )

        oldServiceState.value = restState(remainingSeconds = 20)
        newServiceState.value = restState(remainingSeconds = 30)
        advanceUntilIdle()

        assertEquals(30, manager.timerState.value.restRemainingSeconds)
    }

    @Test
    fun canceledCollectorDoesNotRelayStaleServiceUpdates() = runTest(dispatcher) {
        val manager = TimerServiceManager(ContextWrapper(null))
        val oldServiceState = MutableStateFlow(restState(remainingSeconds = 0))

        manager.collectServiceStateForTest(serviceState = oldServiceState)
        oldServiceState.value = restState(remainingSeconds = 15)
        advanceUntilIdle()

        assertEquals(15, manager.timerState.value.restRemainingSeconds)

        manager.cancelServiceStateCollectionForTest()
        oldServiceState.value = restState(remainingSeconds = 45)
        advanceUntilIdle()

        assertEquals(15, manager.timerState.value.restRemainingSeconds)
    }

    @Test
    fun staleOverlappingEmissionCannotOverwriteCurrentServiceState() = runTest(dispatcher) {
        val manager = TimerServiceManager(ContextWrapper(null))
        val oldServiceState = MutableStateFlow(restState(remainingSeconds = 0))
        val newServiceState = MutableStateFlow(restState(remainingSeconds = 0))
        var currentGeneration = 1

        manager.collectServiceStateForTest(
            serviceState = oldServiceState,
            isCurrentService = { currentGeneration == 1 }
        )
        oldServiceState.value = restState(remainingSeconds = 5)
        advanceUntilIdle()

        currentGeneration = 2
        manager.collectServiceStateForTest(
            serviceState = newServiceState,
            isCurrentService = { currentGeneration == 2 }
        )

        newServiceState.value = restState(remainingSeconds = 60)
        oldServiceState.value = restState(remainingSeconds = 6)
        advanceUntilIdle()

        assertEquals(60, manager.timerState.value.restRemainingSeconds)
    }

    private fun restState(remainingSeconds: Int): WorkoutTimerService.TimerServiceState {
        val timer = RestTimer(durationSeconds = remainingSeconds)
        return WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestActive(timer, remainingSeconds),
            isRunning = true,
            restRemainingSeconds = remainingSeconds
        )
    }
}
