package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.testutils.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive Integration Test Suite for Workout Components
 * 
 * This test suite validates the integration and interaction between all unified
 * workout components including:
 * - UnifiedWorkoutCard and ModernActionButton integration
 * - Component composition and visual consistency
 * - Complex user workflows and state management
 * - Cross-component accessibility compliance
 * - Performance validation for component combinations
 * - Visual regression prevention through systematic testing
 * 
 * Serves as the comprehensive validation layer ensuring all redesigned
 * components work together seamlessly following the unified design system.
 */
@RunWith(AndroidJUnit4::class)
class ComponentsTestSuite {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Integration Testing: UnifiedWorkoutCard + ModernActionButtons
    @Test
    fun workoutCardWithActionButtons_integration_worksCorrectly() {
        var editClicked = false
        var startClicked = false
        var deleteClicked = false
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = "Push Day Workout",
                subtitle = "6 exercises • 45 min",
                actions = {
                    TertiaryActionButton(
                        text = "Delete",
                        onClick = { deleteClicked = true },
                        modifier = Modifier.testTag("delete_button")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SecondaryActionButton(
                        text = "Edit",
                        onClick = { editClicked = true },
                        modifier = Modifier.testTag("edit_button")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PrimaryActionButton(
                        text = "Start",
                        onClick = { startClicked = true },
                        modifier = Modifier.testTag("start_button")
                    )
                }
            ) {
                Text("Ready to begin your push workout")
            }
        }
        
        // Verify all components are displayed correctly
        composeTestRule
            .onNodeWithText("Push Day Workout")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("6 exercises • 45 min")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Ready to begin your push workout")
            .assertIsDisplayed()
        
        // Test button hierarchy and interactions
        composeTestRule
            .onNodeWithTag("delete_button")
            .assertIsDisplayed()
            .performClick()
        
        composeTestRule
            .onNodeWithTag("edit_button")
            .assertIsDisplayed()
            .performClick()
        
        composeTestRule
            .onNodeWithTag("start_button")
            .assertIsDisplayed()
            .performClick()
        
        // Verify all callbacks were triggered
        assertTrue(deleteClicked)
        assertTrue(editClicked)
        assertTrue(startClicked)
    }
    
    @Test
    fun multipleWorkoutCards_visualConsistency_maintained() {
        composeTestRule.setLiftrixContent {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card with short content
                UnifiedWorkoutCard(
                    title = "Quick Workout",
                    subtitle = "3 exercises",
                    modifier = Modifier.testTag("short_card")
                ) {
                    Text("Fast session")
                }
                
                // Card with medium content
                UnifiedWorkoutCard(
                    title = "Standard Push Day",
                    subtitle = "6 exercises • 45 min",
                    actions = {
                        SecondaryActionButton(
                            text = "Edit",
                            onClick = { /* Test edit */ }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Start",
                            onClick = { /* Test start */ }
                        )
                    },
                    modifier = Modifier.testTag("medium_card")
                ) {
                    Text("Complete upper body workout focusing on chest, shoulders, and triceps")
                }
                
                // Card with long content
                UnifiedWorkoutCard(
                    title = "Advanced Full Body Strength Training Session",
                    subtitle = "12 exercises • 90 min • High intensity",
                    onClick = { /* Test click */ },
                    actions = {
                        TertiaryActionButton(
                            text = "Details",
                            onClick = { /* Test details */ }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SecondaryActionButton(
                            text = "Modify",
                            onClick = { /* Test modify */ }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Begin Workout",
                            onClick = { /* Test begin */ }
                        )
                    },
                    modifier = Modifier.testTag("long_card")
                ) {
                    Text("Comprehensive strength training session covering all major muscle groups with progressive overload principles. Includes compound movements, isolation exercises, and proper rest periods for optimal results.")
                }
            }
        }
        
        // Verify visual consistency across different content sizes
        composeTestRule.assertVisualConsistency(
            shortContent = "Quick Workout",
            longContent = "Advanced Full Body Strength Training Session"
        )
        
        // Verify all cards display correctly
        composeTestRule
            .onNodeWithTag("short_card")
            .assertUnifiedDesignSystem()
        
        composeTestRule
            .onNodeWithTag("medium_card")
            .assertUnifiedDesignSystem()
        
        composeTestRule
            .onNodeWithTag("long_card")
            .assertUnifiedDesignSystem()
    }
    
