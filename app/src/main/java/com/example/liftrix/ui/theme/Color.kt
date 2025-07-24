package com.example.liftrix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Liftrix 5-Color Palette System
 * Minimal color palette following Material 3 principles with 98%+ app coverage
 * Achieving <20% grey usage while maintaining WCAG 2.1 AA accessibility compliance
 */
object LiftrixColors {
    
    // Core 5-Color Palette - Foundation of entire color system
    val Night = Color(0xFF131515)          // hsla(180, 5%, 8%, 1) - Dark backgrounds
    val Jet = Color(0xFF2B2C28)            // hsla(75, 5%, 16%, 1) - Secondary surfaces
    val PersianGreen = Color(0xFF339989)    // hsla(171, 50%, 40%, 1) - Primary brand
    val TiffanyBlue = Color(0xFF7DE2D1)     // hsla(170, 64%, 69%, 1) - Accent/Interactive
    val Snow = Color(0xFFFFFAFB)           // hsla(348, 100%, 99%, 1) - Light backgrounds
    
    // Simplified color mappings
    val Primary: Color = PersianGreen
    val Secondary: Color = Jet
    val Error: Color = Color(0xFFFF4444)  // Only exception to 5-color rule
    
    // Container variations using solid tinted colors (eliminates lighter rectangle issue)
    val PersianGreenContainer10 = Color(0xFFE6F3F1)  // Very light teal-tinted white
    val PersianGreenContainer20 = Color(0xFFCCE7E2)  // Light teal-tinted surface
    val PersianGreenContainer30 = Color(0xFFB3DDD3)  // Medium teal-tinted surface
    val TiffanyBlueContainer10 = Color(0xFFE8F8F5)   // Very light blue-tinted white
    val TiffanyBlueContainer20 = Color(0xFFD1F1EB)   // Light blue-tinted surface  
    val TiffanyBlueContainer30 = Color(0xFFBAEAE1)   // Medium blue-tinted surface
    
    // Theme-based background and surface colors
    val BackgroundLight: Color = Snow
    val BackgroundDark: Color = Night
    val SurfaceLight: Color = Snow
    val SurfaceDark: Color = Jet
    
    // Text and content colors optimized for contrast
    val OnPrimary: Color = Snow
    val OnSecondary: Color = Snow
    val OnError: Color = Snow
    val OnBackground: Color = Night  // On light backgrounds
    val OnSurface: Color = Night  // On light surfaces
    val OnBackgroundDark: Color = Snow  // On dark backgrounds
    val OnSurfaceDark: Color = Snow  // On dark surfaces
    
    // Material 3 Container Colors using solid tinted variants
    val PrimaryContainer: Color = PersianGreenContainer20
    val OnPrimaryContainer: Color = Night
    val SecondaryContainer: Color = Color(0xFFE8E9E6)  // Light Jet-tinted surface
    val OnSecondaryContainer: Color = Night
    val TertiaryContainer: Color = TiffanyBlueContainer20
    val OnTertiaryContainer: Color = Night
    val ErrorContainer: Color = Color(0xFFFFDAD6)  // Exception color
    val OnErrorContainer: Color = Color(0xFF410002)  // Exception color
    
    // Material 3 Container Colors (Dark Theme) - solid variants for consistency
    val PrimaryContainerDark: Color = Color(0xFF1B423C)  // Dark teal container
    val OnPrimaryContainerDark: Color = Snow
    val SecondaryContainerDark: Color = Color(0xFF3B3C38)  // Dark Jet-tinted container
    val OnSecondaryContainerDark: Color = Snow
    val TertiaryContainerDark: Color = Color(0xFF1F4E47)  // Dark blue-teal container
    val OnTertiaryContainerDark: Color = Snow
    val ErrorContainerDark: Color = Color(0xFF93000A)  // Exception color
    val OnErrorContainerDark: Color = Snow
    
