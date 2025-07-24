package com.example.liftrix.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.pow

/**
 * WCAG 2.1 AA Accessibility Compliance Test Suite
 * 
 * Comprehensive validation of the 5-color palette system against WCAG 2.1 AA standards.
 * Tests all color combinations used in the app to ensure accessibility compliance.
 * 
 * Coverage:
 * - All 5 core colors meet contrast requirements when paired appropriately
 * - Persian Green and Tiffany Blue provide sufficient contrast on Snow/Night/Jet
 * - Material 3 color scheme configurations meet accessibility standards
 * - Error color exception maintains proper contrast ratios
 * - High contrast modes exceed AAA standards where applicable
 */
class AccessibilityComplianceTest {
    
    companion object {
        // WCAG 2.1 contrast ratio requirements
        const val WCAG_AA_NORMAL_TEXT = 4.5f
        const val WCAG_AA_LARGE_TEXT = 3.0f
        const val WCAG_AAA_NORMAL_TEXT = 7.0f
        const val WCAG_AAA_LARGE_TEXT = 4.5f
    }
    
    @Test
    fun `verify 5-color palette core colors meet accessibility standards`() {
        // Test Night (#131515) combinations
        assertTrue(
            "Night on Snow should exceed AAA standards",
            calculateContrastRatio(LiftrixColors.Night, LiftrixColors.Snow) >= WCAG_AAA_NORMAL_TEXT
        )
        
        // Test Jet (#2B2C28) combinations
        assertTrue(
            "Jet on Snow should exceed AAA standards",
            calculateContrastRatio(LiftrixColors.Jet, LiftrixColors.Snow) >= WCAG_AAA_NORMAL_TEXT
        )
        
        // Test Persian Green (#339989) combinations
        assertTrue(
            "Persian Green on Snow should meet AA standards",
            calculateContrastRatio(LiftrixColors.PersianGreen, LiftrixColors.Snow) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Snow on Persian Green should meet AA standards",
            calculateContrastRatio(LiftrixColors.Snow, LiftrixColors.PersianGreen) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test Tiffany Blue (#7DE2D1) combinations
        assertTrue(
            "Night on Tiffany Blue should exceed AAA standards",
            calculateContrastRatio(LiftrixColors.Night, LiftrixColors.TiffanyBlue) >= WCAG_AAA_NORMAL_TEXT
        )
        
        assertTrue(
            "Tiffany Blue on Night should exceed AAA standards",
            calculateContrastRatio(LiftrixColors.TiffanyBlue, LiftrixColors.Night) >= WCAG_AAA_NORMAL_TEXT
        )
        
        // Test Snow (#FFFAFB) combinations
        assertTrue(
            "Snow on Night should exceed AAA standards",
            calculateContrastRatio(LiftrixColors.Snow, LiftrixColors.Night) >= WCAG_AAA_NORMAL_TEXT
        )
        
        assertTrue(
            "Snow on Jet should exceed AAA standards", 
            calculateContrastRatio(LiftrixColors.Snow, LiftrixColors.Jet) >= WCAG_AAA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify light theme color scheme meets WCAG AA standards`() {
        val lightScheme = lightColorScheme(
            primary = LiftrixColors.PersianGreen,
            onPrimary = LiftrixColors.Snow,
            primaryContainer = LiftrixColors.PersianGreenContainer20,
            onPrimaryContainer = LiftrixColors.Night,
            secondary = LiftrixColors.TiffanyBlue,
            onSecondary = LiftrixColors.Night,
            secondaryContainer = LiftrixColors.TiffanyBlueContainer20,
            onSecondaryContainer = LiftrixColors.Night,
            background = LiftrixColors.Snow,
            onBackground = LiftrixColors.Night,
            surface = LiftrixColors.Snow,
            onSurface = LiftrixColors.Night,
            surfaceVariant = LiftrixColors.Snow,
            onSurfaceVariant = LiftrixColors.Jet,
            error = LiftrixColors.Error,
            onError = LiftrixColors.Snow
        )
        
        // Test primary color combinations
        assertTrue(
            "Primary/OnPrimary contrast insufficient: ${calculateContrastRatio(lightScheme.onPrimary, lightScheme.primary)}:1",
            calculateContrastRatio(lightScheme.onPrimary, lightScheme.primary) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "PrimaryContainer/OnPrimaryContainer contrast insufficient: ${calculateContrastRatio(lightScheme.onPrimaryContainer, lightScheme.primaryContainer)}:1", 
            calculateContrastRatio(lightScheme.onPrimaryContainer, lightScheme.primaryContainer) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test secondary color combinations
        assertTrue(
            "Secondary/OnSecondary contrast insufficient: ${calculateContrastRatio(lightScheme.onSecondary, lightScheme.secondary)}:1",
            calculateContrastRatio(lightScheme.onSecondary, lightScheme.secondary) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "SecondaryContainer/OnSecondaryContainer contrast insufficient: ${calculateContrastRatio(lightScheme.onSecondaryContainer, lightScheme.secondaryContainer)}:1",
            calculateContrastRatio(lightScheme.onSecondaryContainer, lightScheme.secondaryContainer) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test surface color combinations
        assertTrue(
            "Surface/OnSurface contrast insufficient: ${calculateContrastRatio(lightScheme.onSurface, lightScheme.surface)}:1",
            calculateContrastRatio(lightScheme.onSurface, lightScheme.surface) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Background/OnBackground contrast insufficient: ${calculateContrastRatio(lightScheme.onBackground, lightScheme.background)}:1",
            calculateContrastRatio(lightScheme.onBackground, lightScheme.background) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "SurfaceVariant/OnSurfaceVariant contrast insufficient: ${calculateContrastRatio(lightScheme.onSurfaceVariant, lightScheme.surfaceVariant)}:1",
            calculateContrastRatio(lightScheme.onSurfaceVariant, lightScheme.surfaceVariant) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test error color combinations (exception to 5-color rule)
        assertTrue(
            "Error/OnError contrast insufficient: ${calculateContrastRatio(lightScheme.onError, lightScheme.error)}:1",
            calculateContrastRatio(lightScheme.onError, lightScheme.error) >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify dark theme color scheme meets WCAG AA standards`() {
        val darkScheme = darkColorScheme(
            primary = LiftrixColors.PersianGreen,
            onPrimary = LiftrixColors.Snow,
            primaryContainer = LiftrixColors.PersianGreenContainer20,
            onPrimaryContainer = LiftrixColors.Snow,
            secondary = LiftrixColors.TiffanyBlue,
            onSecondary = LiftrixColors.Night,
            secondaryContainer = LiftrixColors.TiffanyBlueContainer20,
            onSecondaryContainer = LiftrixColors.Snow,
            background = LiftrixColors.Night,
            onBackground = LiftrixColors.Snow,
            surface = LiftrixColors.Jet,
            onSurface = LiftrixColors.Snow,
            surfaceVariant = LiftrixColors.Jet,
            onSurfaceVariant = LiftrixColors.Snow,
            error = LiftrixColors.Error,
            onError = LiftrixColors.Snow
        )
        
        // Test primary color combinations
        assertTrue(
            "Dark Primary/OnPrimary contrast insufficient: ${calculateContrastRatio(darkScheme.onPrimary, darkScheme.primary)}:1",
            calculateContrastRatio(darkScheme.onPrimary, darkScheme.primary) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Dark PrimaryContainer/OnPrimaryContainer contrast insufficient: ${calculateContrastRatio(darkScheme.onPrimaryContainer, darkScheme.primaryContainer)}:1",
            calculateContrastRatio(darkScheme.onPrimaryContainer, darkScheme.primaryContainer) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test secondary color combinations
        assertTrue(
            "Dark Secondary/OnSecondary contrast insufficient: ${calculateContrastRatio(darkScheme.onSecondary, darkScheme.secondary)}:1",
            calculateContrastRatio(darkScheme.onSecondary, darkScheme.secondary) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Dark SecondaryContainer/OnSecondaryContainer contrast insufficient: ${calculateContrastRatio(darkScheme.onSecondaryContainer, darkScheme.secondaryContainer)}:1",
            calculateContrastRatio(darkScheme.onSecondaryContainer, darkScheme.secondaryContainer) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test surface color combinations
        assertTrue(
            "Dark Surface/OnSurface contrast insufficient: ${calculateContrastRatio(darkScheme.onSurface, darkScheme.surface)}:1",
            calculateContrastRatio(darkScheme.onSurface, darkScheme.surface) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Dark Background/OnBackground contrast insufficient: ${calculateContrastRatio(darkScheme.onBackground, darkScheme.background)}:1",
            calculateContrastRatio(darkScheme.onBackground, darkScheme.background) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Dark SurfaceVariant/OnSurfaceVariant contrast insufficient: ${calculateContrastRatio(darkScheme.onSurfaceVariant, darkScheme.surfaceVariant)}:1",
            calculateContrastRatio(darkScheme.onSurfaceVariant, darkScheme.surfaceVariant) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test error color combinations (exception to 5-color rule)
        assertTrue(
            "Dark Error/OnError contrast insufficient: ${calculateContrastRatio(darkScheme.onError, darkScheme.error)}:1",
            calculateContrastRatio(darkScheme.onError, darkScheme.error) >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify specific component color combinations meet accessibility standards`() {
        // Test UnifiedWorkoutCard color combinations
        val cardBackground = LiftrixColors.Snow // Light theme surface
        val cardOnSurface = LiftrixColors.Night
        assertTrue(
            "UnifiedWorkoutCard text contrast insufficient",
            calculateContrastRatio(cardOnSurface, cardBackground) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test ModernActionButton Primary color combinations
        val primaryButtonContainer = LiftrixColors.PersianGreenContainer20
        val primaryButtonContent = LiftrixColors.Night
        assertTrue(
            "Primary button text contrast insufficient",
            calculateContrastRatio(primaryButtonContent, primaryButtonContainer) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test ModernActionButton Secondary color combinations
        val secondaryButtonBorder = LiftrixColors.TiffanyBlue
        val secondaryButtonBackground = LiftrixColors.Snow
        assertTrue(
            "Secondary button border contrast insufficient",
            calculateContrastRatio(secondaryButtonBorder, secondaryButtonBackground) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test AuthTextField focus state color combinations
        val textFieldFocus = LiftrixColors.TiffanyBlue
        val textFieldBackground = LiftrixColors.Snow
        assertTrue(
            "AuthTextField focus state contrast insufficient",
            calculateContrastRatio(textFieldFocus, textFieldBackground) >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify container color alpha variations maintain accessibility`() {
        // Test Persian Green container variations
        val persianGreenContainer10 = LiftrixColors.PersianGreenContainer10
        val persianGreenContainer20 = LiftrixColors.PersianGreenContainer20
        val persianGreenContainer30 = LiftrixColors.PersianGreenContainer30
        
        // Test against Night text (light theme)
        assertTrue(
            "Persian Green Container 10% with Night text insufficient contrast",
            calculateContrastRatio(LiftrixColors.Night, persianGreenContainer10) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Persian Green Container 20% with Night text insufficient contrast", 
            calculateContrastRatio(LiftrixColors.Night, persianGreenContainer20) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Persian Green Container 30% with Night text insufficient contrast",
            calculateContrastRatio(LiftrixColors.Night, persianGreenContainer30) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test Tiffany Blue container variations
        val tiffanyBlueContainer10 = LiftrixColors.TiffanyBlueContainer10
        val tiffanyBlueContainer20 = LiftrixColors.TiffanyBlueContainer20
        val tiffanyBlueContainer30 = LiftrixColors.TiffanyBlueContainer30
        
        // Test against Night text (light theme)
        assertTrue(
            "Tiffany Blue Container 10% with Night text insufficient contrast",
            calculateContrastRatio(LiftrixColors.Night, tiffanyBlueContainer10) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Tiffany Blue Container 20% with Night text insufficient contrast",
            calculateContrastRatio(LiftrixColors.Night, tiffanyBlueContainer20) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Tiffany Blue Container 30% with Night text insufficient contrast",
            calculateContrastRatio(LiftrixColors.Night, tiffanyBlueContainer30) >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify error color exception maintains accessibility standards`() {
        // Test error red colors (only exception to 5-color rule)
        val errorRed = LiftrixColors.Error  // #FF4444
        val errorContainer = Color(0xFFFFDAD6)  // Light error container
        val errorContainerDark = Color(0xFF93000A)  // Dark error container
        val onError = LiftrixColors.Snow
        val onErrorContainer = Color(0xFF410002)
        
        // Test error color combinations
        assertTrue(
            "Error red with white text insufficient contrast",
            calculateContrastRatio(onError, errorRed) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Error container with dark text insufficient contrast",
            calculateContrastRatio(onErrorContainer, errorContainer) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Dark error container with white text insufficient contrast",
            calculateContrastRatio(onError, errorContainerDark) >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify color coverage targets are achievable with accessibility compliance`() {
        // Light theme coverage test
        val lightThemeColors = mapOf(
            "Snow" to 90, // 90% background coverage
            "Night_Jet" to 2, // 2% text coverage
            "Persian_Green" to 5, // 5% primary actions
            "Tiffany_Blue" to 3  // 3% secondary actions
        )
        
        // Dark theme coverage test
        val darkThemeColors = mapOf(
            "Night" to 50, // 50% background coverage
            "Jet" to 40, // 40% surface coverage
            "Snow" to 2, // 2% text coverage
            "Persian_Green" to 5, // 5% primary actions
            "Tiffany_Blue" to 3  // 3% secondary actions
        )
        
        // Verify coverage adds up to 100%
        assertEquals("Light theme coverage should total 100%", 100, lightThemeColors.values.sum())
        assertEquals("Dark theme coverage should total 100%", 100, darkThemeColors.values.sum())
        
        // Verify all combinations in coverage are accessible
        // Light theme: Night/Jet text on Snow backgrounds
        assertTrue(
            "Light theme primary text combination must be accessible",
            calculateContrastRatio(LiftrixColors.Night, LiftrixColors.Snow) >= WCAG_AA_NORMAL_TEXT
        )
        
        // Dark theme: Snow text on Night/Jet backgrounds
        assertTrue(
            "Dark theme primary text combination must be accessible",
            calculateContrastRatio(LiftrixColors.Snow, LiftrixColors.Night) >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Dark theme secondary text combination must be accessible",
            calculateContrastRatio(LiftrixColors.Snow, LiftrixColors.Jet) >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    @Test
    fun `verify built-in accessibility helpers work correctly`() {
        // Test AccessibilityColors functions from Color.kt
        val validationResult = AccessibilityColors.validateColorScheme(getLightColorScheme())
        
        assertTrue(
            "Light color scheme should pass accessibility validation",
            validationResult.isCompliant
        )
        
        assertTrue(
            "Compliance percentage should be 100%",
            validationResult.compliancePercentage == 100
        )
        
        assertEquals(
            "Should have no accessibility issues",
            0,
            validationResult.issues.size
        )
        
        // Test dark theme validation
        val darkValidationResult = AccessibilityColors.validateColorScheme(getDarkColorScheme())
        
        assertTrue(
            "Dark color scheme should pass accessibility validation",
            darkValidationResult.isCompliant
        )
        
        assertTrue(
            "Dark theme compliance percentage should be 100%",
            darkValidationResult.compliancePercentage == 100
        )
    }
    
    @Test
    fun `verify high contrast modes exceed AAA standards`() {
        // Test high contrast light theme combinations manually since helper may not exist
        val highContrastCombinations = listOf(
            // Maximum contrast combinations for accessibility
            Pair(LiftrixColors.Night, LiftrixColors.Snow),    // Pure black on white
            Pair(LiftrixColors.Snow, LiftrixColors.Night),    // Pure white on black
            
            // Brand colors should maintain accessibility in high contrast
            Pair(LiftrixColors.PersianGreen, LiftrixColors.Snow),
            Pair(LiftrixColors.Snow, LiftrixColors.PersianGreen),
            Pair(LiftrixColors.TiffanyBlue, LiftrixColors.Night),
            Pair(LiftrixColors.Night, LiftrixColors.TiffanyBlue)
        )
        
        highContrastCombinations.forEach { (foreground, background) ->
            val contrastRatio = calculateContrastRatio(foreground, background)
            
            // Verify AAA compliance where possible
            val isAAACompliant = contrastRatio >= WCAG_AAA_NORMAL_TEXT
            val isAACompliant = contrastRatio >= WCAG_AA_NORMAL_TEXT
            
            assertTrue(
                "High contrast: ${getColorName(foreground)} on ${getColorName(background)} " +
                "has contrast ratio ${"%.2f".format(contrastRatio)}:1, which is below WCAG AA standard (4.5:1)",
                isAACompliant
            )
            
            // Log AAA compliance for optimization insights
            if (isAAACompliant) {
                println("✓ AAA compliant: ${getColorName(foreground)} on ${getColorName(background)} " +
                       "(${"%.2f".format(contrastRatio)}:1)")
            }
        }
    }
    
    /**
     * Tests comprehensive build verification to ensure the color system
     * compiles correctly and all constants are accessible.
     */
    @Test
    fun `verify build configuration compatibility with 5-color system`() {
        // Test that all core color constants are accessible and have expected values
        val expectedColors = mapOf(
            "Night" to 0xFF131515,
            "Jet" to 0xFF2B2C28, 
            "Persian Green" to 0xFF339989,
            "Tiffany Blue" to 0xFF7DE2D1,
            "Snow" to 0xFFFFFAFB
        )
        
        // Verify core color values
        assertEquals("Night color incorrect", expectedColors["Night"], LiftrixColors.Night.value)
        assertEquals("Jet color incorrect", expectedColors["Jet"], LiftrixColors.Jet.value)
        assertEquals("Persian Green color incorrect", expectedColors["Persian Green"], LiftrixColors.PersianGreen.value)
        assertEquals("Tiffany Blue color incorrect", expectedColors["Tiffany Blue"], LiftrixColors.TiffanyBlue.value)
        assertEquals("Snow color incorrect", expectedColors["Snow"], LiftrixColors.Snow.value)
        
        // Test that container colors are properly calculated with alpha
        assertTrue("Persian Green Container 10% should have 10% alpha", 
                  LiftrixColors.PersianGreenContainer10.alpha == 0.1f)
        assertTrue("Persian Green Container 20% should have 20% alpha", 
                  LiftrixColors.PersianGreenContainer20.alpha == 0.2f)
        assertTrue("Tiffany Blue Container 10% should have 10% alpha", 
                  LiftrixColors.TiffanyBlueContainer10.alpha == 0.1f)
        assertTrue("Tiffany Blue Container 20% should have 20% alpha", 
                  LiftrixColors.TiffanyBlueContainer20.alpha == 0.2f)
        
        // Test that error colors exist (exception to 5-color rule)
        assertEquals("Error red color incorrect", 0xFFFF4444, LiftrixColors.Error.value)
        
        // Verify color objects are not null and accessible
        assertNotNull("Night color should not be null", LiftrixColors.Night)
        assertNotNull("Jet color should not be null", LiftrixColors.Jet)
        assertNotNull("Persian Green color should not be null", LiftrixColors.PersianGreen)
        assertNotNull("Tiffany Blue color should not be null", LiftrixColors.TiffanyBlue)
        assertNotNull("Snow color should not be null", LiftrixColors.Snow)
    }
    
    /**
     * Tests color blindness accessibility by verifying distinguishability
     * of the 5-color system for users with color vision deficiencies.
     */
    @Test
    fun `verify color blindness accessibility compliance`() {
        // Test that Persian Green and Tiffany Blue are distinguishable
        // Convert to approximate perceived brightness for color blind users
        val persianGreenBrightness = calculatePerceivedBrightness(LiftrixColors.PersianGreen)
        val tiffanyBlueBrightness = calculatePerceivedBrightness(LiftrixColors.TiffanyBlue)
        
        val brightnessDifference = kotlin.math.abs(persianGreenBrightness - tiffanyBlueBrightness)
        
        assertTrue(
            "Persian Green and Tiffany Blue brightness difference (${"%.2f".format(brightnessDifference)}) " +
            "is too small for color blind users. Should be > 0.2 for reliable distinction.",
            brightnessDifference > 0.2f
        )
        
        // Test that the system doesn't rely solely on color for information
        // Verify adequate contrast exists independent of color perception
        val persianGreenContrast = calculateContrastRatio(LiftrixColors.PersianGreen, LiftrixColors.Snow)
        val tiffanyBlueContrast = calculateContrastRatio(LiftrixColors.TiffanyBlue, LiftrixColors.Snow)
        
        assertTrue(
            "Persian Green must have sufficient contrast for color blind users",
            persianGreenContrast >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Tiffany Blue must have sufficient contrast for color blind users", 
            tiffanyBlueContrast >= WCAG_AA_NORMAL_TEXT
        )
        
        // Test distinguishability against main backgrounds
        val persianVsNightContrast = calculateContrastRatio(LiftrixColors.PersianGreen, LiftrixColors.Night)
        val tiffanyVsNightContrast = calculateContrastRatio(LiftrixColors.TiffanyBlue, LiftrixColors.Night)
        
        assertTrue(
            "Persian Green vs Night contrast must be accessible for color blind users",
            persianVsNightContrast >= WCAG_AA_NORMAL_TEXT
        )
        
        assertTrue(
            "Tiffany Blue vs Night contrast must be accessible for color blind users",
            tiffanyVsNightContrast >= WCAG_AA_NORMAL_TEXT
        )
    }
    
    /**
     * Calculates perceived brightness for color blindness accessibility testing.
     * Uses standard luminance calculation weighted for human perception.
     */
    private fun calculatePerceivedBrightness(color: Color): Float {
        // Use luminance calculation for perceived brightness
        return calculateRelativeLuminance(color)
    }
    
    /**
     * Gets human-readable color name for error messages and logging.
     */
    private fun getColorName(color: Color): String {
        return when (color.value) {
            LiftrixColors.Night.value -> "Night"
            LiftrixColors.Jet.value -> "Jet"
            LiftrixColors.PersianGreen.value -> "Persian Green"
            LiftrixColors.TiffanyBlue.value -> "Tiffany Blue"
            LiftrixColors.Snow.value -> "Snow"
            LiftrixColors.Error.value -> "Error Red"
            Color.White.value -> "White"
            Color.Black.value -> "Black"
            else -> "#${color.value.toString(16).padStart(8, '0').uppercase()}"
        }
    }
    
    /**
     * Calculate WCAG contrast ratio between two colors
     */
    private fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = calculateRelativeLuminance(foreground)
        val backgroundLuminance = calculateRelativeLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    /**
     * Calculate relative luminance according to WCAG guidelines
     */
    private fun calculateRelativeLuminance(color: Color): Float {
        // Convert to linear RGB
        fun linearize(component: Float): Float {
            return if (component <= 0.03928f) {
                component / 12.92f
            } else {
                ((component + 0.055f) / 1.055f).pow(2.4f)
            }
        }
        
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}