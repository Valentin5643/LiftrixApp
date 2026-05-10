package com.example.liftrix.ui.workout.active

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.active.components.REST_TIMER_ADD_TIME_TAG
import com.example.liftrix.ui.workout.active.components.REST_TIMER_COUNTDOWN_TAG
import com.example.liftrix.ui.workout.active.components.REST_TIMER_DISPLAY_TAG
import com.example.liftrix.ui.workout.active.components.REST_TIMER_PAUSE_RESUME_TAG
import com.example.liftrix.ui.workout.active.components.REST_TIMER_SKIP_TAG
import com.example.liftrix.ui.workout.active.components.REST_TIMER_SUBTRACT_TIME_TAG
import com.example.liftrix.ui.workout.active.components.RestTimerDisplay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RestTimerDisplayUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun activeRest_displaysRemainingTimeAndControls() {
        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = restState(
                        timerState = WorkoutTimerService.TimerState.RestActive(
                            restTimer = RestTimer(durationSeconds = 90),
                            remainingSeconds = 45
                        ),
                        remainingSeconds = 45
                    ),
                    formattedTime = "0:45"
                )
            }
        }

        composeTestRule.onNodeWithTag(REST_TIMER_DISPLAY_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(REST_TIMER_COUNTDOWN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("0:45").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause rest timer").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Skip rest timer").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Add 15 seconds").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Subtract 15 seconds").assertIsDisplayed()
    }

    @Test
    fun pausedRest_displaysPausedStateAndResumeControl() {
        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = restState(
                        timerState = WorkoutTimerService.TimerState.RestPaused(
                            restTimer = RestTimer(durationSeconds = 120),
                            remainingSeconds = 75
                        ),
                        remainingSeconds = 75
                    ),
                    formattedTime = "1:15"
                )
            }
        }

        composeTestRule.onNodeWithText("Rest Paused").assertIsDisplayed()
        composeTestRule.onNodeWithText("1:15").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Resume rest timer").assertIsDisplayed()
    }

    @Test
    fun controls_invokeCallbacks() {
        var pauseCount = 0
        var skipCount = 0
        var addCount = 0
        var subtractCount = 0

        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = restState(
                        timerState = WorkoutTimerService.TimerState.RestActive(
                            restTimer = RestTimer(durationSeconds = 90),
                            remainingSeconds = 45
                        ),
                        remainingSeconds = 45
                    ),
                    formattedTime = "0:45",
                    onPause = { pauseCount++ },
                    onSkip = { skipCount++ },
                    onAddTime = { addCount++ },
                    onSubtractTime = { subtractCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag(REST_TIMER_PAUSE_RESUME_TAG).performClick()
        composeTestRule.onNodeWithTag(REST_TIMER_SKIP_TAG).performClick()
        composeTestRule.onNodeWithTag(REST_TIMER_ADD_TIME_TAG).performClick()
        composeTestRule.onNodeWithTag(REST_TIMER_SUBTRACT_TIME_TAG).performClick()

        assertEquals(1, pauseCount)
        assertEquals(1, skipCount)
        assertEquals(1, addCount)
        assertEquals(1, subtractCount)
    }

    @Test
    fun resumeControl_invokesResumeCallbackWhenPaused() {
        var resumeCount = 0

        composeTestRule.setContent {
            LiftrixTheme {
                RestTimerDisplay(
                    timerState = restState(
                        timerState = WorkoutTimerService.TimerState.RestPaused(
                            restTimer = RestTimer(durationSeconds = 90),
                            remainingSeconds = 45
                        ),
                        remainingSeconds = 45
                    ),
                    formattedTime = "0:45",
                    onResume = { resumeCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag(REST_TIMER_PAUSE_RESUME_TAG).performClick()

        assertEquals(1, resumeCount)
    }

    private fun restState(
        timerState: WorkoutTimerService.TimerState,
        remainingSeconds: Int
    ): WorkoutTimerService.TimerServiceState {
        return WorkoutTimerService.TimerServiceState(
            timerState = timerState,
            isRunning = timerState is WorkoutTimerService.TimerState.RestActive,
            restRemainingSeconds = remainingSeconds
        )
    }
}
