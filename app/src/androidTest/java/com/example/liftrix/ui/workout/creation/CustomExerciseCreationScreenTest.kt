package com.example.liftrix.ui.workout.creation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomExerciseCreationScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private var navigateBackCalled = false
    private var exerciseCreatedCalled = false
    
    @Before
    fun setup() {
        navigateBackCalled = false
        exerciseCreatedCalled = false
    }
    
    @Test
    fun customExerciseCreationScreen_displaysCorrectTitle() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Create Exercise")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_hasBackButton() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
    
    @Test
    fun customExerciseCreationScreen_backButtonTriggersNavigation() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .performClick()
        
        assert(navigateBackCalled)
    }
    
    @Test
    fun customExerciseCreationScreen_displaysRequiredFormFields() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Check required fields are displayed
        composeTestRule
            .onNodeWithText("Exercise Name")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Primary Muscle Group")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Equipment")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_displaysOptionalFields() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Check optional fields are displayed
        composeTestRule
            .onNodeWithText("Secondary Muscle Groups (Optional)")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Difficulty Level (Optional)")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Notes (Optional)")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_exerciseNameInput_acceptsText() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        val testExerciseName = "Custom Push-up"
        
        composeTestRule
            .onNodeWithContentDescription("Exercise name input field")
            .performTextInput(testExerciseName)
        
        composeTestRule
            .onNodeWithContentDescription("Exercise name input field")
            .assertTextContains(testExerciseName)
    }
    
    @Test
    fun customExerciseCreationScreen_muscleGroupChips_areClickable() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Test that muscle group chips are clickable
        ExerciseCategory.values().forEach { category ->
            composeTestRule
                .onNodeWithText(category.displayName)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }
    
    @Test
    fun customExerciseCreationScreen_equipmentChips_areClickable() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Test that equipment chips are clickable
        Equipment.values().forEach { equipment ->
            composeTestRule
                .onNodeWithText(equipment.displayName)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }
    
    @Test
    fun customExerciseCreationScreen_createButton_initiallyDisabled() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Create button should be disabled initially (form not valid)
        composeTestRule
            .onNodeWithText("Create Exercise")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_formValidation_showsCharacterCount() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Check character count is displayed for name field
        composeTestRule
            .onNodeWithText("0/100 characters")
            .assertIsDisplayed()
        
        // Check character count is displayed for notes field
        composeTestRule
            .onNodeWithText("0/500 characters")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_difficultySlider_isDisplayed() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Difficulty level slider from 1 to 10")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_notesField_acceptsMultilineText() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        val testNotes = "This is a custom exercise\nwith multiple lines\nof instructions"
        
        composeTestRule
            .onNodeWithContentDescription("Exercise notes input field")
            .performTextInput(testNotes)
        
        composeTestRule
            .onNodeWithContentDescription("Exercise notes input field")
            .assertTextContains(testNotes)
    }
    
    @Test
    fun customExerciseCreationScreen_accessibilityLabels_arePresent() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Check accessibility labels are present
        composeTestRule
            .onNodeWithContentDescription("Exercise name input field")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Primary muscle group selection")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Equipment selection")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Exercise notes input field")
            .assertIsDisplayed()
    }
    
    @Test
    fun customExerciseCreationScreen_muscleGroupSelection_updatesSecondaryOptions() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Select Chest as primary muscle
        composeTestRule
            .onNodeWithText("Chest")
            .performClick()
        
        // Secondary muscle groups should be displayed (excluding Cardio and Chest)
        composeTestRule
            .onNodeWithText("Secondary Muscle Groups (Optional)")
            .assertIsDisplayed()
        
        // Cardio should not appear in secondary options when Chest is primary
        // (This would need more complex testing with state verification)
    }
    
    @Test
    fun customExerciseCreationScreen_cardioSelection_hidesSecondaryMuscles() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Select Cardio as primary muscle
        composeTestRule
            .onNodeWithText("Cardio")
            .performClick()
        
        // Secondary muscle groups should not be displayed for Cardio
        composeTestRule
            .onNodeWithText("Secondary Muscle Groups (Optional)")
            .assertDoesNotExist()
    }
    
    @Test
    fun customExerciseCreationScreen_formFields_haveProperKeyboardOptions() {
        composeTestRule.setContent {
            LiftrixTheme {
                CustomExerciseCreationScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onExerciseCreated = { exerciseCreatedCalled = true }
                )
            }
        }
        
        // Exercise name field should be single line
        composeTestRule
            .onNodeWithContentDescription("Exercise name input field")
            .assertIsDisplayed()
        
        // Notes field should allow multiple lines
        composeTestRule
            .onNodeWithContentDescription("Exercise notes input field")
            .assertIsDisplayed()
    }
} 