package com.example.liftrix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Liftrix Brand Color System
 * Centralized color definitions following Material 3 principles
 * with accessibility compliance (4.5:1 contrast ratio minimum)
 */
object LiftrixColors {
    
    // Primary Brand Colors
    val Primary: Color = Color(0xFF20C9B7)  // Teal
    val Secondary: Color = Color(0xFF2A3B7D)  // Indigo
    val Accent: Color = Color(0xFFFF6B6B)  // Coral
    val Error: Color = Color(0xFFFF4444)
    
    // Extended Brand Palette
    val TealLight: Color = Color(0xFF4DD0C7)
    val TealDark: Color = Color(0xFF00695C)
    val IndigoLight: Color = Color(0xFF5C6BC0)
    val IndigoDark: Color = Color(0xFF1A237E)
    val CoralLight: Color = Color(0xFFFF8A80)
    val CoralDark: Color = Color(0xFFD32F2F)
    
    // Light Theme Colors
    val BackgroundLight: Color = Color(0xFFF8F9FA)
    val SurfaceLight: Color = Color(0xFFFFFFFF)
    val OnPrimary: Color = Color.White
    val OnSecondary: Color = Color.White
    val OnAccent: Color = Color.White
    val OnError: Color = Color.White
    val OnBackground: Color = Color(0xFF1C1B1F)
    val OnSurface: Color = Color(0xFF1C1B1F)
    
    // Dark Theme Colors - OLED Optimized
    val BackgroundDark: Color = Color(0xFF0F0F0F)  // Deep black for OLED optimization
    val SurfaceDark: Color = Color(0xFF1E1E1E)
    val OnBackgroundDark: Color = Color(0xFFE6E1E5)
    val OnSurfaceDark: Color = Color(0xFFE6E1E5)
    
    // Material 3 Container Colors (Light Theme)
    val PrimaryContainer: Color = Color(0xFFB2F2EA)
    val OnPrimaryContainer: Color = Color(0xFF003A35)
    val SecondaryContainer: Color = Color(0xFFDDE1FF)
    val OnSecondaryContainer: Color = Color(0xFF0F1B37)
    val TertiaryContainer: Color = Color(0xFFFFDAD8)
    val OnTertiaryContainer: Color = Color(0xFF410006)
    val ErrorContainer: Color = Color(0xFFFFDAD6)
    val OnErrorContainer: Color = Color(0xFF410002)
    
    // Material 3 Container Colors (Dark Theme)
    val PrimaryContainerDark: Color = Color(0xFF005047)
    val OnPrimaryContainerDark: Color = Color(0xFFB2F2EA)
    val SecondaryContainerDark: Color = Color(0xFF1E2A4E)
    val OnSecondaryContainerDark: Color = Color(0xFFDDE1FF)
    val TertiaryContainerDark: Color = Color(0xFF5F0008)
    val OnTertiaryContainerDark: Color = Color(0xFFFFDAD8)
    val ErrorContainerDark: Color = Color(0xFF93000A)
    val OnErrorContainerDark: Color = Color(0xFFFFDAD6)
    
    // Outline Colors
    val Outline: Color = Color(0xFF79747E)
    val OutlineDark: Color = Color(0xFF938F99)
    val OutlineVariant: Color = Color(0xFFCAC4CF)
    val OutlineVariantDark: Color = Color(0xFF49454E)
    
    // Surface Variants
    val SurfaceVariant: Color = Color(0xFFE7E0EC)
    val OnSurfaceVariant: Color = Color(0xFF49454E)
    val SurfaceVariantDark: Color = Color(0xFF49454E)
    val OnSurfaceVariantDark: Color = Color(0xFFCAC4CF)
    
    // Time-based Color Variations
    object TimeBasedColors {
        // Morning Colors (6AM - 11AM) - Energizing warm tones
        val MorningPrimary: Color = Color(0xFF26D0BE)  // Brighter teal
        val MorningAccent: Color = Color(0xFFFFB74D)   // Warm amber
        val MorningBackground: Color = Color(0xFFFFFBF0)  // Warm white
        
