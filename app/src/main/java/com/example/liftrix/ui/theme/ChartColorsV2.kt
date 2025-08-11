package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Liftrix V2 Chart Color System
 * 8-color complementary palette optimized for data visualization
 * Designed for accessibility and visual harmony across both light and dark themes
 */
object ChartColorsV2 {
    
    // ============================================================================
    // PRIMARY DATA SERIES COLORS
    // ============================================================================
    
    /**
     * 8-color complementary palette for data series
     * Colors chosen for optimal contrast and accessibility in both light and dark themes
     */
    val Series1 = Color(0xFF06B6D4)    // Teal (Primary brand color)
    val Series2 = Color(0xFFF97316)    // Orange (Complementary to teal)
    val Series3 = Color(0xFF3B82F6)    // Blue (Analogous harmony)
    val Series4 = Color(0xFF10B981)    // Green (Success/positive metrics)
    val Series5 = Color(0xFF8B5CF6)    // Purple (Split complementary)
    val Series6 = Color(0xFFEC4899)    // Pink (Triadic harmony)
    val Series7 = Color(0xFF6B7280)    // Gray (Neutral/baseline data)
    val Series8 = Color(0xFFF59E0B)    // Yellow (Alerts/highlights)
    
    /**
     * Get all series colors as an ordered list for easy iteration
     */
    fun getAllSeriesColors(): List<Color> = listOf(
        Series1, Series2, Series3, Series4, 
        Series5, Series6, Series7, Series8
    )
    
    /**
     * Get a specific series color by index (0-7), cycling if needed
     */
    fun getSeriesColor(index: Int): Color {
        val colors = getAllSeriesColors()
        return colors[index % colors.size]
    }
    
    // ============================================================================
    // CHART INFRASTRUCTURE COLORS
    // ============================================================================
    