    @Test
    fun compactAndRegularCards_integration_worksCorrectly() {
        composeTestRule.setLiftrixContent {
            Column {
                // Regular UnifiedWorkoutCard
                UnifiedWorkoutCard(
                    title = "Regular Card",
                    subtitle = "Standard spacing",
                    modifier = Modifier.testTag("regular_card")
                ) {
                    Text("Regular card content with full spacing")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Compact UnifiedWorkoutCard
                CompactUnifiedWorkoutCard(
                    title = "Compact Card",
                    subtitle = "Reduced spacing",
                    modifier = Modifier.testTag("compact_card")
                ) {
                    Text("Compact card content with reduced spacing")
                }
            }
        }
        
        // Verify both card variants display correctly
        composeTestRule
            .onNodeWithText("Regular Card")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Compact Card")
            .assertIsDisplayed()
        
        // Verify compact card has different accessibility description
        composeTestRule
            .onNodeWithContentDescription("Compact card: Compact Card. Reduced spacing")
            .assertIsDisplayed()
    }

    // Complex State Management Testing
    @Test
    fun dynamicCardContent_stateChanges_updateCorrectly() {
        var workoutState by mutableStateOf("Ready")
        var exerciseCount by mutableStateOf(6)
        var isStarted by mutableStateOf(false)
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = "Dynamic Workout ($workoutState)",
                subtitle = "$exerciseCount exercises",
                actions = {
                    if (!isStarted) {
                        PrimaryActionButton(
                            text = "Start Workout",
                            onClick = {
                                workoutState = "In Progress"
                                isStarted = true
                            },
                            modifier = Modifier.testTag("start_dynamic")
                        )
                    } else {
                        SecondaryActionButton(
                            text = "Pause",
                            onClick = {
                                workoutState = "Paused"
                            },
                            modifier = Modifier.testTag("pause_dynamic")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Finish",
                            onClick = {
                                workoutState = "Completed"
                                exerciseCount = 0
                            },
                            modifier = Modifier.testTag("finish_dynamic")
                        )
                    }
                }
            ) {
                Text("Workout status: $workoutState")
            }
        }
        
        // Test initial state
        composeTestRule
            .onNodeWithText("Dynamic Workout (Ready)")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("6 exercises")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Workout status: Ready")
            .assertIsDisplayed()
        
        // Start workout
        composeTestRule
            .onNodeWithTag("start_dynamic")
            .performClick()
        
        // Verify state change
        composeTestRule
            .onNodeWithText("Dynamic Workout (In Progress)")
            .assertIsDisplayed()
        
        // Pause workout
        composeTestRule
            .onNodeWithTag("pause_dynamic")
            .performClick()
        
        composeTestRule
            .onNodeWithText("Dynamic Workout (Paused)")
            .assertIsDisplayed()
        
        // Finish workout
        composeTestRule
            .onNodeWithTag("finish_dynamic")
            .performClick()
        
