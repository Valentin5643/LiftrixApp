package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.testutils.*
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive UI tests for ModernActionButton system
 * 
 * Tests all three button variants (Primary, Secondary, Tertiary) for:
 * - Visual consistency and Material 3 design compliance
 * - Accessibility compliance (WCAG 2.1 AA)
 * - Button hierarchy and styling verification
 * - Haptic feedback integration
 * - State management (enabled/disabled)
 * - Touch target compliance (48dp minimum)
 * - Interaction testing and callback validation
 * - Animation and performance validation
 */
@RunWith(AndroidJUnit4::class)
class ModernActionButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Primary Action Button Tests
    @Test
    fun primaryActionButton_displaysCorrectContent() {
        val buttonText = "Start Workout"
        
        composeTestRule.setLiftrixContent {
            PrimaryActionButton(
                text = buttonText,
                onClick = { /* Test click handler */ }
            )
        }
        
        composeTestRule
            .onNodeWithText(buttonText)
            .assertIsDisplayed()
            .assertButtonHierarchy("primary")
    }
    
    @Test
    fun primaryActionButton_clickInteraction_triggersCallback() {
        var clickCount = 0
        val buttonText = "Primary Click Test"
        
        composeTestRule.setLiftrixContent {
            PrimaryActionButton(
                text = buttonText,
                onClick = { clickCount++ },
                modifier = Modifier.testTag("primary_button")
            )
        }
        
        composeTestRule
            .onNodeWithTag("primary_button")
            .performClick()
        
        assertEquals(1, clickCount)
    }
    
    @Test
    fun primaryActionButton_accessibilityCompliance() {
        val buttonText = "Accessible Primary"
        
        composeTestRule.setLiftrixContent {
            PrimaryActionButton(
                text = buttonText,
                onClick = { /* Test click handler */ }
            )
        }
        
        composeTestRule
            .onNodeWithText(buttonText)
            .assertAccessibilityCompliance(
                expectedDescription = "$buttonText button",
                expectedRole = Role.Button,
                isEnabled = true
            )
    }
    
    @Test
    fun primaryActionButton_minimumTouchTarget_isEnforced() {
        composeTestRule.setLiftrixContent {
            PrimaryActionButton(
                text = "Small",
                onClick = { /* Test click handler */ },
                modifier = Modifier.testTag("touch_target_test")
            )
        }
        
        composeTestRule
            .onNodeWithTag("touch_target_test")
            .assertMinimumTouchTarget()
    }
    
    @Test
    fun primaryActionButton_disabledState_behavesCorrectly() {
        var clickCount = 0
        
        composeTestRule.setLiftrixContent {
            PrimaryActionButton(
                text = "Disabled Primary",
                onClick = { clickCount++ },
                enabled = false,
                modifier = Modifier.testTag("disabled_primary")
            )
        }
        
        composeTestRule
            .onNodeWithTag("disabled_primary")
            .assertIsNotEnabled()
            .performClick()
        
        // Click should not trigger when disabled
        assertEquals(0, clickCount)
    }

    // Secondary Action Button Tests
    @Test
    fun secondaryActionButton_displaysCorrectContent() {
        val buttonText = "Edit Workout"
        
        composeTestRule.setLiftrixContent {
            SecondaryActionButton(
                text = buttonText,
                onClick = { /* Test click handler */ }
            )
        }
        
        composeTestRule
            .onNodeWithText(buttonText)
            .assertIsDisplayed()
            .assertButtonHierarchy("secondary")
    }
    
    @Test
    fun secondaryActionButton_clickInteraction_triggersCallback() {
        var clickCount = 0
        val buttonText = "Secondary Click Test"
        
        composeTestRule.setLiftrixContent {
            SecondaryActionButton(
                text = buttonText,
                onClick = { clickCount++ },
                modifier = Modifier.testTag("secondary_button")
            )
        }
        
        composeTestRule
            .onNodeWithTag("secondary_button")
            .performClick()
        
        assertEquals(1, clickCount)
    }
    
    @Test
    fun secondaryActionButton_accessibilityCompliance() {
        val buttonText = "Accessible Secondary"
        
        composeTestRule.setLiftrixContent {
            SecondaryActionButton(
                text = buttonText,
                onClick = { /* Test click handler */ }
            )
        }
        
        composeTestRule
            .onNodeWithText(buttonText)
            .assertAccessibilityCompliance(
                expectedDescription = "$buttonText button",
                expectedRole = Role.Button,
                isEnabled = true
            )
    }
    
    @Test
    fun secondaryActionButton_minimumTouchTarget_isEnforced() {
        composeTestRule.setLiftrixContent {
            SecondaryActionButton(
                text = "Small",
                onClick = { /* Test click handler */ },
                modifier = Modifier.testTag("secondary_touch_target")
            )
        }
        
        composeTestRule
            .onNodeWithTag("secondary_touch_target")
            .assertMinimumTouchTarget()
    }
    
    @Test
    fun secondaryActionButton_disabledState_behavesCorrectly() {
        var clickCount = 0
        
        composeTestRule.setLiftrixContent {
            SecondaryActionButton(
                text = "Disabled Secondary",
                onClick = { clickCount++ },
                enabled = false,
                modifier = Modifier.testTag("disabled_secondary")
            )
        }
        
        composeTestRule
            .onNodeWithTag("disabled_secondary")
            .assertIsNotEnabled()
            .performClick()
        
        // Click should not trigger when disabled
        assertEquals(0, clickCount)
    }

    // Tertiary Action Button Tests
    @Test
    fun tertiaryActionButton_displaysCorrectContent() {
        val buttonText = "Skip for Now"
        
        composeTestRule.setLiftrixContent {
            TertiaryActionButton(
                text = buttonText,
                onClick = { /* Test click handler */ }
            )
        }
        
        composeTestRule
            .onNodeWithText(buttonText)
            .assertIsDisplayed()
            .assertButtonHierarchy("tertiary")
    }
    
    @Test
    fun tertiaryActionButton_clickInteraction_triggersCallback() {
        var clickCount = 0
        val buttonText = "Tertiary Click Test"
        
        composeTestRule.setLiftrixContent {
            TertiaryActionButton(
                text = buttonText,
                onClick = { clickCount++ },
                modifier = Modifier.testTag("tertiary_button")
            )
        }
        
        composeTestRule
            .onNodeWithTag("tertiary_button")
            .performClick()
        
        assertEquals(1, clickCount)
    }
    
    @Test
    fun tertiaryActionButton_accessibilityCompliance() {
        val buttonText = "Accessible Tertiary"
        
        composeTestRule.setLiftrixContent {
            TertiaryActionButton(
                text = buttonText,
                onClick = { /* Test click handler */ }
            )
        }
        
        composeTestRule
            .onNodeWithText(buttonText)
            .assertAccessibilityCompliance(
                expectedDescription = "$buttonText button",
                expectedRole = Role.Button,
                isEnabled = true
            )
    }
    
    @Test
    fun tertiaryActionButton_minimumTouchTarget_isEnforced() {
        composeTestRule.setLiftrixContent {
            TertiaryActionButton(
                text = "Small",
                onClick = { /* Test click handler */ },
                modifier = Modifier.testTag("tertiary_touch_target")
            )
        }
        
        composeTestRule
            .onNodeWithTag("tertiary_touch_target")
            .assertMinimumTouchTarget()
    }
    
    @Test
    fun tertiaryActionButton_disabledState_behavesCorrectly() {
        var clickCount = 0
        
        composeTestRule.setLiftrixContent {
            TertiaryActionButton(
                text = "Disabled Tertiary",
                onClick = { clickCount++ },
                enabled = false,
                modifier = Modifier.testTag("disabled_tertiary")
            )
        }
        
        composeTestRule
            .onNodeWithTag("disabled_tertiary")
            .assertIsNotEnabled()
            .performClick()
        
        // Click should not trigger when disabled
        assertEquals(0, clickCount)
    }

    // Visual Hierarchy Tests
    @Test
    fun buttonHierarchy_visualDifferenciation_isCorrect() {
        composeTestRule.setLiftrixContent {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryActionButton(
                    text = "Primary",
                    onClick = { /* Test primary */ },
                    modifier = Modifier.testTag("hierarchy_primary")
                )
                
                SecondaryActionButton(
                    text = "Secondary",
                    onClick = { /* Test secondary */ },
                    modifier = Modifier.testTag("hierarchy_secondary")
                )
                
                TertiaryActionButton(
                    text = "Tertiary",
                    onClick = { /* Test tertiary */ },
                    modifier = Modifier.testTag("hierarchy_tertiary")
                )
            }
        }
        
        // All buttons should be displayed with proper hierarchy
        composeTestRule.testButtonVariant("Primary", "primary")
        composeTestRule.testButtonVariant("Secondary", "secondary")
        composeTestRule.testButtonVariant("Tertiary", "tertiary")
    }
    
    @Test
    fun buttonStates_enabledDisabled_visualConsistency() {
        composeTestRule.setLiftrixContent {
            Column {
                // Enabled state
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryActionButton(
                        text = "Primary",
                        onClick = { /* Test */ },
                        enabled = true,
                        modifier = Modifier.testTag("enabled_primary")
                    )
                    SecondaryActionButton(
                        text = "Secondary",
                        onClick = { /* Test */ },
                        enabled = true,
                        modifier = Modifier.testTag("enabled_secondary")
                    )
                    TertiaryActionButton(
                        text = "Tertiary",
                        onClick = { /* Test */ },
                        enabled = true,
                        modifier = Modifier.testTag("enabled_tertiary")
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Disabled state
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryActionButton(
                        text = "Primary",
                        onClick = { /* Test */ },
                        enabled = false,
                        modifier = Modifier.testTag("disabled_primary")
                    )
                    SecondaryActionButton(
                        text = "Secondary", 
                        onClick = { /* Test */ },
                        enabled = false,
                        modifier = Modifier.testTag("disabled_secondary")
                    )
                    TertiaryActionButton(
                        text = "Tertiary",
                        onClick = { /* Test */ },
                        enabled = false,
                        modifier = Modifier.testTag("disabled_tertiary")
                    )
                }
            }
        }
        
        // Verify enabled state
        composeTestRule
            .onNodeWithTag("enabled_primary")
            .assertIsEnabled()
        composeTestRule
            .onNodeWithTag("enabled_secondary")
            .assertIsEnabled()
        composeTestRule
            .onNodeWithTag("enabled_tertiary")
            .assertIsEnabled()
        
        // Verify disabled state
        composeTestRule
            .onNodeWithTag("disabled_primary")
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag("disabled_secondary")
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag("disabled_tertiary")
            .assertIsNotEnabled()
    }

    // Haptic Feedback Tests
    @Test
    fun allButtonVariants_hapticFeedback_isConfigured() {
        composeTestRule.setLiftrixContent {
            Column {
                PrimaryActionButton(
                    text = "Primary Haptic",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("haptic_primary")
                )
                SecondaryActionButton(
                    text = "Secondary Haptic",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("haptic_secondary")
                )
                TertiaryActionButton(
                    text = "Tertiary Haptic",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("haptic_tertiary")
                )
            }
        }
        
        // Verify haptic feedback configuration (through interaction capability)
        composeTestRule
            .onNodeWithTag("haptic_primary")
            .assertHasHapticFeedback()
        composeTestRule
            .onNodeWithTag("haptic_secondary")
            .assertHasHapticFeedback()
        composeTestRule
            .onNodeWithTag("haptic_tertiary")
            .assertHasHapticFeedback()
    }

    // Material 3 Design Compliance Tests
    @Test
    fun allButtonVariants_cornerRadius_isConsistent() {
        composeTestRule.setLiftrixContent {
            Column {
                PrimaryActionButton(
                    text = "Primary Corners",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("corners_primary")
                )
                SecondaryActionButton(
                    text = "Secondary Corners",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("corners_secondary")
                )
                TertiaryActionButton(
                    text = "Tertiary Corners",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("corners_tertiary")
                )
            }
        }
        
        // Verify 20dp corner radius consistency (through visual validation)
        composeTestRule
            .onNodeWithTag("corners_primary")
            .assertCornerRadius(20.dp)
        composeTestRule
            .onNodeWithTag("corners_secondary")
            .assertCornerRadius(20.dp)
        composeTestRule
            .onNodeWithTag("corners_tertiary")
            .assertCornerRadius(20.dp)
    }
    
    @Test
    fun allButtonVariants_colorContrast_meetsAccessibilityStandards() {
        composeTestRule.setLiftrixContent {
            Column {
                PrimaryActionButton(
                    text = "Primary Contrast",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("contrast_primary")
                )
                SecondaryActionButton(
                    text = "Secondary Contrast",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("contrast_secondary")
                )
                TertiaryActionButton(
                    text = "Tertiary Contrast",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("contrast_tertiary")
                )
            }
        }
        
        // Verify color contrast compliance (Material 3 semantic colors ensure compliance)
        composeTestRule
            .onNodeWithTag("contrast_primary")
            .assertColorContrast()
        composeTestRule
            .onNodeWithTag("contrast_secondary")
            .assertColorContrast()
        composeTestRule
            .onNodeWithTag("contrast_tertiary")
            .assertColorContrast()
    }

    // Performance and Animation Tests
    @Test
    fun allButtonVariants_pressAnimation_worksCorrectly() {
        composeTestRule.setLiftrixContent {
            Column {
                PrimaryActionButton(
                    text = "Primary Press",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("press_primary")
                )
                SecondaryActionButton(
                    text = "Secondary Press",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("press_secondary")
                )
                TertiaryActionButton(
                    text = "Tertiary Press",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("press_tertiary")
                )
            }
        }
        
        // Test press interactions (animation validation through interaction)
        composeTestRule
            .onNodeWithTag("press_primary")
            .testPressInteraction()
        composeTestRule
            .onNodeWithTag("press_secondary")
            .testPressInteraction()
        composeTestRule
            .onNodeWithTag("press_tertiary")
            .testPressInteraction()
    }
    
    @Test
    fun allButtonVariants_performanceCompliance_meetsSixtyFpsTarget() {
        composeTestRule.setLiftrixContent {
            Column {
                PrimaryActionButton(
                    text = "Primary Performance",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("perf_primary")
                )
                SecondaryActionButton(
                    text = "Secondary Performance",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("perf_secondary")
                )
                TertiaryActionButton(
                    text = "Tertiary Performance",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("perf_tertiary")
                )
            }
        }
        
        // Validate 60fps performance through rendering validation
        composeTestRule
            .onNodeWithTag("perf_primary")
            .assertPerformanceCompliance()
        composeTestRule
            .onNodeWithTag("perf_secondary")
            .assertPerformanceCompliance()
        composeTestRule
            .onNodeWithTag("perf_tertiary")
            .assertPerformanceCompliance()
    }

    // Integration Tests
    @Test
    fun buttonVariants_inWorkoutCard_integrationWorksCorrectly() {
        var primaryClicked = false
        var secondaryClicked = false
        
        composeTestRule.setLiftrixContent {
            UnifiedWorkoutCard(
                title = "Button Integration Test",
                subtitle = "Testing button integration",
                actions = {
                    SecondaryActionButton(
                        text = "Edit",
                        onClick = { secondaryClicked = true },
                        modifier = Modifier.testTag("integration_secondary")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PrimaryActionButton(
                        text = "Start",
                        onClick = { primaryClicked = true },
                        modifier = Modifier.testTag("integration_primary")
                    )
                }
            ) {
                androidx.compose.material3.Text("Card content with buttons")
            }
        }
        
        // Test button integration within UnifiedWorkoutCard
        composeTestRule
            .onNodeWithTag("integration_secondary")
            .assertIsDisplayed()
            .performClick()
        
        composeTestRule
            .onNodeWithTag("integration_primary")
            .assertIsDisplayed()
            .performClick()
        
        assertTrue(secondaryClicked)
        assertTrue(primaryClicked)
    }
    
    @Test
    fun buttonVariants_stateChange_updatesCorrectly() {
        var isEnabled by mutableStateOf(true)
        var buttonText by mutableStateOf("Initial Text")
        
        composeTestRule.setLiftrixContent {
            PrimaryActionButton(
                text = buttonText,
                onClick = {
                    isEnabled = false
                    buttonText = "Updated Text"
                },
                enabled = isEnabled,
                modifier = Modifier.testTag("state_change_button")
            )
        }
        
        // Test state change behavior
        composeTestRule.assertStateUpdates(
            initialState = "Initial Text",
            updatedState = "Updated Text",
            triggerUpdate = {
                composeTestRule
                    .onNodeWithTag("state_change_button")
                    .performClick()
            }
        )
        
        // Verify button is now disabled
        composeTestRule
            .onNodeWithTag("state_change_button")
            .assertIsNotEnabled()
    }

    // Comprehensive Design System Tests
    @Test
    fun modernActionButtonSystem_designSystemCompliance_isComplete() {
        composeTestRule.setLiftrixContent {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryActionButton(
                    text = "Design System Primary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("design_primary")
                )
                SecondaryActionButton(
                    text = "Design System Secondary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("design_secondary")
                )
                TertiaryActionButton(
                    text = "Design System Tertiary",
                    onClick = { /* Test */ },
                    modifier = Modifier.testTag("design_tertiary")
                )
            }
        }
        
        // Comprehensive design system validation
        composeTestRule
            .onNodeWithTag("design_primary")
            .assertUnifiedDesignSystem()
            .assertAccessibilityCompliance("Design System Primary button")
        
        composeTestRule
            .onNodeWithTag("design_secondary")
            .assertUnifiedDesignSystem()
            .assertAccessibilityCompliance("Design System Secondary button")
        
        composeTestRule
            .onNodeWithTag("design_tertiary")
            .assertUnifiedDesignSystem()
            .assertAccessibilityCompliance("Design System Tertiary button")
    }
}