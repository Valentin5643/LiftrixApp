package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Chart Color Provider
 * 
 * Centralized chart color selection system providing consistent data visualization colors
 * across all chart components. Uses the 8-color complementary palette from ChartColorsV2
 * for optimal accessibility and visual harmony.
 * 
 * This is the primary interface for chart components to access colors, providing a clean
 * abstraction over the more comprehensive ChartColorsV2 system.
 */
object ChartColorProvider {
    
    /**
     * 8-color complementary palette optimized for data visualization
     * Colors chosen for optimal contrast and accessibility in both light and dark themes
     */
    private val palette = listOf(
        Color(0xFF06B6D4), // Teal (Primary brand color)
        Color(0xFFF97316), // Orange (Complementary to teal)
        Color(0xFF3B82F6), // Blue (Analogous harmony)
        Color(0xFF10B981), // Green (Success/positive metrics)
        Color(0xFF8B5CF6), // Purple (Split complementary)
        Color(0xFFEC4899), // Pink (Triadic harmony)
        Color(0xFF6B7280), // Gray (Neutral/baseline data)
        Color(0xFFF59E0B)  // Yellow (Alerts/highlights)
    )
    
    /**
     * Get a specific series color by index (0-7), cycling if needed
     * 
     * @param index The series index (0-based)
     * @return Color for the specified series, cycling through palette if index > 7
     */
    fun getSeriesColor(index: Int): Color {
        return palette[index % palette.size]
    }
    
    /**
     * Get all series colors as a list for batch operations
     * 
     * @return List of all 8 series colors in order
     */
    fun getAllSeriesColors(): List<Color> = palette
    
    /**
     * Create a vertical gradient brush for chart fill areas
     * 
     * @param index The series index to create gradient for
     * @param isDarkTheme Whether the current theme is dark (affects alpha values)
     * @return Brush with vertical gradient from series color to transparent
     */
    fun getGradient(index: Int, isDarkTheme: Boolean = true): Brush {
        val color = getSeriesColor(index)
        val startAlpha = if (isDarkTheme) 0.4f else 0.3f
        return Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = startAlpha),
                color.copy(alpha = 0.0f)
            )
        )
    }
    
    /**
     * Create a gradient brush for a specific color
     * 
     * @param color The base color for the gradient
     * @param isDarkTheme Whether the current theme is dark (affects alpha values)
     * @return Brush with vertical gradient from color to transparent
     */
    fun getGradientForColor(color: Color, isDarkTheme: Boolean = true): Brush {
        val startAlpha = if (isDarkTheme) 0.4f else 0.3f
        return Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = startAlpha),
                color.copy(alpha = 0.0f)
            )
        )
    }
    
    /**
     * Get primary chart color (Teal - index 0)
     * Convenience method for single-series charts
     * 
     * @return Primary teal color
     */
    fun getPrimaryColor(): Color = palette[0]
    
    /**
     * Get secondary chart color (Orange - index 1) 
     * Convenience method for dual-series charts
     * 
     * @return Secondary orange color
     */
    fun getSecondaryColor(): Color = palette[1]
    
    /**
     * Get success color (Green - index 3)
     * For positive metrics and growth indicators
     * 
     * @return Success green color
     */
    fun getSuccessColor(): Color = palette[3]
    
    /**
     * Get warning color (Yellow - index 7)
     * For alerts and attention-needed data
     * 
     * @return Warning yellow color
     */
    fun getWarningColor(): Color = palette[7]
    
    /**
     * Get neutral color (Gray - index 6)
     * For baseline data and neutral states
     * 
     * @return Neutral gray color
     */
    fun getNeutralColor(): Color = palette[6]
}