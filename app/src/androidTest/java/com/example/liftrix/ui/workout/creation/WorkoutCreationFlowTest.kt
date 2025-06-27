package com.example.liftrix.ui.workout.creation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.repository.WorkoutRepository
import com.example.liftrix.domain.service.WeightMemoryService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the complete workout creation flow
 * Tests end-to-end functionality from exercise selection to workout saving
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutCreationFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: LiftrixDatabase

    @Inject
    lateinit var workoutRepository: WorkoutRepository

    @Inject
    lateinit var exerciseLibraryRepository: ExerciseLibraryRepository

    @Inject
    lateinit var weightMemoryService: WeightMemoryService

    @Inject
    lateinit var authRepository: AuthRepository

    private val testUserId = "test-user-integration"

    @Before
    fun setUp() {
        hiltRule.inject()
        
        // Set up test user authentication
        runTest {
            // Mock authenticated user for testing
            // This would typically be done through the auth repository mock
        }
    }

    @Test
    fun completeWorkoutCreation_savesSuccessfully() = runTest {
        // Navigate to workout creation screen
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .assertIsDisplayed()
            .performClick()

        // Verify workout creation screen is displayed
        composeTestRule.onNodeWithText("Create Workout")
            .assertIsDisplayed()

        // Enter workout name
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .performTextInput("Integration Test Workout")

        // Open exercise selector
        composeTestRule.onNodeWithText("Add Exercise")
            .assertIsDisplayed()
            .performClick()

        // Wait for exercise selector modal to appear
        composeTestRule.waitForIdle()

        // Verify exercise selector is displayed
        composeTestRule.onNodeWithText("Select Exercise")
            .assertIsDisplayed()

        // Search for an exercise
        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Bench Press")

        composeTestRule.waitForIdle()

        // Select first exercise from search results
        composeTestRule.onNodeWithText("Bench Press")
            .assertIsDisplayed()
            .performClick()

        // Verify exercise was added to workout
        composeTestRule.onNodeWithText("Bench Press")
            .assertIsDisplayed()

        // Add sets to the exercise
        composeTestRule.onNodeWithContentDescription("Add set to Bench Press")
            .performClick()

        // Fill in set details
        composeTestRule.onNodeWithContentDescription("Weight input for set 1")
            .performTextInput("80")

        composeTestRule.onNodeWithContentDescription("Reps input for set 1")
            .performTextInput("10")

        // Add another set
        composeTestRule.onNodeWithContentDescription("Add set to Bench Press")
            .performClick()

        composeTestRule.onNodeWithContentDescription("Weight input for set 2")
            .performTextInput("85")

        composeTestRule.onNodeWithContentDescription("Reps input for set 2")
            .performTextInput("8")

        // Add second exercise
        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Squats")

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Squats")
            .performClick()

        // Add sets to second exercise
        composeTestRule.onNodeWithContentDescription("Add set to Squats")
            .performClick()

        composeTestRule.onNodeWithContentDescription("Weight input for set 1")
            .performTextInput("100")

        composeTestRule.onNodeWithContentDescription("Reps input for set 1")
            .performTextInput("12")

        // Save the workout
        composeTestRule.onNodeWithText("Save Workout")
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitForIdle()

        // Verify workout was saved successfully
        composeTestRule.onNodeWithText("Workout saved successfully")
            .assertIsDisplayed()

        // Verify workout exists in database
        val savedWorkouts = workoutRepository.getAllWorkouts(testUserId).getOrNull()
        assertNotNull(savedWorkouts)
        assertTrue(savedWorkouts.isNotEmpty())
        
        val savedWorkout = savedWorkouts.first { it.name == "Integration Test Workout" }
        assertEquals("Integration Test Workout", savedWorkout.name)
        assertEquals(2, savedWorkout.exercises.size)
        
        // Verify exercise details
        val benchPressExercise = savedWorkout.exercises.find { it.name == "Bench Press" }
        assertNotNull(benchPressExercise)
        assertEquals(2, benchPressExercise.sets.size)
        
        val squatsExercise = savedWorkout.exercises.find { it.name == "Squats" }
        assertNotNull(squatsExercise)
        assertEquals(1, squatsExercise.sets.size)
    }

    @Test
    fun exerciseSelection_addsToWorkout() = runTest {
        // Navigate to workout creation
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .performClick()

        // Open exercise selector
        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        // Select exercise from recent exercises section
        composeTestRule.onNodeWithTag("recent_exercises_section")
            .assertIsDisplayed()

        // If no recent exercises, search for one
        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Deadlift")

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Deadlift")
            .performClick()

        // Verify exercise was added
        composeTestRule.onNodeWithText("Deadlift")
            .assertIsDisplayed()

        // Verify exercise card shows proper structure
        composeTestRule.onNodeWithContentDescription("Exercise card for Deadlift")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Add set to Deadlift")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun setInput_validatesCorrectly() = runTest {
        // Add exercise first
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .performClick()

        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Push-ups")

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Push-ups")
            .performClick()

        // Add a set
        composeTestRule.onNodeWithContentDescription("Add set to Push-ups")
            .performClick()

        // Test invalid weight input
        composeTestRule.onNodeWithContentDescription("Weight input for set 1")
            .performTextInput("abc")

        // Verify validation error
        composeTestRule.onNodeWithText("Invalid weight")
            .assertIsDisplayed()

        // Test invalid reps input
        composeTestRule.onNodeWithContentDescription("Reps input for set 1")
            .performTextInput("-5")

        // Verify validation error
        composeTestRule.onNodeWithText("Reps must be positive")
            .assertIsDisplayed()

        // Test valid inputs
        composeTestRule.onNodeWithContentDescription("Weight input for set 1")
            .performTextInput("0") // Clear invalid input

        composeTestRule.onNodeWithContentDescription("Reps input for set 1")
            .performTextInput("15")

        // Verify no validation errors
        composeTestRule.onNodeWithText("Invalid weight")
            .assertDoesNotExist()

        composeTestRule.onNodeWithText("Reps must be positive")
            .assertDoesNotExist()
    }

    @Test
    fun weightMemory_prePopulates() = runTest {
        // Set up weight memory for an exercise
        weightMemoryService.updateExerciseWeight(
            userId = testUserId,
            exerciseId = "bench-press",
            weight = 75.0f
        )

        // Navigate to workout creation
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .performClick()

        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        // Search and select bench press
        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Bench Press")

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Bench Press")
            .performClick()

        // Add a set
        composeTestRule.onNodeWithContentDescription("Add set to Bench Press")
            .performClick()

        // Verify weight is pre-populated from memory
        composeTestRule.onNodeWithText("75")
            .assertIsDisplayed()
    }

    @Test
    fun workoutValidation_preventsInvalidSave() = runTest {
        // Navigate to workout creation
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .performClick()

        // Try to save empty workout
        composeTestRule.onNodeWithText("Save Workout")
            .assertIsNotEnabled()

        // Add workout name only
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .performTextInput("Empty Workout")

        // Save button should still be disabled (no exercises)
        composeTestRule.onNodeWithText("Save Workout")
            .assertIsNotEnabled()

        // Add exercise but no sets
        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Curls")

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Curls")
            .performClick()

        // Save button should still be disabled (no sets)
        composeTestRule.onNodeWithText("Save Workout")
            .assertIsNotEnabled()

        // Add a set with valid data
        composeTestRule.onNodeWithContentDescription("Add set to Curls")
            .performClick()

        composeTestRule.onNodeWithContentDescription("Weight input for set 1")
            .performTextInput("20")

        composeTestRule.onNodeWithContentDescription("Reps input for set 1")
            .performTextInput("12")

        // Now save button should be enabled
        composeTestRule.onNodeWithText("Save Workout")
            .assertIsEnabled()
    }

    @Test
    fun exerciseSearch_respondsQuickly() = runTest {
        // Navigate to exercise selector
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .performClick()

        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        val startTime = System.currentTimeMillis()

        // Perform search
        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("Bench")

        composeTestRule.waitForIdle()

        val endTime = System.currentTimeMillis()
        val searchTime = endTime - startTime

        // Verify search responds within 200ms requirement
        assertTrue(searchTime < 200, "Search took ${searchTime}ms, should be under 200ms")

        // Verify search results are displayed
        composeTestRule.onNodeWithText("Bench Press")
            .assertIsDisplayed()
    }

    @Test
    fun equipmentFiltering_worksCorrectly() = runTest {
        // Navigate to exercise selector
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .performClick()

        composeTestRule.onNodeWithText("Add Exercise")
            .performClick()

        composeTestRule.waitForIdle()

        // Apply equipment filter
        composeTestRule.onNodeWithText("Barbell")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify only barbell exercises are shown
        composeTestRule.onNodeWithText("Bench Press")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Squats")
            .assertIsDisplayed()

        // Push-ups (bodyweight) should not be visible
        composeTestRule.onNodeWithText("Push-ups")
            .assertDoesNotExist()

        // Remove filter
        composeTestRule.onNodeWithText("Barbell")
            .performClick()

        composeTestRule.waitForIdle()

        // Now bodyweight exercises should be visible again
        composeTestRule.onNodeWithText("Push-ups")
            .assertIsDisplayed()
    }
} 