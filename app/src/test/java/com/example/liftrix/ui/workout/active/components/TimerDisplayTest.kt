package com.example.liftrix.ui.workout.active.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for timer display components.
 * 
 * Tests cover:
 * - SessionTimerDisplay component behavior and formatting
 * - RestTimerDisplay component visibility and state handling
 * - Timer formatting utility functions
 * - Accessibility content descriptions
 */
@RunWith(AndroidJUnit4::class)
class TimerDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sessionTimerDisplay_showsCorrectTimeFormat() {
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionRunning(
                startTime = Clock.System.now(),
                elapsedSeconds = 1845 // 30:45
            ),
            isRunning = true,
            sessionDurationSeconds = 1845
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SessionTimerDisplay(
                    timerState = timerState,
                    formattedTime = "30:45"
                )
            }
        }

        composeTestRule
            .onNodeWithText("30:45")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Session active")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Session time: 30:45")
            .assertIsDisplayed()
    }

    @Test
    fun sessionTimerDisplay_showsStoppedState() {
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.Stopped
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SessionTimerDisplay(
                    timerState = timerState,
                    formattedTime = "00:00"
                )
            }
        }

        composeTestRule
            .onNodeWithText("00:00")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Ready to start")
            .assertIsDisplayed()
    }

    @Test
    fun sessionTimerDisplay_showsPausedState() {
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionPaused(
                startTime = Clock.System.now(),
                pausedAtSeconds = 3725 // 1:02:05
            ),
            sessionDurationSeconds = 3725
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SessionTimerDisplay(
                    timerState = timerState,
                    formattedTime = "1:02:05"
                )
            }
        }

        composeTestRule
            .onNodeWithText("1:02:05")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Session paused")
            .assertIsDisplayed()
    }

    @Test
    fun restTimerDisplay_showsActiveRestTimer() {
        val restTimer = RestTimer(
            name = "Set Rest",
            durationSeconds = 90
        )
        
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestActive(
                restTimer = restTimer,
                remainingSeconds = 45
            ),
            restRemainingSeconds = 45
        )

        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = timerState,
                    formattedTime = "0:45"
                )
            }
        }

        composeTestRule
            .onNodeWithText("0:45")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Rest Timer")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Set Rest")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Rest time remaining: 0:45")
            .assertIsDisplayed()
    }

    @Test
    fun restTimerDisplay_showsPausedRestTimer() {
        val restTimer = RestTimer(
            name = "Exercise Rest",
            durationSeconds = 120
        )
        
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestPaused(
                restTimer = restTimer,
                remainingSeconds = 75
            ),
            restRemainingSeconds = 75
        )

        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = timerState,
                    formattedTime = "1:15"
                )
            }
        }

        composeTestRule
            .onNodeWithText("1:15")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Rest Paused")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Exercise Rest")
            .assertIsDisplayed()
    }

    @Test
    fun restTimerDisplay_hiddenWhenNoRestTimer() {
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.Stopped
        )

        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = timerState,
                    formattedTime = ""
                )
            }
        }

        // Rest timer should not be visible when not active
        composeTestRule
            .onNodeWithText("Rest Timer")
            .assertDoesNotExist()
    }

    @Test
    fun restTimerDisplay_showsDefaultNameWhenEmpty() {
        val restTimer = RestTimer(
            name = "",
            durationSeconds = 60
        )
        
        val timerState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestActive(
                restTimer = restTimer,
                remainingSeconds = 30
            ),
            restRemainingSeconds = 30
        )

        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = timerState,
                    formattedTime = "0:30"
                )
            }
        }

        composeTestRule
            .onNodeWithText("Rest Break")
            .assertIsDisplayed()
    }

    @Test
    fun formatSessionTime_formatsCorrectly() {
        // Test minutes only
        assert(formatSessionTime(125) == "2:05")
        
        // Test hours, minutes, seconds
        assert(formatSessionTime(3725) == "1:02:05")
        
        // Test zero
        assert(formatSessionTime(0) == "0:00")
        
        // Test exact hour
        assert(formatSessionTime(3600) == "1:00:00")
    }

    @Test
    fun formatRestTime_formatsCorrectly() {
        // Test minutes and seconds
        assert(formatRestTime(125) == "2:05")
        
        // Test zero
        assert(formatRestTime(0) == "0:00")
        
        // Test single digit seconds
        assert(formatRestTime(65) == "1:05")
        
        // Test exact minute
        assert(formatRestTime(60) == "1:00")
    }

    @Test
    fun getSessionTimeFromState_extractsCorrectTime() {
        val runningState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionRunning(
                startTime = Clock.System.now(),
                elapsedSeconds = 1845
            )
        )
        assert(getSessionTimeFromState(runningState) == "30:45")
        
        val pausedState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionPaused(
                startTime = Clock.System.now(),
                pausedAtSeconds = 3725
            )
        )
        assert(getSessionTimeFromState(pausedState) == "1:02:05")
        
        val stoppedState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.Stopped
        )
        assert(getSessionTimeFromState(stoppedState) == "00:00")
    }

    @Test
    fun getRestTimeFromState_extractsCorrectTime() {
        val restTimer = RestTimer("Test", 90)
        
        val activeState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestActive(
                restTimer = restTimer,
                remainingSeconds = 45
            )
        )
        assert(getRestTimeFromState(activeState) == "0:45")
        
        val pausedState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestPaused(
                restTimer = restTimer,
                remainingSeconds = 75
            )
        )
        assert(getRestTimeFromState(pausedState) == "1:15")
        
        val stoppedState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.Stopped
        )
        assert(getRestTimeFromState(stoppedState) == "")
    }

    @Test
    fun shouldShowRestTimer_returnsCorrectVisibility() {
        val restTimer = RestTimer("Test", 90)
        
        val activeState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestActive(
                restTimer = restTimer,
                remainingSeconds = 45
            )
        )
        assert(shouldShowRestTimer(activeState))
        
        val pausedState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestPaused(
                restTimer = restTimer,
                remainingSeconds = 75
            )
        )
        assert(shouldShowRestTimer(pausedState))
        
        val stoppedState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.Stopped
        )
        assert(!shouldShowRestTimer(stoppedState))
        
        val sessionState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionRunning(
                startTime = Clock.System.now(),
                elapsedSeconds = 100
            )
        )
        assert(!shouldShowRestTimer(sessionState))
    }
} 