        composeTestRule
            .onNodeWithText("Dynamic Workout (Completed)")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("0 exercises")
            .assertIsDisplayed()
    }

    // Performance Integration Testing
    @Test
    fun multipleCardsWithAnimations_performanceCompliance_maintained() {
        composeTestRule.setLiftrixContent {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Multiple cards with click animations
                repeat(5) { index ->
                    UnifiedWorkoutCard(
                        title = "Workout ${index + 1}",
                        subtitle = "Performance test card",
                        onClick = { /* Test click animation */ },
                        modifier = Modifier.testTag("perf_card_$index"),
                        actions = {
                            PrimaryActionButton(
                                text = "Action $index",
                                onClick = { /* Test button animation */ },
                                modifier = Modifier.testTag("perf_button_$index")
                            )
                        }
                    ) {
                        Text("Performance testing content for card $index")
                    }
                }
            }
        }
        
        // Test performance compliance for all cards
        repeat(5) { index ->
            composeTestRule
                .onNodeWithTag("perf_card_$index")
                .assertPerformanceCompliance()
                .assertIsDisplayed()
            
            composeTestRule
                .onNodeWithTag("perf_button_$index")
                .assertPerformanceCompliance()
                .assertHasHapticFeedback()
        }
    }
    
    // Accessibility Integration Testing
    @Test
    fun componentIntegration_accessibilityCompliance_complete() {
        composeTestRule.setLiftrixContent {
            Column {
                UnifiedWorkoutCard(
                    title = "Accessibility Test Workout",
                    subtitle = "WCAG 2.1 AA compliance validation",
                    onClick = { /* Clickable card test */ },
                    modifier = Modifier.testTag("accessibility_card"),
                    actions = {
                        TertiaryActionButton(
                            text = "More Info",
                            onClick = { /* Info action */ },
                            modifier = Modifier.testTag("accessibility_tertiary")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SecondaryActionButton(
                            text = "Edit Workout",
                            onClick = { /* Edit action */ },
                            modifier = Modifier.testTag("accessibility_secondary")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Start Workout",
                            onClick = { /* Start action */ },
                            modifier = Modifier.testTag("accessibility_primary")
                        )
                    }
                ) {
                    Text("Comprehensive accessibility testing content")
                }
            }
        }
        
        // Comprehensive accessibility validation
        composeTestRule
            .onNodeWithTag("accessibility_card")
            .assertAccessibilityCompliance(
                expectedDescription = "Accessibility Test Workout. WCAG 2.1 AA compliance validation",
                isEnabled = true
            )
        
        composeTestRule
            .onNodeWithTag("accessibility_tertiary")
            .assertAccessibilityCompliance("More Info button")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("accessibility_secondary")
            .assertAccessibilityCompliance("Edit Workout button")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("accessibility_primary")
            .assertAccessibilityCompliance("Start Workout button")
            .assertMinimumTouchTarget()
    }

    // Error State Integration Testing
    @Test
    fun errorStateComponents_integration_handlesCorrectly() {
        var hasError by mutableStateOf(false)
        var errorMessage by mutableStateOf("")
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = if (hasError) "Error: $errorMessage" else "Normal Workout",
                subtitle = if (hasError) "Please try again" else "6 exercises",
                modifier = Modifier.testTag("error_state_card"),
                actions = {
                    if (hasError) {
                        SecondaryActionButton(
                            text = "Retry",
                            onClick = {
                                hasError = false
                                errorMessage = ""
                            },
                            modifier = Modifier.testTag("retry_button")
                        )
                    } else {
                        PrimaryActionButton(
                            text = "Simulate Error",
                            onClick = {
                                hasError = true
                                errorMessage = "Network timeout"
                            },
                            modifier = Modifier.testTag("error_button")
                        )
                    }
                }
            ) {
                Text(
                    text = if (hasError) "An error occurred. Please check your connection." 
                           else "Ready to start your workout"
                )
            }
        }
        
        // Test normal state
        composeTestRule
            .onNodeWithText("Normal Workout")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Ready to start your workout")
            .assertIsDisplayed()
        
        // Trigger error state
        composeTestRule
            .onNodeWithTag("error_button")
            .performClick()
        
        // Verify error state
        composeTestRule
            .onNodeWithText("Error: Network timeout")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Please try again")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("An error occurred. Please check your connection.")
            .assertIsDisplayed()
        
        // Test retry functionality
        composeTestRule
            .onNodeWithTag("retry_button")
            .performClick()
        
        // Verify return to normal state
        composeTestRule
            .onNodeWithText("Normal Workout")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Ready to start your workout")
            .assertIsDisplayed()
    }

    // Complex User Flow Integration Testing
    @Test
    fun workoutCreationFlow_integration_worksEndToEnd() {
        var currentStep by mutableStateOf(1)
        var workoutName by mutableStateOf("")
        var exercisesSelected by mutableStateOf(0)
        
        composeTestRule.setLiftrixContent {
            when (currentStep) {
                1 -> {
                    // Step 1: Name the workout
                    UnifiedWorkoutCard(
                        title = "Create New Workout",
                        subtitle = "Step 1: Name your workout",
                        modifier = Modifier.testTag("step1_card"),
                        actions = {
                            PrimaryActionButton(
                                text = "Next",
                                onClick = {
                                    workoutName = "My Custom Workout"
                                    currentStep = 2
                                },
                                modifier = Modifier.testTag("step1_next")
                            )
                        }
                    ) {
                        Text("Enter a name for your workout")
                    }
                }
                2 -> {
                    // Step 2: Select exercises
                    UnifiedWorkoutCard(
                        title = "Add Exercises",
                        subtitle = "Step 2: Choose your exercises",
                        modifier = Modifier.testTag("step2_card"),
                        actions = {
                            SecondaryActionButton(
                                text = "Back",
                                onClick = { currentStep = 1 },
                                modifier = Modifier.testTag("step2_back")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            PrimaryActionButton(
                                text = "Create",
                                onClick = {
                                    exercisesSelected = 5
                                    currentStep = 3
                                },
                                modifier = Modifier.testTag("step2_create")
                            )
                        }
                    ) {
                        Text("Select exercises for: $workoutName")
                    }
                }
                3 -> {
                    // Step 3: Completion
                    UnifiedWorkoutCard(
                        title = "Workout Created!",
                        subtitle = "$workoutName • $exercisesSelected exercises",
                        modifier = Modifier.testTag("step3_card"),
                        actions = {
                            PrimaryActionButton(
                                text = "Start Workout",
                                onClick = { /* Start workout */ },
                                modifier = Modifier.testTag("step3_start")
                            )
                        }
                    ) {
                        Text("Your workout has been created successfully")
                    }
                }
            }
        }
        
        // Test step 1
        composeTestRule
            .onNodeWithTag("step1_card")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Step 1: Name your workout")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("step1_next")
            .performClick()
        
        // Test step 2
        composeTestRule
            .onNodeWithTag("step2_card")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Select exercises for: My Custom Workout")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("step2_create")
            .performClick()
        
        // Test step 3 (completion)
        composeTestRule
            .onNodeWithTag("step3_card")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("My Custom Workout • 5 exercises")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Your workout has been created successfully")
            .assertIsDisplayed()
        
        // Test final action
        composeTestRule
            .onNodeWithTag("step3_start")
            .assertIsDisplayed()
            .assertAccessibilityCompliance("Start Workout button")
    }
}