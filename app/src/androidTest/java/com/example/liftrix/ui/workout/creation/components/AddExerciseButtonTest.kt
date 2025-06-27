package com.example.liftrix.ui.workout.creation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddExerciseButtonTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun addExerciseButton_displaysCorrectText() {
        composeTestRule.setContent {
            LiftrixTheme {
                AddExerciseButton(onClick = {})
            }
        }
        
        composeTestRule
            .onNodeWithText("Add Exercise")
            .assertIsDisplayed()
    }
    
    @Test
    fun addExerciseButton_hasCorrectContentDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                AddExerciseButton(onClick = {})
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Add exercise to workout")
            .assertIsDisplayed()
    }
    
    @Test
    fun addExerciseButton_clickTriggersCallback() {
        var clicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                AddExerciseButton(onClick = { clicked = true })
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Add exercise to workout")
            .performClick()
        
        assert(clicked)
    }
    
    @Test
    fun addExerciseButton_enabledByDefault() {
        composeTestRule.setContent {
            LiftrixTheme {
                AddExerciseButton(onClick = {})
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Add exercise to workout")
            .assertIsEnabled()
    }
    
    @Test
    fun addExerciseButton_canBeDisabled() {
        composeTestRule.setContent {
            LiftrixTheme {
                AddExerciseButton(
                    onClick = {},
                    enabled = false
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Add exercise to workout")
            .assertIsNotEnabled()
    }
} 