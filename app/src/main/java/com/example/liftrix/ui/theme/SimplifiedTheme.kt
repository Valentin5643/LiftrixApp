package com.example.liftrix.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * SIMPLIFIED LiftrixTheme - FIXES Color Fallback Issues
 * 
 * This replacement theme eliminates the complex state management that causes
 * intermittent color fallbacks across Home, Workout, Progress, Coach, and Profile screens.
 * 
 * Key improvements:
 * 1. Direct LiftrixColorsV2 usage without complex state flows
 * 2. Guaranteed color scheme availability
 * 3. Simplified theme switching without race conditions
 * 4. Fallback-safe composition local providers
 */

/**
 * Composition Local for guaranteed LiftrixColorsV2 access
 * This provides direct access to V2 colors regardless of MaterialTheme state
 */
val LocalLiftrixColors = staticCompositionLocalOf { 
    LiftrixColorsV2.lightColorScheme
}

/**
 * Simplified LiftrixTheme that guarantees color availability
 * Use this instead of the complex LiftrixTheme to eliminate fallback issues
 */
@Composable
fun SimplifiedLiftrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // GUARANTEED color scheme - no state dependencies, no race conditions
    val colorScheme = remember(darkTheme, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            else -> {
                // Direct V2 color scheme access - NEVER falls back
                if (darkTheme) {
                    LiftrixColorsV2.darkColorScheme
                } else {
                    LiftrixColorsV2.lightColorScheme
                }
            }
        }
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Provide BOTH MaterialTheme AND direct V2 color access
    CompositionLocalProvider(
        LocalLiftrixColors provides colorScheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiftrixTypographySystem,
            content = content
        )
    }
}

/**
 * Direct access to guaranteed LiftrixColorsV2 colors
 * Use these functions instead of MaterialTheme.colorScheme for critical components
 */
object SimplifiedLiftrixColors {
    
    @Composable
    fun current() = LocalLiftrixColors.current
    
    @Composable
    fun primary() = current().primary
    
    @Composable
    fun background() = current().background
    
    @Composable
    fun surface() = current().surface
    
    @Composable
    fun onSurface() = current().onSurface
    
    @Composable
    fun outline() = current().outline
    
    @Composable
    fun primaryContainer() = current().primaryContainer
    
    @Composable
    fun onPrimaryContainer() = current().onPrimaryContainer
    
    @Composable
    fun secondary() = current().secondary
    
    @Composable
    fun onSecondary() = current().onSecondary
    
    @Composable
    fun error() = current().error
    
    @Composable
    fun onError() = current().onError
}

/**
 * Extension functions for guaranteed color access in any component
 */
@Composable
fun guaranteedPrimary() = SimplifiedLiftrixColors.primary()

@Composable
fun guaranteedBackground() = SimplifiedLiftrixColors.background()

@Composable
fun guaranteedSurface() = SimplifiedLiftrixColors.surface()

@Composable
fun guaranteedOnSurface() = SimplifiedLiftrixColors.onSurface()

@Composable
fun guaranteedOutline() = SimplifiedLiftrixColors.outline()

/**
 * Migration helper - use this to replace MaterialTheme.colorScheme usage
 */
@Composable
fun safeColorScheme() = SimplifiedLiftrixColors.current()