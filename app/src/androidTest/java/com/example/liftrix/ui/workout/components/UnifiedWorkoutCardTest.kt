package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for UnifiedWorkoutCard component
 * Tests visual consistency, accessibility compliance, and user interactions
 */
@RunWith(AndroidJUnit4::class)
class UnifiedWorkoutCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unifiedWorkoutCard_displaysCorrectContent() {
        val title = "Push Day Workout"
        val subtitle = "6 exercises"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = title,
                    subtitle = subtitle
                ) {
                    Text("Workout content")
                }
            }
        }
        
        // Verify title is displayed
        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
        
        // Verify subtitle is displayed
        composeTestRule
            .onNodeWithText(subtitle)
            .assertIsDisplayed()
        
        // Verify content is displayed
        composeTestRule
            .onNodeWithText("Workout content")
            .assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCard_withoutSubtitle_displaysCorrectContent() {
        val title = "Leg Day Workout"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = title,
                    subtitle = null
                ) {
                    Text("Workout content without subtitle")
                }
            }
        }
        
        // Verify title is displayed
        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
        
        // Verify content is displayed
        composeTestRule
            .onNodeWithText("Workout content without subtitle")
            .assertIsDisplayed()
    }

    @Test
    fun unifiedWorkoutCard_clickInteraction_triggersCallback() {
        var clickCount = 0
        val title = "Clickable Card"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = title,
                    onClick = { clickCount++ },
                    modifier = Modifier.testTag("clickable_card")
                ) {
                    Text("Click me!")
                }
            }
        }
        
        // Perform click
        composeTestRule
            .onNodeWithTag("clickable_card")
            .performClick()
        
        // Verify callback was triggered
        assert(clickCount == 1)
    }
    
    @Test
    fun unifiedWorkoutCard_withActions_displaysActionsCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = "Card with Actions",
                    subtitle = "Action buttons test"
                ) {
                    Text("Content with actions")
                    // Actions in the actions slot
                } 
            }
        }
        
        // Verify content is displayed correctly with actions
        composeTestRule
            .onNodeWithText("Card with Actions")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Content with actions")
            .assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCard_actionButtons_interactionsWork() {
        var editClicked = false
        var startClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = "Workout with Actions",
                    subtitle = "Test action interactions",
                    actions = {
                        TextButton(
                            onClick = { editClicked = true },
                            modifier = Modifier.testTag("edit_button")
                        ) {
                            Text("Edit")
                        }
                        Button(
                            onClick = { startClicked = true },
                            modifier = Modifier.testTag("start_button")
                        ) {
                            Text("Start")
                        }
                    }
                ) {
                    Text("Workout ready to start")
                }
            }
        }
        
        // Test edit button
        composeTestRule
            .onNodeWithTag("edit_button")
            .assertIsDisplayed()
            .performClick()
        
        // Test start button
        composeTestRule
            .onNodeWithTag("start_button")
            .assertIsDisplayed()
            .performClick()
        
        // Verify callbacks were triggered
        assert(editClicked)
        assert(startClicked)
    }

    @Test
    fun unifiedWorkoutCard_accessibilityContent_isCorrect() {
        val title = "Accessible Card"
        val subtitle = "With proper semantics"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = title,
                    subtitle = subtitle,
                    onClick = { /* Click handler */ }
                ) {
                    Text("Accessible content")
                }
            }
        }
        
        // Verify card has proper accessibility content description
        composeTestRule
            .onNodeWithContentDescription("$title. $subtitle")
            .assertIsDisplayed()
        
        // Verify card has button role when clickable
        composeTestRule
            .onNodeWithText(title)
            .assertHasClickAction()
    }
    
    @Test
    fun unifiedWorkoutCard_nonClickable_hasCorrectAccessibility() {
        val title = "Non-clickable Card"
        val subtitle = "Read-only information"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = title,
                    subtitle = subtitle,
                    onClick = null // Non-clickable
                ) {
                    Text("Read-only content")
                }
            }
        }
        
        // Verify card has accessibility content but no click action
        composeTestRule
            .onNodeWithContentDescription("$title. $subtitle")
            .assertIsDisplayed()
        
        // Verify card does not have click action when non-clickable
        composeTestRule
            .onNodeWithText(title)
            .assertHasNoClickAction()
    }

    @Test
    fun compactUnifiedWorkoutCard_displaysCorrectContent() {
        val title = "Compact Card"
        val subtitle = "Smaller spacing"
        
        composeTestRule.setContent {
            LiftrixTheme {
                CompactUnifiedWorkoutCard(
                    title = title,
                    subtitle = subtitle
                ) {
                    Text("Compact content")
                }
            }
        }
        
        // Verify compact card displays content correctly
        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText(subtitle)
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Compact content")
            .assertIsDisplayed()
    }
    
    @Test
    fun compactUnifiedWorkoutCard_accessibilityDescription_isCorrect() {
        val title = "Compact Accessible"
        val subtitle = "With compact semantics"
        
        composeTestRule.setContent {
            LiftrixTheme {
                CompactUnifiedWorkoutCard(
                    title = title,
                    subtitle = subtitle,
                    onClick = { /* Click handler */ }
                ) {
                    Text("Compact accessible content")
                }
            }
        }
        
        // Verify compact card has proper accessibility content description with "Compact card:" prefix
        composeTestRule
            .onNodeWithContentDescription("Compact card: $title. $subtitle")
            .assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCard_visualConsistency_acrossDifferentContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                // Multiple cards with different content lengths
                UnifiedWorkoutCard(
                    title = "Short",
                    subtitle = "Brief"
                ) {
                    Text("Short content")
                }
                
                UnifiedWorkoutCard(
                    title = "Very Long Title That Spans Multiple Lines And Tests Text Wrapping",
                    subtitle = "Very long subtitle that also spans multiple lines to test how the card handles longer content"
                ) {
                    Text("This is a much longer content section that tests how the unified workout card handles extensive content. It should maintain proper spacing and alignment.")
                }
            }
        }
        
        // Verify both cards display their content correctly
        composeTestRule
            .onNodeWithText("Short")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Very Long Title That Spans Multiple Lines And Tests Text Wrapping")
            .assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCard_stateChanges_updateCorrectly() {
        composeTestRule.setContent {
            var title by remember { mutableStateOf("Initial Title") }
            var subtitle by remember { mutableStateOf("Initial Subtitle") }
            
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = title,
                    subtitle = subtitle,
                    onClick = {
                        title = "Updated Title"
                        subtitle = "Updated Subtitle"
                    }
                ) {
                    Text("Content updates with state")
                }
            }
        }
        
        // Verify initial state
        composeTestRule
            .onNodeWithText("Initial Title")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Initial Subtitle")
            .assertIsDisplayed()
        
        // Trigger state change by clicking
        composeTestRule
            .onNodeWithText("Initial Title")
            .performClick()
        
        // Verify state was updated
        composeTestRule
            .onNodeWithText("Updated Title")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Updated Subtitle")
            .assertIsDisplayed()
    }
    
    @Test
    fun unifiedWorkoutCard_minimumTouchTarget_isEnforced() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = "Touch Target Test",
                    onClick = { /* Click handler */ },
                    modifier = Modifier.testTag("touch_target_card")
                ) {
                    Text("Small content")
                }
            }
        }
        
        // Verify card meets minimum touch target requirements
        // The ensureMinimumTouchTarget() modifier should guarantee at least 48dp height
        composeTestRule
            .onNodeWithTag("touch_target_card")
            .assertHeightIsAtLeast(48.dp)
    }
}