    object Infrastructure {
        // Grid and axis colors (theme-aware)
        fun getGridColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(0xFF2A2A2A).copy(alpha = 0.3f)
            } else {
                Color(0xFFE5E7EB).copy(alpha = 0.7f)
            }
        }
        
        fun getAxisColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(0xFF6B7280)
            } else {
                Color(0xFF374151)
            }
        }
        
        fun getAxisLabelColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                LiftrixColorsV2.Dark.TextSecondary
            } else {
                LiftrixColorsV2.Light.TextSecondary
            }
        }
        
        // Chart background (transparent by default)
        val ChartBackground = Color.Transparent
        
        // Selection and interaction colors
        fun getSelectionColor(isDarkTheme: Boolean): Color {
            return LiftrixColorsV2.Teal.copy(alpha = if (isDarkTheme) 0.3f else 0.2f)
        }
        
        fun getHoverColor(isDarkTheme: Boolean): Color {
            return LiftrixColorsV2.TealHover.copy(alpha = if (isDarkTheme) 0.2f else 0.1f)
        }
    }
    
    // ============================================================================
    // SEMANTIC DATA COLORS
    // ============================================================================
    
    object Semantic {
        // Progress and growth indicators
        val Positive = Color(0xFF10B981)     // Green - growth, success, gains
        val Negative = Color(0xFFEF4444)     // Red - decline, errors, losses
        val Neutral = Color(0xFF6B7280)      // Gray - no change, baseline
        val Warning = Color(0xFFF59E0B)      // Yellow - attention needed
        val Info = LiftrixColorsV2.Teal      // Teal - informational data
        
        // Performance categories
        val Excellent = Color(0xFF059669)    // Dark green
        val Good = Color(0xFF10B981)         // Green
        val Average = Color(0xFFF59E0B)      // Yellow
        val Poor = Color(0xFFEF4444)         // Red
        val Critical = Color(0xFFDC2626)     // Dark red
        
        /**
         * Get performance color by score (0-100)
         */
        fun getPerformanceColor(score: Float): Color {
            return when {
                score >= 90f -> Excellent
                score >= 75f -> Good
                score >= 50f -> Average
                score >= 25f -> Poor
                else -> Critical
            }
        }
        
        /**
         * Get trend color based on change percentage
         */
        fun getTrendColor(changePercent: Float): Color {
            return when {
                changePercent > 5f -> Positive
                changePercent < -5f -> Negative
                else -> Neutral
            }
        }
    }
    
    // ============================================================================
    // GRADIENT DEFINITIONS
    // ============================================================================
    
    object Gradients {
        /**
         * Create a gradient for a specific series color
         */
        fun getSeriesGradient(seriesColor: Color, isDarkTheme: Boolean = true): Brush {
            val startAlpha = if (isDarkTheme) 0.4f else 0.3f
            return Brush.verticalGradient(
                colors = listOf(
                    seriesColor.copy(alpha = startAlpha),
                    seriesColor.copy(alpha = 0.0f)
                )
            )
        }
        
        /**
         * Primary chart gradient (Teal-based)
         */
        fun getPrimaryGradient(isDarkTheme: Boolean): Brush {
            return getSeriesGradient(LiftrixColorsV2.Teal, isDarkTheme)
        }
        
        /**
         * Success gradient (Green)
         */
        val SuccessGradient = Brush.verticalGradient(
            colors = listOf(
                Semantic.Positive.copy(alpha = 0.3f),
                Semantic.Positive.copy(alpha = 0.0f)
            )
        )
        
        /**
         * Error gradient (Red)
         */
        val ErrorGradient = Brush.verticalGradient(
            colors = listOf(
                Semantic.Negative.copy(alpha = 0.3f),
                Semantic.Negative.copy(alpha = 0.0f)
            )
        )
        
        /**
         * Warning gradient (Yellow)
         */
        val WarningGradient = Brush.verticalGradient(
            colors = listOf(
                Semantic.Warning.copy(alpha = 0.3f),
                Semantic.Warning.copy(alpha = 0.0f)
            )
        )
    }
    
    // ============================================================================
    // HEATMAP COLOR SCALE
    // ============================================================================
    
    object Heatmap {
        // 5-step gradient from light to dark for heatmap visualizations
        val Step1 = Color(0xFFE0F7FA)  // Lightest teal
        val Step2 = Color(0xFF67E8F9)  // Light teal
        val Step3 = LiftrixColorsV2.Teal // Medium teal
        val Step4 = LiftrixColorsV2.TealDark // Dark teal
        val Step5 = Color(0xFF064E5B)  // Darkest teal
        
        /**
         * Get all heatmap colors as a list
         */
        fun getHeatmapColors(): List<Color> = listOf(
            Step1, Step2, Step3, Step4, Step5
        )
        
        /**
         * Get heatmap color by intensity (0.0 to 1.0)
         */
        fun getHeatmapColor(intensity: Float): Color {
            val clampedIntensity = intensity.coerceIn(0f, 1f)
            val colors = getHeatmapColors()
            val index = (clampedIntensity * (colors.size - 1)).toInt()
            return colors[index.coerceIn(0, colors.size - 1)]
        }
        
        /**
         * Get heatmap color with interpolation between steps
         */
        fun getInterpolatedHeatmapColor(intensity: Float): Color {
            val clampedIntensity = intensity.coerceIn(0f, 1f)
            val colors = getHeatmapColors()
            val maxIndex = colors.size - 1
            val exactIndex = clampedIntensity * maxIndex
            val lowerIndex = exactIndex.toInt().coerceIn(0, maxIndex)
            val upperIndex = (lowerIndex + 1).coerceIn(0, maxIndex)
            
            if (lowerIndex == upperIndex) {
                return colors[lowerIndex]
            }
            
            val lerpFactor = exactIndex - lowerIndex
            val lowerColor = colors[lowerIndex]
            val upperColor = colors[upperIndex]
            
            return Color(
                red = lowerColor.red + (upperColor.red - lowerColor.red) * lerpFactor,
                green = lowerColor.green + (upperColor.green - lowerColor.green) * lerpFactor,
                blue = lowerColor.blue + (upperColor.blue - lowerColor.blue) * lerpFactor,
                alpha = lowerColor.alpha + (upperColor.alpha - lowerColor.alpha) * lerpFactor
            )
        }
    }
    
    // ============================================================================
    // ACCESSIBILITY HELPERS
    // ============================================================================
    
    object Accessibility {
        /**
         * Get contrasting color for text overlays on chart elements
         */
        fun getContrastingColor(backgroundColor: Color): Color {
            // Simple luminance-based contrast calculation
            val luminance = 0.299 * backgroundColor.red + 
                           0.587 * backgroundColor.green + 
                           0.114 * backgroundColor.blue
            
            return if (luminance > 0.5) {
                Color(0xFF1A1A1A) // Dark text on light background
            } else {
                Color(0xFFFFFFFF) // Light text on dark background
            }
        }
        
        /**
         * Validate that series colors meet WCAG AA contrast requirements
         */
        fun validateSeriesContrast(backgroundColor: Color): List<ValidationResult> {
            val results = mutableListOf<ValidationResult>()
            val seriesColors = getAllSeriesColors()
            
            seriesColors.forEachIndexed { index, seriesColor ->
                val contrast = calculateContrastRatio(seriesColor, backgroundColor)
                val meetsAA = contrast >= 3.0f // WCAG AA for graphics
                
                results.add(
                    ValidationResult(
                        colorName = "Series${index + 1}",
                        color = seriesColor,
                        contrastRatio = contrast,
                        meetsAA = meetsAA,
                        recommendation = if (!meetsAA) "Consider darker variant" else null
                    )
                )
            }
            
            return results
        }
        
        private fun calculateContrastRatio(foreground: Color, background: Color): Float {
            val fgLuminance = calculateLuminance(foreground)
            val bgLuminance = calculateLuminance(background)
            val lighter = maxOf(fgLuminance, bgLuminance)
            val darker = minOf(fgLuminance, bgLuminance)
            return (lighter + 0.05f) / (darker + 0.05f)
        }
        
        private fun calculateLuminance(color: Color): Float {
            fun linearize(component: Float): Float {
                return if (component <= 0.03928f) {
                    component / 12.92f
                } else {
                    ((component + 0.055) / 1.055).toDouble().pow(2.4).toFloat()
                }
            }
            
            val r = linearize(color.red)
            val g = linearize(color.green)
            val b = linearize(color.blue)
            
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
    }
    
    // ============================================================================
    // COLOR BLINDNESS SUPPORT
    // ============================================================================
    
    object ColorBlindness {
        /**
         * Get color-blind friendly series colors
         * Uses patterns and distinct hues that work well for protanopia, deuteranopia, and tritanopia
         */
        fun getColorBlindFriendlySeries(): List<Color> = listOf(
            Color(0xFF0073E6),    // Blue (safe for all types)
            Color(0xFFE69F00),    // Orange (protanopia/deuteranopia safe)
            Color(0xFF56B4E9),    // Sky blue (safe for all types)
            Color(0xFF009E73),    // Bluish green (safe for all types)
            Color(0xFFF0E442),    // Yellow (tritanopia safe)
            Color(0xFF0072B2),    // Blue (safe for all types)
            Color(0xFFD55E00),    // Vermillion (protanopia/deuteranopia safe)
            Color(0xFFCC79A7)     // Pink (safe for all types)
        )
        
        /**
         * Convert regular series to color-blind friendly version
         */
        fun getAccessibleSeriesColor(seriesIndex: Int): Color {
            val friendlyColors = getColorBlindFriendlySeries()
            return friendlyColors[seriesIndex % friendlyColors.size]
        }
    }
}

/**
 * Validation result for accessibility checking
 */
data class ValidationResult(
    val colorName: String,
    val color: Color,
    val contrastRatio: Float,
    val meetsAA: Boolean,
    val recommendation: String? = null
)

/**
 * Extension functions for easy chart color access
 */
fun Color.withChartAlpha(isDarkTheme: Boolean): Color {
    return this.copy(alpha = if (isDarkTheme) 0.9f else 0.8f)
}

fun Color.asChartGradient(isDarkTheme: Boolean): Brush {
    return ChartColorsV2.Gradients.getSeriesGradient(this, isDarkTheme)
}