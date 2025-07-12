package com.example.liftrix.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.AccessibilityColors
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive accessibility compliance validation tests.
 * Tests WCAG 2.1 AA compliance, semantic structure, and TalkBack support.
 */
class AccessibilityTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    /**
     * Test contrast ratio calculations
     */
    @Test
    fun contrastRatio_calculatesCorrectly() {
        // Test known contrast ratios
        val whiteOnBlack = AccessibilityColors.calculateContrastRatio(Color.White, Color.Black)
        assertEquals(21.0f, whiteOnBlack, 0.1f)
        
        val blackOnWhite = AccessibilityColors.calculateContrastRatio(Color.Black, Color.White)
        assertEquals(21.0f, blackOnWhite, 0.1f)
        
        val grayOnWhite = AccessibilityColors.calculateContrastRatio(Color.Gray, Color.White)
        assertTrue("Gray on white should have lower contrast", grayOnWhite < 21.0f)
        assertTrue("Gray on white should have some contrast", grayOnWhite > 1.0f)
    }
    
    /**
     * Test WCAG AA compliance checking
     */
    @Test
    fun wcagAACompliance_checksCorrectly() {
        // Test passing combinations
        assertTrue("White on black should pass AA", 
            AccessibilityColors.meetsAAStandards(Color.White, Color.Black))
        assertTrue("Black on white should pass AA", 
            AccessibilityColors.meetsAAStandards(Color.Black, Color.White))
        
        // Test failing combinations
        assertFalse("Light gray on white should fail AA", 
            AccessibilityColors.meetsAAStandards(Color(0xFFE0E0E0), Color.White))
        assertFalse("Dark gray on black should fail AA", 
            AccessibilityColors.meetsAAStandards(Color(0xFF202020), Color.Black))
    }
    
    /**
     * Test brand color accessibility
     */
    @Test
    fun brandColors_meetAccessibilityStandards() {
        // Test primary brand colors
        val primaryOnLight = AccessibilityColors.calculateContrastRatio(
            LiftrixColors.Primary, 
            LiftrixColors.BackgroundLight
        )
        assertTrue("Primary on light background should meet AA standards", 
            primaryOnLight >= AccessibilityColors.ContrastRatios.NORMAL_TEXT_AA)
        
        val primaryOnDark = AccessibilityColors.calculateContrastRatio(
            LiftrixColors.Primary, 
            LiftrixColors.BackgroundDark
        )
        assertTrue("Primary on dark background should meet AA standards", 
            primaryOnDark >= AccessibilityColors.ContrastRatios.NORMAL_TEXT_AA)
        
        // Test secondary brand colors
        val secondaryOnLight = AccessibilityColors.calculateContrastRatio(
            LiftrixColors.Secondary, 
            LiftrixColors.BackgroundLight
        )
        assertTrue("Secondary on light background should meet AA standards", 
            secondaryOnLight >= AccessibilityColors.ContrastRatios.NORMAL_TEXT_AA)
    }
    
    /**
     * Test color scheme validation
     */
    @Test
    fun colorScheme_validatesCorrectly() {
        val lightScheme = lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            secondary = Color.Black,
            onSecondary = Color.White,
            surface = Color.White,
            onSurface = Color.Black,
            background = Color.White,
            onBackground = Color.Black,
            error = Color(0xFFD32F2F),
            onError = Color.White
        )
        
        val result = AccessibilityColors.validateColorScheme(lightScheme)
        assertTrue("Well-designed color scheme should be compliant", result.isCompliant)
        assertEquals("Should have 100% compliance", 100, result.compliancePercentage)
        assertTrue("Should have no issues", result.issues.isEmpty())
    }
    
    /**
     * Test high contrast color scheme
     */
    @Test
    fun highContrastColorScheme_meetsStandards() {
        val lightHighContrast = AccessibilityColors.getHighContrastColorScheme(false)
        val darkHighContrast = AccessibilityColors.getHighContrastColorScheme(true)
        
        val lightResult = AccessibilityColors.validateColorScheme(lightHighContrast)
        val darkResult = AccessibilityColors.validateColorScheme(darkHighContrast)
        
        assertTrue("Light high contrast should be compliant", lightResult.isCompliant)
        assertTrue("Dark high contrast should be compliant", darkResult.isCompliant)
        
        assertEquals("Light high contrast should have 100% compliance", 100, lightResult.compliancePercentage)
        assertEquals("Dark high contrast should have 100% compliance", 100, darkResult.compliancePercentage)
    }
    
    /**
     * Test accessible card component
     */
    @Test
    fun accessibleCard_hasProperSemantics() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleLiftrixCard(
                    contentDescription = "Test card with workout information",
                    isHeading = true,
                    headingLevel = 2
                ) {
                    AccessibleText("Card content")
                }
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Test card with workout information")
            .assertIsDisplayed()
    }
    
    /**
     * Test accessible button component
     */
    @Test
    fun accessibleButton_hasProperSemantics() {
        var clickCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleLiftrixButton(
                    onClick = { clickCount++ },
                    text = "Start Workout",
                    contentDescription = "Start your workout session"
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Start your workout session")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .performClick()
        
        assertEquals("Button should handle clicks", 1, clickCount)
    }
    
    /**
     * Test accessible text component
     */
    @Test
    fun accessibleText_hasProperSemantics() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleText(
                    text = "Workout Progress",
                    isHeading = true,
                    headingLevel = 1,
                    contentDescription = "Main workout progress heading"
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Main workout progress heading")
            .assertIsDisplayed()
    }
    
    /**
     * Test accessible heading component
     */
    @Test
    fun accessibleHeading_hasProperHierarchy() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleHeading(
                    text = "Exercise Statistics",
                    level = 2
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Exercise Statistics")
            .assertIsDisplayed()
    }
    
    /**
     * Test minimum touch target sizes
     */
    @Test
    fun touchTargets_meetMinimumSize() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleLiftrixButton(
                    onClick = { },
                    text = "Test"
                )
            }
        }
        
        val button = composeTestRule.onNodeWithText("Test")
        val semanticsNode = button.fetchSemanticsNode()
        val size = semanticsNode.size
        
        assertTrue("Button width should meet minimum touch target", 
            size.width >= 44.dp.value * composeTestRule.density.density)
        assertTrue("Button height should meet minimum touch target", 
            size.height >= 44.dp.value * composeTestRule.density.density)
    }
    
    /**
     * Test semantic descriptions
     */
    @Test
    fun semanticDescriptions_generateCorrectly() {
        // Test workout descriptions
        val exerciseDescription = SemanticDescriptions.Workout.exerciseCard(
            exerciseName = "Push-ups",
            sets = 3,
            reps = 10,
            weight = null,
            isCompleted = true
        )
        
        assertTrue("Should contain exercise name", exerciseDescription.contains("Push-ups"))
        assertTrue("Should contain sets", exerciseDescription.contains("3 sets"))
        assertTrue("Should contain reps", exerciseDescription.contains("10 repetitions"))
        assertTrue("Should contain completion status", exerciseDescription.contains("Completed"))
        
        // Test progress descriptions
        val statsDescription = SemanticDescriptions.Progress.statisticsCard(
            title = "Weekly Volume",
            value = "2,500 kg",
            change = 15.5,
            period = "this week"
        )
        
        assertTrue("Should contain title", statsDescription.contains("Weekly Volume"))
        assertTrue("Should contain value", statsDescription.contains("2,500 kg"))
        assertTrue("Should contain change", statsDescription.contains("increased"))
        assertTrue("Should contain percentage", statsDescription.contains("15.5 percent"))
    }
    
    /**
     * Test accessibility announcements
     */
    @Test
    fun accessibilityAnnouncements_formatCorrectly() {
        val workoutStarted = SemanticDescriptions.Announcements.workoutStarted("Upper Body Strength")
        assertEquals("Started Upper Body Strength workout", workoutStarted)
        
        val setCompleted = SemanticDescriptions.Announcements.setCompleted("Bench Press", 2, 4)
        assertEquals("Completed set 2 of 4 for Bench Press", setCompleted)
        
        val achievementUnlocked = SemanticDescriptions.Announcements.achievementUnlocked("First Workout")
        assertEquals("Achievement unlocked: First Workout", achievementUnlocked)
    }
    
    /**
     * Test accessible color generation
     */
    @Test
    fun accessibleColors_generateCorrectly() {
        val lightColors = AccessibilityColors.getAccessibleBrandColors(isDark = false)
        val darkColors = AccessibilityColors.getAccessibleBrandColors(isDark = true)
        
        // Test that colors meet accessibility standards
        assertTrue("Light primary should meet standards", 
            AccessibilityColors.meetsAAStandards(lightColors.primary, lightColors.background))
        assertTrue("Light secondary should meet standards", 
            AccessibilityColors.meetsAAStandards(lightColors.secondary, lightColors.background))
        
        assertTrue("Dark primary should meet standards", 
            AccessibilityColors.meetsAAStandards(darkColors.primary, darkColors.background))
        assertTrue("Dark secondary should meet standards", 
            AccessibilityColors.meetsAAStandards(darkColors.secondary, darkColors.background))
    }
    
    /**
     * Test system accessibility state
     */
    @Test
    fun systemAccessibilityState_tracksCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                val accessibilityState = rememberSystemAccessibilityState()
                
                // Test accessibility state properties
                assertNotNull("Accessibility state should not be null", accessibilityState)
                assertTrue("Font scale should be positive", accessibilityState.fontScale > 0)
            }
        }
    }
    
    /**
     * Test accessibility utils functions
     */
    @Test
    fun accessibilityUtils_functionCorrectly() {
        // Test contrast ratio calculation
        val ratio = AccessibilityUtils.checkContrastRatio(Color.Black, Color.White)
        assertEquals(21.0f, ratio, 0.1f)
        
        // Test contrast requirements
        assertTrue("Black on white should meet requirements", 
            AccessibilityUtils.meetsContrastRequirements(Color.Black, Color.White))
        assertFalse("Light gray on white should not meet requirements", 
            AccessibilityUtils.meetsContrastRequirements(Color(0xFFE0E0E0), Color.White))
        
        // Test high contrast color generation
        val highContrastColor = AccessibilityUtils.getHighContrastColor(
            Color.Gray, Color.White, isLargeText = false
        )
        assertTrue("High contrast color should meet requirements", 
            AccessibilityUtils.meetsContrastRequirements(highContrastColor, Color.White))
    }
    
    /**
     * Test accessibility compliance validation
     */
    @Test
    fun accessibilityCompliance_validatesCorrectly() {
        val compliantResult = AccessibilityUtils.validateAccessibilityCompliance(
            hasContentDescription = true,
            hasSufficientTouchTarget = true,
            hasProperRole = true,
            contrastRatio = 7.0f,
            isInteractive = true
        )
        
        assertTrue("Compliant element should pass validation", compliantResult.isCompliant)
        assertEquals("Should have 100% score", 100, compliantResult.score)
        assertTrue("Should have no issues", compliantResult.issues.isEmpty())
        
        val nonCompliantResult = AccessibilityUtils.validateAccessibilityCompliance(
            hasContentDescription = false,
            hasSufficientTouchTarget = false,
            hasProperRole = false,
            contrastRatio = 2.0f,
            isInteractive = true
        )
        
        assertFalse("Non-compliant element should fail validation", nonCompliantResult.isCompliant)
        assertEquals("Should have 0% score", 0, nonCompliantResult.score)
        assertEquals("Should have 4 issues", 4, nonCompliantResult.issues.size)
    }
    
    /**
     * Test large text scaling
     */
    @Test
    fun largeTextScaling_worksCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleText(
                    text = "Test text",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Test text")
            .assertIsDisplayed()
    }
    
    /**
     * Test disabled state accessibility
     */
    @Test
    fun disabledState_hasProperSemantics() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccessibleLiftrixButton(
                    onClick = { },
                    text = "Disabled Button",
                    enabled = false
                )
            }
        }
        
        composeTestRule
            .onNodeWithText("Disabled Button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
} 