package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.max
import kotlin.math.min

/**
 * Test suite for Liftrix color system accessibility and contrast compliance
 * Ensures all brand colors meet WCAG 2.1 AA standards (4.5:1 contrast ratio minimum)
 */
class ColorTest {

    /**
     * Calculate contrast ratio between two colors
     * Formula: (L1 + 0.05) / (L2 + 0.05) where L1 is lighter and L2 is darker
     */
    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val luminance1 = foreground.luminance() + 0.05
        val luminance2 = background.luminance() + 0.05
        return max(luminance1, luminance2) / min(luminance1, luminance2)
    }

    /**
     * Assert that contrast ratio meets WCAG AA standards (4.5:1 minimum)
     */
    private fun assertContrastCompliance(
        foreground: Color,
        background: Color,
        minimumRatio: Double = 4.5,
        description: String
    ) {
        val ratio = calculateContrastRatio(foreground, background)
        assertTrue(
            "$description should have contrast ratio >= $minimumRatio, but was ${"%.2f".format(ratio)}",
            ratio >= minimumRatio
        )
    }

    @Test
    fun testBrandColorContrastRatios() {
        // Test primary brand colors against white and black backgrounds
        assertContrastCompliance(
            LiftrixColors.Primary,
            Color.White,
            description = "Primary teal (#20C9B7) on white background"
        )

        assertContrastCompliance(
            Color.White,
            LiftrixColors.Primary,
            description = "White text on primary teal background"
        )

        assertContrastCompliance(
            LiftrixColors.Secondary,
            Color.White,
            description = "Secondary indigo (#2A3B7D) on white background"
        )

        assertContrastCompliance(
            Color.White,
            LiftrixColors.Secondary,
            description = "White text on secondary indigo background"
        )

        assertContrastCompliance(
            LiftrixColors.Accent,
            Color.White,
            description = "Accent coral (#FF6B6B) on white background"
        )
    }

    @Test
    fun testLightThemeContrastCompliance() {
        val lightScheme = getLightColorScheme()

        // Test primary color combinations
        assertContrastCompliance(
            lightScheme.onPrimary,
            lightScheme.primary,
            description = "Light theme: onPrimary text on primary background"
        )

        assertContrastCompliance(
            lightScheme.onPrimaryContainer,
            lightScheme.primaryContainer,
            description = "Light theme: onPrimaryContainer text on primaryContainer background"
        )

        // Test secondary color combinations
        assertContrastCompliance(
            lightScheme.onSecondary,
            lightScheme.secondary,
            description = "Light theme: onSecondary text on secondary background"
        )

        assertContrastCompliance(
            lightScheme.onSecondaryContainer,
            lightScheme.secondaryContainer,
            description = "Light theme: onSecondaryContainer text on secondaryContainer background"
        )

        // Test surface and background combinations
        assertContrastCompliance(
            lightScheme.onBackground,
            lightScheme.background,
            description = "Light theme: onBackground text on background"
        )

        assertContrastCompliance(
            lightScheme.onSurface,
            lightScheme.surface,
            description = "Light theme: onSurface text on surface"
        )

        assertContrastCompliance(
            lightScheme.onSurfaceVariant,
            lightScheme.surfaceVariant,
            description = "Light theme: onSurfaceVariant text on surfaceVariant"
        )
    }

    @Test
    fun testDarkThemeContrastCompliance() {
        val darkScheme = getDarkColorScheme()

        // Test primary color combinations
        assertContrastCompliance(
            darkScheme.onPrimary,
            darkScheme.primary,
            description = "Dark theme: onPrimary text on primary background"
        )

        assertContrastCompliance(
            darkScheme.onPrimaryContainer,
            darkScheme.primaryContainer,
            description = "Dark theme: onPrimaryContainer text on primaryContainer background"
        )

        // Test secondary color combinations
        assertContrastCompliance(
            darkScheme.onSecondary,
            darkScheme.secondary,
            description = "Dark theme: onSecondary text on secondary background"
        )

        assertContrastCompliance(
            darkScheme.onSecondaryContainer,
            darkScheme.secondaryContainer,
            description = "Dark theme: onSecondaryContainer text on secondaryContainer background"
        )

        // Test surface and background combinations
        assertContrastCompliance(
            darkScheme.onBackground,
            darkScheme.background,
            description = "Dark theme: onBackground text on background"
        )

        assertContrastCompliance(
            darkScheme.onSurface,
            darkScheme.surface,
            description = "Dark theme: onSurface text on surface"
        )

        assertContrastCompliance(
            darkScheme.onSurfaceVariant,
            darkScheme.surfaceVariant,
            description = "Dark theme: onSurfaceVariant text on surfaceVariant"
        )
    }

    @Test
    fun testTimeBasedColorSchemes() {
        // Test morning colors (6-11 AM)
        val morningColors = getTimeBasedColorScheme(9)
        assertContrastCompliance(
            Color.White,
            morningColors.primary,
            description = "Morning primary color contrast with white"
        )

        // Test afternoon colors (12-5 PM)
        val afternoonColors = getTimeBasedColorScheme(14)
        assertContrastCompliance(
            Color.White,
            afternoonColors.primary,
            description = "Afternoon primary color contrast with white"
        )

        // Test evening colors (6-11 PM)
        val eveningColors = getTimeBasedColorScheme(20)
        assertContrastCompliance(
            Color.White,
            eveningColors.primary,
            description = "Evening primary color contrast with white"
        )

        // Test night colors (12-5 AM)
        val nightColors = getTimeBasedColorScheme(2)
        assertContrastCompliance(
            Color.White,
            nightColors.primary,
            description = "Night primary color contrast with white"
        )
    }

    @Test
    fun testBrandColorPalette() {
        val brandColors = brandColorPalette()

        // Test that brand colors are correctly defined
        assertEquals(
            "Brand primary should match LiftrixColors.Primary",
            LiftrixColors.Primary,
            brandColors.primary
        )

        assertEquals(
            "Brand secondary should match LiftrixColors.Secondary",
            LiftrixColors.Secondary,
            brandColors.secondary
        )

        assertEquals(
            "Brand accent should match LiftrixColors.Accent",
            LiftrixColors.Accent,
            brandColors.accent
        )

        assertEquals(
            "Brand background should match LiftrixColors.BackgroundLight",
            LiftrixColors.BackgroundLight,
            brandColors.background
        )
    }

    @Test
    fun testColorSchemeAccessibility() {
        // Test that all required color scheme functions exist and return valid values
        val lightScheme = getLightColorScheme()
        val darkScheme = getDarkColorScheme()

        // Ensure schemes are properly initialized
        assertNotNull("Light color scheme should not be null", lightScheme)
        assertNotNull("Dark color scheme should not be null", darkScheme)

        // Test that primary colors are different between light and dark themes
        assertNotEquals(
            "Light and dark themes should have different surface colors",
            lightScheme.surface,
            darkScheme.surface
        )

        assertNotEquals(
            "Light and dark themes should have different background colors",
            lightScheme.background,
            darkScheme.background
        )
    }

    @Test
    fun testExtendedColorPalette() {
        // Test that extended color variations maintain proper relationships
        val tealLightLuminance = LiftrixColors.TealLight.luminance()
        val tealDarkLuminance = LiftrixColors.TealDark.luminance()
        val tealBaseLuminance = LiftrixColors.Primary.luminance()

        assertTrue(
            "Teal light should be lighter than base teal",
            tealLightLuminance > tealBaseLuminance
        )

        assertTrue(
            "Teal dark should be darker than base teal",
            tealDarkLuminance < tealBaseLuminance
        )

        // Test similar relationships for other color families
        val indigoLightLuminance = LiftrixColors.IndigoLight.luminance()
        val indigoDarkLuminance = LiftrixColors.IndigoDark.luminance()
        val indigoBaseLuminance = LiftrixColors.Secondary.luminance()

        assertTrue(
            "Indigo light should be lighter than base indigo",
            indigoLightLuminance > indigoBaseLuminance
        )

        assertTrue(
            "Indigo dark should be darker than base indigo",
            indigoDarkLuminance < indigoBaseLuminance
        )
    }

    @Test
    fun testColorConsistency() {
        // Test that color definitions are consistent across the system
        val brandPalette = brandColorPalette()

        // Verify brand colors match the exact hex values specified in requirements
        assertEquals(
            "Primary teal should be #20C9B7",
            Color(0xFF20C9B7),
            LiftrixColors.Primary
        )

        assertEquals(
            "Secondary indigo should be #2A3B7D",
            Color(0xFF2A3B7D),
            LiftrixColors.Secondary
        )

        assertEquals(
            "Accent coral should be #FF6B6B",
            Color(0xFFFF6B6B),
            LiftrixColors.Accent
        )
    }

    @Test
    fun testErrorColorContrast() {
        // Test error color accessibility
        assertContrastCompliance(
            LiftrixColors.OnError,
            LiftrixColors.Error,
            description = "Error text on error background"
        )

        assertContrastCompliance(
            LiftrixColors.OnErrorContainer,
            LiftrixColors.ErrorContainer,
            description = "Error container text on error container background"
        )

        assertContrastCompliance(
            LiftrixColors.OnErrorContainerDark,
            LiftrixColors.ErrorContainerDark,
            description = "Dark theme error container text on error container background"
        )
    }
} 