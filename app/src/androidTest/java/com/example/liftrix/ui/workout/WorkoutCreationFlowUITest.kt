package com.example.liftrix.ui.workout

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.create.CreateWorkoutScreen
import com.example.liftrix.ui.workout.active.ActiveWorkoutScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for Workout Creation and Active Session Flow
 * 
 * Tests the complete workout creation journey:
 * - Workout template creation and naming
 * - Exercise selection and addition
 * - Active workout session management
 * - Set completion and weight input validation
 * - Session timer and progress tracking
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutCreationFlowUITest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun createWorkoutScreen_displaysInitialState() {
        composeTestRule.setContent {
            LiftrixTheme {
                CreateWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseSelection = { },
                    onNavigateToActiveWorkout = { }
                )
            }
        }

        // Then: Initial UI elements are displayed
        composeTestRule.onNodeWithText("Create Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Exercises").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Workout").assertIsDisplayed()
    }

    @Test
    fun createWorkout_validatesWorkoutName() {
        composeTestRule.setContent {
            LiftrixTheme {
                CreateWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseSelection = { },
                    onNavigateToActiveWorkout = { }
                )
            }
        }

        // When: User tries to create workout without name
        composeTestRule.onNodeWithText("Start Workout").performClick()

        // Then: Validation error is shown
        composeTestRule.onNodeWithText("Workout name is required")
            .assertIsDisplayed()
    }

    @Test
    fun createWorkout_addsExercisesSuccessfully() {
        var exerciseSelectionNavigated = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                CreateWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseSelection = { exerciseSelectionNavigated = true },
                    onNavigateToActiveWorkout = { }
                )
            }
        }

        // Given: User enters workout name
        composeTestRule.onNodeWithText("Workout Name")
            .performTextInput("Push Day Workout")

        // When: User taps Add Exercises
        composeTestRule.onNodeWithText("Add Exercises").performClick()

        // Then: Navigation to exercise selection occurs
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            exerciseSelectionNavigated
        }
    }

    @Test
    fun activeWorkoutScreen_displaysSessionTimer() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActiveWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseLibrary = { },
                    onNavigateToWorkoutComplete = { }
                )
            }
        }

        // Then: Session timer components are visible
        composeTestRule.onNodeWithContentDescription("Session timer")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("timer_progress_indicator"))
            .assertIsDisplayed()
    }

    @Test
    fun activeWorkout_validatesWeightInput() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActiveWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseLibrary = { },
                    onNavigateToWorkoutComplete = { }
                )
            }
        }

        // Given: User is in an active workout with exercises
        // (This would require mock data setup in a real implementation)

        // When: User enters invalid weight
        composeTestRule.onNodeWithText("Weight")
            .performTextInput("invalid")

        // Then: Input validation prevents invalid characters
        composeTestRule.onNodeWithText("Weight")
            .assertTextEquals("")
    }

    @Test
    fun activeWorkout_completesSetSuccessfully() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActiveWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseLibrary = { },
                    onNavigateToWorkoutComplete = { }
                )
            }
        }

        // Given: User has entered set data
        composeTestRule.onNodeWithText("Weight").performTextInput("135")
        composeTestRule.onNodeWithText("Reps").performTextInput("10")

        // When: User marks set as complete
        composeTestRule.onNodeWithContentDescription("Complete set")
            .performClick()

        // Then: Set is marked as completed
        composeTestRule.onNode(hasTestTag("completed_set_indicator"))
            .assertIsDisplayed()
    }

    @Test
    fun activeWorkout_pausesAndResumesSession() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActiveWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseLibrary = { },
                    onNavigateToWorkoutComplete = { }
                )
            }
        }

        // When: User pauses the workout
        composeTestRule.onNodeWithText("Pause").performClick()

        // Then: Pause state is reflected in UI
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("paused_indicator"))
            .assertIsDisplayed()

        // When: User resumes the workout
        composeTestRule.onNodeWithText("Resume").performClick()

        // Then: Resume state is reflected
        composeTestRule.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_endsSessionWithConfirmation() {
        var workoutCompleteNavigated = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ActiveWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseLibrary = { },
                    onNavigateToWorkoutComplete = { workoutCompleteNavigated = true }
                )
            }
        }

        // When: User ends the workout
        composeTestRule.onNodeWithText("End Workout").performClick()

        // Then: Confirmation dialog is shown
        composeTestRule.onNodeWithText("End Workout?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to end this workout?")
            .assertIsDisplayed()

        // When: User confirms ending
        composeTestRule.onNodeWithText("End Workout", useUnmergedTree = true)
            .performClick()

        // Then: Navigation to workout complete occurs
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            workoutCompleteNavigated
        }
    }

    @Test
    fun activeWorkout_supportsAccessibilityForTimerControls() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActiveWorkoutScreen(
                    onNavigateBack = { },
                    onNavigateToExerciseLibrary = { },
                    onNavigateToWorkoutComplete = { }
                )
            }
        }

        // Then: Timer controls have proper accessibility labels
        composeTestRule.onNodeWithContentDescription("Pause workout session")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("End workout session")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Session timer")
            .assertIsDisplayed()
    }
}