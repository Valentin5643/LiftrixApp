package com.example.liftrix.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.liftrix.ui.common.AccessibilityUtils.ContrastRatios
import com.example.liftrix.ui.common.AccessibilityUtils.checkContrastRatio
import com.example.liftrix.ui.common.AccessibilityUtils.meetsContrastRequirements
import com.example.liftrix.ui.common.AccessibilityUtils.getHighContrastColor
import com.example.liftrix.ui.common.AccessibilityUtils.validateAccessibilityCompliance
import io.mockk.MockKAnnotations
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for AccessibilityUtils ensuring WCAG 2.1 AA compliance.
 * Tests contrast calculations, validation, and accessibility features.
 */
class AccessibilityUtilsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `contrast ratio calculation is accurate for known color pairs`() {
        // Black on white should be 21:1 (maximum contrast)
        val blackOnWhite = checkContrastRatio(Color.Black, Color.White)
        assertEquals(21.0f, blackOnWhite, 0.1f)

        // White on black should be the same
        val whiteOnBlack = checkContrastRatio(Color.White, Color.Black)
        assertEquals(21.0f, whiteOnBlack, 0.1f)

        // Same colors should be 1:1 (no contrast)
        val sameColor = checkContrastRatio(Color.Red, Color.Red)
        assertEquals(1.0f, sameColor, 0.1f)

