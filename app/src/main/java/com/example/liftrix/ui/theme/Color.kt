package com.example.liftrix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// REMOVED: Old V1 5-color system (LiftrixColors object)
// All color definitions now use LiftrixColorsV2 system only

/**
 * Data class representing brand colors for accessibility functions
 */
data class BrandColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color
)

/**
 * WCAG 2.1 AA Contrast Validation for LiftrixColorsV2
 * Ensures all color combinations meet accessibility standards
 */
object AccessibilityColors {
    
    /**
     * WCAG 2.1 contrast ratio requirements
     */
    object ContrastRatios {
        const val NORMAL_TEXT_AA = 4.5f
        const val LARGE_TEXT_AA = 3.0f
        const val NON_TEXT_AA = 3.0f
        const val ENHANCED_AAA = 7.0f
    }
    
    /**
     * Calculate luminance of a color according to WCAG guidelines
     */
    fun Color.luminance(): Float {
        // Convert to linear RGB
        fun linearize(component: Float): Float {
            return if (component <= 0.03928f) {
                component / 12.92f
            } else {
                ((component + 0.055f) / 1.055f).pow(2.4f)
            }
        }
        
        val r = linearize(red)
        val g = linearize(green)
        val b = linearize(blue)
        
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
    
    /**
     * Calculate contrast ratio between two colors
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = kotlin.math.max(foregroundLuminance, backgroundLuminance)
        val darker = kotlin.math.min(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    /**
     * Check if color combination meets WCAG AA standards
     */
    fun meetsAAStandards(
        foreground: Color,
        background: Color,
        isLargeText: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        val minimumRatio = if (isLargeText) ContrastRatios.LARGE_TEXT_AA else ContrastRatios.NORMAL_TEXT_AA
        return ratio >= minimumRatio
    }
    
    /**
     * Check if color combination meets WCAG AAA standards
     */
    fun meetsAAAStandards(
        foreground: Color,
        background: Color,
        isLargeText: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        val minimumRatio = if (isLargeText) 4.5f else ContrastRatios.ENHANCED_AAA
        return ratio >= minimumRatio
    }
    
    /**
     * Get accessible color variant from LiftrixColorsV2 that meets WCAG AA standards
     * Prioritizes using V2 high contrast colors
     */
    fun getAccessibleColor(
        originalColor: Color,
        backgroundColor: Color,
        isLargeText: Boolean = false,
        preferOriginal: Boolean = true
    ): Color {
        if (meetsAAStandards(originalColor, backgroundColor, isLargeText)) {
            return originalColor
        }
        
        // Try to adjust the original color first
        if (preferOriginal) {
            val adjustedColor = adjustColorForContrast(originalColor, backgroundColor, isLargeText)
            if (adjustedColor != null) {
                return adjustedColor
            }
        }
        
        // Fall back to LiftrixColorsV2 high contrast colors
        return if (backgroundColor.luminance() > 0.5f) {
            LiftrixColorsV2.Dark.BackgroundPrimary  // Use V2 dark background
        } else {
            LiftrixColorsV2.Light.BackgroundPrimary   // Use V2 light background
        }
    }
    
    /**
     * Adjust color brightness to meet contrast requirements while preserving hue
     */
    private fun adjustColorForContrast(
        originalColor: Color,
        backgroundColor: Color,
        isLargeText: Boolean
    ): Color? {
        val targetRatio = if (isLargeText) ContrastRatios.LARGE_TEXT_AA else ContrastRatios.NORMAL_TEXT_AA
        val backgroundLuminance = backgroundColor.luminance()
        
        // Convert to HSV for easier manipulation
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (originalColor.red * 255).toInt(),
                (originalColor.green * 255).toInt(),
                (originalColor.blue * 255).toInt()
            ), hsv
        )
        
        // Try adjusting brightness (value component)
        val originalValue = hsv[2]
        
