package com.example.liftrix.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.liftrix.ui.common.PerformanceOptimizations
import java.util.Calendar

// Performance-optimized color scheme creation with caching
private val LightColorScheme = PerformanceOptimizations.ThemeLoadingOptimizer.getCachedColorScheme("light") {
    lightColorScheme(
        primary = LiftrixColors.Primary,
        onPrimary = LiftrixColors.OnPrimary,
        primaryContainer = LiftrixColors.PrimaryContainer,
        onPrimaryContainer = LiftrixColors.OnPrimaryContainer,
        secondary = LiftrixColors.Secondary,
        onSecondary = LiftrixColors.OnSecondary,
        secondaryContainer = LiftrixColors.SecondaryContainer,
        onSecondaryContainer = LiftrixColors.OnSecondaryContainer,
        tertiary = LiftrixColors.Accent,
        onTertiary = LiftrixColors.OnAccent,
        tertiaryContainer = LiftrixColors.TertiaryContainer,
        onTertiaryContainer = LiftrixColors.OnTertiaryContainer,
        error = LiftrixColors.Error,
        onError = LiftrixColors.OnError,
        errorContainer = LiftrixColors.ErrorContainer,
        onErrorContainer = LiftrixColors.OnErrorContainer,
        background = LiftrixColors.BackgroundLight,
        onBackground = LiftrixColors.OnBackground,
        surface = LiftrixColors.SurfaceLight,
        onSurface = LiftrixColors.OnSurface,
        surfaceVariant = LiftrixColors.SurfaceVariant,
        onSurfaceVariant = LiftrixColors.OnSurfaceVariant,
        outline = LiftrixColors.Outline,
        outlineVariant = LiftrixColors.OutlineVariant,
        inverseSurface = LiftrixColors.InverseSurface,
        inverseOnSurface = LiftrixColors.InverseOnSurface,
        inversePrimary = LiftrixColors.InversePrimary,
    )
}

private val DarkColorScheme = PerformanceOptimizations.ThemeLoadingOptimizer.getCachedColorScheme("dark") {
    darkColorScheme(
        primary = LiftrixColors.Primary,
        onPrimary = LiftrixColors.OnPrimary,
        primaryContainer = LiftrixColors.PrimaryContainerDark,
        onPrimaryContainer = LiftrixColors.OnPrimaryContainerDark,
        secondary = LiftrixColors.Secondary,
        onSecondary = LiftrixColors.OnSecondary,
        secondaryContainer = LiftrixColors.SecondaryContainerDark,
        onSecondaryContainer = LiftrixColors.OnSecondaryContainerDark,
        tertiary = LiftrixColors.Accent,
        onTertiary = LiftrixColors.OnAccent,
        tertiaryContainer = LiftrixColors.TertiaryContainerDark,
        onTertiaryContainer = LiftrixColors.OnTertiaryContainerDark,
        error = LiftrixColors.Error,
        onError = LiftrixColors.OnError,
        errorContainer = LiftrixColors.ErrorContainerDark,
        onErrorContainer = LiftrixColors.OnErrorContainerDark,
        background = LiftrixColors.BackgroundDark,
        onBackground = LiftrixColors.OnBackgroundDark,
        surface = LiftrixColors.SurfaceDark,
        onSurface = LiftrixColors.OnSurfaceDark,
        surfaceVariant = LiftrixColors.SurfaceVariantDark,
        onSurfaceVariant = LiftrixColors.OnSurfaceVariantDark,
        outline = LiftrixColors.OutlineDark,
        outlineVariant = LiftrixColors.OutlineVariantDark,
        inverseSurface = LiftrixColors.InverseSurfaceDark,
        inverseOnSurface = LiftrixColors.InverseOnSurfaceDark,
        inversePrimary = LiftrixColors.InversePrimaryDark,
    )
}

/**
 * Create time-based color scheme for light theme with performance optimization
 */
private fun getTimeBasedLightColorScheme(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) = 
    PerformanceOptimizations.ThemeLoadingOptimizer.getCachedColorScheme("time_light_$hour") {
        lightColorScheme(
            primary = getTimeBasedColorScheme(hour).primary,
            onPrimary = LiftrixColors.OnPrimary,
            primaryContainer = LiftrixColors.PrimaryContainer,
            onPrimaryContainer = LiftrixColors.OnPrimaryContainer,
            secondary = LiftrixColors.Secondary,
            onSecondary = LiftrixColors.OnSecondary,
            secondaryContainer = LiftrixColors.SecondaryContainer,
            onSecondaryContainer = LiftrixColors.OnSecondaryContainer,
            tertiary = getTimeBasedColorScheme(hour).accent,
            onTertiary = LiftrixColors.OnAccent,
            tertiaryContainer = LiftrixColors.TertiaryContainer,
            onTertiaryContainer = LiftrixColors.OnTertiaryContainer,
            error = LiftrixColors.Error,
            onError = LiftrixColors.OnError,
            errorContainer = LiftrixColors.ErrorContainer,
            onErrorContainer = LiftrixColors.OnErrorContainer,
            background = getTimeBasedColorScheme(hour).background,
            onBackground = LiftrixColors.OnBackground,
            surface = LiftrixColors.SurfaceLight,
            onSurface = LiftrixColors.OnSurface,
            surfaceVariant = LiftrixColors.SurfaceVariant,
            onSurfaceVariant = LiftrixColors.OnSurfaceVariant,
            outline = LiftrixColors.Outline,
            outlineVariant = LiftrixColors.OutlineVariant,
            inverseSurface = LiftrixColors.InverseSurface,
            inverseOnSurface = LiftrixColors.InverseOnSurface,
            inversePrimary = LiftrixColors.InversePrimary,
        )
    }

