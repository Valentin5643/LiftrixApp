package com.example.liftrix.ui.workout.creation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCardTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private val sampleExercise = ExerciseLibrary(
        id = "1",
        name = "Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 5,
        instructions = "Barbell bench press",
        isCompound = true,
        searchableTerms = listOf("bench", "press", "chest", "barbell")
    )
    
    private val selectedExercise = SelectedExercise(
        libraryExercise = sampleExercise,
        sets = listOf(
            SetInput(reps = "10", rpe = "7", weight = "", isWeightSupported = true),
            SetInput(reps = "8", rpe = "8", weight = "65", isWeightSupported = true)
        ),
        orderIndex = 0
    )
    
    @Test
    fun exerciseCard_displaysExerciseName() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {}
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()
    }
    
    @Test
    fun exerciseCard_displaysSetsCount() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {}
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("2 sets")
            .assertIsDisplayed()
    }
    
    @Test
    fun exerciseCard_displaysLastUsedWeight() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {},
                    isExpanded = true,
                    lastUsedWeight = 60.0f
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Last: 60.0 kg")
            .assertIsDisplayed()
    }
    
    @Test
    fun exerciseCard_expandsWhenExpandButtonClicked() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {},
                    isExpanded = false
                )
            }
        }
        
        // Initially collapsed - sets section should not be visible
        composeTestRule
            .onNodeWithText("Sets")
            .assertDoesNotExist()
        
        // Click expand button
        composeTestRule
            .onNodeWithContentDescription("Expand sets")
            .performClick()
        
        // Now sets section should be visible
        composeTestRule
            .onNodeWithText("Sets")
            .assertIsDisplayed()
    }
    
    @Test
    fun exerciseCard_collapsesWhenCollapseButtonClicked() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {},
                    isExpanded = true
                )
            }
        }
        
        // Initially expanded - sets section should be visible
        composeTestRule
            .onNodeWithText("Sets")
            .assertIsDisplayed()
        
        // Click collapse button
        composeTestRule
            .onNodeWithContentDescription("Collapse sets")
            .performClick()
        
        // Sets section should be hidden after collapse
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Sets")
            .assertDoesNotExist()
    }
    
    @Test
    fun exerciseCard_showsAddSetButton() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {},
                    isExpanded = true
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Add Set")
            .assertIsDisplayed()
    }
    
    @Test
    fun exerciseCard_showsRemoveExerciseButton() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {}
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Remove Bench Press exercise")
            .assertIsDisplayed()
    }
    
    @Test
    fun exerciseCard_bodyweightExercise_hidesLastUsedWeight() {
        val bodyweightExercise = sampleExercise.copy(
            name = "Push-ups",
            equipment = Equipment.BODYWEIGHT_ONLY
        )
        
        val bodyweightSelectedExercise = SelectedExercise(
            libraryExercise = bodyweightExercise,
            sets = listOf(
                SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)
            ),
            orderIndex = 0
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = bodyweightSelectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {},
                    isExpanded = true,
                    lastUsedWeight = 60.0f // Should not be displayed for bodyweight exercises
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Last: 60.0 kg")
            .assertDoesNotExist()
    }
    
    @Test
    fun exerciseCard_accessibilityContentDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {}
                )
            }
        }
        
        composeTestRule
            .onNode(hasContentDescription("Bench Press exercise card with 2 sets"))
            .assertIsDisplayed()
    }
}