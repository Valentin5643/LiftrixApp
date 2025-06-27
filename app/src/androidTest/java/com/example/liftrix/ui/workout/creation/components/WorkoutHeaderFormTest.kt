package com.example.liftrix.ui.workout.creation.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for WorkoutHeaderForm component following android.mdc testing guidelines
 */
@RunWith(AndroidJUnit4::class)
class WorkoutHeaderFormTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun workoutHeaderForm_displaysCorrectly() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "Test Workout",
                    onWorkoutNameChange = {},
                    workoutDescription = "Test Description",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("Workout Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description (Optional)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Description").assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_showsPlaceholderText() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("Enter workout name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add workout description...").assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_showsCharacterCount() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "Test",
                    onWorkoutNameChange = {},
                    workoutDescription = "Description",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("4/${WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH}").assertIsDisplayed()
        composeTestRule.onNodeWithText("11/${WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH}").assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_handlesTextInput() {
        // Arrange
        var capturedWorkoutName = ""
        var capturedDescription = ""
        
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = capturedWorkoutName,
                    onWorkoutNameChange = { capturedWorkoutName = it },
                    workoutDescription = capturedDescription,
                    onWorkoutDescriptionChange = { capturedDescription = it }
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .performTextInput("New Workout")
        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .performTextInput("New Description")
        
        // Assert
        assertEquals("New Workout", capturedWorkoutName)
        assertEquals("New Description", capturedDescription)
    }
    
    @Test
    fun workoutHeaderForm_enforcesNameCharacterLimit() {
        // Arrange
        var capturedWorkoutName = ""
        val longName = "a".repeat(WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH + 10)
        
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = capturedWorkoutName,
                    onWorkoutNameChange = { capturedWorkoutName = it },
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .performTextInput(longName)
        
        // Assert
        assertEquals(WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH, capturedWorkoutName.length)
    }
    
    @Test
    fun workoutHeaderForm_enforcesDescriptionCharacterLimit() {
        // Arrange
        var capturedDescription = ""
        val longDescription = "a".repeat(WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH + 10)
        
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = capturedDescription,
                    onWorkoutDescriptionChange = { capturedDescription = it }
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .performTextInput(longDescription)
        
        // Assert
        assertEquals(WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH, capturedDescription.length)
    }
    
    @Test
    fun workoutHeaderForm_displaysNameError() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {},
                    workoutNameError = "Workout name is required"
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("Workout name is required").assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_displaysDescriptionError() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "Test",
                    onWorkoutNameChange = {},
                    workoutDescription = "Long description",
                    onWorkoutDescriptionChange = {},
                    workoutDescriptionError = "Description cannot exceed 500 characters"
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("Description cannot exceed 500 characters").assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_hidesCharacterCountWhenErrorShown() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {},
                    workoutNameError = "Workout name is required",
                    workoutDescriptionError = "Description error"
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("Workout name is required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description error").assertIsDisplayed()
        composeTestRule.onNodeWithText("0/${WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH}").assertDoesNotExist()
        composeTestRule.onNodeWithText("0/${WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH}").assertDoesNotExist()
    }
    
    @Test
    fun workoutHeaderForm_respectsEnabledState() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "Test",
                    onWorkoutNameChange = {},
                    workoutDescription = "Description",
                    onWorkoutDescriptionChange = {},
                    enabled = false
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .assertIsNotEnabled()
    }
    
    @Test
    fun workoutHeaderForm_fieldsAreEnabledByDefault() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .assertIsEnabled()
    }
    
    @Test
    fun workoutHeaderForm_hasProperAccessibilitySupport() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "Test Workout",
                    onWorkoutNameChange = {},
                    workoutDescription = "Test Description",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert - Check content descriptions exist
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_nameFieldIsSingleLine() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "Test\nWorkout\nWith\nMultiple\nLines",
                    onWorkoutNameChange = {},
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert - Single line field should display text in one line
        composeTestRule.onNodeWithText("Test\nWorkout\nWith\nMultiple\nLines")
            .assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_descriptionFieldIsMultiLine() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = "Multi\nLine\nDescription",
                    onWorkoutDescriptionChange = {}
                )
            }
        }
        
        // Assert - Multi-line field should display text with line breaks
        composeTestRule.onNodeWithText("Multi\nLine\nDescription")
            .assertIsDisplayed()
    }
    
    @Test
    fun workoutHeaderForm_textReplacementWorks() {
        // Arrange
        var capturedWorkoutName = "Initial Name"
        var capturedDescription = "Initial Description"
        
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = capturedWorkoutName,
                    onWorkoutNameChange = { capturedWorkoutName = it },
                    workoutDescription = capturedDescription,
                    onWorkoutDescriptionChange = { capturedDescription = it }
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .performTextReplacement("Replaced Name")
        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .performTextReplacement("Replaced Description")
        
        // Assert
        assertEquals("Replaced Name", capturedWorkoutName)
        assertEquals("Replaced Description", capturedDescription)
    }
    
    @Test
    fun workoutHeaderForm_validationFunctions_workCorrectly() {
        // Test validateWorkoutName
        assertNull(validateWorkoutName("Valid Name"))
        assertEquals("Workout name is required", validateWorkoutName(""))
        assertEquals("Workout name is required", validateWorkoutName("   "))
        
        val longName = "a".repeat(WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH + 1)
        assertEquals(
            "Workout name cannot exceed ${WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH} characters",
            validateWorkoutName(longName)
        )
        
        // Test validateWorkoutDescription
        assertNull(validateWorkoutDescription("Valid Description"))
        assertNull(validateWorkoutDescription(""))
        
        val longDescription = "a".repeat(WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH + 1)
        assertEquals(
            "Description cannot exceed ${WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH} characters",
            validateWorkoutDescription(longDescription)
        )
    }
} 