        // Afternoon Colors (12PM - 5PM) - Balanced neutral tones  
        val AfternoonPrimary: Color = Primary  // Standard teal
        val AfternoonAccent: Color = Color(0xFF42A5F5)  // Cool blue
        val AfternoonBackground: Color = BackgroundLight  // Standard background
        
        // Evening Colors (6PM - 11PM) - Calming cool tones
        val EveningPrimary: Color = Color(0xFF1DB5A6)  // Deeper teal
        val EveningAccent: Color = Color(0xFF7986CB)  // Soft indigo
        val EveningBackground: Color = Color(0xFFF3F4F6)  // Cool gray
        
        // Night Colors (12AM - 5AM) - Deep, restful tones
        val NightPrimary: Color = Color(0xFF00695C)  // Dark teal
        val NightAccent: Color = Color(0xFF5C6BC0)  // Muted indigo
        val NightBackground: Color = Color(0xFF1A1A1A)  // Deep dark
    }
    
    // Enhanced Brand Gradients
    object BrandGradients {
        val TealCoral: Brush = Brush.linearGradient(
            colors = listOf(Primary, Accent)
        )
        
        val IndigoTeal: Brush = Brush.linearGradient(
            colors = listOf(Secondary, Primary)
        )
        
        val CoralTeal: Brush = Brush.linearGradient(
            colors = listOf(Accent, Primary)
        )
        
        val TealIndigo: Brush = Brush.linearGradient(
            colors = listOf(Primary, Secondary)
        )
        
        // Radial gradients for cards and surfaces
        val TealRadial: Brush = Brush.radialGradient(
            colors = listOf(TealLight, Primary, TealDark)
        )
        
        val IndigoRadial: Brush = Brush.radialGradient(
            colors = listOf(IndigoLight, Secondary, IndigoDark)
        )
        
        val CoralRadial: Brush = Brush.radialGradient(
            colors = listOf(CoralLight, Accent, CoralDark)
        )
        
        // Vertical gradients for depth
        val SurfaceElevation: Brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.1f),
                Color.Transparent
            )
        )
        
        val CardElevation: Brush = Brush.verticalGradient(
            colors = listOf(
                Primary.copy(alpha = 0.05f),
                Color.Transparent
            )
        )
        
        // Time-based gradients
        val MorningGradient: Brush = Brush.linearGradient(
            colors = listOf(TimeBasedColors.MorningPrimary, TimeBasedColors.MorningAccent)
        )
        
        val EveningGradient: Brush = Brush.linearGradient(
            colors = listOf(TimeBasedColors.EveningPrimary, TimeBasedColors.EveningAccent)
        )
    }
    
    // Legacy gradients for backward compatibility
    val TealCoralGradient: Brush = BrandGradients.TealCoral
    val IndigoTealGradient: Brush = BrandGradients.IndigoTeal
    
    // Inverse Colors
    val InverseSurface: Color = Color(0xFF313033)
    val InverseOnSurface: Color = Color(0xFFF4EFF4)
    val InversePrimary: Color = Color(0xFFB2F2EA)
    val InverseSurfaceDark: Color = Color(0xFFE6E1E5)
    val InverseOnSurfaceDark: Color = Color(0xFF313033)
    val InversePrimaryDark: Color = Color(0xFF005047)
}

/**
 * Data class representing brand colors for easy access
 */
data class BrandColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color
)

/**
 * Get time-based color scheme based on current hour
 * @param hour Current hour (0-23), defaults to current time
 * @return BrandColors adapted for the time of day
 */