        // Try darker first if background is light
        if (backgroundLuminance > 0.5f) {
            var value = originalValue - 0.1f
            while (value >= 0.1f) {
                hsv[2] = value
                val adjustedColor = Color(android.graphics.Color.HSVToColor(hsv))
                if (calculateContrastRatio(adjustedColor, backgroundColor) >= targetRatio) {
                    return adjustedColor
                }
                value -= 0.1f
            }
        } else {
            // Try lighter if background is dark
            var value = originalValue + 0.1f
            while (value <= 1.0f) {
                hsv[2] = value
                val adjustedColor = Color(android.graphics.Color.HSVToColor(hsv))
                if (calculateContrastRatio(adjustedColor, backgroundColor) >= targetRatio) {
                    return adjustedColor
                }
                value += 0.1f
            }
        }
        
        return null
    }
    
    /**
     * Get high contrast color scheme using LiftrixColorsV2 for accessibility
     */
    fun getHighContrastColorScheme(isDark: Boolean): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                primaryContainer = LiftrixColorsV2.Dark.BackgroundSecondary,
                onPrimaryContainer = Color.White,
                secondary = Color.White,
                onSecondary = Color.Black,
                secondaryContainer = LiftrixColorsV2.Dark.BackgroundSecondary,
                onSecondaryContainer = Color.White,
                tertiary = Color.White,
                onTertiary = Color.Black,
                tertiaryContainer = LiftrixColorsV2.Dark.BackgroundSecondary,
                onTertiaryContainer = Color.White,
                error = LiftrixColorsV2.Dark.Error,
                onError = Color.Black,
                errorContainer = Color(0xFF5F0008),
                onErrorContainer = Color.White,
                background = LiftrixColorsV2.Dark.BackgroundPrimary,
                onBackground = Color.White,
                surface = LiftrixColorsV2.Dark.BackgroundPrimary,
                onSurface = Color.White,
                surfaceVariant = LiftrixColorsV2.Dark.BackgroundSecondary,
                onSurfaceVariant = Color.White,
                outline = LiftrixColorsV2.Teal.copy(alpha = 0.87f),
                outlineVariant = LiftrixColorsV2.Teal.copy(alpha = 0.38f),
                scrim = Color.Black,
                inverseSurface = Color.White,
                inverseOnSurface = Color.Black,
                inversePrimary = Color.Black
            )
        } else {
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color.White.copy(alpha = 0.9f),
                onPrimaryContainer = Color.Black,
                secondary = Color.Black,
                onSecondary = Color.White,
                secondaryContainer = Color.White.copy(alpha = 0.9f),
                onSecondaryContainer = Color.Black,
                tertiary = Color.Black,
                onTertiary = Color.White,
                tertiaryContainer = Color.White.copy(alpha = 0.9f),
                onTertiaryContainer = Color.Black,
                error = LiftrixColorsV2.Light.Error,
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color.Black,
                background = LiftrixColorsV2.Light.BackgroundPrimary,
                onBackground = Color.Black,
                surface = LiftrixColorsV2.Light.BackgroundPrimary,
                onSurface = Color.Black,
                surfaceVariant = Color.White.copy(alpha = 0.95f),
                onSurfaceVariant = Color.Black,
                outline = LiftrixColorsV2.Teal.copy(alpha = 0.60f),
                outlineVariant = LiftrixColorsV2.Teal.copy(alpha = 0.24f),
                scrim = Color.Black,
                inverseSurface = Color.Black,
                inverseOnSurface = Color.White,
                inversePrimary = Color.White
            )
        }
    }
    
    /**
     * Validate entire color scheme for accessibility compliance
     */
    fun validateColorScheme(colorScheme: ColorScheme): AccessibilityValidationResult {
        val issues = mutableListOf<String>()
        var totalTests = 0
        var passedTests = 0
        
        // Test primary color combinations
        totalTests++
        if (meetsAAStandards(colorScheme.onPrimary, colorScheme.primary)) {
            passedTests++
        } else {
            issues.add("Primary color contrast insufficient: ${String.format("%.1f", calculateContrastRatio(colorScheme.onPrimary, colorScheme.primary))}:1")
        }
        
        // Test secondary color combinations
        totalTests++
        if (meetsAAStandards(colorScheme.onSecondary, colorScheme.secondary)) {
            passedTests++
        } else {
            issues.add("Secondary color contrast insufficient: ${String.format("%.1f", calculateContrastRatio(colorScheme.onSecondary, colorScheme.secondary))}:1")
        }
        
        // Test surface color combinations
        totalTests++
        if (meetsAAStandards(colorScheme.onSurface, colorScheme.surface)) {
            passedTests++
        } else {
            issues.add("Surface color contrast insufficient: ${String.format("%.1f", calculateContrastRatio(colorScheme.onSurface, colorScheme.surface))}:1")
        }
        
        // Test background color combinations
        totalTests++
        if (meetsAAStandards(colorScheme.onBackground, colorScheme.background)) {
            passedTests++
        } else {
            issues.add("Background color contrast insufficient: ${String.format("%.1f", calculateContrastRatio(colorScheme.onBackground, colorScheme.background))}:1")
        }
        
        // Test error color combinations
        totalTests++
        if (meetsAAStandards(colorScheme.onError, colorScheme.error)) {
            passedTests++
        } else {
            issues.add("Error color contrast insufficient: ${String.format("%.1f", calculateContrastRatio(colorScheme.onError, colorScheme.error))}:1")
        }
        
        val compliancePercentage = (passedTests * 100) / totalTests
        
        return AccessibilityValidationResult(
            isCompliant = issues.isEmpty(),
            compliancePercentage = compliancePercentage,
            issues = issues,
            passedTests = passedTests,
            totalTests = totalTests
        )
    }
    
    /**
     * Get brand colors from LiftrixColorsV2 with accessibility validation
     */
    fun getAccessibleBrandColors(
        isDark: Boolean,
        isHighContrast: Boolean = false
    ): BrandColors {
        val backgroundColor = if (isDark) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
        
        return if (isHighContrast) {
            BrandColors(
                primary = if (isDark) Color.White else Color.Black,
                secondary = if (isDark) Color.White else Color.Black,
                accent = if (isDark) LiftrixColorsV2.TealLight.copy(alpha = 0.8f) else LiftrixColorsV2.Teal,
                background = backgroundColor
            )
        } else {
            BrandColors(
                primary = getAccessibleColor(LiftrixColorsV2.Teal, backgroundColor),
                secondary = getAccessibleColor(LiftrixColorsV2.TealHover, backgroundColor),
                accent = getAccessibleColor(LiftrixColorsV2.TealLight, backgroundColor),
                background = backgroundColor
            )
        }
    }
}

