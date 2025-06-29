package com.example.liftrix.service

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.service.WorkoutTimerService.TimerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive unit tests for WorkoutTimerService covering timer accuracy,
 * state management, and service lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkoutTimerServiceTest {

    private lateinit var serviceController: ServiceController<WorkoutTimerService>
    private lateinit var service: WorkoutTimerService
    private lateinit var mockNotificationManager: NotificationManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        // Create mock NotificationManager
        mockNotificationManager = mockk(relaxed = true)
        
        // Create service controller
        serviceController = Robolectric.buildService(WorkoutTimerService::class.java)
        service = serviceController.create().get()
        
        // Inject mock NotificationManager
        service.notificationManager = mockNotificationManager
    }

    // MARK: - Timer Accuracy Tests

    @Test
    fun `session timer accuracy within one second over 5 seconds`() = runTest {
        val startTime = Clock.System.now()
        
        service.serviceState.test {
            // Start session
            val result = service.startSession()
            assertTrue(result.isSuccess)
            
            // Skip initial state
            val initialState = awaitItem()
            assertEquals(TimerState.Stopped, initialState.timerState)
            
            // Advance time by 5 seconds
            testScope.advanceTimeBy(5000)
            
            // Check timer state after 5 seconds
            val finalState = awaitItem()
            assertIs<TimerState.SessionRunning>(finalState.timerState)
            
            // Verify accuracy within ±1 second
            val elapsedSeconds = finalState.timerState.elapsedSeconds
            assertTrue(elapsedSeconds in 4L..6L, "Expected 4-6 seconds, got $elapsedSeconds")
            assertEquals(elapsedSeconds, finalState.sessionDurationSeconds)
        }
    }

    @Test
    fun `session timer accuracy within one second over 30 seconds`() = runTest {
        service.serviceState.test {
            service.startSession()
            
            // Skip initial states
            awaitItem() // Stopped
            
            // Advance time by 30 seconds
            testScope.advanceTimeBy(30000)
            
            val state = awaitItem()
            assertIs<TimerState.SessionRunning>(state.timerState)
            
            // Verify accuracy within ±1 second
            val elapsedSeconds = state.timerState.elapsedSeconds
            assertTrue(elapsedSeconds in 29L..31L, "Expected 29-31 seconds, got $elapsedSeconds")
        }
    }

    @Test
    fun `rest timer countdown accuracy within one second`() = runTest {
        val restTimer = RestTimer(durationSeconds = 10, isEnabled = true)
        
        // Start session first
        service.startSession()
        
        service.serviceState.test {
            // Start rest timer
            val result = service.startRestTimer(restTimer)
            assertTrue(result.isSuccess)
            
            // Skip initial states
            skipItems(1)
            
            // Advance time by 5 seconds
            testScope.advanceTimeBy(5000)
            
            val state = awaitItem()
            assertIs<TimerState.RestActive>(state.timerState)
            
            // Should have 5 seconds remaining (±1 second)
            val remainingSeconds = state.timerState.remainingSeconds
            assertTrue(remainingSeconds in 4..6, "Expected 4-6 seconds remaining, got $remainingSeconds")
            assertEquals(remainingSeconds, state.restRemainingSeconds)
        }
    }

    @Test
    fun `rest timer completes and returns to session accurately`() = runTest {
        val restTimer = RestTimer(durationSeconds = 3, isEnabled = true)
        
        // Start session first
        service.startSession()
        testScope.advanceTimeBy(5000) // 5 seconds of session time
        
        service.serviceState.test {
            // Start rest timer
            service.startRestTimer(restTimer)
            
            // Skip initial states
            skipItems(1)
            
            // Advance time to complete rest timer
            testScope.advanceTimeBy(3000)
            
            val state = awaitItem()
            assertIs<TimerState.SessionRunning>(state.timerState)
            
            // Should return to session timer
            assertTrue(state.isRunning)
            assertEquals(0, state.restRemainingSeconds)
        }
    }

    @Test
    fun `pause and resume timer maintains accurate elapsed time`() = runTest {
        service.serviceState.test {
            // Start session
            service.startSession()
            skipItems(1) // Skip initial state
            
            // Run for 3 seconds
            testScope.advanceTimeBy(3000)
            
            // Pause timer
            val pauseResult = service.pauseTimer()
            assertTrue(pauseResult.isSuccess)
            
            val pausedState = awaitItem()
            assertIs<TimerState.SessionPaused>(pausedState.timerState)
            assertFalse(pausedState.isRunning)
            
            val pausedAtSeconds = pausedState.timerState.pausedAtSeconds
            assertTrue(pausedAtSeconds in 2L..4L, "Expected 2-4 seconds, got $pausedAtSeconds")
            
            // Wait while paused (time should not advance)
            testScope.advanceTimeBy(2000)
            
            // Resume timer
            val resumeResult = service.resumeTimer()
            assertTrue(resumeResult.isSuccess)
            
            // Run for another 2 seconds
            testScope.advanceTimeBy(2000)
            
            val resumedState = awaitItem()
            assertIs<TimerState.SessionRunning>(resumedState.timerState)
            
            // Total elapsed should be ~5 seconds (3 + 2), not including pause time
            val totalElapsed = resumedState.timerState.elapsedSeconds
            assertTrue(totalElapsed in 4L..6L, "Expected 4-6 seconds total, got $totalElapsed")
        }
    }

    // MARK: - State Management Tests

    @Test
    fun `initial service state is stopped`() = runTest {
        service.serviceState.test {
            val initialState = awaitItem()
            assertEquals(TimerState.Stopped, initialState.timerState)
            assertFalse(initialState.isRunning)
            assertEquals(0L, initialState.sessionDurationSeconds)
            assertEquals(0, initialState.restRemainingSeconds)
        }
    }

    @Test
    fun `startSession transitions from stopped to session running`() = runTest {
        service.serviceState.test {
            val result = service.startSession()
            
            assertTrue(result.isSuccess)
            
            // Initial state
            val initialState = awaitItem()
            assertEquals(TimerState.Stopped, initialState.timerState)
            
            // After starting session
            testScope.advanceTimeBy(1000)
            val runningState = awaitItem()
            assertIs<TimerState.SessionRunning>(runningState.timerState)
            assertTrue(runningState.isRunning)
        }
    }

    @Test
    fun `pauseTimer transitions session running to session paused`() = runTest {
        service.startSession()
        testScope.advanceTimeBy(2000)
        
        service.serviceState.test {
            val pauseResult = service.pauseTimer()
            assertTrue(pauseResult.isSuccess)
            
            val pausedState = awaitItem()
            assertIs<TimerState.SessionPaused>(pausedState.timerState)
            assertFalse(pausedState.isRunning)
            assertTrue(pausedState.timerState.pausedAtSeconds > 0)
        }
    }

    @Test
    fun `resumeTimer transitions session paused to session running`() = runTest {
        service.startSession()
        testScope.advanceTimeBy(2000)
        service.pauseTimer()
        
        service.serviceState.test {
            val resumeResult = service.resumeTimer()
            assertTrue(resumeResult.isSuccess)
            
            testScope.advanceTimeBy(1000)
            val resumedState = awaitItem()
            assertIs<TimerState.SessionRunning>(resumedState.timerState)
            assertTrue(resumedState.isRunning)
        }
    }

    @Test
    fun `startRestTimer transitions to rest active state`() = runTest {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        service.startSession()
        
        service.serviceState.test {
            val restResult = service.startRestTimer(restTimer)
            assertTrue(restResult.isSuccess)
            
            skipItems(1) // Skip current state
            testScope.advanceTimeBy(1000)
            
            val restState = awaitItem()
            assertIs<TimerState.RestActive>(restState.timerState)
            assertTrue(restState.isRunning)
            assertEquals(restTimer, restState.timerState.restTimer)
            assertTrue(restState.restRemainingSeconds > 0)
        }
    }

    @Test
    fun `pauseTimer transitions rest active to rest paused`() = runTest {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        service.startSession()
        service.startRestTimer(restTimer)
        testScope.advanceTimeBy(5000)
        
        service.serviceState.test {
            val pauseResult = service.pauseTimer()
            assertTrue(pauseResult.isSuccess)
            
            val pausedState = awaitItem()
            assertIs<TimerState.RestPaused>(pausedState.timerState)
            assertFalse(pausedState.isRunning)
            assertTrue(pausedState.timerState.remainingSeconds < 60)
        }
    }

    @Test
    fun `skipRest transitions from rest state back to session running`() = runTest {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        service.startSession()
        testScope.advanceTimeBy(3000)
        service.startRestTimer(restTimer)
        testScope.advanceTimeBy(2000)
        
        service.serviceState.test {
            val skipResult = service.skipRest()
            assertTrue(skipResult.isSuccess)
            
            val sessionState = awaitItem()
            assertIs<TimerState.SessionRunning>(sessionState.timerState)
            assertTrue(sessionState.isRunning)
            assertEquals(0, sessionState.restRemainingSeconds)
        }
    }

    @Test
    fun `stopTimer transitions to stopped state and clears all data`() = runTest {
        service.startSession()
        testScope.advanceTimeBy(5000)
        
        service.serviceState.test {
            val stopResult = service.stopTimer()
            assertTrue(stopResult.isSuccess)
            
            val stoppedState = awaitItem()
            assertEquals(TimerState.Stopped, stoppedState.timerState)
            assertFalse(stoppedState.isRunning)
            assertEquals(0L, stoppedState.sessionDurationSeconds)
            assertEquals(0, stoppedState.restRemainingSeconds)
        }
    }

    // MARK: - Service Lifecycle Tests

    @Test
    fun `service creates notification channel on onCreate`() {
        serviceController.create()
        
        // Verify notification channel was created
        verify { mockNotificationManager.createNotificationChannel(any()) }
    }

    @Test
    fun `service returns binder on onBind`() {
        val intent = Intent()
        val binder = service.onBind(intent)
        
        assertNotNull(binder)
        assertIs<WorkoutTimerService.TimerBinder>(binder)
        
        val boundService = (binder as WorkoutTimerService.TimerBinder).getService()
        assertEquals(service, boundService)
    }

    @Test
    fun `service handles action intents correctly`() = runTest {
        // Start session first
        service.startSession()
        testScope.advanceTimeBy(2000)
        
        // Test pause action
        val pauseIntent = Intent().apply { action = "com.example.liftrix.PAUSE_TIMER" }
        val pauseResult = service.onStartCommand(pauseIntent, 0, 1)
        assertEquals(android.app.Service.START_STICKY, pauseResult)
        
        // Verify state changed to paused
        service.serviceState.test {
            val state = awaitItem()
            assertIs<TimerState.SessionPaused>(state.timerState)
        }
        
        // Test resume action
        val resumeIntent = Intent().apply { action = "com.example.liftrix.RESUME_TIMER" }
        service.onStartCommand(resumeIntent, 0, 2)
        
        // Verify state changed back to running
        service.serviceState.test {
            testScope.advanceTimeBy(1000)
            val state = awaitItem()
            assertIs<TimerState.SessionRunning>(state.timerState)
        }
    }

    @Test
    fun `service stops timer on destroy`() = runTest {
        service.startSession()
        testScope.advanceTimeBy(3000)
        
        service.serviceState.test {
            serviceController.destroy()
            
            val finalState = awaitItem()
            assertEquals(TimerState.Stopped, finalState.timerState)
            assertFalse(finalState.isRunning)
        }
    }

    @Test
    fun `service starts foreground when session starts`() = runTest {
        val startResult = service.startSession()
        
        assertTrue(startResult.isSuccess)
        // Note: Foreground service behavior is tested indirectly through notification updates
        verify { mockNotificationManager.notify(any(), any()) }
    }

    // MARK: - Error Handling Tests

    @Test
    fun `startSession fails if session already running`() = runTest {
        service.startSession()
        
        val secondStartResult = service.startSession()
        assertTrue(secondStartResult.isFailure)
        
        val exception = secondStartResult.exceptionOrNull()
        assertIs<IllegalStateException>(exception)
        assertEquals("Session already running", exception.message)
    }

    @Test
    fun `pauseTimer fails if no active timer`() = runTest {
        val pauseResult = service.pauseTimer()
        
        assertTrue(pauseResult.isFailure)
        val exception = pauseResult.exceptionOrNull()
        assertIs<IllegalStateException>(exception)
        assertEquals("No active timer to pause", exception.message)
    }

    @Test
    fun `resumeTimer fails if no paused timer`() = runTest {
        val resumeResult = service.resumeTimer()
        
        assertTrue(resumeResult.isFailure)
        val exception = resumeResult.exceptionOrNull()
        assertIs<IllegalStateException>(exception)
        assertEquals("No paused timer to resume", exception.message)
    }

    @Test
    fun `skipRest fails if no rest timer active`() = runTest {
        service.startSession()
        
        val skipResult = service.skipRest()
        assertTrue(skipResult.isFailure)
        
        val exception = skipResult.exceptionOrNull()
        assertIs<IllegalStateException>(exception)
        assertEquals("No rest timer to skip", exception.message)
    }

    @Test
    fun `startRestTimer with disabled timer returns success but does nothing`() = runTest {
        val disabledRestTimer = RestTimer(durationSeconds = 60, isEnabled = false)
        service.startSession()
        
        service.serviceState.test {
            val restResult = service.startRestTimer(disabledRestTimer)
            assertTrue(restResult.isSuccess)
            
            // State should remain session running
            testScope.advanceTimeBy(1000)
            val state = awaitItem()
            assertIs<TimerState.SessionRunning>(state.timerState)
        }
    }

    // MARK: - Integration Tests

    @Test
    fun `notification is updated when timer state changes`() = runTest {
        // Start session
        service.startSession()
        verify { mockNotificationManager.notify(any(), any()) }
        
        // Pause timer
        testScope.advanceTimeBy(2000)
        service.pauseTimer()
        verify(atLeast = 2) { mockNotificationManager.notify(any(), any()) }
        
        // Resume timer
        service.resumeTimer()
        verify(atLeast = 3) { mockNotificationManager.notify(any(), any()) }
    }

    @Test
    fun `complex workflow session to rest to session completes successfully`() = runTest {
        val restTimer = RestTimer(durationSeconds = 5, isEnabled = true)
        
        service.serviceState.test {
            // Start session and run for 3 seconds
            service.startSession()
            skipItems(1) // Initial state
            testScope.advanceTimeBy(3000)
            
            val sessionState1 = awaitItem()
            assertIs<TimerState.SessionRunning>(sessionState1.timerState)
            assertTrue(sessionState1.timerState.elapsedSeconds in 2L..4L)
            
            // Start rest timer
            service.startRestTimer(restTimer)
            testScope.advanceTimeBy(2000)
            
            val restState = awaitItem()
            assertIs<TimerState.RestActive>(restState.timerState)
            assertTrue(restState.timerState.remainingSeconds in 2..4)
            
            // Complete rest timer (advance remaining time)
            testScope.advanceTimeBy(3000)
            
            val sessionState2 = awaitItem()
            assertIs<TimerState.SessionRunning>(sessionState2.timerState)
            assertEquals(0, sessionState2.restRemainingSeconds)
            assertTrue(sessionState2.isRunning)
        }
    }

    @Test
    fun `service maintains state consistency through multiple operations`() = runTest {
        val restTimer = RestTimer(durationSeconds = 10, isEnabled = true)
        
        service.serviceState.test {
            // Complex state transitions
            service.startSession()
            skipItems(1)
            testScope.advanceTimeBy(2000)
            
            service.pauseTimer()
            testScope.advanceTimeBy(1000) // Paused time shouldn't count
            
            service.resumeTimer()
            testScope.advanceTimeBy(1000)
            
            service.startRestTimer(restTimer)
            testScope.advanceTimeBy(3000)
            
            service.pauseTimer() // Pause rest
            testScope.advanceTimeBy(2000) // Paused rest time shouldn't count
            
            service.resumeTimer() // Resume rest
            testScope.advanceTimeBy(2000)
            
            service.skipRest() // Back to session
            testScope.advanceTimeBy(1000)
            
            val finalState = awaitItem()
            assertIs<TimerState.SessionRunning>(finalState.timerState)
            
            // Total session time should be approximately 4 seconds (2+1+1), not including paused time
            val totalElapsed = finalState.timerState.elapsedSeconds
            assertTrue(totalElapsed in 3L..5L, "Expected 3-5 seconds total session time, got $totalElapsed")
            assertTrue(finalState.isRunning)
            assertEquals(0, finalState.restRemainingSeconds)
        }
    }
}