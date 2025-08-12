package com.example.liftrix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Liftrix V2 Color System
 * Modern color palette with improved contrast and accessibility
 * Replaces the 5-color system with a refined palette based on Teal (#06B6D4)
 */
object LiftrixColorsV2 {
    
    // ============================================================================
    // CORE BRAND COLORS
    // ============================================================================
    
    // Primary Brand Color - Teal (replacing Persian Green #339989)
    val Teal = Color(0xFF00BCD4)           // Cyan 500 - Primary actions, branding
    val TealHover = Color(0xFF0891B2)      // Hover states, secondary actions
    val TealDark = Color(0xFF0E7490)       // Pressed states, emphasis
    val TealLight = Color(0xFF67E8F9)      // Highlights, accents
    val TealContainer = Color(0xFF083E4D)  // Dark containers
    val TealSurface = Color(0xFFE0F7FA)    // Light containers
    
    // ============================================================================
    // DARK THEME COLORS
    // ============================================================================
    
    object Dark {
        // Background hierarchy
        val BackgroundPrimary = Color(0xFF0B0C0B)     // Very dark green-black - main background
        val BackgroundSecondary = Color(0xFF0F0F0F)   // Card backgrounds
        val BackgroundProgress = Color(0xFF17191A)    // Progress tab card backgrounds
        val BackgroundTertiary = Color(0xFF2A2A2A)    // Input fields, elevated surfaces
        val BackgroundElevated = Color(0xFF3A3A3A)    // Modals, overlays
        
        // Text hierarchy
        val TextPrimary = Color(0xFFFFFFFF)           // Primary text
        val TextSecondary = Color(0xFFB3B3B3)         // Secondary text
        val TextTertiary = Color(0xFF888888)          // Tertiary text, hints
        val TextDisabled = Color(0xFF555555)          // Disabled state
        
        // Interactive elements
        val Primary = Teal                            // Primary actions
        val PrimaryVariant = TealHover                // Hover states
        val Secondary = TealLight                     // Secondary actions
        val SecondaryVariant = TealLight.copy(alpha = 0.8f)
        
        // Surface colors
        val Surface = BackgroundSecondary             // Card surfaces
        val SurfaceVariant = BackgroundTertiary       // Elevated surfaces
        val SurfaceContainerHigh = BackgroundElevated // Highest elevation
        
        // Semantic colors
        val Error = Color(0xFFEF4444)                 // Error states
        val ErrorContainer = Color(0xFF7F1D1D)        // Error containers
        val Warning = Color(0xFFF59E0B)               // Warning states
        val Success = Color(0xFF10B981)               // Success states
        val Info = Color(0xFF3B82F6)                  // Information
        
        // Borders and dividers
        val Outline = Color(0xFF3A3A3A)               // Borders
        val OutlineVariant = Color(0xFF2A2A2A)        // Subtle borders
        val Divider = Color(0xFF2A2A2A)               // Dividers
    }
    
    // ============================================================================
    // LIGHT THEME COLORS
    // ============================================================================
    
    object Light {
        // Background hierarchy
        val BackgroundPrimary = Color(0xFFFFFFFF)     // Pure white - main background
        val BackgroundSecondary = Color(0xFFF8F9FA)   // Card backgrounds
        val BackgroundTertiary = Color(0xFFF3F4F6)    // Input fields
        val BackgroundElevated = Color(0xFFFFFFFF)    // Modals, overlays
        
        // Text hierarchy
        val TextPrimary = Color(0xFF1A1A1A)           // Primary text
        val TextSecondary = Color(0xFF6B7280)         // Secondary text
        val TextTertiary = Color(0xFF9CA3AF)          // Tertiary text, hints
        val TextDisabled = Color(0xFFD1D5DB)          // Disabled state
        
        // Interactive elements
        val Primary = Teal                            // Primary actions
        val PrimaryVariant = TealDark                 // Pressed states
        val Secondary = TealHover                     // Secondary actions
        val SecondaryVariant = TealHover.copy(alpha = 0.9f)
        
        // Surface colors
        val Surface = BackgroundSecondary             // Card surfaces
        val SurfaceVariant = BackgroundTertiary       // Input surfaces
        val SurfaceContainerHigh = BackgroundSecondary // Elevated surfaces
        
        // Semantic colors
        val Error = Color(0xFFDC2626)                 // Error states
        val ErrorContainer = Color(0xFFFEE2E2)        // Error containers
        val Warning = Color(0xFFD97706)               // Warning states
        val Success = Color(0xFF059669)               // Success states
        val Info = Color(0xFF2563EB)                  // Information
        
        // Borders and dividers
        val Outline = Color(0xFFE5E7EB)               // Borders
        val OutlineVariant = Color(0xFFF3F4F6)        // Subtle borders
        val Divider = Color(0xFFE5E7EB)               // Dividers
    }
    
    // ============================================================================
    // DATA VISUALIZATION PALETTE
    // ============================================================================
    
    object DataViz {
        // Primary data series - optimized for both themes
        val Series1 = Teal                            // Primary series (base teal)
        val Series2 = Color(0xFFF97316)               // Orange - complementary contrast
        val Series3 = Color(0xFF3B82F6)               // Blue - analogous harmony
        val Series4 = Color(0xFF10B981)               // Green - positive metrics
        val Series5 = Color(0xFF8B5CF6)               // Purple - split complementary
        val Series6 = Color(0xFFEC4899)               // Pink - triadic harmony
        val Series7 = Color(0xFF6B7280)               // Gray - neutral/baseline
        val Series8 = Color(0xFFF59E0B)               // Yellow - alerts/highlights
        
        // Chart-specific colors
        val ChartGrid = Color(0xFF2A2A2A).copy(alpha = 0.2f)  // Grid lines
        val ChartAxis = Color(0xFF6B7280)             // Axis labels
        val ChartBackground = Color(0x00000000)       // Transparent
        val ChartSelection = Teal.copy(alpha = 0.2f)  // Selection overlay
        
        // Gradient colors for charts
        val GradientStart = Teal.copy(alpha = 0.3f)
        val GradientEnd = Teal.copy(alpha = 0.0f)
        
        // Heatmap colors (5-step gradient)
        val HeatLow = Color(0xFFE0F7FA)               // Lightest
        val HeatMediumLow = Color(0xFF67E8F9)
        val HeatMedium = Teal
        val HeatMediumHigh = TealDark
        val HeatHigh = Color(0xFF064E5B)              // Darkest
        
        // Status indicators
        val Positive = Color(0xFF10B981)              // Growth, success
        val Negative = Color(0xFFEF4444)              // Decline, error
        val Neutral = Color(0xFF6B7280)               // No change
        val Warning = Color(0xFFF59E0B)               // Attention needed
        
        // Get contrasting color for overlays
        fun getContrastColor(isDark: Boolean): Color {
            return if (isDark) Color.White else Color.Black
        }
        
        // Get all series colors as list
        fun getAllSeriesColors(): List<Color> {
            return listOf(Series1, Series2, Series3, Series4, Series5, Series6, Series7, Series8)
        }
        
        // Get gradient for a specific series
        fun getSeriesGradient(seriesColor: Color, isDark: Boolean = true): Brush {
            return Brush.verticalGradient(
                colors = listOf(
                    seriesColor.copy(alpha = if (isDark) 0.4f else 0.3f),
                    seriesColor.copy(alpha = 0.0f)
                )
            )
        }
    }
    
    // ============================================================================
    // MATERIAL 3 COLOR SCHEMES
    // ============================================================================
    
    /**
     * Dark color scheme following Material 3 guidelines
     */
    val darkColorScheme = darkColorScheme(
        // Primary colors
        primary = Dark.Primary,
        onPrimary = Color(0xFF000000),
        primaryContainer = TealContainer,
        onPrimaryContainer = TealLight,
        
        // Secondary colors
        secondary = Dark.Secondary,
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF064E5B),
        onSecondaryContainer = Color(0xFF67E8F9),
        
        // Tertiary colors
        tertiary = DataViz.Series4,  // Green for variety
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF064239),
        onTertiaryContainer = Color(0xFF6EE7B7),
        
        // Error colors
        error = Dark.Error,
        onError = Color(0xFF000000),
        errorContainer = Dark.ErrorContainer,
        onErrorContainer = Color(0xFFFFB4AB),
        
        // Background colors
        background = Dark.BackgroundPrimary,
        onBackground = Dark.TextPrimary,
        
        // Surface colors
        surface = Dark.BackgroundPrimary,
        onSurface = Dark.TextPrimary,
        surfaceVariant = Dark.BackgroundSecondary,
        onSurfaceVariant = Dark.TextSecondary,
        surfaceContainerLow = Dark.BackgroundSecondary,
        surfaceContainer = Dark.BackgroundSecondary,
        surfaceContainerHigh = Dark.BackgroundProgress,
        surfaceContainerHighest = Dark.BackgroundElevated,
        
        // Outline colors
        outline = Dark.Outline,
        outlineVariant = Dark.OutlineVariant,
        
        // Other colors
        scrim = Color(0xFF000000),
        inverseSurface = Light.BackgroundPrimary,
        inverseOnSurface = Light.TextPrimary,
        inversePrimary = Teal
    )
    
    /**
     * Light color scheme following Material 3 guidelines
     */
    val lightColorScheme = lightColorScheme(
        // Primary colors
        primary = Light.Primary,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = TealSurface,
        onPrimaryContainer = Color(0xFF00626E),
        
        // Secondary colors
        secondary = Light.Secondary,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD0F0F7),
        onSecondaryContainer = Color(0xFF00414D),
        
        // Tertiary colors
        tertiary = DataViz.Series4,  // Green for variety
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD1FAE5),
        onTertiaryContainer = Color(0xFF064E3B),
        
        // Error colors
        error = Light.Error,
        onError = Color(0xFFFFFFFF),
        errorContainer = Light.ErrorContainer,
        onErrorContainer = Color(0xFF7F1D1D),
        
        // Background colors
        background = Light.BackgroundPrimary,
        onBackground = Light.TextPrimary,
        
        // Surface colors
        surface = Light.BackgroundPrimary,
        onSurface = Light.TextPrimary,
        surfaceVariant = Light.BackgroundSecondary,
        onSurfaceVariant = Light.TextSecondary,
        surfaceContainerLow = Light.BackgroundSecondary,
        surfaceContainer = Light.BackgroundSecondary,
        surfaceContainerHigh = Light.BackgroundTertiary,
        surfaceContainerHighest = Light.BackgroundSecondary,
        
        // Outline colors
        outline = Light.Outline,
        outlineVariant = Light.OutlineVariant,
        
        // Other colors
        scrim = Color(0x33000000),
        inverseSurface = Dark.BackgroundPrimary,
        inverseOnSurface = Dark.TextPrimary,
        inversePrimary = TealLight
    )
    
    // ============================================================================
    // GRADIENT DEFINITIONS
    // ============================================================================
    
    object Gradients {
        // Primary gradients
        val PrimaryGradient = Brush.linearGradient(
            colors = listOf(Teal, TealHover)
        )
        
        val PrimaryVerticalGradient = Brush.verticalGradient(
            colors = listOf(Teal, TealDark)
        )
        
        // Card elevation gradients
        val CardElevationGradientDark = Brush.verticalGradient(
            colors = listOf(
                Teal.copy(alpha = 0.05f),
                Color.Transparent
            )
        )
        
        val CardElevationGradientLight = Brush.verticalGradient(
            colors = listOf(
                Teal.copy(alpha = 0.02f),
                Color.Transparent
            )
        )
        
        // Chart gradients
        val ChartGradientDark = Brush.verticalGradient(
            colors = listOf(
                Teal.copy(alpha = 0.3f),
                Teal.copy(alpha = 0.0f)
            )
        )
        
        val ChartGradientLight = Brush.verticalGradient(
            colors = listOf(
                Teal.copy(alpha = 0.2f),
                Teal.copy(alpha = 0.0f)
            )
        )
        
        // Success gradient
        val SuccessGradient = Brush.linearGradient(
            colors = listOf(
                DataViz.Series4,
                DataViz.Series4.copy(alpha = 0.8f)
            )
        )
        
        // Error gradient
        val ErrorGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFFEF4444),
                Color(0xFFDC2626)
            )
        )
    }
    
    // ============================================================================
    // MIGRATION HELPERS
    // ============================================================================
    
    /**
     * Maps old 5-color system colors to new V2 colors
     * Use this during migration to ensure consistency
     */
    object Migration {
        fun mapOldToNew(oldColor: Color): Color {
            return when (oldColor) {
                // Old Persian Green → New Teal
                Color(0xFF339989) -> Teal
                // Old Tiffany Blue → New Teal Hover
                Color(0xFF7DE2D1) -> TealHover
                // Old Night → Pure Black
                Color(0xFF131515) -> Dark.BackgroundPrimary
                // Old Jet → New Secondary Background
                Color(0xFF2B2C28) -> Dark.BackgroundSecondary
                // Old Snow → Pure White
                Color(0xFFFFFAFB) -> Light.BackgroundPrimary
                // Keep errors as-is
                else -> oldColor
            }
        }
        
        /**
         * Get migration report for tracking color replacements
         */
        fun getMigrationMap(): Map<String, Pair<Color, Color>> {
            return mapOf(
                "Persian Green" to Pair(Color(0xFF339989), Teal),
                "Tiffany Blue" to Pair(Color(0xFF7DE2D1), TealHover),
                "Night" to Pair(Color(0xFF131515), Dark.BackgroundPrimary),
                "Jet" to Pair(Color(0xFF2B2C28), Dark.BackgroundSecondary),
                "Snow" to Pair(Color(0xFFFFFAFB), Light.BackgroundPrimary)
            )
        }
    }
}

/**
 * Extension functions for color manipulation
 */
fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)

fun Color.darken(amount: Float = 0.1f): Color {
    return Color(
        red = (red * (1 - amount)).coerceIn(0f, 1f),
        green = (green * (1 - amount)).coerceIn(0f, 1f),
        blue = (blue * (1 - amount)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun Color.lighten(amount: Float = 0.1f): Color {
    return Color(
        red = (red + (1 - red) * amount).coerceIn(0f, 1f),
        green = (green + (1 - green) * amount).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * amount).coerceIn(0f, 1f),
        alpha = alpha
    )
}