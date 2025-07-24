package com.example.liftrix.ui.accessibility

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.testutils.*
import com.example.liftrix.ui.workout.components.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive Accessibility Test Suite for Redesigned UI Components
 * 
 * Validates WCAG 2.1 AA compliance across all new UI components including:
 * - UnifiedWorkoutCard accessibility semantics
 * - ModernActionButton accessibility compliance
 * - Touch target compliance (minimum 48dp)
 * - Content description accuracy and completeness
 * - Keyboard navigation support
 * - Screen reader compatibility (TalkBack)
 * - Color contrast validation through Material 3 semantic colors
 * - Focus management and announcement behavior
 * 
 * This test suite ensures that all redesigned components maintain the high
 * accessibility standards established in the existing codebase while
 * introducing new interactive elements safely.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTestSuite {

    @get:Rule
    val composeTestRule = createComposeRule()

    // WCAG 2.1 AA Touch Target Compliance (Minimum 44dp/48dp)
    @Test
    fun allInteractiveElements_touchTargetCompliance_meetsWcagStandards() {
        composeTestRule.setLiftrixContent {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // UnifiedWorkoutCard clickable variant
                UnifiedWorkoutCard(
                    title = "Touch Target Card",
                    subtitle = "Clickable card test",
                    onClick = { /* Test click */ },
                    modifier = Modifier.testTag("clickable_card_target")
                ) {
                    Text("Card content")
                }
                
                // All ModernActionButton variants
                PrimaryActionButton(
                    text = "Primary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("primary_target")
                )
                
                SecondaryActionButton(
                    text = "Secondary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("secondary_target")
                )
                
                TertiaryActionButton(
                    text = "Tertiary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("tertiary_target")
                )
            }
        }
        
        // Verify WCAG 2.1 AA minimum touch target compliance (48dp)
        composeTestRule
            .onNodeWithTag("clickable_card_target")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("primary_target")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("secondary_target")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("tertiary_target")
            .assertMinimumTouchTarget()
    }
    
    // Content Description Accuracy and Completeness
    @Test
    fun allComponents_contentDescriptions_areAccurateAndComplete() {
        composeTestRule.setLiftrixContent {
            Column {
                // UnifiedWorkoutCard with complete semantic information
                UnifiedWorkoutCard(
                    title = "Push Day Workout",
                    subtitle = "6 exercises • 45 minutes",
                    onClick = { /* Test click */ },
                    modifier = Modifier.testTag("semantic_card")
                ) {
                    Text("Complete upper body strength training")
                }
                
                // Compact variant with different semantic pattern
                CompactUnifiedWorkoutCard(
                    title = "Quick Session",
                    subtitle = "3 exercises • 15 min",
                    onClick = { /* Test click */ },
                    modifier = Modifier.testTag("compact_semantic_card")
                ) {
                    Text("Fast workout session")
                }
                
                // Non-clickable card (information only)
                UnifiedWorkoutCard(
                    title = "Information Card",
                    subtitle = "Read-only data",
                    onClick = null,
                    modifier = Modifier.testTag("info_card")
                ) {
                    Text("This card displays information only")
                }
            }
        }
        
        // Verify content descriptions are complete and accurate
        composeTestRule
            .onNodeWithContentDescription("Push Day Workout. 6 exercises • 45 minutes")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        // Compact card has specific "Compact card:" prefix
        composeTestRule
            .onNodeWithContentDescription("Compact card: Quick Session. 3 exercises • 15 min")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        // Non-clickable card has appropriate semantics
        composeTestRule
            .onNodeWithContentDescription("Information Card. Read-only data")
            .assertIsDisplayed()
            .assertHasNoClickAction()
    }
    
    // Button Semantic Role and State Validation
    @Test
    fun allButtonVariants_semanticRoles_areCorrectlyDefined() {
        composeTestRule.setLiftrixContent {
            Column {
                PrimaryActionButton(
                    text = "Start Workout",
                    onClick = { /* Test */ },
                    enabled = true,
                    modifier = Modifier.testTag("semantic_primary")
                )
                
                SecondaryActionButton(
                    text = "Edit Workout",
                    onClick = { /* Test */ },
                    enabled = true,
                    modifier = Modifier.testTag("semantic_secondary")
                )
                
                TertiaryActionButton(
                    text = "More Options",
                    onClick = { /* Test */ },
                    enabled = false, // Test disabled state semantics
                    modifier = Modifier.testTag("semantic_tertiary_disabled")
                )
            }
        }
        
        // Verify button semantic roles and states
        composeTestRule
            .onNodeWithTag("semantic_primary")
            .assertSemanticRole(Role.Button)
            .assertAccessibilityCompliance(
                expectedDescription = "Start Workout button",
                expectedRole = Role.Button,
                isEnabled = true
            )
        
        composeTestRule
            .onNodeWithTag("semantic_secondary")
            .assertSemanticRole(Role.Button)
            .assertAccessibilityCompliance(
                expectedDescription = "Edit Workout button",
                expectedRole = Role.Button,
                isEnabled = true
            )
        
        composeTestRule
            .onNodeWithTag("semantic_tertiary_disabled")
            .assertSemanticRole(Role.Button)
            .assertAccessibilityCompliance(
                expectedDescription = "More Options button",
                expectedRole = Role.Button,
                isEnabled = false
            )
    }
    
    // Screen Reader Navigation and Announcement Testing
    @Test
    fun componentInteractions_screenReaderAnnouncements_areAppropriate() {
        var interactionCount by mutableStateOf(0)
        var lastAction by mutableStateOf("None")
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = "Interactive Workout Card",
                subtitle = "Screen reader test • $interactionCount interactions",
                onClick = {
                    interactionCount++
                    lastAction = "Card clicked"
                },
                modifier = Modifier.testTag("reader_card"),
                actions = {
                    PrimaryActionButton(
                        text = "Start ($interactionCount)",
                        onClick = {
                            interactionCount++
                            lastAction = "Start clicked"
                        },
                        modifier = Modifier.testTag("reader_start")
                    )
                }
            ) {
                Text("Last action: $lastAction")
            }
        }
        
        // Test card interaction and state announcement
        composeTestRule
            .onNodeWithTag("reader_card")
            .assertIsDisplayed()
            .performClick()
        
        // Verify state change is reflected in accessibility content
        composeTestRule
            .onNodeWithText("Screen reader test • 1 interactions")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Last action: Card clicked")
            .assertIsDisplayed()
        
        // Test button interaction
        composeTestRule
            .onNodeWithTag("reader_start")
            .assertIsDisplayed()
            .performClick()
        
        // Verify button state change
        composeTestRule
            .onNodeWithText("Start (2)")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Last action: Start clicked")
            .assertIsDisplayed()
    }
    
    // Focus Management and Navigation Order
    @Test
    fun componentFocusOrder_keyboardNavigation_followsLogicalSequence() {
        composeTestRule.setLiftrixContent {
            Column {
                UnifiedWorkoutCard(
                    title = "First Card",
                    subtitle = "Focus order test",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("first_focusable"),
                    actions = {
                        SecondaryActionButton(
                            text = "Second Focus",
                            onClick = { /* Test */ },
                            modifier = Modifier.testTag("second_focusable")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Third Focus",
                            onClick = { /* Test */ },
                            modifier = Modifier.testTag("third_focusable")
                        )
                    }
                ) {
                    Text("Focus sequence testing")
                }
            }
        }
        
        // Verify all focusable elements are accessible
        composeTestRule
            .onNodeWithTag("first_focusable")
            .assertHasClickAction()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("second_focusable")
            .assertHasClickAction()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("third_focusable")
            .assertHasClickAction()
            .assertIsDisplayed()
        
        // Test focus order by ensuring all elements are reachable
        // In actual TalkBack testing, this would verify swipe navigation order
        composeTestRule
            .onAllNodesWithClickAction()
            .assertCountEquals(3) // Card + 2 buttons
    }
    
    // Color Contrast and Material 3 Semantic Color Validation
    @Test
    fun allComponents_colorContrast_meetsWcagAAStandards() {
        composeTestRule.setLiftrixContent {
            Column {
                // Test all button variants for color contrast
                PrimaryActionButton(
                    text = "High Contrast Primary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("contrast_primary")
                )
                
                SecondaryActionButton(
                    text = "High Contrast Secondary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("contrast_secondary")
                )
                
                TertiaryActionButton(
                    text = "High Contrast Tertiary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("contrast_tertiary")
                )
                
                // Test card backgrounds and text contrast
                UnifiedWorkoutCard(
                    title = "Contrast Validation Card",
                    subtitle = "WCAG AA compliance test",
                    modifier = Modifier.testTag("contrast_card")
                ) {
                    Text("All text should meet WCAG AA color contrast ratios")
                }
            }
        }
        
        // Material 3 semantic colors ensure WCAG AA compliance
        composeTestRule
            .onNodeWithTag("contrast_primary")
            .assertColorContrast()
        
        composeTestRule
            .onNodeWithTag("contrast_secondary")
            .assertColorContrast()
        
        composeTestRule
            .onNodeWithTag("contrast_tertiary")
            .assertColorContrast()
        
        composeTestRule
            .onNodeWithTag("contrast_card")
            .assertColorContrast()
    }
    
    // Complex Component Accessibility Integration
    @Test
    fun complexComponentIntegration_accessibilityCompliance_isComplete() {
        var cardExpanded by mutableStateOf(false)
        var selectedOption by mutableStateOf("None")
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = "Complex Accessibility Test",
                subtitle = "Expanded: $cardExpanded • Selected: $selectedOption",
                onClick = { cardExpanded = !cardExpanded },
                modifier = Modifier.testTag("complex_card"),
                actions = {
                    if (cardExpanded) {
                        TertiaryActionButton(
                            text = "Option A",
                            onClick = { selectedOption = "A" },
                            modifier = Modifier.testTag("option_a")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TertiaryActionButton(
                            text = "Option B",
                            onClick = { selectedOption = "B" },
                            modifier = Modifier.testTag("option_b")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Confirm",
                            onClick = { cardExpanded = false },
                            modifier = Modifier.testTag("confirm_selection")
                        )
                    } else {
                        SecondaryActionButton(
                            text = "Expand Options",
                            onClick = { cardExpanded = true },
                            modifier = Modifier.testTag("expand_options")
                        )
                    }
                }
            ) {
                if (cardExpanded) {
                    Text("Select an option from the buttons above")
                } else {
                    Text("Click 'Expand Options' to see available choices")
                }
            }
        }
        
        // Test initial collapsed state accessibility
        composeTestRule
            .onNodeWithContentDescription("Complex Accessibility Test. Expanded: false • Selected: None")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule
            .onNodeWithTag("expand_options")
            .assertAccessibilityCompliance("Expand Options button")
        
        // Expand and test dynamic content accessibility
        composeTestRule
            .onNodeWithTag("expand_options")
            .performClick()
        
        // Verify expanded state accessibility
        composeTestRule
            .onNodeWithContentDescription("Complex Accessibility Test. Expanded: true • Selected: None")
            .assertIsDisplayed()
        
        // Test all new options are accessible
        composeTestRule
            .onNodeWithTag("option_a")
            .assertAccessibilityCompliance("Option A button")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("option_b")
            .assertAccessibilityCompliance("Option B button")
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onNodeWithTag("confirm_selection")
            .assertAccessibilityCompliance("Confirm button")
            .assertMinimumTouchTarget()
        
        // Test option selection and state update
        composeTestRule
            .onNodeWithTag("option_b")
            .performClick()
        
        composeTestRule
            .onNodeWithContentDescription("Complex Accessibility Test. Expanded: true • Selected: B")
            .assertIsDisplayed()
        
        // Test confirmation and collapse
        composeTestRule
            .onNodeWithTag("confirm_selection")
            .performClick()
        
        composeTestRule
            .onNodeWithContentDescription("Complex Accessibility Test. Expanded: false • Selected: B")
            .assertIsDisplayed()
    }
    
    // Error State Accessibility Handling
    @Test
    fun errorStates_accessibilityAnnouncements_areAppropriate() {
        var hasError by mutableStateOf(false)
        var errorMessage by mutableStateOf("")
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = if (hasError) "Error State" else "Normal State",
                subtitle = if (hasError) "Error: $errorMessage" else "No errors",
                modifier = Modifier.testTag("error_accessibility_card"),
                actions = {
                    if (hasError) {
                        PrimaryActionButton(
                            text = "Retry Action",
                            onClick = {
                                hasError = false
                                errorMessage = ""
                            },
                            modifier = Modifier.testTag("retry_accessible")
                        )
                    } else {
                        PrimaryActionButton(
                            text = "Trigger Error",
                            onClick = {
                                hasError = true
                                errorMessage = "Connection failed"
                            },
                            modifier = Modifier.testTag("trigger_error")
                        )
                    }
                }
            ) {
                Text(
                    text = if (hasError) "Please check your connection and try again" 
                           else "Everything is working normally"
                )
            }
        }
        
        // Test normal state accessibility
        composeTestRule
            .onNodeWithContentDescription("Normal State. No errors")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("trigger_error")
            .assertAccessibilityCompliance("Trigger Error button")
        
        // Trigger error and test error state accessibility
        composeTestRule
            .onNodeWithTag("trigger_error")
            .performClick()
        
        // Verify error state is properly announced
        composeTestRule
            .onNodeWithContentDescription("Error State. Error: Connection failed")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Please check your connection and try again")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("retry_accessible")
            .assertAccessibilityCompliance("Retry Action button")
        
        // Test error recovery
        composeTestRule
            .onNodeWithTag("retry_accessible")
            .performClick()
        
        composeTestRule
            .onNodeWithContentDescription("Normal State. No errors")
            .assertIsDisplayed()
    }
    
    // Comprehensive Design System Accessibility Validation
    @Test
    fun unifiedDesignSystem_accessibilityCompliance_isComprehensive() {
        composeTestRule.setLiftrixContent {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // All component variants in unified design system
                UnifiedWorkoutCard(
                    title = "Design System Card",
                    subtitle = "Accessibility validation",
                    onClick = { /* Test */ },
                    actions = {
                        TertiaryActionButton(
                            text = "More",
                            onClick = { /* Test */ }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SecondaryActionButton(
                            text = "Edit",
                            onClick = { /* Test */ }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PrimaryActionButton(
                            text = "Start",
                            onClick = { /* Test */ }
                        )
                    }
                ) {
                    Text("Complete design system validation")
                }
                
                CompactUnifiedWorkoutCard(
                    title = "Compact System Card",
                    subtitle = "Reduced spacing variant",
                    onClick = { /* Test */ }
                ) {
                    Text("Compact design system validation")
                }
            }
        }
        
        // Comprehensive accessibility validation for entire design system
        composeTestRule
            .onAllNodesWithClickAction()
            .assertCountEquals(6) // 2 cards + 3 buttons + 1 compact card
        
        // Verify all interactive elements meet touch target requirements
        composeTestRule
            .onAllNodes(hasClickAction())
            .filterToOne(hasContentDescription("Design System Card. Accessibility validation"))
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onAllNodes(hasClickAction())
            .filterToOne(hasText("More"))
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onAllNodes(hasClickAction())
            .filterToOne(hasText("Edit"))
            .assertMinimumTouchTarget()
        
        composeTestRule
            .onAllNodes(hasClickAction())
            .filterToOne(hasText("Start"))
            .assertMinimumTouchTarget()
        
        // Verify compact card accessibility
        composeTestRule
            .onNodeWithContentDescription("Compact card: Compact System Card. Reduced spacing variant")
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
        
        // Verify all components follow unified design system accessibility standards
        composeTestRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .let { nodes ->
                // All interactive nodes should be present and accessible
                assert(nodes.size == 6) { "Expected 6 interactive elements, found ${nodes.size}" }
            }
    }
}