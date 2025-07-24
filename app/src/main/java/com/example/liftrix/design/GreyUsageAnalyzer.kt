package com.example.liftrix.design

import androidx.compose.ui.graphics.Color
import com.example.liftrix.ui.theme.LiftrixColors
import kotlin.math.abs

/**
 * Simple Grey Usage Validator
 * 
 * Validates that grey usage stays below 20% of interface elements
 * as required by FR-001 AC3.
 */
object GreyUsageAnalyzer {
    
    /**
     * Check if a color is grey (RGB components are similar)
     */
    fun isGreyColor(color: Color, tolerance: Float = 0.05f): Boolean {
        val r = color.red
        val g = color.green  
        val b = color.blue
        val avg = (r + g + b) / 3f
        
        return abs(r - avg) <= tolerance &&
               abs(g - avg) <= tolerance &&
               abs(b - avg) <= tolerance
    }
    
    /**
     * Calculate grey percentage in color list
     */
    fun calculateGreyPercentage(colors: List<Color>): Double {
        if (colors.isEmpty()) return 0.0
        
        val greyCount = colors.count { isGreyColor(it) }
        return (greyCount.toDouble() / colors.size.toDouble()) * 100.0
    }
    
    /**
     * Validate that grey usage is under 20%
     */
    fun validateGreyUsage(colors: List<Color>): ValidationResult {
        val percentage = calculateGreyPercentage(colors)
        return ValidationResult(
            greyPercentage = percentage,
            isValid = percentage < 20.0,
            totalColors = colors.size,
            greyColors = colors.count { isGreyColor(it) }
        )
    }
    
    /**
     * Get current theme colors for validation
     */
    fun getThemeColors(): List<Color> = listOf(
        LiftrixColors.Primary,
        LiftrixColors.Secondary, 
        LiftrixColors.TiffanyBlue,
        LiftrixColors.BackgroundLight,
        LiftrixColors.SurfaceLight,
        LiftrixColors.SurfaceVariant,
        LiftrixColors.Outline,
        LiftrixColors.OutlineVariant
    )
}

data class ValidationResult(
    val greyPercentage: Double,
    val isValid: Boolean,
    val totalColors: Int,
    val greyColors: Int
)