/**
 * Result of accessibility validation
 */
data class AccessibilityValidationResult(
    val isCompliant: Boolean,
    val compliancePercentage: Int,
    val issues: List<String>,
    val passedTests: Int,
    val totalTests: Int
)

/**
 * LiftrixColorsV2 Palette Compliance Validation
 * Ensures color usage adheres to the modern Teal-based color system
 * Validates grey usage and provides V2 alternatives
 */
object ColorPaletteValidator {
    
    /**
     * Measures grey content in a color to determine if it's primarily grey
     */
    fun isGreyColor(color: Color, tolerance: Float = 0.05f): Boolean {
        val r = color.red
        val g = color.green  
        val b = color.blue
        val avg = (r + g + b) / 3f
        
        // Check if all RGB components are within tolerance of the average (indicating grey)
        return kotlin.math.abs(r - avg) <= tolerance &&
               kotlin.math.abs(g - avg) <= tolerance &&
               kotlin.math.abs(b - avg) <= tolerance
    }
    
    /**
     * Calculates grey usage percentage in a list of colors
     */
    fun calculateGreyUsagePercentage(colors: List<Color>): GreyUsageReport {
        if (colors.isEmpty()) {
            return GreyUsageReport(0, 0, 0.0, true, emptyList())
        }
        
        val greyColors = mutableListOf<Color>()
        colors.forEach { color ->
            if (isGreyColor(color)) {
                greyColors.add(color)
            }
        }
        
        val greyPercentage = (greyColors.size.toDouble() / colors.size.toDouble()) * 100.0
        val meetsTarget = greyPercentage < 20.0
        
        return GreyUsageReport(
            totalColors = colors.size,
            greyColors = greyColors.size,
            greyPercentage = greyPercentage,
            meetsTarget = meetsTarget,
            identifiedGreyColors = greyColors
        )
    }
    