/**
 * Create time-based color scheme for dark theme with performance optimization
 */
private fun getTimeBasedDarkColorScheme(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) = 
    PerformanceOptimizations.ThemeLoadingOptimizer.getCachedColorScheme("time_dark_$hour") {
        darkColorScheme(
            primary = getTimeBasedColorScheme(hour).primary,
            onPrimary = LiftrixColors.OnPrimary,
            primaryContainer = LiftrixColors.PrimaryContainerDark,
            onPrimaryContainer = LiftrixColors.OnPrimaryContainerDark,
            secondary = LiftrixColors.Secondary,
            onSecondary = LiftrixColors.OnSecondary,
            secondaryContainer = LiftrixColors.SecondaryContainerDark,
            onSecondaryContainer = LiftrixColors.OnSecondaryContainerDark,
            tertiary = getTimeBasedColorScheme(hour).accent,
            onTertiary = LiftrixColors.OnAccent,
            tertiaryContainer = LiftrixColors.TertiaryContainerDark,
            onTertiaryContainer = LiftrixColors.OnTertiaryContainerDark,
            error = LiftrixColors.Error,
            onError = LiftrixColors.OnError,
            errorContainer = LiftrixColors.ErrorContainerDark,
            onErrorContainer = LiftrixColors.OnErrorContainerDark,
            background = if (hour in 0..5) LiftrixColors.TimeBasedColors.NightBackground else LiftrixColors.BackgroundDark,
            onBackground = LiftrixColors.OnBackgroundDark,
            surface = LiftrixColors.SurfaceDark,
            onSurface = LiftrixColors.OnSurfaceDark,
            surfaceVariant = LiftrixColors.SurfaceVariantDark,
            onSurfaceVariant = LiftrixColors.OnSurfaceVariantDark,
            outline = LiftrixColors.OutlineDark,
            outlineVariant = LiftrixColors.OutlineVariantDark,
            inverseSurface = LiftrixColors.InverseSurfaceDark,
            inverseOnSurface = LiftrixColors.InverseOnSurfaceDark,
            inversePrimary = LiftrixColors.InversePrimaryDark,
        )
    }

/**
 * Enhanced Liftrix theme with performance optimization and fast transitions
 * Now supports theme state management with persistence and 60fps monitoring
 */
@Composable
fun LiftrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default to use Liftrix brand colors
    themeManager: ThemeManager? = null, // Optional theme manager for state management
    content: @Composable () -> Unit
) {
    // Performance monitoring for theme rendering
    PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
        key = "LiftrixTheme"
    ) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val context = LocalContext.current
        
        // Use ThemeManager if provided, otherwise use defaults
        val effectiveThemeManager = themeManager ?: ThemeManager.getInstance(context)
        val themeState = effectiveThemeManager.getCurrentThemeState()
        
        // Determine if dark theme should be used
        val shouldUseDarkTheme = when (themeState.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> darkTheme
            ThemeMode.TIME_BASED -> darkTheme // Respect system setting for time-based
        }
        
        // Performance-optimized color scheme selection with caching
        val targetColorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val dynamicKey = "dynamic_${if (shouldUseDarkTheme) "dark" else "light"}"
                PerformanceOptimizations.ThemeLoadingOptimizer.getCachedColorScheme(dynamicKey) {
                    if (shouldUseDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
            }
            
            themeState.mode == ThemeMode.TIME_BASED || themeState.timeBasedEnabled -> {
                if (shouldUseDarkTheme) getTimeBasedDarkColorScheme(currentHour) else getTimeBasedLightColorScheme(currentHour)
            }
            
            shouldUseDarkTheme -> DarkColorScheme
            
            else -> LightColorScheme
        }
        
        val colorScheme = if (themeState.fastTransitionsEnabled) {
            animatedColorScheme(targetColorScheme, ThemeUtils.fastThemeTransition)
        } else {
            targetColorScheme
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = EnhancedTypography,
            content = content
        )
    }
}

/**
 * Legacy LiftrixTheme overload for backward compatibility
 */
@Composable
fun LiftrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    timeBasedColors: Boolean = false,
    fastTransitions: Boolean = true,
    content: @Composable () -> Unit
) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    
    val targetColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        
        themeMode == ThemeMode.TIME_BASED || timeBasedColors -> {
            if (darkTheme) getTimeBasedDarkColorScheme(currentHour) else getTimeBasedLightColorScheme(currentHour)
        }
        
        themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && darkTheme) -> DarkColorScheme
        
        else -> LightColorScheme
    }
    
    val colorScheme = if (fastTransitions) {
        animatedColorScheme(targetColorScheme, ThemeUtils.fastThemeTransition)
    } else {
        targetColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EnhancedTypography,
        content = content
    )
}

/**
 * Get light color scheme - for components that need direct access
 */
fun getLightColorScheme() = LightColorScheme

/**
 * Get dark color scheme - for components that need direct access
 */
fun getDarkColorScheme() = DarkColorScheme

/**
 * Get time-based color scheme with specified parameters
 */
fun getTimeBasedColorScheme(hour: Int, isDark: Boolean = false) = 
    if (isDark) getTimeBasedDarkColorScheme(hour) else getTimeBasedLightColorScheme(hour)