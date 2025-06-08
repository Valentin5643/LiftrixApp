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
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
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

private val DarkColorScheme = darkColorScheme(
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

@Composable
fun LiftrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled by default to use Liftrix brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}