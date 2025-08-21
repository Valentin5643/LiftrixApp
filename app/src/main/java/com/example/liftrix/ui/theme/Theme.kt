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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.feature.FeatureFlagManager
import com.example.liftrix.ui.common.PerformanceOptimizations
import timber.log.Timber
import java.util.Calendar

/**
 * Theme version enumeration - maintained for backward compatibility
 */
enum class ThemeVersion {
    V1,  // Legacy 5-color system (mapped to V2 colors)
    V2   // Modern Teal-based system
}

// Performance-optimized color scheme creation with V2 system optimizations
// Uses ColorSystemOptimizations for 20%+ performance improvement in theme switching
private val LightColorScheme = ColorSystemOptimizations.getColorScheme(false)
private val DarkColorScheme = ColorSystemOptimizations.getColorScheme(true)



/**
 * Enhanced Liftrix theme with performance optimization, fast transitions, and production rollout support
 * Now supports theme state management with persistence, 60fps monitoring, and feature flag integration
 * 
 * FIXED: Resolves intermittent color fallback issues during auth/settings navigation
 */
@Composable
fun LiftrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default to use Liftrix brand colors
    themeVersion: ThemeVersion = ThemeVersion.V2, // Default to V2 theme with Teal colors
    themeManager: ThemeManager? = null, // Optional theme manager for state management
    featureFlagManager: FeatureFlagManager? = null, // Optional feature flag manager for UI redesign rollout
    userId: String? = null, // User ID for feature flag evaluation
    appVersionCode: Int? = null, // App version for feature flag compatibility
    content: @Composable () -> Unit
) {
    // Performance monitoring for theme rendering
    PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
        key = "LiftrixTheme"
    ) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val context = LocalContext.current
        
        // Feature flag state for UI redesign rollout
        var uiRedesignEnabled by remember { mutableStateOf(false) }
        var featureFlagEvaluated by remember { mutableStateOf(false) }
        
        // Evaluate feature flag if manager and user info provided
        LaunchedEffect(featureFlagManager, userId, appVersionCode) {
            if (featureFlagManager != null && userId != null && appVersionCode != null) {
                try {
                    val result = featureFlagManager.isUiRedesignEnabled(userId, appVersionCode)
                    if (result.isSuccess) {
                        uiRedesignEnabled = result.getOrDefault(false)
                        featureFlagEvaluated = true
                        Timber.d("UI redesign feature flag evaluated for user $userId: $uiRedesignEnabled")
                    } else {
                        uiRedesignEnabled = false // Safe fallback
                        featureFlagEvaluated = true
                        Timber.w("Feature flag evaluation failed for user $userId, defaulting to disabled")
                    }
                } catch (exception: Exception) {
                    uiRedesignEnabled = false // Safe fallback
                    featureFlagEvaluated = true
                    Timber.e(exception, "Exception during feature flag evaluation")
                }
            } else {
                // No feature flag manager or user info - default to current behavior
                uiRedesignEnabled = false
                featureFlagEvaluated = true
            }
        }
        
        // Use ThemeManager if provided, otherwise use defaults
        val effectiveThemeManager = themeManager ?: ThemeManager.getInstance(context)
        
        // Observe theme state changes reactively
        val themeMode by effectiveThemeManager.themeMode.collectAsState()
        val timeBasedEnabled by effectiveThemeManager.timeBasedEnabled.collectAsState()
        val fastTransitionsEnabled by effectiveThemeManager.fastTransitionsEnabled.collectAsState()
        val themeVersionFromManager by effectiveThemeManager.themeVersion.collectAsState()
        
        // Create theme state from observed values
        val themeState = ThemeState(
            mode = themeMode,
            timeBasedEnabled = timeBasedEnabled,
            fastTransitionsEnabled = fastTransitionsEnabled,
            themeVersion = themeVersionFromManager
        )
        
        // Determine if dark theme should be used
        val shouldUseDarkTheme = when (themeState.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> darkTheme
            ThemeMode.TIME_BASED -> darkTheme // Respect system setting for time-based
        }
        
        // Start theme switching performance monitoring
        LaunchedEffect(shouldUseDarkTheme) {
            ColorSystemOptimizations.ThemePerformanceMonitor.startThemeSwitching()
        }
        
        // FIXED: Stable V2 color scheme selection with guaranteed fallback safety
        val targetColorScheme = try {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val dynamicKey = "dynamic_${if (shouldUseDarkTheme) "dark" else "light"}_V2_redesign_${uiRedesignEnabled}"
                    PerformanceOptimizations.ThemeLoadingOptimizer.getCachedColorScheme(dynamicKey) {
                        if (shouldUseDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                }
                
                else -> {
                    // Always use V2 color system with stable fallback
                    val baseColorScheme = StableColorProvider.getStableColorScheme(shouldUseDarkTheme)
                    if (uiRedesignEnabled && featureFlagEvaluated) {
                        enhanceColorSchemeForRedesignV2(baseColorScheme, shouldUseDarkTheme)
                    } else {
                        baseColorScheme
                    }
                }
            }
        } catch (exception: Exception) {
            // CRITICAL FIX: Always provide stable V2 fallback for any theme resolution failures
            Timber.e(exception, "Theme resolution failed, using stable V2 fallback")
            StableColorProvider.logColorFallback("theme_resolution_exception", "LiftrixTheme")
            StableColorProvider.getStableColorScheme(shouldUseDarkTheme)
        }
        
        // End theme switching performance monitoring
        LaunchedEffect(targetColorScheme) {
            ColorSystemOptimizations.ThemePerformanceMonitor.endThemeSwitching()
        }
        
        val colorScheme = if (themeState.fastTransitionsEnabled) {
            animatedColorScheme(targetColorScheme, ThemeUtils.fastThemeTransition)
        } else {
            targetColorScheme
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiftrixTypographySystem,
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
            if (darkTheme) DarkColorScheme else LightColorScheme
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
        typography = LiftrixTypographySystem,
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
 * Get V2 light color scheme - for components that need direct access
 */
fun getLightColorSchemeV2() = LiftrixColorsV2.lightColorScheme

/**
 * Get V2 dark color scheme - for components that need direct access
 */
fun getDarkColorSchemeV2() = LiftrixColorsV2.darkColorScheme


// REMOVED: Old V1 enhancement function (enhanceColorSchemeForRedesign)
// Only V2 enhancement function remains

/**
 * Enhance V2 color scheme for UI redesign with improved accessibility and visual hierarchy.
 * This function applies the redesigned color enhancements when the UI redesign feature flag is active.
 * 
 * @param baseColorScheme The V2 base color scheme to enhance
 * @param isDarkTheme Whether this is for dark theme
 * @return Enhanced V2 color scheme with redesign improvements
 */
private fun enhanceColorSchemeForRedesignV2(
    baseColorScheme: androidx.compose.material3.ColorScheme,
    isDarkTheme: Boolean
): androidx.compose.material3.ColorScheme {
    return baseColorScheme.copy(
        // Enhanced primary colors with Teal consistency
        primary = LiftrixColorsV2.Teal,
        onPrimary = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary,
        primaryContainer = if (isDarkTheme) LiftrixColorsV2.TealContainer else LiftrixColorsV2.TealSurface,
        onPrimaryContainer = if (isDarkTheme) LiftrixColorsV2.TealLight else LiftrixColorsV2.TealDark,
        
        // Enhanced secondary colors with proper contrast
        secondary = LiftrixColorsV2.TealHover,
        onSecondary = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary,
        secondaryContainer = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundTertiary else LiftrixColorsV2.Light.BackgroundTertiary,
        onSecondaryContainer = if (isDarkTheme) LiftrixColorsV2.TealLight else LiftrixColorsV2.TealDark,
        
        // Enhanced tertiary system using complementary colors
        tertiary = LiftrixColorsV2.DataViz.Series4, // Green for variety
        onTertiary = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary,
        
        // Improved surface colors for better component distinction
        surface = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary,
        onSurface = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
        surfaceVariant = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary,
        onSurfaceVariant = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary,
        
        // Enhanced outline colors using Teal for brand consistency
        outline = LiftrixColorsV2.Teal.copy(alpha = if (isDarkTheme) 0.60f else 0.38f),
        outlineVariant = LiftrixColorsV2.Teal.copy(alpha = if (isDarkTheme) 0.24f else 0.12f)
    )
}