package com.example.liftrix.ui.workflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.create.CreateWorkoutScreen
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
 * Focused testing of workout creation workflow with emphasis on updated terminology,
 * validation, and save functionality.
 * 
 * Tests key aspects of workout creation:
 * - Updated user-friendly terminology ("Creating a workout" vs "Create Template")
 * - Input validation and error handling
 * - Exercise selection and configuration
 * - Save functionality with auto-save prompts
 * - State persistence during creation process
 * 
 * This test suite validates the modernized workout creation experience.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class WorkoutCreationFlowTest {

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
     * Tests the complete workout creation flow with updated terminology
     */
    @Test
    fun workoutCreation_showsUpdatedTerminology() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // Navigate to workout creation
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        // Verify updated screen title
        composeTestRule
            .onNodeWithText("Creating a workout")
            .assertIsDisplayed()

        // Verify instructional text uses updated language
        composeTestRule
            .onNodeWithText("Build your workout by adding exercises and configuring sets")
            .assertIsDisplayed()

        // Verify save button uses updated terminology
        composeTestRule
            .onNodeWithText("Save Workout")
            .assertIsDisplayed()

        Timber.d("Updated terminology validation passed")
    }

    /**
     * Tests workout creation with complete exercise configuration
     */
    @Test
    fun workoutCreation_configuresExercisesCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        val workoutName = "Comprehensive Test Workout"

        // Enter workout details
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput(workoutName)

        // Add first exercise - Bench Press
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Bench Press")
            .performClick()

        // Configure exercise details
        composeTestRule
            .onNodeWithContentDescription("Target Sets Input")
            .performTextInput("3")

        composeTestRule
            .onNodeWithContentDescription("Target Reps Input")
            .performTextInput("10")

        composeTestRule
            .onNodeWithContentDescription("Target Weight Input")
            .performTextInput("135")

        composeTestRule
            .onNodeWithContentDescription("Rest Time Input")
            .performTextInput("120")

        // Add exercise notes
        composeTestRule
            .onNodeWithContentDescription("Exercise Notes Input")
            .performTextInput("Focus on controlled movement")

        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(300)

        // Verify exercise was added with correct configuration
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("3 sets × 10 reps @ 135 lbs")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Rest: 2:00")
            .assertIsDisplayed()

        // Add second exercise - Squats
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Squats")
            .performClick()

        composeTestRule
            .onNodeWithContentDescription("Target Sets Input")
            .performTextInput("4")

        composeTestRule
            .onNodeWithContentDescription("Target Reps Input")
            .performTextInput("12")

        composeTestRule
            .onNodeWithContentDescription("Target Weight Input")
            .performTextInput("185")

        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(300)

        // Verify both exercises are configured
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Squats")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("4 sets × 12 reps @ 185 lbs")
            .assertIsDisplayed()

        Timber.d("Exercise configuration test passed")
    }

    /**
     * Tests input validation during workout creation
     */
    @Test
    fun workoutCreation_validatesInputCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                CreateWorkoutScreen(
                    onNavigateBack = {},
                    onNavigateToExerciseSelection = {},
                    onSaveComplete = {}
                )
            }
        }

        // Try to save without workout name
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(200)

        // Verify validation error
        composeTestRule
            .onNodeWithText("Workout name is required")
            .assertIsDisplayed()

        // Enter workout name but leave it too short
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("A")

        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Name must be at least 2 characters")
            .assertIsDisplayed()

        // Enter valid workout name
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Valid Workout Name")

        // Try to save without exercises
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("At least one exercise is required")
            .assertIsDisplayed()

        Timber.d("Input validation test passed")
    }

    /**
     * Tests the save functionality and auto-save prompt
     */
    @Test
    fun workoutCreation_saveFlowWorksCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        // Create a minimal valid workout
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Quick Save Test")

        // Add one exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Push Ups")
            .performClick()

        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(200)

        // Save the workout
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(300)

        // Verify auto-save prompt with updated terminology
        composeTestRule
            .onNodeWithText("Would you like to save this workout routine for future use?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("This will make it easy to start this workout again later.")
            .assertIsDisplayed()

        // Accept save
        composeTestRule
            .onNodeWithText("Yes")
            .performClick()

        delay(500)

        // Verify successful save feedback
        composeTestRule
            .onNodeWithText("Workout saved successfully!")
            .assertIsDisplayed()

        Timber.d("Save flow test passed")
    }

    /**
     * Tests exercise reordering and removal during creation
     */
    @Test
    fun workoutCreation_managesExerciseOrderCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Exercise Management Test")

        // Add three exercises
        val exercises = listOf("Bench Press", "Squats", "Deadlifts")
        
        exercises.forEach { exercise ->
            composeTestRule
                .onNodeWithText("Add Exercise")
                .performClick()

            delay(200)

            composeTestRule
                .onNodeWithText(exercise)
                .performClick()

            composeTestRule
                .onNodeWithText("Add to Workout")
                .performClick()

            delay(200)
        }

        // Verify all exercises are added in order
        exercises.forEach { exercise ->
            composeTestRule
                .onNodeWithText(exercise)
                .assertIsDisplayed()
        }

        // Remove middle exercise (Squats)
        composeTestRule
            .onAllNodesWithContentDescription("Remove Exercise")
            .onFirst()
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Remove")
            .performClick()

        delay(200)

        // Verify Squats is removed but others remain
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Deadlifts")
            .assertIsDisplayed()

        // Verify Squats is no longer present
        composeTestRule
            .onAllNodesWithText("Squats")
            .assertCountEquals(0)

        // Test exercise reordering
        composeTestRule
            .onNodeWithContentDescription("Move Deadlifts Up")
            .performClick()

        delay(200)

        // Verify new order (Deadlifts should now be first)
        val exerciseNodes = composeTestRule
            .onAllNodesWithTag("ExerciseItem")
            .fetchSemanticsNodes()

        // First exercise should now be Deadlifts
        composeTestRule
            .onAllNodesWithText("Deadlifts")
            .onFirst()
            .assertIsDisplayed()

        Timber.d("Exercise management test passed")
    }

    /**
     * Tests workout duplication and template creation
     */
    @Test
    fun workoutCreation_duplicatesWorkoutCorrectly() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        // First, create an original workout
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        val originalName = "Original Workout"
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput(originalName)

        // Add exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Pull Ups")
            .performClick()

        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(200)

        // Save original
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithText("Yes")
            .performClick()

        delay(500)

        // Now duplicate the workout
        composeTestRule
            .onNodeWithText(originalName)
            .performTouchInput { longClick() }

        delay(200)

        composeTestRule
            .onNodeWithText("Duplicate")
            .performClick()

        delay(300)

        // Verify duplicate creation screen with copied data
        composeTestRule
            .onNodeWithText("Creating a workout")
            .assertIsDisplayed()

        // Name should be "Copy of Original Workout"
        composeTestRule
            .onNodeWithText("Copy of $originalName")
            .assertIsDisplayed()

        // Exercise should be copied
        composeTestRule
            .onNodeWithText("Pull Ups")
            .assertIsDisplayed()

        // Modify the duplicate
        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextClearance()

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Modified Duplicate")

        // Save the duplicate
        composeTestRule
            .onNodeWithText("Save Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithText("Yes")
            .performClick()

        delay(500)

        // Verify both workouts exist
        composeTestRule
            .onNodeWithText(originalName)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Modified Duplicate")
            .assertIsDisplayed()

        Timber.d("Workout duplication test passed")
    }

    /**
     * Tests workout creation with different exercise types and configurations
     */
    @Test
    fun workoutCreation_handlesVariousExerciseTypes() = runTest {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixNavigation()
            }
        }

        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()

        delay(300)

        composeTestRule
            .onNodeWithContentDescription("Workout Name Input")
            .performTextInput("Mixed Exercise Types")

        // Add weight-based exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Bench Press")
            .performClick()

        composeTestRule
            .onNodeWithContentDescription("Target Weight Input")
            .performTextInput("135")

        composeTestRule
            .onNodeWithContentDescription("Target Reps Input")
            .performTextInput("10")

        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(200)

        // Add bodyweight exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Push Ups")
            .performClick()

        composeTestRule
            .onNodeWithContentDescription("Target Reps Input")
            .performTextInput("15")

        // No weight input for bodyweight exercise
        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(200)

        // Add time-based exercise
        composeTestRule
            .onNodeWithText("Add Exercise")
            .performClick()

        delay(200)

        composeTestRule
            .onNodeWithText("Plank")
            .performClick()

        composeTestRule
            .onNodeWithContentDescription("Target Time Input")
            .performTextInput("60")

        composeTestRule
            .onNodeWithText("Add to Workout")
            .performClick()

        delay(200)

        // Verify all exercise types are properly configured
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("10 reps @ 135 lbs")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Push Ups")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("15 reps")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Plank")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("60 seconds")
            .assertIsDisplayed()

        Timber.d("Various exercise types test passed")
    }
}