package com.example.liftrix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Local composition for accessing the V2 color system
 */
val LocalLiftrixV2Colors = compositionLocalOf { LiftrixColorsV2 }

// Note: ThemeVersion is now defined in Theme.kt to avoid duplication

/**
 * Theme configuration data class
 */
data class ThemeConfig(
    val version: ThemeVersion = ThemeVersion.V2,
    val useDynamicColors: Boolean = false,
    val highContrastMode: Boolean = false
)

/**
 * Enhanced Liftrix theme with V2 color system
 * Supports gradual migration from V1 to V2 color system
 * 
 * @param darkTheme Whether to use dark theme
 * @param themeConfig Configuration for theme version and features
 * @param content The composable content
 */
@Composable
fun LiftrixThemeV2(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeConfig: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeConfig.version) {
        ThemeVersion.V1 -> {
            // V1 now maps to V2 color system for consistency
            if (darkTheme) {
                LiftrixColorsV2.darkColorScheme
            } else {
                LiftrixColorsV2.lightColorScheme
            }
        }
        ThemeVersion.V2 -> {
            // Use V2 color system
            if (darkTheme) {
                LiftrixColorsV2.darkColorScheme
            } else {
                LiftrixColorsV2.lightColorScheme
            }
        }
    }
    
    // Apply high contrast modifications if needed
    val finalColorScheme = if (themeConfig.highContrastMode) {
        applyHighContrast(colorScheme, darkTheme)
    } else {
        colorScheme
    }
    
    CompositionLocalProvider(
        LocalLiftrixV2Colors provides LiftrixColorsV2
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = LiftrixTypographySystem,
            content = content
        )
    }
}

/**
 * Apply high contrast modifications to color scheme
 */
private fun applyHighContrast(
    baseScheme: androidx.compose.material3.ColorScheme,
    isDark: Boolean
): androidx.compose.material3.ColorScheme {
    return if (isDark) {
        baseScheme.copy(
            // Increase contrast for dark theme
            onBackground = androidx.compose.ui.graphics.Color.White,
            onSurface = androidx.compose.ui.graphics.Color.White,
            onSurfaceVariant = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
            outline = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
            outlineVariant = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
        )
    } else {
        baseScheme.copy(
            // Increase contrast for light theme
            onBackground = androidx.compose.ui.graphics.Color.Black,
            onSurface = androidx.compose.ui.graphics.Color.Black,
            onSurfaceVariant = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
            outline = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
            outlineVariant = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f)
        )
    }
}

/**
 * Migration helper composable that provides access to both color systems
 * Use this during the migration period to gradually update components
 */
@Composable
fun MigrationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preferV2: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeConfig = ThemeConfig(
        version = if (preferV2) ThemeVersion.V2 else ThemeVersion.V1
    )
    
    LiftrixThemeV2(
        darkTheme = darkTheme,
        themeConfig = themeConfig,
        content = content
    )
}

/**
 * Preview helper for comparing V1 and V2 themes side by side
 */
@Composable
fun ThemeComparisonPreview(
    darkTheme: Boolean = false,
    v1Content: @Composable () -> Unit,
    v2Content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Row {
        // V1 Theme
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.weight(1f)
        ) {
            LiftrixThemeV2(
                darkTheme = darkTheme,
                themeConfig = ThemeConfig(version = ThemeVersion.V1)
            ) {
                v1Content()
            }
        }
        
        // V2 Theme
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.weight(1f)
        ) {
            LiftrixThemeV2(
                darkTheme = darkTheme,
                themeConfig = ThemeConfig(version = ThemeVersion.V2)
            ) {
                v2Content()
            }
        }
    }
}

/**
 * Extension properties for easy access to V2 colors
 */
val MaterialTheme.v2Colors: LiftrixColorsV2
    @Composable
    get() = LocalLiftrixV2Colors.current

/**
 * Check if current theme is using V2 colors
 */
@Composable
fun isUsingV2Colors(): Boolean {
    val primaryColor = MaterialTheme.colorScheme.primary
    return primaryColor == LiftrixColorsV2.Teal
}

/**
 * Get appropriate gradient based on theme version
 */
@Composable
fun getPrimaryGradient(): androidx.compose.ui.graphics.Brush {
    return if (isUsingV2Colors()) {
        LiftrixColorsV2.Gradients.PrimaryGradient
    } else {
        // Fallback to V1 gradient
        PrimaryGradient
    }
}

/**
 * Get data visualization colors based on theme version
 */
@Composable
fun getDataVizColors(): List<androidx.compose.ui.graphics.Color> {
    return if (isUsingV2Colors()) {
        LiftrixColorsV2.DataViz.getAllSeriesColors()
    } else {
        // Fallback to V1 colors
        listOf(
            LiftrixColors.PersianGreen,
            LiftrixColors.TiffanyBlue,
            LiftrixColors.Error,
            LiftrixColors.PersianGreen.copy(alpha = 0.7f),
            LiftrixColors.TiffanyBlue.copy(alpha = 0.7f),
            LiftrixColors.Jet,
            LiftrixColors.Snow,
            LiftrixColors.Night
        )
    }
}