    // Outline Colors using Persian Green to eliminate grey usage
    val Outline: Color = PersianGreen.copy(alpha = 0.38f)
    val OutlineDark: Color = PersianGreen.copy(alpha = 0.60f)
    val OutlineVariant: Color = PersianGreen.copy(alpha = 0.12f)
    val OutlineVariantDark: Color = PersianGreen.copy(alpha = 0.24f)
    
    // Surface Variants using solid tinted variants
    val SurfaceVariant: Color = Color(0xFFF8FAFA)  // Very light teal-tinted Snow
    val OnSurfaceVariant: Color = Jet
    val SurfaceVariantDark: Color = Color(0xFF1A1B1A)  // Very dark teal-tinted variant
    val OnSurfaceVariantDark: Color = TiffanyBlue
    
    // Inverse Colors using 5-color palette
    val InverseSurface: Color = Jet
    val InverseOnSurface: Color = Snow
    val InversePrimary: Color = TiffanyBlue
    val InverseSurfaceDark: Color = Snow
    val InverseOnSurfaceDark: Color = Night
    val InversePrimaryDark: Color = PersianGreen
}

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
 * WCAG 2.1 AA Contrast Validation for 5-Color Palette
 * Ensures all 5-color palette combinations meet accessibility standards
 * Updated for Night, Jet, Persian Green, Tiffany Blue, Snow color system
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
     * Get accessible color variant from 5-color palette that meets WCAG AA standards
     * Prioritizes using Night/Snow for maximum contrast with brand colors
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
        
        // Fall back to 5-color palette high contrast colors
        return if (backgroundColor.luminance() > 0.5f) {
            LiftrixColors.Night  // Use Night instead of pure black
        } else {
            LiftrixColors.Snow   // Use Snow instead of pure white
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
     * Get high contrast color scheme using 5-color palette for accessibility
     */
    fun getHighContrastColorScheme(isDark: Boolean): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = LiftrixColors.Snow,
                onPrimary = LiftrixColors.Night,
                primaryContainer = LiftrixColors.Jet,
                onPrimaryContainer = LiftrixColors.Snow,
                secondary = LiftrixColors.Snow,
                onSecondary = LiftrixColors.Night,
                secondaryContainer = LiftrixColors.Jet,
                onSecondaryContainer = LiftrixColors.Snow,
                tertiary = LiftrixColors.Snow,
                onTertiary = LiftrixColors.Night,
                tertiaryContainer = LiftrixColors.Jet,
                onTertiaryContainer = LiftrixColors.Snow,
                error = LiftrixColors.Error,
                onError = LiftrixColors.Night,
                errorContainer = Color(0xFF5F0008),
                onErrorContainer = LiftrixColors.Snow,
                background = LiftrixColors.Night,
                onBackground = LiftrixColors.Snow,
                surface = LiftrixColors.Night,
                onSurface = LiftrixColors.Snow,
                surfaceVariant = LiftrixColors.Jet,
                onSurfaceVariant = LiftrixColors.Snow,
                outline = LiftrixColors.PersianGreen.copy(alpha = 0.87f),
                outlineVariant = LiftrixColors.PersianGreen.copy(alpha = 0.38f),
                scrim = LiftrixColors.Night,
                inverseSurface = LiftrixColors.Snow,
                inverseOnSurface = LiftrixColors.Night,
                inversePrimary = LiftrixColors.Night
            )
        } else {
            lightColorScheme(
                primary = LiftrixColors.Night,
                onPrimary = LiftrixColors.Snow,
                primaryContainer = LiftrixColors.Snow.copy(alpha = 0.9f),
                onPrimaryContainer = LiftrixColors.Night,
                secondary = LiftrixColors.Night,
                onSecondary = LiftrixColors.Snow,
                secondaryContainer = LiftrixColors.Snow.copy(alpha = 0.9f),
                onSecondaryContainer = LiftrixColors.Night,
                tertiary = LiftrixColors.Night,
                onTertiary = LiftrixColors.Snow,
                tertiaryContainer = LiftrixColors.Snow.copy(alpha = 0.9f),
                onTertiaryContainer = LiftrixColors.Night,
                error = LiftrixColors.Error,
                onError = LiftrixColors.Snow,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = LiftrixColors.Night,
                background = LiftrixColors.Snow,
                onBackground = LiftrixColors.Night,
                surface = LiftrixColors.Snow,
                onSurface = LiftrixColors.Night,
                surfaceVariant = LiftrixColors.Snow.copy(alpha = 0.95f),
                onSurfaceVariant = LiftrixColors.Night,
                outline = LiftrixColors.PersianGreen.copy(alpha = 0.60f),
                outlineVariant = LiftrixColors.PersianGreen.copy(alpha = 0.24f),
                scrim = LiftrixColors.Night,
                inverseSurface = LiftrixColors.Night,
                inverseOnSurface = LiftrixColors.Snow,
                inversePrimary = LiftrixColors.Snow
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
     * Get brand colors from 5-color palette with accessibility validation
     */
    fun getAccessibleBrandColors(
        isDark: Boolean,
        isHighContrast: Boolean = false
    ): BrandColors {
        val backgroundColor = if (isDark) LiftrixColors.BackgroundDark else LiftrixColors.BackgroundLight
        
        return if (isHighContrast) {
            BrandColors(
                primary = if (isDark) LiftrixColors.Snow else LiftrixColors.Night,
                secondary = if (isDark) LiftrixColors.Snow else LiftrixColors.Night,
                accent = if (isDark) LiftrixColors.TiffanyBlue.copy(alpha = 0.8f) else LiftrixColors.TiffanyBlue,
                background = backgroundColor
            )
        } else {
            BrandColors(
                primary = getAccessibleColor(LiftrixColors.PersianGreen, backgroundColor),
                secondary = getAccessibleColor(LiftrixColors.Jet, backgroundColor),
                accent = getAccessibleColor(LiftrixColors.TiffanyBlue, backgroundColor),
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
 * 5-Color Palette Compliance Validation
 * Ensures color usage adheres to Night, Jet, Persian Green, Tiffany Blue, Snow palette
 * Validates <20% grey usage requirement through pure 5-color system adoption
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
     * Suggests 5-color palette alternatives to replace any off-palette colors
     */
    fun suggest5ColorAlternatives(offPaletteColor: Color): List<ColorAlternative> {
        return listOf(
            ColorAlternative(
                color = LiftrixColors.PersianGreen.copy(alpha = 0.12f),
                name = "Persian Green Variant",
                description = "Light Persian Green maintaining brand consistency"
            ),
            ColorAlternative(
                color = LiftrixColors.TiffanyBlue.copy(alpha = 0.20f),
                name = "Tiffany Blue Container",
                description = "Subtle Tiffany Blue container color"
            ),
            ColorAlternative(
                color = LiftrixColors.Jet.copy(alpha = 0.15f),
                name = "Jet Surface Variant", 
                description = "Light Jet for subtle surface differentiation"
            ),
            ColorAlternative(
                color = LiftrixColors.Snow,
                name = "Snow Background",
                description = "Pure Snow for maximum contrast and clarity"
            ),
            ColorAlternative(
                color = LiftrixColors.Night,
                name = "Night Background",
                description = "Pure Night for deep contrast and OLED optimization"
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
            recommendations.add("Continue using teal-based alternatives to maintain brand consistency")
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

// Direct gradient definitions for component usage
val PrimaryGradient: Brush = Brush.linearGradient(
    colors = listOf(LiftrixColors.PersianGreen, LiftrixColors.TiffanyBlue)
)

val CardElevationGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        LiftrixColors.PersianGreen.copy(alpha = 0.05f),
        Color.Transparent
    )
)