fun getTimeBasedColorScheme(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): BrandColors {
    return when (hour) {
        in 6..11 -> BrandColors(  // Morning
            primary = LiftrixColors.TimeBasedColors.MorningPrimary,
            secondary = LiftrixColors.Secondary,
            accent = LiftrixColors.TimeBasedColors.MorningAccent,
            background = LiftrixColors.TimeBasedColors.MorningBackground
        )
        in 12..17 -> BrandColors(  // Afternoon
            primary = LiftrixColors.TimeBasedColors.AfternoonPrimary,
            secondary = LiftrixColors.Secondary,
            accent = LiftrixColors.TimeBasedColors.AfternoonAccent,
            background = LiftrixColors.TimeBasedColors.AfternoonBackground
        )
        in 18..23 -> BrandColors(  // Evening
            primary = LiftrixColors.TimeBasedColors.EveningPrimary,
            secondary = LiftrixColors.Secondary,
            accent = LiftrixColors.TimeBasedColors.EveningAccent,
            background = LiftrixColors.TimeBasedColors.EveningBackground
        )
        else -> BrandColors(  // Night (0-5)
            primary = LiftrixColors.TimeBasedColors.NightPrimary,
            secondary = LiftrixColors.Secondary,
            accent = LiftrixColors.TimeBasedColors.NightAccent,
            background = LiftrixColors.TimeBasedColors.NightBackground
        )
    }
}

/**
 * WCAG 2.1 AA Contrast Validation and Enhancement
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
     * Get accessible color variant that meets WCAG AA standards
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
        
        // Fall back to high contrast colors
        return if (backgroundColor.luminance() > 0.5f) {
            Color.Black
        } else {
            Color.White
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
     * Get high contrast color scheme for accessibility
     */
    fun getHighContrastColorScheme(isDark: Boolean): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF333333),
                onPrimaryContainer = Color.White,
                secondary = Color(0xFFFFFFFF),
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF333333),
                onSecondaryContainer = Color.White,
                tertiary = Color(0xFFFFFFFF),
                onTertiary = Color.Black,
                tertiaryContainer = Color(0xFF333333),
                onTertiaryContainer = Color.White,
                error = Color(0xFFFF6B6B),
                onError = Color.Black,
                errorContainer = Color(0xFF5F0008),
                onErrorContainer = Color.White,
                background = Color.Black,
                onBackground = Color.White,
                surface = Color.Black,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF1E1E1E),
                onSurfaceVariant = Color.White,
                outline = Color(0xFF666666),
                outlineVariant = Color(0xFF333333),
                scrim = Color.Black,
                inverseSurface = Color.White,
                inverseOnSurface = Color.Black,
                inversePrimary = Color.Black
            )
        } else {
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = Color.Black,
                secondary = Color.Black,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0E0E0),
                onSecondaryContainer = Color.Black,
                tertiary = Color.Black,
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE0E0E0),
                onTertiaryContainer = Color.Black,
                error = Color(0xFFD32F2F),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFF5F5F5),
                onSurfaceVariant = Color.Black,
                outline = Color(0xFF999999),
                outlineVariant = Color(0xFFCCCCCC),
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
     * Get brand colors with accessibility validation
     */
    fun getAccessibleBrandColors(
        isDark: Boolean,
        isHighContrast: Boolean = false
    ): BrandColors {
        val backgroundColor = if (isDark) LiftrixColors.BackgroundDark else LiftrixColors.BackgroundLight
        
        return if (isHighContrast) {
            BrandColors(
                primary = if (isDark) Color.White else Color.Black,
                secondary = if (isDark) Color.White else Color.Black,
                accent = if (isDark) LiftrixColors.Accent.copy(alpha = 0.8f) else LiftrixColors.Accent,
                background = backgroundColor
            )
        } else {
            BrandColors(
                primary = getAccessibleColor(LiftrixColors.Primary, backgroundColor),
                secondary = getAccessibleColor(LiftrixColors.Secondary, backgroundColor),
                accent = getAccessibleColor(LiftrixColors.Accent, backgroundColor),
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

// Legacy color references for backward compatibility
val LiftrixTeal: Color = LiftrixColors.Primary
val LiftrixIndigo: Color = LiftrixColors.Secondary  
val LiftrixCoral: Color = LiftrixColors.Accent