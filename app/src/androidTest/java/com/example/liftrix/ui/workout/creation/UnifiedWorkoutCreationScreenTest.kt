package com.example.liftrix.ui.workout.creation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for UnifiedWorkoutCreationScreen following android.mdc testing guidelines
 */
@RunWith(AndroidJUnit4::class)
class UnifiedWorkoutCreationScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun unifiedWorkoutCreationScreen_displaysCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Create Workout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Workout").assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCreationScreen_showsEmptyStateInitially() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("No exercises added yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add exercises to start building your workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Exercise").assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCreationScreen_hasCorrectAccessibility() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }
        
        composeTestRule.onNodeWithContentDescription("Navigate back")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
    
    @Test
    fun unifiedWorkoutCreationScreen_workoutNameInputWorks() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }
        
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .performTextInput("My Workout")
        
        composeTestRule.onNodeWithText("My Workout").assertIsDisplayed()
    }
} 