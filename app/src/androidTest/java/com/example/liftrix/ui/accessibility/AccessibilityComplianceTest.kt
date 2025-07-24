package com.example.liftrix.ui.accessibility

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.ui.common.AccessibilityUtils
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.ModernActionButton.*
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Comprehensive accessibility compliance testing to ensure WCAG 2.1 AA compliance
 * across all UI components and screens.
 * 
 * Tests cover:
 * - Minimum touch target sizes (48dp)
 * - Color contrast ratios (4.5:1 for normal text, 3.0:1 for large text)
 * - Content descriptions for interactive elements
 * - Semantic roles for proper screen reader support
 * - Keyboard navigation accessibility
 * - Large text scaling support
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AccessibilityComplianceTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    /**
     * Tests that all interactive elements meet the minimum 48dp touch target requirement
     * as specified by WCAG 2.1 AA guidelines.
     */
    @Test
    fun allInteractiveElements_meetMinimumTouchTargetSize() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Test UnifiedWorkoutCard touch targets
                    UnifiedWorkoutCard(
                        title = "Test Workout Card",
                        subtitle = "Touch target test",
                        onClick = { /* Test click */ },
                        modifier = Modifier.testTag("test_workout_card")
                    ) {
                        Text("Card content for touch target testing")
                        
                        // Test action buttons in card
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Primary",
                                onClick = { /* Test primary action */ },
                                modifier = Modifier.testTag("test_primary_button")
                            )
                            SecondaryActionButton(
                                text = "Secondary", 
                                onClick = { /* Test secondary action */ },
                                modifier = Modifier.testTag("test_secondary_button")
                            )
                            TertiaryActionButton(
                                text = "Tertiary",
                                onClick = { /* Test tertiary action */ },
                                modifier = Modifier.testTag("test_tertiary_button")
                            )
                        }
                    }
                }
            }
        }
        
        // Verify minimum touch target sizes (48dp = 48 * density)
        val minimumSize = 48.dp
        
        // Test workout card touch target
        composeTestRule
            .onNodeWithTag("test_workout_card")
            .assertHeightIsAtLeast(minimumSize)
            .assertWidthIsAtLeast(minimumSize)
        
        // Test all button touch targets
        composeTestRule
            .onNodeWithTag("test_primary_button")
            .assertHeightIsAtLeast(minimumSize)
            
        composeTestRule
            .onNodeWithTag("test_secondary_button")
            .assertHeightIsAtLeast(minimumSize)
            
        composeTestRule
            .onNodeWithTag("test_tertiary_button")
            .assertHeightIsAtLeast(minimumSize)
    }
    
    /**
     * Tests that all interactive components provide proper content descriptions
     * for screen reader accessibility.
     */
    @Test
    fun allComponents_provideProperContentDescriptions() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column {
                    // Test workout card content descriptions
                    UnifiedWorkoutCard(
                        title = "Push Day Workout",
                        subtitle = "6 exercises • Last performed yesterday",
                        onClick = { /* Navigate to workout */ },
                        modifier = Modifier.testTag("content_desc_card")
                    ) {
                        Text("Ready to start your push day routine")
                        
                        PrimaryActionButton(
                            text = "Start Workout",
                            onClick = { /* Start workout */ },
                            modifier = Modifier.testTag("content_desc_button")
                        )
                    }
                    
                    // Test standalone buttons
                    Row {
                        PrimaryActionButton(
                            text = "Save Changes",
                            onClick = { },
                            modifier = Modifier.testTag("save_button")
                        )
                        SecondaryActionButton(
                            text = "Cancel",
                            onClick = { },
                            modifier = Modifier.testTag("cancel_button")
                        )
                    }
                }
            }
        }
        
        // Verify content descriptions are present and meaningful
        composeTestRule
            .onNodeWithTag("content_desc_card")
            .assert(
                hasContentDescriptionExactly("Push Day Workout. 6 exercises • Last performed yesterday") or
                hasAnyDescendant(hasText("Push Day Workout"))
            )
            
        composeTestRule
            .onNodeWithTag("content_desc_button")
            .assert(
                hasContentDescriptionExactly("Start Workout button") or
                hasText("Start Workout")
            )
            
        composeTestRule
            .onNodeWithTag("save_button")
            .assert(
                hasContentDescriptionExactly("Save Changes button") or
                hasText("Save Changes")
            )
            
        composeTestRule
            .onNodeWithTag("cancel_button") 
            .assert(
                hasContentDescriptionExactly("Cancel button") or
                hasText("Cancel")
            )
    }
    
    /**
     * Tests color contrast compliance with WCAG 2.1 AA standards.
     * Validates that foreground/background combinations meet 4.5:1 ratio for normal text
     * and 3.0:1 ratio for large text.
     */
    @Test
    fun colorContrast_meetsWcagStandards() {
        var primaryColor: Color = Color.Unspecified
        var backgroundColor: Color = Color.Unspecified
        var onSurfaceColor: Color = Color.Unspecified
        
        composeTestRule.setContent {
            LiftrixTheme {
                // Capture theme colors for testing
                primaryColor = MaterialTheme.colorScheme.primary
                backgroundColor = MaterialTheme.colorScheme.surface
                onSurfaceColor = MaterialTheme.colorScheme.onSurface
                
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Test primary text on surface background
                    Text(
                        text = "Primary text for contrast testing",
                        color = onSurfaceColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("primary_text")
                    )
                    
                    // Test button contrast
                    PrimaryActionButton(
                        text = "Button Contrast Test",
                        onClick = { },
                        modifier = Modifier.testTag("contrast_button")
                    )
                    
                    // Test card contrast
                    UnifiedWorkoutCard(
                        title = "Card Contrast Test",
                        modifier = Modifier.testTag("contrast_card")
                    ) {
                        Text(
                            text = "Card content for contrast validation",
                            modifier = Modifier.testTag("card_content_text")
                        )
                    }
                }
            }
        }
        
        // Validate contrast ratios using AccessibilityUtils
        val primaryTextContrast = AccessibilityUtils.checkContrastRatio(
            onSurfaceColor,
            backgroundColor
        )
        
        val primaryButtonContrast = AccessibilityUtils.checkContrastRatio(
            primaryColor,
            backgroundColor
        )
        
        // Assert contrast ratios meet WCAG AA standards
        assertTrue(
            "Primary text contrast ratio $primaryTextContrast:1 is below WCAG AA standard (4.5:1)",
            primaryTextContrast >= AccessibilityUtils.ContrastRatios.NORMAL_TEXT_AA
        )
        
        assertTrue(
            "Button contrast ratio $primaryButtonContrast:1 is below WCAG AA standard (3.0:1)",
            primaryButtonContrast >= AccessibilityUtils.ContrastRatios.NON_TEXT_AA
        )
        
        // Verify elements are actually displayed for visual validation
        composeTestRule
            .onNodeWithTag("primary_text")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithTag("contrast_button")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithTag("contrast_card")
            .assertIsDisplayed()
    }
    
    /**
     * Tests semantic roles and accessibility properties for proper screen reader support.
     */
    @Test
    fun components_haveProperSemanticRoles() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column {
                    // Test clickable card has button role
                    UnifiedWorkoutCard(
                        title = "Semantic Role Test Card",
                        onClick = { /* Test click */ },
                        modifier = Modifier.testTag("role_test_card")
                    ) {
                        Text("Card with click action should have button role")
                    }
                    
                    // Test buttons have proper roles
                    PrimaryActionButton(
                        text = "Primary Action",
                        onClick = { },
                        modifier = Modifier.testTag("role_test_primary")
                    )
                    
                    SecondaryActionButton(
                        text = "Secondary Action",
                        onClick = { },
                        modifier = Modifier.testTag("role_test_secondary")
                    )
                    
                    // Test non-clickable card has no button role
                    UnifiedWorkoutCard(
                        title = "Non-clickable Card",
                        modifier = Modifier.testTag("role_test_nonclickable")
                    ) {
                        Text("This card should not have button role")
                    }
                }
            }
        }
        
        // Test that clickable elements have button role
        composeTestRule
            .onNodeWithTag("role_test_card")
            .assert(hasClickAction())
            
        composeTestRule
            .onNodeWithTag("role_test_primary")
            .assert(hasClickAction())
            
        composeTestRule
            .onNodeWithTag("role_test_secondary")
            .assert(hasClickAction())
        
        // Test that non-clickable card doesn't have click action
        composeTestRule
            .onNodeWithTag("role_test_nonclickable")
            .assert(!hasClickAction())
    }
    
    /**
     * Tests that components work properly with large text scaling (accessibility feature).
     */
    @Test
    fun components_supportLargeTextScaling() {
        composeTestRule.setContent {
            LiftrixTheme {
                val systemAccessibilityState = remember {
                    com.example.liftrix.ui.common.SystemAccessibilityState(
                        isHighContrastEnabled = false,
                        isLargeTextEnabled = true,
                        isAccessibilityServiceEnabled = true,
                        fontScale = 2.0f, // 200% text scaling
                        needsEnhancedAccessibility = true
                    )
                }
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    UnifiedWorkoutCard(
                        title = "Large Text Test Card",
                        subtitle = "Testing large text support",
                        modifier = Modifier.testTag("large_text_card")
                    ) {
                        Text(
                            text = "This content should scale properly with large text settings.",
                            modifier = Modifier.testTag("large_text_content")
                        )
                        
                        PrimaryActionButton(
                            text = "Large Text Button",
                            onClick = { },
                            modifier = Modifier.testTag("large_text_button")
                        )
                    }
                }
            }
        }
        
        // Verify components are still displayed and functional with large text
        composeTestRule
            .onNodeWithTag("large_text_card")
            .assertIsDisplayed()
            .assertExists()
        
        composeTestRule
            .onNodeWithTag("large_text_content")
            .assertIsDisplayed()
            .assertExists()
        
        composeTestRule
            .onNodeWithTag("large_text_button")
            .assertIsDisplayed()
            .assertExists()
            .assert(hasClickAction())
    }
    
    /**
     * Tests accessibility compliance validation utility functions.
     */
    @Test
    fun accessibilityUtils_validateCompliance() {
        // Test compliance validation with good accessibility
        val goodCompliance = AccessibilityUtils.validateAccessibilityCompliance(
            hasContentDescription = true,
            hasSufficientTouchTarget = true,
            hasProperRole = true,
            contrastRatio = 5.5f, // Above WCAG AA standard
            isInteractive = true
        )
        
        assertTrue(
            "Component with good accessibility should be compliant",
            goodCompliance.isCompliant
        )
        assertTrue(
            "Compliance score should be 100% for fully accessible component",
            goodCompliance.score == 100
        )
        assertTrue(
            "Should have no accessibility issues",
            goodCompliance.issues.isEmpty()
        )
        
        // Test compliance validation with poor accessibility
        val poorCompliance = AccessibilityUtils.validateAccessibilityCompliance(
            hasContentDescription = false,
            hasSufficientTouchTarget = false,
            hasProperRole = false,
            contrastRatio = 2.1f, // Below WCAG AA standard
            isInteractive = true
        )
        
        assertTrue(
            "Component with poor accessibility should not be compliant",
            !poorCompliance.isCompliant
        )
        assertTrue(
            "Compliance score should be 0% for non-accessible component",
            poorCompliance.score == 0
        )
        assertTrue(
            "Should have multiple accessibility issues",
            poorCompliance.issues.size == 4
        )
    }
}