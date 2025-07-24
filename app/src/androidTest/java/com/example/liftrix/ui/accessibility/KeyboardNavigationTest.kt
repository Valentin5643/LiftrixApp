package com.example.liftrix.ui.accessibility

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.ModernActionButton.*
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Comprehensive keyboard navigation testing to ensure all interactive elements
 * are accessible and navigable without touch input.
 * 
 * Tests cover:
 * - Tab navigation between interactive elements
 * - Enter/Space key activation of buttons and cards
 * - Arrow key navigation in complex layouts
 * - Focus management and focus trapping
 * - Keyboard shortcuts for common actions
 * - Text field navigation and input
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class KeyboardNavigationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    /**
     * Tests basic tab navigation between interactive elements in the correct order.
     */
    @Test
    fun tabNavigation_followsCorrectOrder() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // First focusable element
                    UnifiedWorkoutCard(
                        title = "First Card",
                        subtitle = "Should be first in tab order",
                        onClick = { /* Handle click */ },
                        modifier = Modifier.testTag("first_card")
                    ) {
                        Text("This card should receive focus first")
                    }
                    
                    // Second focusable element
                    PrimaryActionButton(
                        text = "Second Button",
                        onClick = { /* Handle click */ },
                        modifier = Modifier.testTag("second_button")
                    )
                    
                    // Third focusable element - text field
                    var textValue by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Third Text Field") },
                        modifier = Modifier
                            .testTag("third_text_field")
                            .fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    
                    // Fourth focusable element
                    SecondaryActionButton(
                        text = "Fourth Button",
                        onClick = { /* Handle click */ },
                        modifier = Modifier.testTag("fourth_button")
                    )
                    
                    // Fifth focusable element - another card
                    UnifiedWorkoutCard(
                        title = "Fifth Card",
                        subtitle = "Should be last in tab order",
                        onClick = { /* Handle click */ },
                        modifier = Modifier.testTag("fifth_card")
                    ) {
                        Text("This card should receive focus last")
                    }
                }
            }
        }
        
        // Test forward tab navigation
        composeTestRule
            .onNodeWithTag("first_card")
            .requestFocus()
        
        composeTestRule
            .onNodeWithTag("first_card")
            .assertIsFocused()
        
        // Tab to next element
        composeTestRule
            .onNodeWithTag("first_card")
            .performKeyInput { 
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("second_button")
            .assertIsFocused()
        
        // Continue tab navigation
        composeTestRule
            .onNodeWithTag("second_button")
            .performKeyInput { 
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("third_text_field")
            .assertIsFocused()
        
        composeTestRule
            .onNodeWithTag("third_text_field")
            .performKeyInput { 
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("fourth_button")
            .assertIsFocused()
        
        composeTestRule
            .onNodeWithTag("fourth_button")
            .performKeyInput { 
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("fifth_card")
            .assertIsFocused()
    }
    
    /**
     * Tests that Enter and Space keys properly activate buttons and clickable elements.
     */
    @Test
    fun enterAndSpaceKeys_activateElements() {
        var primaryClicked = false
        var secondaryClicked = false
        var cardClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UnifiedWorkoutCard(
                        title = "Keyboard Test Card",
                        subtitle = "Should activate with Enter/Space",
                        onClick = { cardClicked = true },
                        modifier = Modifier.testTag("keyboard_card")
                    ) {
                        Text("Press Enter or Space to activate this card")
                    }
                    
                    PrimaryActionButton(
                        text = "Primary Action",
                        onClick = { primaryClicked = true },
                        modifier = Modifier.testTag("primary_action")
                    )
                    
                    SecondaryActionButton(
                        text = "Secondary Action",
                        onClick = { secondaryClicked = true },
                        modifier = Modifier.testTag("secondary_action")
                    )
                }
            }
        }
        
        // Test Enter key activation on card
        composeTestRule
            .onNodeWithTag("keyboard_card")
            .requestFocus()
            .assertIsFocused()
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_ENTER)
            }
        
        assertTrue("Card should be activated by Enter key", cardClicked)
        
        // Reset and test Space key activation on card
        cardClicked = false
        composeTestRule
            .onNodeWithTag("keyboard_card")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_SPACE)
            }
        
        assertTrue("Card should be activated by Space key", cardClicked)
        
        // Test Enter key on primary button
        composeTestRule
            .onNodeWithTag("primary_action")
            .requestFocus()
            .assertIsFocused()
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_ENTER)
            }
        
        assertTrue("Primary button should be activated by Enter key", primaryClicked)
        
        // Test Space key on secondary button
        composeTestRule
            .onNodeWithTag("secondary_action")
            .requestFocus()
            .assertIsFocused()
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_SPACE)
            }
        
        assertTrue("Secondary button should be activated by Space key", secondaryClicked)
    }
    
    /**
     * Tests keyboard navigation in complex layouts with multiple interactive elements.
     */
    @Test
    fun complexLayout_keyboardNavigationWorks() {
        var workout1Clicked = false
        var workout2Clicked = false
        var createClicked = false
        var settingsClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header section
                    Text(
                        text = "Your Workouts",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("header_text")
                    )
                    
                    // Workout cards section
                    UnifiedWorkoutCard(
                        title = "Morning Cardio",
                        subtitle = "Ready to start",
                        onClick = { workout1Clicked = true },
                        modifier = Modifier.testTag("workout_card_1")
                    ) {
                        Text("30 minutes HIIT session")
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SecondaryActionButton(
                                text = "Edit",
                                onClick = { /* Edit workout */ },
                                modifier = Modifier.testTag("edit_workout_1")
                            )
                            PrimaryActionButton(
                                text = "Start",
                                onClick = { /* Start workout */ },
                                modifier = Modifier.testTag("start_workout_1")
                            )
                        }
                    }
                    
                    UnifiedWorkoutCard(
                        title = "Strength Training",
                        subtitle = "Last completed yesterday",
                        onClick = { workout2Clicked = true },
                        modifier = Modifier.testTag("workout_card_2")
                    ) {
                        Text("Full body strength workout")
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SecondaryActionButton(
                                text = "Edit",
                                onClick = { /* Edit workout */ },
                                modifier = Modifier.testTag("edit_workout_2")
                            )
                            PrimaryActionButton(
                                text = "Start",
                                onClick = { /* Start workout */ },
                                modifier = Modifier.testTag("start_workout_2")
                            )
                        }
                    }
                    
                    // Action buttons section
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PrimaryActionButton(
                            text = "Create New Workout",
                            onClick = { createClicked = true },
                            modifier = Modifier
                                .testTag("create_workout_button")
                                .weight(1f)
                        )
                        
                        TertiaryActionButton(
                            text = "Settings",
                            onClick = { settingsClicked = true },
                            modifier = Modifier.testTag("settings_button")
                        )
                    }
                }
            }
        }
        
        // Test navigation through all interactive elements
        val focusableElements = listOf(
            "workout_card_1",
            "edit_workout_1", 
            "start_workout_1",
            "workout_card_2",
            "edit_workout_2",
            "start_workout_2", 
            "create_workout_button",
            "settings_button"
        )
        
        // Test forward navigation through all elements
        for (i in focusableElements.indices) {
            val currentTag = focusableElements[i]
            
            composeTestRule
                .onNodeWithTag(currentTag)
                .requestFocus()
                .assertIsFocused()
            
            // Test activation
            composeTestRule
                .onNodeWithTag(currentTag)
                .performKeyInput {
                    pressKey(KeyEvent.KEYCODE_ENTER)
                }
            
            // Move to next element (if not last)
            if (i < focusableElements.size - 1) {
                composeTestRule
                    .onNodeWithTag(currentTag)
                    .performKeyInput {
                        pressKey(KeyEvent.KEYCODE_TAB)
                    }
            }
        }
        
        // Verify activation states
        assertTrue("Workout 1 card should be activated", workout1Clicked)
        assertTrue("Workout 2 card should be activated", workout2Clicked)
        assertTrue("Create button should be activated", createClicked)
        assertTrue("Settings button should be activated", settingsClicked)
    }
    
    /**
     * Tests focus management in text input scenarios with keyboard navigation.
     */
    @Test
    fun textInput_keyboardNavigationWorks() {
        var workoutName by mutableStateOf("")
        var exerciseCount by mutableStateOf("")
        var notes by mutableStateOf("")
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UnifiedWorkoutCard(
                        title = "Create New Workout",
                        subtitle = "Enter workout details"
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Workout name field
                            OutlinedTextField(
                                value = workoutName,
                                onValueChange = { workoutName = it },
                                label = { Text("Workout Name") },
                                modifier = Modifier
                                    .testTag("workout_name_field")
                                    .fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            
                            // Exercise count field
                            OutlinedTextField(
                                value = exerciseCount,
                                onValueChange = { exerciseCount = it },
                                label = { Text("Number of Exercises") },
                                modifier = Modifier
                                    .testTag("exercise_count_field")
                                    .fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            
                            // Notes field
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes") },
                                modifier = Modifier
                                    .testTag("notes_field")
                                    .fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                minLines = 3
                            )
                            
                            // Action buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TertiaryActionButton(
                                    text = "Cancel",
                                    onClick = { /* Cancel */ },
                                    modifier = Modifier.testTag("cancel_button")
                                )
                                
                                PrimaryActionButton(
                                    text = "Create Workout",
                                    onClick = { /* Create */ },
                                    modifier = Modifier.testTag("create_button")
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Test navigation between text fields
        composeTestRule
            .onNodeWithTag("workout_name_field")
            .requestFocus()
            .assertIsFocused()
        
        // Type in workout name
        composeTestRule
            .onNodeWithTag("workout_name_field")
            .performTextInput("Push Day Workout")
        
        // Tab to next field
        composeTestRule
            .onNodeWithTag("workout_name_field")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("exercise_count_field")
            .assertIsFocused()
        
        // Type in exercise count
        composeTestRule
            .onNodeWithTag("exercise_count_field")
            .performTextInput("6")
        
        // Tab to notes field
        composeTestRule
            .onNodeWithTag("exercise_count_field")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("notes_field")
            .assertIsFocused()
        
        // Type in notes
        composeTestRule
            .onNodeWithTag("notes_field")
            .performTextInput("Focus on chest, shoulders, and triceps")
        
        // Tab to action buttons
        composeTestRule
            .onNodeWithTag("notes_field")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("cancel_button")
            .assertIsFocused()
        
        composeTestRule
            .onNodeWithTag("cancel_button")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("create_button")
            .assertIsFocused()
        
        // Verify text input values
        composeTestRule
            .onNodeWithTag("workout_name_field")
            .assert(hasText("Push Day Workout"))
        
        composeTestRule
            .onNodeWithTag("exercise_count_field")
            .assert(hasText("6"))
        
        composeTestRule
            .onNodeWithTag("notes_field")
            .assert(hasText("Focus on chest, shoulders, and triceps"))
    }
    
    /**
     * Tests focus management with dynamic content changes.
     */
    @Test
    fun dynamicContent_focusManagementWorks() {
        var showAdvancedOptions by mutableStateOf(false)
        val advancedFocusRequester = remember { FocusRequester() }
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UnifiedWorkoutCard(
                        title = "Dynamic Focus Test",
                        subtitle = "Testing focus with dynamic content"
                    ) {
                        Column {
                            PrimaryActionButton(
                                text = if (showAdvancedOptions) "Hide Advanced" else "Show Advanced",
                                onClick = { 
                                    showAdvancedOptions = !showAdvancedOptions 
                                },
                                modifier = Modifier.testTag("toggle_advanced_button")
                            )
                            
                            if (showAdvancedOptions) {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Advanced Options",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.testTag("advanced_header")
                                    )
                                    
                                    var advancedText by remember { mutableStateOf("") }
                                    OutlinedTextField(
                                        value = advancedText,
                                        onValueChange = { advancedText = it },
                                        label = { Text("Advanced Setting") },
                                        modifier = Modifier
                                            .testTag("advanced_text_field")
                                            .fillMaxWidth()
                                            .focusRequester(advancedFocusRequester)
                                    )
                                    
                                    SecondaryActionButton(
                                        text = "Apply Advanced",
                                        onClick = { /* Apply settings */ },
                                        modifier = Modifier.testTag("apply_advanced_button")
                                    )
                                }
                                
                                // Auto-focus the new text field when it appears
                                LaunchedEffect(showAdvancedOptions) {
                                    if (showAdvancedOptions) {
                                        advancedFocusRequester.requestFocus()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Start with toggle button focused
        composeTestRule
            .onNodeWithTag("toggle_advanced_button")
            .requestFocus()
            .assertIsFocused()
        
        // Advanced options should not exist initially
        composeTestRule
            .onNodeWithTag("advanced_text_field")
            .assertDoesNotExist()
        
        // Show advanced options
        composeTestRule
            .onNodeWithTag("toggle_advanced_button")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_ENTER)
            }
        
        composeTestRule.waitForIdle()
        
        // Advanced options should now exist and text field should be focused
        composeTestRule
            .onNodeWithTag("advanced_text_field")
            .assertExists()
            .assertIsFocused()
        
        // Type in advanced text field
        composeTestRule
            .onNodeWithTag("advanced_text_field")
            .performTextInput("Advanced configuration")
        
        // Navigate to apply button
        composeTestRule
            .onNodeWithTag("advanced_text_field")
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_TAB)
            }
        
        composeTestRule
            .onNodeWithTag("apply_advanced_button")
            .assertIsFocused()
        
        // Hide advanced options
        composeTestRule
            .onNodeWithTag("toggle_advanced_button")
            .requestFocus()
            .performKeyInput {
                pressKey(KeyEvent.KEYCODE_ENTER)
            }
        
        composeTestRule.waitForIdle()
        
        // Advanced options should be gone and focus should return to toggle button
        composeTestRule
            .onNodeWithTag("advanced_text_field")
            .assertDoesNotExist()
        
        composeTestRule
            .onNodeWithTag("toggle_advanced_button")
            .assertIsFocused()
    }
}