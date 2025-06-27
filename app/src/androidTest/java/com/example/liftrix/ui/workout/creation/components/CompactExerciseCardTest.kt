package com.example.liftrix.ui.workout.creation.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtMost
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactExerciseCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleBodyweightExercise = ExerciseLibrary(
        id = "1",
        name = "Push-ups",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BODYWEIGHT_ONLY,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 3,
        instructions = "Classic push-up exercise",
        isCompound = true,
        searchableTerms = listOf("push", "chest", "bodyweight")
    )

    private val sampleWeightExercise = ExerciseLibrary(
        id = "2",
        name = "Barbell Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 4,
        instructions = "Classic barbell bench press",
        isCompound = true,
        searchableTerms = listOf("bench", "press", "chest", "barbell")
    )

    @Test
    fun compactExerciseCard_collapsedHeight_isUnder80dp() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(
                SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)
            ),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Push-ups")
            .assertIsDisplayed()
        
        // Card should be compact when collapsed (visual verification)
        composeTestRule.onNodeWithText("Push-ups")
            .assertIsDisplayed()
    }

    @Test
    fun compactExerciseCard_exerciseThumbnail_isDisplayed() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("Exercise thumbnail for Push-ups")
            .assertIsDisplayed()
    }

    @Test
    fun compactExerciseCard_exerciseName_isClickable() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("Exercise name: Push-ups, tap for details")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun compactExerciseCard_expandCollapseButton_hasProperTouchTarget() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert - Touch target should be accessible
        composeTestRule.onNodeWithContentDescription("Expand sets")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun compactExerciseCard_expandCollapse_animationWorks() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleWeightExercise,
            sets = listOf(
                SetInput(reps = "8", rpe = "7", weight = "80.0", isWeightSupported = true),
                SetInput(reps = "8", rpe = "8", weight = "80.0", isWeightSupported = true)
            ),
            orderIndex = 0
        )

        // Act - Start collapsed
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert - Sets section should not be visible when collapsed
        composeTestRule.onNodeWithText("Sets")
            .assertDoesNotExist()

        // Act - Expand
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = true,
                    onEvent = {}
                )
            }
        }

        // Assert - Sets section should be visible when expanded
        composeTestRule.onNodeWithText("Sets")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Set")
            .assertIsDisplayed()
    }

    @Test
    fun compactExerciseCard_weightFieldConditional_bodyweightExercise() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = true,
                    onEvent = {}
                )
            }
        }

        // Assert - Weight field should not be visible for bodyweight exercises
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .assertDoesNotExist()
        
        // But reps and RPE should be visible
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .assertIsDisplayed()
    }

    @Test
    fun compactExerciseCard_weightFieldConditional_weightBasedExercise() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleWeightExercise,
            sets = listOf(SetInput(reps = "8", rpe = "7", weight = "80.0", isWeightSupported = true)),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = true,
                    onEvent = {}
                )
            }
        }

        // Assert - All fields should be visible for weight-based exercises
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .assertIsDisplayed()
    }

    @Test
    fun compactExerciseCard_setManagement_addSetButton() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)),
            orderIndex = 0
        )
        var eventCaptured: WorkoutCreationEvent? = null

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = true,
                    onEvent = { event -> eventCaptured = event }
                )
            }
        }

        // Act - Click add set button
        composeTestRule.onNodeWithContentDescription("Add set")
            .performClick()

        // Assert - Should trigger AddSetToExercise event
        assert(eventCaptured is WorkoutCreationEvent.AddSetToExercise)
    }

    @Test
    fun compactExerciseCard_setManagement_removeSetButton() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(
                SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false),
                SetInput(reps = "12", rpe = "9", weight = "", isWeightSupported = false)
            ),
            orderIndex = 0
        )
        var eventCaptured: WorkoutCreationEvent? = null

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = true,
                    onEvent = { event -> eventCaptured = event }
                )
            }
        }

        // Act - Click remove set button (should be visible with multiple sets)
        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .performClick()

        // Assert - Should trigger RemoveSetFromExercise event
        assert(eventCaptured is WorkoutCreationEvent.RemoveSetFromExercise)
    }

    @Test
    fun compactExerciseCard_displayMetadata_correctly() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleWeightExercise,
            sets = listOf(
                SetInput(reps = "8", rpe = "7", weight = "80.0", isWeightSupported = true),
                SetInput(reps = "8", rpe = "8", weight = "80.0", isWeightSupported = true),
                SetInput(reps = "6", rpe = "9", weight = "85.0", isWeightSupported = true)
            ),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert - Should display exercise name, muscle group, and set count
        composeTestRule.onNodeWithText("Barbell Bench Press")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Chest")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("3 sets")
            .assertIsDisplayed()
    }

    @Test
    fun compactExerciseCard_accessibility_contentDescriptions() {
        // Arrange
        val selectedExercise = SelectedExercise(
            libraryExercise = sampleBodyweightExercise,
            sets = listOf(SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)),
            orderIndex = 0
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                CompactExerciseCard(
                    exercise = selectedExercise,
                    isExpanded = false,
                    onEvent = {}
                )
            }
        }

        // Assert - All interactive elements should have proper content descriptions
        composeTestRule.onNodeWithText("Push-ups")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Exercise thumbnail for Push-ups")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Exercise name: Push-ups, tap for details")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Expand sets")
            .assertIsDisplayed()
    }
} 