package com.example.liftrix.ui.workflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.active.ActiveWorkoutScreen
import com.example.liftrix.ui.navigation.LiftrixNavigation
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Comprehensive testing of active workout session management focusing on
 * UnifiedWorkoutSessionManager integration and state transitions.
 * 
 * Tests critical aspects of active sessions:
 * - Session initialization and state management
 * - Set completion and progress tracking
 * - Session pause/resume functionality
 * - Exercise navigation and order management
 * - State persistence during session lifecycle
 * - Error recovery and data integrity
 * 
 * This test suite validates the core workout session experience.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ActiveSessionFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
        Timber.plant(Timber.DebugTree())
    }

    /**
     * Tests active session initialization and basic functionality
     */
    @Test
    fun activeSession_initializesCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start a blank workout session
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Verify session screen shows updated terminology
        composeTestRule
            .onNodeWithText("Starting a workout")
            .assertIsDisplayed()

        // Verify session controls are available
        composeTestRule
            .onNodeWithContentDescription("Pause Session")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Session Timer")
            .assertIsDisplayed()

        // Verify exercise management is available
        composeTestRule
            .onNodeWithText("Add Exercise")
            .assertIsDisplayed()

        // Verify session completion option
        composeTestRule
            .onNodeWithText("Finish Workout")
            .assertIsDisplayed()

        Timber.d("Session initialization test passed")
    }

    /**
     * Tests session state management with UnifiedWorkoutSessionManager
     */
    @Test
    fun sessionStateManager_handlesStateTransitions() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add an exercise to the session
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Bench Press")
            .performClick()

        delay(300)

        // Verify exercise was added to session
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        // Test pause functionality
        composeTestRule
            .onNodeWithContentDescription("Pause Session")
            .performClick()

        delay(200)

        // Verify session is paused
        composeTestRule
            .onNodeWithText("Session Paused")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Resume Session")
            .assertIsDisplayed()

        // Resume session
        composeTestRule
            .onNodeWithContentDescription("Resume Session")
            .performClick()

        delay(200)

        // Verify session is active again
        composeTestRule
            .onNodeWithContentDescription("Pause Session")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Session Timer")
            .assertIsDisplayed()

        Timber.d("State transitions test passed")
    }

    /**
     * Tests set completion and progress tracking
     */
    @Test
    fun sessionProgress_tracksSetCompletionCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session and add exercise
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Squats")
            .performClick()

        delay(300)

        // Complete first set
        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextInput("185")

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("10")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(300)

        // Verify set completion
        composeTestRule
            .onNodeWithContentDescription("Set 1 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("185 lbs × 10 reps")
            .assertIsDisplayed()

        // Verify progress indicators
        composeTestRule
            .onNodeWithContentDescription("Exercise Progress: 1 of 3 sets")
            .assertIsDisplayed()

        // Complete second set with different values
        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextInput("190")

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("8")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(300)

        // Verify both sets are tracked
        composeTestRule
            .onNodeWithContentDescription("Set 2 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("190 lbs × 8 reps")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Exercise Progress: 2 of 3 sets")
            .assertIsDisplayed()

        // Complete final set
        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextInput("185")

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("12")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(300)

        // Verify exercise completion
        composeTestRule
            .onNodeWithContentDescription("Exercise Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Exercise Progress: 3 of 3 sets")
            .assertIsDisplayed()

        Timber.d("Set completion tracking test passed")
    }

    /**
     * Tests exercise navigation within active session
     */
    @Test
    fun sessionNavigation_handlesExerciseOrderCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session and add multiple exercises
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        val exercises = listOf("Bench Press", "Squats", "Deadlifts")
        
        exercises.forEach { exercise ->
            composeTestRule
                .onNodeWithText("Add Exercise")
                .performClick()

            delay(200)

            composeTestRule
                .onNodeWithText(exercise)
                .performClick()

            delay(200)
        }

        // Verify current exercise is the first one (Bench Press)
        composeTestRule
            .onNodeWithContentDescription("Current Exercise: Bench Press")
            .assertIsDisplayed()

        // Complete a set for first exercise
        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("10")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Navigate to next exercise
        composeTestRule
            .onNodeWithContentDescription("Next Exercise")
            .performClick()

        delay(200)

        // Verify we're on second exercise (Squats)
        composeTestRule
            .onNodeWithContentDescription("Current Exercise: Squats")
            .assertIsDisplayed()

        // Navigate to third exercise
        composeTestRule
            .onNodeWithContentDescription("Next Exercise")
            .performClick()

        delay(200)

        // Verify we're on third exercise (Deadlifts)
        composeTestRule
            .onNodeWithContentDescription("Current Exercise: Deadlifts")
            .assertIsDisplayed()

        // Navigate back to second exercise
        composeTestRule
            .onNodeWithContentDescription("Previous Exercise")
            .performClick()

        delay(200)

        // Verify we're back on second exercise
        composeTestRule
            .onNodeWithContentDescription("Current Exercise: Squats")
            .assertIsDisplayed()

        // Test exercise overview navigation
        composeTestRule
            .onNodeWithContentDescription("Exercise Overview")
            .performClick()

        delay(200)

        // Verify all exercises are shown with completion status
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Exercise 1: 1 of 3 sets completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Squats")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Exercise 2: 0 of 3 sets completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Deadlifts")
            .assertIsDisplayed()

        // Jump directly to third exercise
        composeTestRule
            .onNodeWithText("Deadlifts")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Current Exercise: Deadlifts")
            .assertIsDisplayed()

        Timber.d("Exercise navigation test passed")
    }

    /**
     * Tests session persistence and recovery
     */
    @Test
    fun sessionPersistence_recoversStateCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session and create some state
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add exercise and complete a set
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Pull Ups")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("8")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(300)

        // Simulate app recreation (configuration change, memory pressure)
        composeTestRule.activity?.recreate()

        delay(1000) // Allow recreation and state recovery

        // Verify session state was persisted and recovered
        composeTestRule
            .onNodeWithText("Starting a workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Pull Ups")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Set 1 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("8 reps")
            .assertIsDisplayed()

        // Verify session can continue normally
        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("7")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Set 2 Completed")
            .assertIsDisplayed()

        Timber.d("Session persistence test passed")
    }

    /**
     * Tests session completion with data integrity
     */
    @Test
    fun sessionCompletion_preservesDataIntegrity() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add exercise and complete sets
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Push Ups")
            .performClick()

        delay(300)

        // Complete multiple sets with different values
        val setData = listOf(
            Pair("15", "bodyweight"),
            Pair("12", "bodyweight"),
            Pair("10", "bodyweight")
        )

        setData.forEachIndexed { index, (reps, _) ->
            composeTestRule
                .onNodeWithContentDescription("Actual Reps Input")
                .performTextInput(reps)

            composeTestRule
                .onNodeWithText("Complete Set")
                .performClick()

            delay(200)

            // Verify set completion
            composeTestRule
                .onNodeWithContentDescription("Set ${index + 1} Completed")
                .assertIsDisplayed()
        }

        // Add session notes
        composeTestRule
            .onNodeWithContentDescription("Session Notes")
            .performTextInput("Great workout! Felt strong today.")

        // Complete the session
        composeTestRule
            .onNodeWithText("Finish Workout")
            .performClick()

        delay(500)

        // Verify completion dialog/screen
        composeTestRule
            .onNodeWithText("Workout Completed!")
            .assertIsDisplayed()

        // Verify session summary shows correct data
        composeTestRule
            .onNodeWithText("Push Ups")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("3 sets completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Total reps: 37")
            .assertIsDisplayed()

        // Verify session notes are preserved
        composeTestRule
            .onNodeWithText("Great workout! Felt strong today.")
            .assertIsDisplayed()

        // Confirm completion
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(500)

        // Verify navigation back to main screen
        composeTestRule
            .onNodeWithText("Recent Workouts")
            .assertIsDisplayed()

        // Verify completed workout appears in history
        composeTestRule
            .onNodeWithContentDescription("Completed Workout")
            .assertIsDisplayed()

        Timber.d("Session completion test passed")
    }

    /**
     * Tests error scenarios during active session
     */
    @Test
    fun sessionErrorHandling_recoversGracefully() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Burpees")
            .performClick()

        delay(300)

        // Test invalid input handling
        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("-5") // Invalid negative value

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Verify error message
        composeTestRule
            .onNodeWithText("Reps must be a positive number")
            .assertIsDisplayed()

        // Verify set was not completed
        composeTestRule
            .onAllNodesWithContentDescription("Set 1 Completed")
            .assertCountEquals(0)

        // Fix input and try again
        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("10")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Verify successful completion
        composeTestRule
            .onNodeWithContentDescription("Set 1 Completed")
            .assertIsDisplayed()

        // Test exercise removal during session
        composeTestRule
            .onNodeWithContentDescription("Remove Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Remove")
            .performClick()

        delay(200)

        // Verify exercise was removed but session continues
        composeTestRule
            .onAllNodesWithText("Burpees")
            .assertCountEquals(0)

        composeTestRule
            .onNodeWithText("Starting a workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Add Exercise")
            .assertIsDisplayed()

        Timber.d("Error handling test passed")
    }

    /**
     * Tests concurrent operations during active session
     */
    @Test
    fun sessionConcurrency_handlesMultipleOperations() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start session with multiple exercises
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add multiple exercises quickly
        val exercises = listOf("Mountain Climbers", "Jumping Jacks", "High Knees")
        
        exercises.forEach { exercise ->
            composeTestRule
                .onNodeWithText("Add Exercise")
                .performClick()

            delay(100) // Reduced delay to test concurrency

            composeTestRule
                .onNodeWithText(exercise)
                .performClick()

            delay(100)
        }

        // Rapidly complete sets for first exercise
        repeat(3) { setIndex ->
            composeTestRule
                .onNodeWithContentDescription("Actual Reps Input")
                .performTextInput("${20 + setIndex}")

            composeTestRule
                .onNodeWithText("Complete Set")
                .performClick()

            delay(100) // Quick succession
        }

        // Verify all sets were completed correctly
        composeTestRule
            .onNodeWithContentDescription("Set 1 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Set 2 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Set 3 Completed")
            .assertIsDisplayed()

        // Switch exercises and complete sets rapidly
        composeTestRule
            .onNodeWithContentDescription("Next Exercise")
            .performClick()

        delay(100)

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("25")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(100)

        composeTestRule
            .onNodeWithContentDescription("Next Exercise")
            .performClick()

        delay(100)

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("30")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Verify session state remains consistent
        composeTestRule
            .onNodeWithContentDescription("Exercise Overview")
            .performClick()

        delay(200)

        // Verify all exercises and their completion status
        exercises.forEach { exercise ->
            composeTestRule
                .onNodeWithText(exercise)
                .assertIsDisplayed()
        }

        Timber.d("Concurrency handling test passed")
    }
}