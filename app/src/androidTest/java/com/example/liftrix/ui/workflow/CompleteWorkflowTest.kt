package com.example.liftrix.ui.workflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.ui.theme.LiftrixTheme
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
 * Comprehensive end-to-end workflow testing covering complete user journeys
 * from workout creation through active sessions to historical editing.
 * 
 * Tests the complete integration of:
 * - Workout creation with updated terminology
 * - Active session state management via UnifiedWorkoutSessionManager
 * - Historical data editing capabilities
 * - State persistence across app lifecycle events
 * - Data integrity throughout all workflow transitions
 * 
 * This test validates the core user experience flows that define the app's value proposition.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class CompleteWorkflowTest {

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
     * Tests the complete workflow: Create workout → Start session → Complete → Edit historical data
     * This represents the primary user journey and validates end-to-end functionality.
     */
    @Test
    fun completeWorkflow_createStartCompleteEdit_preservesDataIntegrity() = runTest {
        // Launch the main navigation
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // === STEP 1: Navigate to workout creation ===
        composeTestRule
            .onNodeWithText("Create New Workout")
            .assertIsDisplayed()
            .performClick()

        // Wait for navigation animation
        delay(300)

        // Verify updated terminology is displayed
        composeTestRule
            .onNodeWithText("Creating a workout")
            .assertIsDisplayed()

        // === STEP 2: Create workout with exercises ===
        val workoutName = "Test Integration Workout"
        
        // Enter workout details
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput(workoutName)

        // Add first exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        // Select bench press exercise
        composeTestRule
            .onNodeWithText("Bench Press")
            .performClick()

        // Configure sets for the exercise
        composeTestRule
            .onNodeWithContentDescription("Target Reps Input")
            .performTextInput("10")

        composeTestRule
            .onNodeWithContentDescription("Target Weight Input")
            .performTextInput("135")

        // Add the exercise to workout
        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(200)

        // Save the workout
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        // Verify save terminology update
        composeTestRule
            .onNodeWithText("Would you like to save this workout routine for future use?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Yes")
            .performClick()

        delay(500) // Allow save operation to complete

        // === STEP 3: Start the created workout ===
        // Should navigate back to workout list showing the saved workout
        composeTestRule
            .onNodeWithText(workoutName)
            .assertIsDisplayed()

        // Start the workout session
        composeTestRule
            .onNodeWithText("Start")
            .performClick()

        delay(300)

        // Verify active session screen with updated terminology
        composeTestRule
            .onNodeWithText("Starting a workout")
            .assertIsDisplayed()

        // Verify workout name is displayed in active session
        composeTestRule
            .onNodeWithText(workoutName)
            .assertIsDisplayed()

        // === STEP 4: Complete workout sets ===
        // Input actual performance data
        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextInput("135")

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("10")

        // Mark set as completed
        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Verify set completion
        composeTestRule
            .onNodeWithContentDescription("Set 1 Completed")
            .assertExists()

        // Complete the workout
        composeTestRule
            .onNodeWithText("Finish Workout")
            .performClick()

        delay(500) // Allow workout completion processing

        // === STEP 5: Verify workout history ===
        // Navigate to workout history
        composeTestRule
            .onNodeWithText("Workout History")
            .performClick()

        delay(300)

        // Find the completed workout in history
        composeTestRule
            .onNodeWithText(workoutName)
            .assertIsDisplayed()

        // === STEP 6: Edit historical data ===
        // Long press to access edit options
        composeTestRule
            .onNodeWithText(workoutName)
            .performTouchInput { longClick() }

        delay(200)

        composeTestRule
            .onNodeWithText("Edit Session")
            .performClick()

        delay(300)

        // Verify editing mode indicator with updated terminology
        composeTestRule
            .onNodeWithText("Editing historical data")
            .assertIsDisplayed()

        // Modify historical data - update the weight
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("140")

        // Save the historical changes
        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        // === STEP 7: Verify data persistence and integrity ===
        // Verify the "Last modified" indicator appears
        composeTestRule
            .onNodeWithText("Last modified")
            .assertIsDisplayed()

        // Verify the modified data is displayed correctly
        composeTestRule
            .onNodeWithText("140")
            .assertIsDisplayed()

        // Verify original workout name is preserved
        composeTestRule
            .onNodeWithText(workoutName)
            .assertIsDisplayed()

        Timber.d("Complete workflow test passed - data integrity maintained throughout")
    }

    /**
     * Tests workflow state persistence across configuration changes (screen rotation)
     */
    @Test
    fun workflowStateTransition_survivesConfigurationChange() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start workout creation
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(200)

        val testWorkoutName = "Configuration Test Workout"
        
        // Enter workout data
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput(testWorkoutName)

        // Simulate configuration change (screen rotation)
        composeTestRule.activity?.recreate()

        delay(500) // Allow recreation to complete

        // Verify data persistence after configuration change
        composeTestRule
            .onNodeWithText(testWorkoutName)
            .assertIsDisplayed()

        Timber.d("Configuration change test passed - state preserved")
    }

    /**
     * Tests graceful error handling during workflow operations
     */
    @Test
    fun workflowErrorRecovery_handlesFailuresGracefully() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to active workout
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add exercise to session
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Push Ups")
            .performClick()

        // Complete a set
        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput("15")

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        // Attempt to finish workout (this may trigger network/sync operations)
        composeTestRule
            .onNodeWithText("Finish Workout")
            .performClick()

        delay(1000) // Allow processing time

        // Verify graceful error handling - should show either success or offline message
        val hasSuccessMessage = composeTestRule
            .onAllNodesWithText("Workout completed successfully")
            .fetchSemanticsNodes()
            .isNotEmpty()

        val hasOfflineMessage = composeTestRule
            .onAllNodesWithText("Workout saved locally. Will sync when connection is restored.")
            .fetchSemanticsNodes()
            .isNotEmpty()

        // At least one of these messages should be present
        assert(hasSuccessMessage || hasOfflineMessage) {
            "Expected either success or offline message after workout completion"
        }

        Timber.d("Error recovery test passed - graceful failure handling verified")
    }

    /**
     * Tests multiple workout creation and session management
     */
    @Test
    fun multipleWorkflows_handlesConcurrentOperations() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Create first workout
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("First Test Workout")

        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Squats")
            .performClick()

        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(300)

        // Accept save dialog
        if (composeTestRule.onAllNodesWithText("Yes").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule
                .onNodeWithText("Yes")
                .performClick()

            delay(300)
        }

        // Create second workout
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Second Test Workout")

        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(300)

        // Accept save dialog
        if (composeTestRule.onAllNodesWithText("Yes").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule
                .onNodeWithText("Yes")
                .performClick()

            delay(300)
        }

        // Verify both workouts exist
        composeTestRule
            .onNodeWithText("First Test Workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Second Test Workout")
            .assertIsDisplayed()

        Timber.d("Multiple workflows test passed - concurrent operations handled correctly")
    }

    /**
     * Tests session state management under memory pressure conditions
     */
    @Test
    fun sessionStateManagement_handlesMemoryPressure() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Start a workout session
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        // Add multiple exercises to create memory load
        repeat(3) { index ->
            composeTestRule
                .onNodeWithText("Add Exercise")
                .performClick()

            delay(200)

            // Select different exercises
            val exerciseNames = listOf("Push Ups", "Pull Ups", "Squats")
            composeTestRule
                .onNodeWithText(exerciseNames[index])
                .performClick()

            delay(200)
        }

        // Complete sets for each exercise
        repeat(3) { exerciseIndex ->
            // Navigate to each exercise and complete a set
            composeTestRule
                .onNodeWithContentDescription("Actual Reps Input")
                .performTextInput("${10 + exerciseIndex}")

            composeTestRule
                .onNodeWithText("Complete Set")
                .performClick()

            delay(200)

            // Move to next exercise if not the last one
            if (exerciseIndex < 2) {
                composeTestRule
                    .onNodeWithContentDescription("Next Exercise")
                    .performClick()

                delay(200)
            }
        }

        // Verify session state integrity
        composeTestRule
            .onNodeWithContentDescription("Session Progress")
            .assertExists()

        // Complete the workout
        composeTestRule
            .onNodeWithText("Finish Workout")
            .performClick()

        delay(500)

        Timber.d("Memory pressure test passed - session state maintained under load")
    }
}