package com.example.liftrix.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ProfileColors - Minimal extension to LiftrixColorsV2 for Profile UI modernization
 * 
 * Only adds green color for achievement progress indicators as requested.
 * All other colors use existing LiftrixColorsV2 system.
 */
object ProfileColors {
    
    /** Progress green for achievement progress indicators */
    val ProgressGreen = Color(0xFF4ADE80)
}

/**
 * Extension functions for profile-specific color manipulations
 */

/**
 * Get a muted version of the color for secondary elements
 */
fun Color.muted(isDark: Boolean = false): Color {
    val alpha = if (isDark) 0.6f else 0.7f
    return this.copy(alpha = alpha)
}

/**
 * Get a subtle version of the color for background elements  
 */
fun Color.subtle(isDark: Boolean = false): Color {
    val alpha = if (isDark) 0.15f else 0.1f
    return this.copy(alpha = alpha)
}

/**
 * Get emphasis version of the color for important elements
 */
fun Color.emphasis(): Color {
    return this.copy(alpha = 1.0f)
}