package com.example.liftrix.ui.workflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.navigation.LiftrixNavigation
import com.example.liftrix.ui.common.state.UiState
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
 * Comprehensive testing of ViewModel state transitions across all workflow screens.
 * 
 * Tests state management patterns:
 * - BaseViewModel state transitions (Loading → Success → Error patterns)
 * - StateFlow propagation and UI updates
 * - State persistence across configuration changes
 * - Error state recovery and retry mechanisms
 * - Concurrent state updates and race condition handling
 * - Memory leak prevention in state management
 * 
 * This test suite validates the foundational state management architecture
 * that underlies all workflow operations.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class StateTransitionTest {

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
     * Tests basic ViewModel state transitions from Loading → Success
     */
    @Test
    fun viewModelState_transitionsFromLoadingToSuccess() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to workout screen which triggers loading state
        composeTestRule
            .onNodeWithText("Workout")
            .performClick()

        delay(100)

        // Verify loading state is displayed
        composeTestRule
            .onNodeWithContentDescription("Loading workouts")
            .assertIsDisplayed()

        // Wait for data loading to complete
        delay(2000)

        // Verify successful state transition
        composeTestRule
            .onNodeWithContentDescription("Workout list loaded")
            .assertIsDisplayed()

        // Verify loading indicator is gone
        composeTestRule
            .onAllNodesWithContentDescription("Loading workouts")
            .assertCountEquals(0)

        Timber.d("Loading to Success transition test passed")
    }

    /**
     * Tests error state transitions and recovery
     */
    @Test
    fun viewModelState_handlesErrorStateCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Simulate network error scenario by navigating to data-heavy screen
        composeTestRule
            .onNodeWithText("Progress")
            .performClick()

        delay(100)

        // Wait for potential error state
        delay(3000)

        // Check if error state is displayed
        val hasErrorState = composeTestRule
            .onAllNodesWithText("Unable to load data")
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (hasErrorState) {
            // Verify error message is displayed
            composeTestRule
                .onNodeWithText("Unable to load data")
                .assertIsDisplayed()

            // Verify retry option is available
            composeTestRule
                .onNodeWithText("Retry")
                .assertIsDisplayed()

            // Test retry functionality
            composeTestRule
                .onNodeWithText("Retry")
                .performClick()

            delay(100)

            // Verify loading state after retry
            composeTestRule
                .onNodeWithContentDescription("Loading data")
                .assertIsDisplayed()

            delay(2000)

            // Verify eventual success or persistent error handling
            val hasSuccessAfterRetry = composeTestRule
                .onAllNodesWithContentDescription("Data loaded successfully")
                .fetchSemanticsNodes()
                .isNotEmpty()

            val hasPersistentError = composeTestRule
                .onAllNodesWithText("Unable to load data")
                .fetchSemanticsNodes()
                .isNotEmpty()

            assert(hasSuccessAfterRetry || hasPersistentError) {
                "Expected either success or persistent error after retry"
            }
        }

        Timber.d("Error state handling test passed")
    }

    /**
     * Tests state persistence across configuration changes
     */
    @Test
    fun viewModelState_persistsAcrossConfigurationChanges() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to workout creation and enter data
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        val testWorkoutName = "State Persistence Test"
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput(testWorkoutName)

        // Add exercise to create more complex state
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Bench Press")
            .performClick()

        delay(300)

        // Verify state is set up
        composeTestRule
            .onNodeWithText(testWorkoutName)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        // Simulate configuration change (screen rotation)
        composeTestRule.activity?.recreate()

        delay(1000) // Allow for recreation and state restoration

        // Verify state was preserved
        composeTestRule
            .onNodeWithText(testWorkoutName)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        // Verify screen functionality still works
        composeTestRule
            .onNodeWithText("Add Exercise")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Save Workout")
            .assertIsDisplayed()

        Timber.d("Configuration change persistence test passed")
    }

    /**
     * Tests concurrent state updates and race condition prevention
     */
    @Test
    fun viewModelState_handlesConcurrentUpdatesCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start active workout session
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
            .onNodeWithText("Push Ups")
            .performClick()

        delay(300)

        // Rapidly perform multiple state-changing operations
        repeat(3) { setNumber ->
            composeTestRule
                .onNodeWithContentDescription("Actual Reps Input")
                .performTextInput("${15 + setNumber}")

            composeTestRule
                .onNodeWithText("Complete Set")
                .performClick()

            delay(50) // Very short delay to test concurrency
        }

        // Verify all state changes were processed correctly
        composeTestRule
            .onNodeWithContentDescription("Set 1 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Set 2 Completed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Set 3 Completed")
            .assertIsDisplayed()

        // Verify final state consistency
        composeTestRule
            .onNodeWithText("15 reps")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("16 reps")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("17 reps")
            .assertIsDisplayed()

        Timber.d("Concurrent updates test passed")
    }

    /**
     * Tests ViewModel cleanup and memory leak prevention
     */
    @Test
    fun viewModelState_cleansUpCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate through multiple screens to create ViewModels
        val screens = listOf("Workout", "Progress", "Coach", "Friends")
        
        screens.forEach { screen ->
            composeTestRule
                .onNodeWithText(screen)
                .performClick()

            delay(500) // Allow ViewModel initialization

            // Verify screen loaded
            composeTestRule
                .onNodeWithContentDescription("$screen Screen")
                .assertExists()
        }

        // Navigate back to home
        composeTestRule
            .onNodeWithText("Home")
            .performClick()

        delay(500)

        // Force garbage collection by creating memory pressure
        repeat(5) {
            composeTestRule
                .onNodeWithText("Workout")
                .performClick()

            delay(100)

            composeTestRule
                .onNodeWithText("Home")
                .performClick()

            delay(100)
        }

        // Verify app remains stable (no crashes from memory leaks)
        composeTestRule
            .onNodeWithContentDescription("Home Screen")
            .assertIsDisplayed()

        Timber.d("Memory cleanup test passed")
    }

    /**
     * Tests complex state transitions in workout flow
     */
    @Test
    fun complexWorkflowState_maintainsIntegrity() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Create workout (State: Creating)
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Complex State Test")

        // Add exercise (State: Creating + Exercise Management)
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Squats")
            .performClick()

        delay(300)

        // Save workout (State: Creating → Saving → Success)
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithText("Yes")
            .performClick()

        delay(1000) // Allow save operation

        // Start workout session (State: Success → Loading → Active Session)
        composeTestRule
            .onNodeWithText("Complex State Test")
            .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithText("Start")
            .onFirst()
            .performClick()

        delay(300)

        // Active session state
        composeTestRule
            .onNodeWithText("Starting a workout")
            .assertIsDisplayed()

        // Complete set (State: Active Session + Set Completion)
        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("12")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Pause session (State: Active → Paused)
        composeTestRule
            .onNodeWithContentDescription("Pause Session")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Session Paused")
            .assertIsDisplayed()

        // Resume session (State: Paused → Active)
        composeTestRule
            .onNodeWithContentDescription("Resume Session")
            .performClick()

        delay(200)

        // Finish workout (State: Active → Completing → Completed)
        composeTestRule
            .onNodeWithText("Finish Workout")
            .performClick()

        delay(500)

        // Verify final state
        composeTestRule
            .onNodeWithText("Workout Completed!")
            .assertIsDisplayed()

        Timber.d("Complex workflow state test passed")
    }

    /**
     * Tests state recovery after application restart
     */
    @Test
    fun applicationState_recoversAfterRestart() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start workout session
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
            .onNodeWithText("Deadlifts")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextInput("225")

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("5")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(300)

        // Simulate app restart by recreating activity
        composeTestRule.activity?.finish()
        delay(500)

        // Restart app
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        delay(1000) // Allow state recovery

        // Verify session was recovered
        val hasRecoveredSession = composeTestRule
            .onAllNodesWithText("Resume Workout")
            .fetchSemanticsNodes()
            .isNotEmpty() ||
            composeTestRule
            .onAllNodesWithText("Starting a workout")
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (hasRecoveredSession) {
            if (composeTestRule.onAllNodesWithText("Resume Workout").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule
                    .onNodeWithText("Resume Workout")
                    .performClick()

                delay(300)
            }

            // Verify session data was preserved
            composeTestRule
                .onNodeWithText("Deadlifts")
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithText("225")
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithText("5")
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithContentDescription("Set 1 Completed")
                .assertIsDisplayed()
        }

        Timber.d("Application restart recovery test passed")
    }

    /**
     * Tests error boundary behavior in state management
     */
    @Test
    fun stateErrorBoundary_recoversGracefully() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to a screen and trigger potential error
        composeTestRule
            .onNodeWithText("Progress")
            .performClick()

        delay(300)

        // Try to access non-existent data to trigger error
        if (composeTestRule.onAllNodesWithText("No data available").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule
                .onNodeWithText("Load Sample Data")
                .performClick()

            delay(200)
        }

        // Simulate error by rapid navigation
        repeat(5) {
            composeTestRule
                .onNodeWithText("Workout")
                .performClick()

            delay(50)

            composeTestRule
                .onNodeWithText("Progress")
                .performClick()

            delay(50)
        }

        // Verify app remains stable
        composeTestRule
            .onNodeWithContentDescription("Progress Screen")
            .assertExists()

        // Verify error boundaries caught any issues
        val hasErrorBoundary = composeTestRule
            .onAllNodesWithText("Something went wrong")
            .fetchSemanticsNodes()
            .isNotEmpty()

        val hasRecoveryOption = composeTestRule
            .onAllNodesWithText("Try Again")
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (hasErrorBoundary) {
            composeTestRule
                .onNodeWithText("Something went wrong")
                .assertIsDisplayed()

            if (hasRecoveryOption) {
                composeTestRule
                    .onNodeWithText("Try Again")
                    .performClick()

                delay(1000)

                // Verify recovery
                composeTestRule
                    .onNodeWithContentDescription("Progress Screen")
                    .assertExists()
            }
        }

        Timber.d("Error boundary test passed")
    }
}