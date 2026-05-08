package com.example.liftrix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeUtils {
    
    /**
     * Animation spec for fast theme transitions
     */
    val fastThemeTransition = tween<Color>(
        durationMillis = 100,
        delayMillis = 0
    )
    
    /**
     * Spring animation spec for smooth color transitions
     */
    val smoothColorTransition = spring<Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /**
     * Standard theme transition spec
     */
    val standardThemeTransition = tween<Color>(
        durationMillis = 150
    )
}

/**
 * Theme mode enum for managing different theme states
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    TIME_BASED  // Uses time-based color adaptations
}

/**
 * Animated color scheme that smoothly transitions between themes
 */
@Composable
fun animatedColorScheme(
    targetScheme: ColorScheme,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Color> = ThemeUtils.fastThemeTransition
): ColorScheme {
    val primary by animateColorAsState(
        targetValue = targetScheme.primary,
        animationSpec = animationSpec,
        label = "primary"
    )
    val onPrimary by animateColorAsState(
        targetValue = targetScheme.onPrimary,
        animationSpec = animationSpec,
        label = "onPrimary"
    )
    val primaryContainer by animateColorAsState(
        targetValue = targetScheme.primaryContainer,
        animationSpec = animationSpec,
        label = "primaryContainer"
    )
    val onPrimaryContainer by animateColorAsState(
        targetValue = targetScheme.onPrimaryContainer,
        animationSpec = animationSpec,
        label = "onPrimaryContainer"
    )
    val secondary by animateColorAsState(
        targetValue = targetScheme.secondary,
        animationSpec = animationSpec,
        label = "secondary"
    )
    val onSecondary by animateColorAsState(
        targetValue = targetScheme.onSecondary,
        animationSpec = animationSpec,
        label = "onSecondary"
    )
    val secondaryContainer by animateColorAsState(
        targetValue = targetScheme.secondaryContainer,
        animationSpec = animationSpec,
        label = "secondaryContainer"
    )
    val onSecondaryContainer by animateColorAsState(
        targetValue = targetScheme.onSecondaryContainer,
        animationSpec = animationSpec,
        label = "onSecondaryContainer"
    )
    val tertiary by animateColorAsState(
        targetValue = targetScheme.tertiary,
        animationSpec = animationSpec,
        label = "tertiary"
    )
    val onTertiary by animateColorAsState(
        targetValue = targetScheme.onTertiary,
        animationSpec = animationSpec,
        label = "onTertiary"
    )
    val tertiaryContainer by animateColorAsState(
        targetValue = targetScheme.tertiaryContainer,
        animationSpec = animationSpec,
        label = "tertiaryContainer"
    )
    val onTertiaryContainer by animateColorAsState(
        targetValue = targetScheme.onTertiaryContainer,
        animationSpec = animationSpec,
        label = "onTertiaryContainer"
    )
    val error by animateColorAsState(
        targetValue = targetScheme.error,
        animationSpec = animationSpec,
        label = "error"
    )
    val onError by animateColorAsState(
        targetValue = targetScheme.onError,
        animationSpec = animationSpec,
        label = "onError"
    )
    val errorContainer by animateColorAsState(
        targetValue = targetScheme.errorContainer,
        animationSpec = animationSpec,
        label = "errorContainer"
    )
    val onErrorContainer by animateColorAsState(
        targetValue = targetScheme.onErrorContainer,
        animationSpec = animationSpec,
        label = "onErrorContainer"
    )
    val background by animateColorAsState(
        targetValue = targetScheme.background,
        animationSpec = animationSpec,
        label = "background"
    )
    val onBackground by animateColorAsState(
        targetValue = targetScheme.onBackground,
        animationSpec = animationSpec,
        label = "onBackground"
    )
    val surface by animateColorAsState(
        targetValue = targetScheme.surface,
        animationSpec = animationSpec,
        label = "surface"
    )
    val onSurface by animateColorAsState(
        targetValue = targetScheme.onSurface,
        animationSpec = animationSpec,
        label = "onSurface"
    )
    val surfaceVariant by animateColorAsState(
        targetValue = targetScheme.surfaceVariant,
        animationSpec = animationSpec,
        label = "surfaceVariant"
    )
    val onSurfaceVariant by animateColorAsState(
        targetValue = targetScheme.onSurfaceVariant,
        animationSpec = animationSpec,
        label = "onSurfaceVariant"
    )
    val outline by animateColorAsState(
        targetValue = targetScheme.outline,
        animationSpec = animationSpec,
        label = "outline"
    )
    val outlineVariant by animateColorAsState(
        targetValue = targetScheme.outlineVariant,
        animationSpec = animationSpec,
        label = "outlineVariant"
    )
    val scrim by animateColorAsState(
        targetValue = targetScheme.scrim,
        animationSpec = animationSpec,
        label = "scrim"
    )
    val inverseSurface by animateColorAsState(
        targetValue = targetScheme.inverseSurface,
        animationSpec = animationSpec,
        label = "inverseSurface"
    )
    val inverseOnSurface by animateColorAsState(
        targetValue = targetScheme.inverseOnSurface,
        animationSpec = animationSpec,
        label = "inverseOnSurface"
    )
    val inversePrimary by animateColorAsState(
        targetValue = targetScheme.inversePrimary,
        animationSpec = animationSpec,
        label = "inversePrimary"
    )
    val surfaceDim by animateColorAsState(
        targetValue = targetScheme.surfaceDim,
        animationSpec = animationSpec,
        label = "surfaceDim"
    )
    val surfaceBright by animateColorAsState(
        targetValue = targetScheme.surfaceBright,
        animationSpec = animationSpec,
        label = "surfaceBright"
    )
    val surfaceContainerLowest by animateColorAsState(
        targetValue = targetScheme.surfaceContainerLowest,
        animationSpec = animationSpec,
        label = "surfaceContainerLowest"
    )
    val surfaceContainerLow by animateColorAsState(
        targetValue = targetScheme.surfaceContainerLow,
        animationSpec = animationSpec,
        label = "surfaceContainerLow"
    )
    val surfaceContainer by animateColorAsState(
        targetValue = targetScheme.surfaceContainer,
        animationSpec = animationSpec,
        label = "surfaceContainer"
    )
    val surfaceContainerHigh by animateColorAsState(
        targetValue = targetScheme.surfaceContainerHigh,
        animationSpec = animationSpec,
        label = "surfaceContainerHigh"
    )
    val surfaceContainerHighest by animateColorAsState(
        targetValue = targetScheme.surfaceContainerHighest,
        animationSpec = animationSpec,
        label = "surfaceContainerHighest"
    )

    return ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest
    )
} 

