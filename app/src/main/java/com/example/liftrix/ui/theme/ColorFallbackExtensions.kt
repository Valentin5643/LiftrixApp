package com.example.liftrix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Color Fallback Extensions for LiftrixColorsV2
 * 
 * These extensions provide guaranteed color access for components that
 * experience intermittent fallback issues during state transitions.
 * 
 * Use these when components need color access during:
 * - Authentication flows
 * - Settings navigation
 * - Theme switching
 * - Feature flag evaluation
 */

/**
 * Get stable primary color with fallback protection
 * Use this instead of MaterialTheme.colorScheme.primary in critical components
 */
@Composable
fun getStablePrimary(): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.primary.takeIf { it != Color.Unspecified }
        ?: StableColorProvider.getStablePrimary(isDarkTheme)
}

/**
 * Get stable background color with fallback protection
 * Use this for critical background colors in auth/settings screens
 */
@Composable
fun getStableBackground(): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.background.takeIf { it != Color.Unspecified }
        ?: StableColorProvider.getStableBackground(isDarkTheme)
}

/**
 * Get stable surface color with fallback protection
 * Use this for card surfaces and elevated components
 */
@Composable
fun getStableSurface(): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.surface.takeIf { it != Color.Unspecified }
        ?: StableColorProvider.getStableSurface(isDarkTheme)
}

/**
 * Get stable text color with fallback protection
 * Use this for critical text that must always be visible
 */
@Composable
fun getStableOnSurface(): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.onSurface.takeIf { it != Color.Unspecified }
        ?: StableColorProvider.getStableOnSurface(isDarkTheme)
}

/**
 * Get stable outline color with fallback protection
 * Use this for borders and dividers in critical components
 */
@Composable
fun getStableOutline(): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.outline.takeIf { it != Color.Unspecified }
        ?: StableColorProvider.getStableOutline(isDarkTheme)
}

/**
 * Extension for MaterialTheme.colorScheme that provides fallback safety
 * Use this pattern: MaterialTheme.colorScheme.primary.orFallback()
 */
@Composable
fun Color?.orFallback(colorType: String = "unknown"): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return this ?: run {
        StableColorProvider.logColorFallback("null_color_$colorType", "Color.orFallback")
        when (colorType) {
            "primary" -> StableColorProvider.getStablePrimary(isDarkTheme)
            "background" -> StableColorProvider.getStableBackground(isDarkTheme)
            "surface" -> StableColorProvider.getStableSurface(isDarkTheme)
            "onSurface" -> StableColorProvider.getStableOnSurface(isDarkTheme)
            "outline" -> StableColorProvider.getStableOutline(isDarkTheme)
            else -> StableColorProvider.getStablePrimary(isDarkTheme)
        }
    }
}

/**
 * Direct LiftrixColorsV2 access with theme awareness
 * Use this when you need guaranteed V2 colors regardless of theme state
 */
object DirectV2Colors {
    @Composable
    fun getPrimary(): Color {
        return LiftrixColorsV2.Teal
    }
    
    @Composable
    fun getBackground(): Color {
        val isDarkTheme = isSystemInDarkTheme()
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.BackgroundPrimary
        } else {
            LiftrixColorsV2.Light.BackgroundPrimary
        }
    }
    
    @Composable
    fun getSurface(): Color {
        val isDarkTheme = isSystemInDarkTheme()
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.BackgroundSecondary
        } else {
            LiftrixColorsV2.Light.BackgroundSecondary
        }
    }
    
    @Composable
    fun getTextPrimary(): Color {
        val isDarkTheme = isSystemInDarkTheme()
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.TextPrimary
        } else {
            LiftrixColorsV2.Light.TextPrimary
        }
    }
    
    @Composable
    fun getTextSecondary(): Color {
        val isDarkTheme = isSystemInDarkTheme()
        return if (isDarkTheme) {
            LiftrixColorsV2.Dark.TextSecondary
        } else {
            LiftrixColorsV2.Light.TextSecondary
        }
    }
}

/**
 * Composable that provides debug information about current color state
 * Use this temporarily to debug color fallback issues
 */
@Composable
fun DebugColorState(component: String) {
    val isDarkTheme = isSystemInDarkTheme()
    val currentPrimary = MaterialTheme.colorScheme.primary
    
    // This will log information about the current color state
    remember(isDarkTheme, currentPrimary) {
        val fallbackReport = buildString {
            appendLine("=== Color Debug: $component ===")
            appendLine("Dark theme: $isDarkTheme")
            appendLine("MaterialTheme.colorScheme.primary: $currentPrimary")
            appendLine("LiftrixColorsV2.Teal: ${LiftrixColorsV2.Teal}")
            appendLine("Fallback report: ${StableColorProvider.getFallbackReport()}")
        }
        
        timber.log.Timber.d(fallbackReport)
        Unit
    }
}