        // Test known values
        val darkGrayOnLightGray = checkContrastRatio(Color(0xFF404040), Color(0xFFE0E0E0))
        assertTrue(darkGrayOnLightGray > 7.0f) // Should meet AAA standard
    }

    @Test
    fun `WCAG AA contrast requirements are properly validated`() {
        // Colors that meet AA standard for normal text (4.5:1)
        assertTrue(
            meetsContrastRequirements(
                foreground = Color.Black,
                background = Color.White,
                isLargeText = false
            )
        )

        // Colors that meet AA standard for large text (3:1) but not normal text
        val mediumGray = Color(0xFF757575)
        val lightGray = Color(0xFFF5F5F5)
        
        assertFalse(
            meetsContrastRequirements(
                foreground = mediumGray,
                background = lightGray,
                isLargeText = false
            )
        )
        
        assertTrue(
            meetsContrastRequirements(
                foreground = mediumGray,
                background = lightGray,
                isLargeText = true
            )
        )
    }

    @Test
    fun `high contrast color adjustment works correctly`() {
        // Colors that already meet requirements should remain unchanged
        val goodForeground = Color.Black
        val background = Color.White
        
        assertEquals(
            goodForeground,
            getHighContrastColor(goodForeground, background, false)
        )

        // Colors that don't meet requirements should be adjusted
        val poorForeground = Color(0xFF999999) // Light gray
        val darkBackground = Color(0xFF333333) // Dark gray
        
        val adjustedColor = getHighContrastColor(poorForeground, darkBackground, false)
        assertTrue(
            meetsContrastRequirements(adjustedColor, darkBackground, false)
        )
    }

    @Test
    fun `accessibility compliance validation detects all issues`() {
        // Test fully compliant element
        val compliantResult = validateAccessibilityCompliance(
            hasContentDescription = true,
            hasSufficientTouchTarget = true,
            hasProperRole = true,
            contrastRatio = 7.0f,
            isInteractive = true
        )
        
        assertTrue(compliantResult.isCompliant)
        assertEquals(100, compliantResult.score)
        assertTrue(compliantResult.issues.isEmpty())

        // Test element with multiple issues
        val nonCompliantResult = validateAccessibilityCompliance(
            hasContentDescription = false,
            hasSufficientTouchTarget = false,
            hasProperRole = false,
            contrastRatio = 2.0f,
            isInteractive = true
        )
        
        assertFalse(nonCompliantResult.isCompliant)
        assertEquals(0, nonCompliantResult.score)
        assertEquals(4, nonCompliantResult.issues.size)
        assertTrue(nonCompliantResult.issues.any { it.contains("content description") })
        assertTrue(nonCompliantResult.issues.any { it.contains("touch target") })
        assertTrue(nonCompliantResult.issues.any { it.contains("semantic role") })
        assertTrue(nonCompliantResult.issues.any { it.contains("contrast ratio") })
    }

    @Test
    fun `non-interactive elements only check contrast ratio`() {
        val nonInteractiveResult = validateAccessibilityCompliance(
            hasContentDescription = false,
            hasSufficientTouchTarget = false,
            hasProperRole = false,
            contrastRatio = 5.0f,
            isInteractive = false
        )
        
        assertTrue(nonInteractiveResult.isCompliant)
        assertEquals(100, nonInteractiveResult.score)
        assertTrue(nonInteractiveResult.issues.isEmpty())

        val nonInteractiveWithPoorContrast = validateAccessibilityCompliance(
            hasContentDescription = false,
            hasSufficientTouchTarget = false,
            hasProperRole = false,
            contrastRatio = 2.0f,
            isInteractive = false
        )
        
        assertFalse(nonInteractiveWithPoorContrast.isCompliant)
        assertEquals(0, nonInteractiveWithPoorContrast.score)
        assertEquals(1, nonInteractiveWithPoorContrast.issues.size)
    }

    @Test
    fun `partial compliance is scored correctly`() {
        // Test 50% compliance (2 out of 4 requirements met)
        val partialResult = validateAccessibilityCompliance(
            hasContentDescription = true,
            hasSufficientTouchTarget = false,
            hasProperRole = true,
            contrastRatio = 2.0f,
            isInteractive = true
        )
        
        assertFalse(partialResult.isCompliant)
        assertEquals(50, partialResult.score)
        assertEquals(2, partialResult.issues.size)
    }

    @Test
    fun `contrast ratios meet WCAG standards`() {
        // Test that our defined contrast ratios match WCAG standards
        assertEquals(4.5f, ContrastRatios.NORMAL_TEXT_AA)
        assertEquals(3.0f, ContrastRatios.LARGE_TEXT_AA)
        assertEquals(3.0f, ContrastRatios.NON_TEXT_AA)
        assertEquals(7.0f, ContrastRatios.ENHANCED_AAA)
    }

    @Test
    fun `minimum touch target size is WCAG compliant`() {
        // WCAG requires minimum 44dp touch targets
        assertEquals(44.0f, AccessibilityUtils.MinimumTouchTargetSize.value)
    }

    @Test
    fun `color luminance calculations are accurate`() {
        // Test known luminance values
        val whiteLuminance = Color.White.luminance()
        val blackLuminance = Color.Black.luminance()
        
        assertTrue(whiteLuminance > blackLuminance)
        
        // White should have high luminance, black should have low
        assertTrue(whiteLuminance > 0.9f)
        assertTrue(blackLuminance < 0.1f)
    }

    @Test
    fun `accessibility state detection works correctly`() {
        composeTestRule.setContent {
            val accessibilityState = rememberAccessibilityState()
            
            // These values depend on the test environment but should be deterministic
            assertTrue(accessibilityState.fontScale > 0)
            assertFalse(accessibilityState.fontScale < 0)
        }
    }

    @Test
    fun `edge cases in contrast calculation are handled`() {
        // Test with transparent colors
        val transparentColor = Color.Black.copy(alpha = 0f)
        val opaqueColor = Color.White
        
        // Should handle transparent colors gracefully
        val contrastWithTransparent = checkContrastRatio(transparentColor, opaqueColor)
        assertTrue(contrastWithTransparent > 0)

        // Test with very similar colors
        val color1 = Color(0xFF808080)
        val color2 = Color(0xFF808081) // Almost identical
        
        val minimalContrast = checkContrastRatio(color1, color2)
        assertTrue(minimalContrast >= 1.0f) // Should be at least 1:1
        assertTrue(minimalContrast < 1.1f) // Should be very close to 1:1
    }

    @Test
    fun `brand colors meet accessibility standards`() {
        // Test that our brand colors have sufficient contrast
        // These should be updated when brand colors are defined
        
        val primaryColor = Color(0xFF20C9B7) // Liftrix Teal
        val onPrimaryColor = Color.White
        val backgroundColor = Color.White
        val onBackgroundColor = Color.Black
        
        // Primary color should have sufficient contrast with its "on" color
        assertTrue(
            meetsContrastRequirements(onPrimaryColor, primaryColor, false),
            "Primary color contrast insufficient"
        )
        
        // Background colors should have sufficient contrast
        assertTrue(
            meetsContrastRequirements(onBackgroundColor, backgroundColor, false),
            "Background color contrast insufficient"
        )
    }
} 