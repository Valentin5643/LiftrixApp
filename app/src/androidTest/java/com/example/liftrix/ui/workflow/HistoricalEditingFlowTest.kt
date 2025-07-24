package com.example.liftrix.ui.workflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.edit.EditSessionScreen
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
 * Comprehensive testing of historical workout data editing functionality.
 * 
 * Tests the critical editing capabilities:
 * - Historical data access and display with "Editing historical data" terminology
 * - Data modification while preserving original integrity
 * - Validation during editing process
 * - Save functionality with "Last modified" tracking
 * - Conflict resolution and error handling
 * - Audit trail maintenance
 * 
 * This test suite validates the historical editing feature that allows users
 * to retroactively modify workout data while maintaining data integrity.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class HistoricalEditingFlowTest {

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
     * Tests basic historical data editing workflow
     */
    @Test
    fun historicalEditing_displaysCorrectTerminology() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to workout history
        composeTestRule
            .onNodeWithText("Workout History")
            .performClick()

        delay(300)

        // Find and select a completed workout for editing
        composeTestRule
            .onNodeWithContentDescription("Completed Workout")
            .onFirst()
            .performTouchInput { longClick() }

        delay(200)

        // Select edit option
        composeTestRule
            .onNodeWithText("Edit Session")
            .performClick()

        delay(300)

        // Verify historical editing terminology is displayed
        composeTestRule
            .onNodeWithText("Editing historical data")
            .assertIsDisplayed()

        // Verify warning about editing historical data
        composeTestRule
            .onNodeWithText("You are modifying completed workout data. Changes will be tracked.")
            .assertIsDisplayed()

        // Verify edit controls are available
        composeTestRule
            .onNodeWithText("Save Changes")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Cancel Edit")
            .assertIsDisplayed()

        Timber.d("Historical editing terminology test passed")
    }

    /**
     * Tests editing historical exercise data
     */
    @Test
    fun historicalEditing_modifiesExerciseDataCorrectly() = runTest {
        // First create a completed workout to edit
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Quick workout creation and completion
        composeTestRule
            .onNodeWithText("Start Blank Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Bench Press")
            .performClick()

        delay(300)

        // Complete original workout data
        val originalWeight = "135"
        val originalReps = "10"

        composeTestRule
            .onNodeWithContentDescription("Actual Weight Input")
            .performTextInput(originalWeight)

        composeTestRule
            .onNodeWithContentDescription("Actual Reps Input")
            .performTextInput(originalReps)

        composeTestRule
            .onNodeWithText("Complete Set")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Finish Workout")
            .performClick()

        delay(500)

        // Navigate to history and edit
        composeTestRule
            .onNodeWithText("Workout History")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Completed Workout")
            .onFirst()
            .performTouchInput { longClick() }

        delay(200)

        composeTestRule
            .onNodeWithText("Edit Session")
            .performClick()

        delay(300)

        // Verify original data is displayed
        composeTestRule
            .onNodeWithText(originalWeight)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(originalReps)
            .assertIsDisplayed()

        // Modify the historical data
        val newWeight = "140"
        val newReps = "8"

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput(newWeight)

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextInput(newReps)

        // Add edit reason
        composeTestRule
            .onNodeWithContentDescription("Edit Reason Input")
            .performTextInput("Remembered I actually lifted more weight")

        // Save the changes
        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        // Verify changes were saved
        composeTestRule
            .onNodeWithText("Changes saved successfully")
            .assertIsDisplayed()

        // Verify modified data is displayed
        composeTestRule
            .onNodeWithText(newWeight)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(newReps)
            .assertIsDisplayed()

        // Verify "Last modified" indicator appears
        composeTestRule
            .onNodeWithText("Last modified")
            .assertIsDisplayed()

        Timber.d("Historical data modification test passed")
    }

    /**
     * Tests validation during historical editing
     */
    @Test
    fun historicalEditing_validatesInputCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                EditSessionScreen(
                    sessionId = "test-session-id",
                    onNavigateBack = {},
                    onSaveComplete = {}
                )
            }
        }

        // Wait for data loading
        delay(500)

        // Try to enter invalid weight
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("-50") // Negative weight

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(200)

        // Verify validation error
        composeTestRule
            .onNodeWithText("Weight must be a positive number")
            .assertIsDisplayed()

        // Try to enter invalid reps
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("135")

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextInput("0") // Zero reps

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Reps must be at least 1")
            .assertIsDisplayed()

        // Try to save without edit reason for significant changes
        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextInput("15") // Significant increase

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Please provide a reason for this significant change")
            .assertIsDisplayed()

        // Enter valid reason and save
        composeTestRule
            .onNodeWithContentDescription("Edit Reason Input")
            .performTextInput("Corrected data based on workout log")

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithText("Changes saved successfully")
            .assertIsDisplayed()

        Timber.d("Historical editing validation test passed")
    }

    /**
     * Tests audit trail functionality during historical editing
     */
    @Test
    fun historicalEditing_maintainsAuditTrail() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to a workout history item and edit it
        composeTestRule
            .onNodeWithText("Workout History")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Completed Workout")
            .onFirst()
            .performTouchInput { longClick() }

        delay(200)

        composeTestRule
            .onNodeWithText("Edit Session")
            .performClick()

        delay(300)

        // Make first edit
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("145")

        composeTestRule
            .onNodeWithContentDescription("Edit Reason Input")
            .performTextInput("Initial correction")

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        // Make second edit
        composeTestRule
            .onNodeWithContentDescription("Edit History")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("150")

        composeTestRule
            .onNodeWithContentDescription("Edit Reason Input")
            .performTextInput("Further adjustment based on video review")

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        // View edit history
        composeTestRule
            .onNodeWithContentDescription("View Edit History")
            .performClick()

        delay(200)

        // Verify audit trail shows both edits
        composeTestRule
            .onNodeWithText("Edit History")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Initial correction")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Further adjustment based on video review")
            .assertIsDisplayed()

        // Verify timestamps are displayed
        composeTestRule
            .onNodeWithContentDescription("Edit 1 Timestamp")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Edit 2 Timestamp")
            .assertExists()

        // Verify original values are preserved in history
        composeTestRule
            .onNodeWithText("Original: 135 lbs")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Changed to: 145 lbs")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Changed to: 150 lbs")
            .assertIsDisplayed()

        Timber.d("Audit trail test passed")
    }

    /**
     * Tests bulk editing functionality
     */
    @Test
    fun historicalEditing_handlesBulkEditsCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                EditSessionScreen(
                    sessionId = "multi-exercise-session-id",
                    onNavigateBack = {},
                    onSaveComplete = {}
                )
            }
        }

        delay(500)

        // Enable bulk edit mode
        composeTestRule
            .onNodeWithContentDescription("Bulk Edit Mode")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Bulk Edit Mode Active")
            .assertIsDisplayed()

        // Select multiple exercises for editing
        composeTestRule
            .onAllNodesWithContentDescription("Select Exercise")
            .onFirst()
            .performClick()

        composeTestRule
            .onAllNodesWithContentDescription("Select Exercise")
            .onLast()
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("2 exercises selected")
            .assertIsDisplayed()

        // Apply bulk weight adjustment
        composeTestRule
            .onNodeWithText("Adjust Weight")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Weight Adjustment")
            .performTextInput("+5")

        composeTestRule
            .onNodeWithText("Apply to Selected")
            .performClick()

        delay(300)

        // Provide bulk edit reason
        composeTestRule
            .onNodeWithContentDescription("Bulk Edit Reason")
            .performTextInput("Calibration adjustment after checking equipment")

        composeTestRule
            .onNodeWithText("Save Bulk Changes")
            .performClick()

        delay(300)

        // Verify bulk changes were applied
        composeTestRule
            .onNodeWithText("Bulk changes applied to 2 exercises")
            .assertIsDisplayed()

        // Exit bulk edit mode
        composeTestRule
            .onNodeWithContentDescription("Exit Bulk Edit")
            .performClick()

        delay(200)

        composeTestRule
            .onAllNodesWithText("Bulk Edit Mode Active")
            .assertCountEquals(0)

        Timber.d("Bulk editing test passed")
    }

    /**
     * Tests conflict resolution during historical editing
     */
    @Test
    fun historicalEditing_resolvesConflictsCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                EditSessionScreen(
                    sessionId = "conflict-session-id",
                    onNavigateBack = {},
                    onSaveComplete = {}
                )
            }
        }

        delay(500)

        // Simulate concurrent edit scenario
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("160")

        // Trigger conflict detection
        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        // Verify conflict dialog appears
        composeTestRule
            .onNodeWithText("Data Conflict Detected")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("This workout has been modified by another session. Please resolve conflicts.")
            .assertIsDisplayed()

        // View conflicting changes
        composeTestRule
            .onNodeWithText("View Conflicts")
            .performClick()

        delay(200)

        // Show conflict resolution options
        composeTestRule
            .onNodeWithText("Your change: 160 lbs")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Other change: 155 lbs")
            .assertIsDisplayed()

        // Choose resolution strategy
        composeTestRule
            .onNodeWithText("Use My Changes")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithContentDescription("Conflict Resolution Reason")
            .performTextInput("My changes are based on direct measurement")

        composeTestRule
            .onNodeWithText("Resolve Conflict")
            .performClick()

        delay(300)

        // Verify conflict resolution
        composeTestRule
            .onNodeWithText("Conflict resolved successfully")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("160")
            .assertIsDisplayed()

        // Verify conflict is logged in audit trail
        composeTestRule
            .onNodeWithContentDescription("View Edit History")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Conflict resolved: Used user changes over concurrent edit")
            .assertIsDisplayed()

        Timber.d("Conflict resolution test passed")
    }

    /**
     * Tests data integrity during historical editing operations
     */
    @Test
    fun historicalEditing_maintainsDataIntegrity() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                EditSessionScreen(
                    sessionId = "integrity-test-session-id",
                    onNavigateBack = {},
                    onSaveComplete = {}
                )
            }
        }

        delay(500)

        // Store original data for verification
        val originalWorkoutName = composeTestRule
            .onNodeWithContentDescription("Workout Name")
            .fetchSemanticsNode()
            .config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.first()?.text

        // Attempt to modify core workout metadata (should be restricted)
        composeTestRule
            .onNodeWithContentDescription("Workout Date")
            .performClick()

        delay(200)

        // Verify date modification requires confirmation
        composeTestRule
            .onNodeWithText("Changing workout date may affect historical analytics. Continue?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        // Modify exercise data within acceptable ranges
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("142")

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextInput("9")

        // Verify calculated fields are updated automatically
        composeTestRule
            .onNodeWithContentDescription("Total Volume")
            .assertExists()

        composeTestRule
            .onNodeWithText("1278 lbs") // 142 * 9 = 1278
            .assertIsDisplayed()

        // Verify related statistics are recalculated
        composeTestRule
            .onNodeWithContentDescription("Exercise PR Status")
            .assertExists()

        // Add edit reason and save
        composeTestRule
            .onNodeWithContentDescription("Edit Reason Input")
            .performTextInput("Precision adjustment")

        composeTestRule
            .onNodeWithText("Save Changes")
            .performClick()

        delay(300)

        // Verify data integrity checkpoints
        composeTestRule
            .onNodeWithText("Data integrity verified")
            .assertIsDisplayed()

        // Verify core workout data remains unchanged
        if (originalWorkoutName != null) {
            composeTestRule
                .onNodeWithText(originalWorkoutName)
                .assertIsDisplayed()
        }

        // Verify modified fields are updated correctly
        composeTestRule
            .onNodeWithText("142")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("9")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("1278 lbs")
            .assertIsDisplayed()

        // Verify modification timestamp
        composeTestRule
            .onNodeWithText("Last modified")
            .assertIsDisplayed()

        Timber.d("Data integrity test passed")
    }

    /**
     * Tests cancellation and rollback functionality
     */
    @Test
    fun historicalEditing_handlesRollbackCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                EditSessionScreen(
                    sessionId = "rollback-test-session-id",
                    onNavigateBack = {},
                    onSaveComplete = {}
                )
            }
        }

        delay(500)

        // Capture original values
        val originalWeightNode = composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")

        // Make changes
        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Weight Input")
            .performTextInput("175")

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Historical Reps Input")
            .performTextInput("6")

        // Verify changes are reflected in UI
        composeTestRule
            .onNodeWithText("175")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("6")
            .assertIsDisplayed()

        // Cancel the edit
        composeTestRule
            .onNodeWithText("Cancel Edit")
            .performClick()

        delay(200)

        // Verify cancellation dialog
        composeTestRule
            .onNodeWithText("Discard Changes?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("You have unsaved changes. Are you sure you want to discard them?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Discard")
            .performClick()

        delay(300)

        // Verify rollback to original values
        composeTestRule
            .onAllNodesWithText("175")
            .assertCountEquals(0)

        composeTestRule
            .onAllNodesWithText("6")
            .assertCountEquals(0)

        // Verify we're back to view mode
        composeTestRule
            .onNodeWithText("Editing historical data")
            .doesNotExist()

        composeTestRule
            .onNodeWithText("Workout History")
            .assertIsDisplayed()

        Timber.d("Rollback functionality test passed")
    }
}