/**
 * Theme manager for handling theme state management and persistence
 * Follows the same pattern as RecommendationCache for SharedPreferences usage
 */
class ThemeManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "liftrix_theme_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TIME_BASED_ENABLED = "time_based_enabled"
        private const val KEY_FAST_TRANSITIONS_ENABLED = "fast_transitions_enabled"
        private const val KEY_THEME_VERSION = "theme_version"
        
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    
    private val _timeBasedEnabled = MutableStateFlow(loadTimeBasedEnabled())
    val timeBasedEnabled: StateFlow<Boolean> = _timeBasedEnabled.asStateFlow()
    
    private val _fastTransitionsEnabled = MutableStateFlow(loadFastTransitionsEnabled())
    val fastTransitionsEnabled: StateFlow<Boolean> = _fastTransitionsEnabled.asStateFlow()
    
    private val _themeVersion = MutableStateFlow(loadThemeVersion())
    val themeVersion: StateFlow<ThemeVersion> = _themeVersion.asStateFlow()
    
    /**
     * Get the effective theme state (what's actually displayed)
     * When in SYSTEM mode, returns whether dark theme is actually active
     * When in LIGHT/DARK mode, returns the explicit setting
     * 
     * @param isSystemInDarkTheme Current system dark theme state
     * @return true if dark theme is active, false if light theme is active
     */
    fun getEffectiveThemeState(isSystemInDarkTheme: Boolean): Boolean {
        return when (_themeMode.value) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.TIME_BASED -> isSystemInDarkTheme // Respect system for time-based
        }
    }
    
    /**
     * Switch theme mode with immediate persistence
     * When toggling in SYSTEM mode, switches to explicit LIGHT/DARK based on current state
     */
    fun switchTheme(mode: ThemeMode) {
        _themeMode.value = mode
        sharedPreferences.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }
    
    /**
     * Toggle theme between light and dark
     * If in SYSTEM mode, switches to opposite of current effective theme
     * If in explicit mode, toggles to the opposite
     * 
     * @param isSystemInDarkTheme Current system dark theme state
     */
    fun toggleTheme(isSystemInDarkTheme: Boolean) {
        val newMode = when (_themeMode.value) {
            ThemeMode.SYSTEM -> {
                // When in system mode, switch to explicit mode opposite of current
                if (isSystemInDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
            }
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.TIME_BASED -> {
                // Similar to system mode handling
                if (isSystemInDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
            }
        }
        switchTheme(newMode)
    }
    
    /**
     * Toggle time-based colors with persistence
     */
    fun setTimeBasedColors(enabled: Boolean) {
        _timeBasedEnabled.value = enabled
        sharedPreferences.edit()
            .putBoolean(KEY_TIME_BASED_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Toggle fast transitions with persistence
     */
    fun setFastTransitions(enabled: Boolean) {
        _fastTransitionsEnabled.value = enabled
        sharedPreferences.edit()
            .putBoolean(KEY_FAST_TRANSITIONS_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Set theme version with persistence
     */
    fun setThemeVersion(version: ThemeVersion) {
        _themeVersion.value = version
        sharedPreferences.edit()
            .putString(KEY_THEME_VERSION, version.name)
            .apply()
    }
    
    /**
     * Get current theme state for composables
     */
    @Composable
    fun getCurrentThemeState(): ThemeState {
        val mode by themeMode.collectAsState()
        val timeBasedEnabled by timeBasedEnabled.collectAsState()
        val fastTransitionsEnabled by fastTransitionsEnabled.collectAsState()
        val themeVersion by themeVersion.collectAsState()
        
        return ThemeState(
            mode = mode,
            timeBasedEnabled = timeBasedEnabled,
            fastTransitionsEnabled = fastTransitionsEnabled,
            themeVersion = themeVersion
        )
    }
    
    private fun loadThemeMode(): ThemeMode {
        // Default to SYSTEM mode so the app respects system theme preferences
        val savedMode = sharedPreferences.getString(KEY_THEME_MODE, null)
        return if (savedMode != null) {
            try {
                ThemeMode.valueOf(savedMode)
            } catch (e: IllegalArgumentException) {
                // If saved mode is invalid, default to SYSTEM
                ThemeMode.SYSTEM
            }
        } else {
            // No saved preference - default to SYSTEM mode
            ThemeMode.SYSTEM
        }
    }
    
    private fun loadTimeBasedEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_TIME_BASED_ENABLED, false)
    }
    
    private fun loadFastTransitionsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_FAST_TRANSITIONS_ENABLED, true)
    }
    
    private fun loadThemeVersion(): ThemeVersion {
        // Always return V2 - no other options available
        return ThemeVersion.V2
    }
}

/**
 * Data class representing current theme state
 */
data class ThemeState(
    val mode: ThemeMode,
    val timeBasedEnabled: Boolean,
    val fastTransitionsEnabled: Boolean,
    val themeVersion: ThemeVersion
)

/**
 * Extension functions for theme management (V2 only)
 */
fun ThemeManager.isUsingV2Theme(): Boolean {
    return true // Always using V2
}

// REMOVED: V1 theme functions - only V2 is supported
