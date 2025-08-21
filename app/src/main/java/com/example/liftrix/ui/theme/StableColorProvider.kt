package com.example.liftrix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Stable Color Provider for LiftrixColorsV2
 * 
 * Solves intermittent color fallback issues by providing guaranteed color access
 * regardless of theme state management timing issues.
 * 
 * Key Features:
 * - Immediate color access without waiting for state resolution
 * - Fallback-safe color definitions
 * - State-independent color access for critical UI components
 * - Debug logging for color fallback detection
 */
object StableColorProvider {
    
    // ============================================================================
    // GUARANTEED COLOR ACCESS (No State Dependencies)
    // ============================================================================
    
    /**
     * Get stable primary color - never falls back to Material defaults
     */
    fun getStablePrimary(isDarkTheme: Boolean): Color {
        return LiftrixColorsV2.Teal
    }
    
    /**
     * Get stable background color - guaranteed to work in all states
     */
    fun getStableBackground(isDarkTheme: Boolean): Color {
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.BackgroundPrimary
        } else {
            LiftrixColorsV2.Light.BackgroundPrimary
        }
    }
    
    /**
     * Get stable surface color - never falls back
     */
    fun getStableSurface(isDarkTheme: Boolean): Color {
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.BackgroundSecondary
        } else {
            LiftrixColorsV2.Light.BackgroundSecondary
        }
    }
    
    /**
     * Get stable text color - guaranteed contrast
     */
    fun getStableOnSurface(isDarkTheme: Boolean): Color {
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.TextPrimary
        } else {
            LiftrixColorsV2.Light.TextPrimary
        }
    }
    
    /**
     * Get stable outline color - no state dependencies
     */
    fun getStableOutline(isDarkTheme: Boolean): Color {
        return LiftrixColorsV2.Teal.copy(alpha = if (isDarkTheme) 0.60f else 0.38f)
    }
    
    // ============================================================================
    // STABLE COLOR SCHEME PROVIDER
    // ============================================================================
    
    /**
     * Get guaranteed stable color scheme for emergency fallback
     * This function NEVER depends on external state and always returns valid colors
     */
    fun getStableColorScheme(isDarkTheme: Boolean): ColorScheme {
        return if (isDarkTheme) {
            LiftrixColorsV2.darkColorScheme
        } else {
            LiftrixColorsV2.lightColorScheme
        }
    }
    
    // ============================================================================
    // FALLBACK DETECTION & LOGGING
    // ============================================================================
    
    private var fallbackCounter = 0
    private val fallbackScenarios = mutableSetOf<String>()
    
    /**
     * Log when color fallback is detected
     */
    fun logColorFallback(scenario: String, component: String) {
        fallbackCounter++
        fallbackScenarios.add(scenario)
        
        Timber.w("StableColorProvider: Color fallback detected in '$component' - scenario: '$scenario' (total fallbacks: $fallbackCounter)")
        
        // Alert if fallbacks become frequent
        if (fallbackCounter > 5) {
            Timber.e("StableColorProvider: High number of color fallbacks detected ($fallbackCounter). Check theme state management.")
        }
    }
    
    /**
     * Get fallback debugging report
     */
    fun getFallbackReport(): String {
        return buildString {
            appendLine("=== Color Fallback Report ===")
            appendLine("Total fallbacks: $fallbackCounter")
            appendLine("Unique scenarios: ${fallbackScenarios.size}")
            appendLine("Scenarios: ${fallbackScenarios.joinToString(", ")}")
        }
    }
    
    /**
     * Reset fallback tracking
     */
    fun resetFallbackTracking() {
        fallbackCounter = 0
        fallbackScenarios.clear()
    }
}

/**
 * Composable wrapper for stable color access
 * Use this when you need guaranteed color access without state dependencies
 */
@Composable
fun WithStableColors(
    scenario: String = "unknown",
    content: @Composable (colors: StableColorAccess) -> Unit
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    val stableColors = remember(isDarkTheme) {
        StableColorAccess(
            primary = StableColorProvider.getStablePrimary(isDarkTheme),
            background = StableColorProvider.getStableBackground(isDarkTheme),
            surface = StableColorProvider.getStableSurface(isDarkTheme),
            onSurface = StableColorProvider.getStableOnSurface(isDarkTheme),
            outline = StableColorProvider.getStableOutline(isDarkTheme),
            colorScheme = StableColorProvider.getStableColorScheme(isDarkTheme)
        )
    }
    
    content(stableColors)
}

/**
 * Data class providing stable color access
 */
data class StableColorAccess(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val outline: Color,
    val colorScheme: ColorScheme
)

/**
 * Extension function for ColorScheme fallback safety
 */
fun ColorScheme?.orStable(isDarkTheme: Boolean): ColorScheme {
    return this ?: run {
        StableColorProvider.logColorFallback("null_colorscheme", "ColorScheme.orStable")
        StableColorProvider.getStableColorScheme(isDarkTheme)
    }
}

/**
 * Extension function for Color fallback safety
 */
fun Color?.orStablePrimary(isDarkTheme: Boolean): Color {
    return this ?: run {
        StableColorProvider.logColorFallback("null_color", "Color.orStablePrimary")
        StableColorProvider.getStablePrimary(isDarkTheme)
    }
}