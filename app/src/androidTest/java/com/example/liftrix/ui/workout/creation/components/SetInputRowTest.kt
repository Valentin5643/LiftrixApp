package com.example.liftrix.ui.workout.creation.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SetInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SetInputRow component following android.mdc testing guidelines
 */
@RunWith(AndroidJUnit4::class)
class SetInputRowTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun setInputRow_displaysCorrectly() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(
                        reps = "10",
                        rpe = "8",
                        weight = "20.0",
                        isWeightSupported = true
                    ),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reps").assertIsDisplayed()
        composeTestRule.onNodeWithText("RPE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weight").assertIsDisplayed()
        composeTestRule.onNodeWithText("10").assertIsDisplayed()
        composeTestRule.onNodeWithText("8").assertIsDisplayed()
        composeTestRule.onNodeWithText("20.0").assertIsDisplayed()
    }
    
    @Test
    fun setInputRow_hidesWeightFieldForBodyweightExercise() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(
                        reps = "15",
                        rpe = "7",
                        isWeightSupported = false
                    ),
                    setNumber = 2,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reps").assertIsDisplayed()
        composeTestRule.onNodeWithText("RPE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weight").assertDoesNotExist()
        composeTestRule.onNodeWithText("15").assertIsDisplayed()
        composeTestRule.onNodeWithText("7").assertIsDisplayed()
    }
    
    @Test
    fun setInputRow_showsPlaceholderText() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("10").assertIsDisplayed() // Reps placeholder
        composeTestRule.onNodeWithText("8").assertIsDisplayed()  // RPE placeholder
        composeTestRule.onNodeWithText("20.0").assertIsDisplayed() // Weight placeholder
    }
    
    @Test
    fun setInputRow_handlesRepsInput() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .performTextInput("12")
        
        // Assert
        assertEquals("12", capturedSetInput.reps)
        assertNull(capturedSetInput.repsError)
    }
    
    @Test
    fun setInputRow_handlesRpeInput() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .performTextInput("9")
        
        // Assert
        assertEquals("9", capturedSetInput.rpe)
        assertNull(capturedSetInput.rpeError)
    }
    
    @Test
    fun setInputRow_handlesWeightInput() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .performTextInput("25.5")
        
        // Assert
        assertEquals("25.5", capturedSetInput.weight)
        assertNull(capturedSetInput.weightError)
    }
    
    @Test
    fun setInputRow_filtersNonNumericRepsInput() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .performTextInput("12abc34")
        
        // Assert
        assertEquals("1234", capturedSetInput.reps)
    }
    
    @Test
    fun setInputRow_limitsRepsInputLength() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .performTextInput("12345")
        
        // Assert
        assertEquals("123", capturedSetInput.reps) // Limited to 3 digits
    }
    
    @Test
    fun setInputRow_filtersNonNumericRpeInput() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .performTextInput("8abc")
        
        // Assert
        assertEquals("8", capturedSetInput.rpe)
    }
    
    @Test
    fun setInputRow_limitsRpeInputLength() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .performTextInput("123")
        
        // Assert
        assertEquals("12", capturedSetInput.rpe) // Limited to 2 digits
    }
    
    @Test
    fun setInputRow_filtersInvalidWeightInput() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .performTextInput("20.5abc")
        
        // Assert
        assertEquals("20.5", capturedSetInput.weight)
    }
    
    @Test
    fun setInputRow_limitsWeightInputLength() {
        // Arrange
        var capturedSetInput = SetInput(isWeightSupported = true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .performTextInput("1234567")
        
        // Assert
        assertEquals("123456", capturedSetInput.weight) // Limited to 6 characters
    }
    
    @Test
    fun setInputRow_handlesRemoveSetClick() {
        // Arrange
        var removeSetCalled = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = { removeSetCalled = true }
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .performClick()
        
        // Assert
        assertTrue(removeSetCalled)
    }
    
    @Test
    fun setInputRow_hidesRemoveButtonWhenRequested() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {},
                    showRemoveButton = false
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .assertDoesNotExist()
    }
    
    @Test
    fun setInputRow_respectsEnabledState() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(
                        reps = "10",
                        rpe = "8",
                        weight = "20.0",
                        isWeightSupported = true
                    ),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {},
                    enabled = false
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .assertIsNotEnabled()
    }
    
    @Test
    fun setInputRow_fieldsAreEnabledByDefault() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .assertIsEnabled()
    }
    
    @Test
    fun setInputRow_hasProperAccessibilitySupport() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 3,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }
        
        // Assert - Check all content descriptions exist
        composeTestRule.onNodeWithContentDescription("Set number 3")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove set 3")
            .assertIsDisplayed()
    }
    
    @Test
    fun setInputRow_removeButtonHasClickAction() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .assertHasClickAction()
    }
    
    @Test
    fun setInputRow_textReplacementWorks() {
        // Arrange
        var capturedSetInput = SetInput(
            reps = "10",
            rpe = "8",
            weight = "20.0",
            isWeightSupported = true
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = capturedSetInput,
                    setNumber = 1,
                    onSetChange = { capturedSetInput = it },
                    onRemoveSet = {}
                )
            }
        }
        
        // Act
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .performTextReplacement("15")
        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .performTextReplacement("9")
        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .performTextReplacement("25.0")
        
        // Assert
        assertEquals("15", capturedSetInput.reps)
        assertEquals("9", capturedSetInput.rpe)
        assertEquals("25.0", capturedSetInput.weight)
    }
    
    @Test
    fun validateSetInput_returnsCorrectValidationState() {
        // Test valid input
        val validInput = SetInput(
            reps = "10",
            rpe = "8",
            weight = "20.0",
            isWeightSupported = true
        )
        val validatedInput = validateSetInput(validInput)
        assertNull(validatedInput.repsError)
        assertNull(validatedInput.rpeError)
        assertNull(validatedInput.weightError)
        
        // Test invalid input
        val invalidInput = SetInput(
            reps = "",
            rpe = "15",
            weight = "-5",
            isWeightSupported = true
        )
        val invalidatedInput = validateSetInput(invalidInput)
        assertEquals("Reps are required", invalidatedInput.repsError)
        assertEquals("RPE cannot exceed 10", invalidatedInput.rpeError)
        assertEquals("Weight cannot be negative", invalidatedInput.weightError)
    }
} 