    /**
     * Audits color scheme for grey usage compliance
     */
    fun auditColorScheme(lightScheme: androidx.compose.material3.ColorScheme, darkScheme: androidx.compose.material3.ColorScheme): ColorSchemeGreyAudit {
        val lightColors = listOf(
            lightScheme.primary, lightScheme.secondary, lightScheme.tertiary,
            lightScheme.background, lightScheme.surface, lightScheme.surfaceVariant,
            lightScheme.outline, lightScheme.outlineVariant
        )
        
        val darkColors = listOf(
            darkScheme.primary, darkScheme.secondary, darkScheme.tertiary,
            darkScheme.background, darkScheme.surface, darkScheme.surfaceVariant,
            darkScheme.outline, darkScheme.outlineVariant
        )
        
        val lightReport = calculateGreyUsagePercentage(lightColors)
        val darkReport = calculateGreyUsagePercentage(darkColors)
        
        return ColorSchemeGreyAudit(
            lightThemeReport = lightReport,
            darkThemeReport = darkReport,
            overallCompliant = lightReport.meetsTarget && darkReport.meetsTarget,
            recommendations = generateRecommendations(lightReport, darkReport)
        )
    }
    
    /**
     * Suggests LiftrixColorsV2 alternatives to replace any off-palette colors
     */
    fun suggestV2ColorAlternatives(offPaletteColor: Color): List<ColorAlternative> {
        return listOf(
            ColorAlternative(
                color = LiftrixColorsV2.Teal.copy(alpha = 0.12f),
                name = "Teal Variant",
                description = "Light Teal maintaining brand consistency"
            ),
            ColorAlternative(
                color = LiftrixColorsV2.TealLight.copy(alpha = 0.20f),
                name = "Teal Light Container",
                description = "Subtle Teal Light container color"
            ),
            ColorAlternative(
                color = LiftrixColorsV2.TealHover.copy(alpha = 0.15f),
                name = "Teal Hover Surface Variant", 
                description = "Light Teal Hover for subtle surface differentiation"
            ),
            ColorAlternative(
                color = LiftrixColorsV2.Light.BackgroundPrimary,
                name = "V2 Light Background",
                description = "Pure white for maximum contrast and clarity"
            ),
            ColorAlternative(
                color = LiftrixColorsV2.Dark.BackgroundPrimary,
                name = "V2 Dark Background",
                description = "Pure black for deep contrast and OLED optimization"
            )
        )
    }
    
    private fun generateRecommendations(lightReport: GreyUsageReport, darkReport: GreyUsageReport): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!lightReport.meetsTarget) {
            recommendations.add("Light theme grey usage (${String.format("%.1f", lightReport.greyPercentage)}%) exceeds 20% target")
            recommendations.add("Consider using teal-tinted variants for surface and outline colors")
        }
        
        if (!darkReport.meetsTarget) {
            recommendations.add("Dark theme grey usage (${String.format("%.1f", darkReport.greyPercentage)}%) exceeds 20% target")
            recommendations.add("Replace grey backgrounds with deep teal-tinted alternatives")
        }
        
        if (lightReport.meetsTarget && darkReport.meetsTarget) {
            recommendations.add("✅ Grey usage is within acceptable limits (<20%)")
            recommendations.add("Continue using LiftrixColorsV2 Teal-based alternatives to maintain brand consistency")
        }
        
        return recommendations
    }
}

/**
 * Report of grey usage analysis
 */
data class GreyUsageReport(
    val totalColors: Int,
    val greyColors: Int,
    val greyPercentage: Double,
    val meetsTarget: Boolean,
    val identifiedGreyColors: List<Color>
)

/**
 * Complete color scheme grey usage audit result
 */
data class ColorSchemeGreyAudit(
    val lightThemeReport: GreyUsageReport,
    val darkThemeReport: GreyUsageReport,
    val overallCompliant: Boolean,
    val recommendations: List<String>
)

/**
 * Color alternative suggestion
 */
data class ColorAlternative(
    val color: Color,
    val name: String,
    val description: String
)

// V2 gradient definitions for component usage
val PrimaryGradient: Brush = Brush.linearGradient(
    colors = listOf(LiftrixColorsV2.Teal, LiftrixColorsV2.TealHover)
)

val CardElevationGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        LiftrixColorsV2.Teal.copy(alpha = 0.05f),
        Color.Transparent
    )
)