package com.example.liftrix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class ThemeVersion {
    V1,
    V2
}

val PrimaryGradient: Brush = Brush.linearGradient(
    colors = listOf(LiftrixColorsV2.Teal, LiftrixColorsV2.TealHover)
)

object LiftrixAnimations {
    const val FAST = 150
    const val STANDARD = 300
    const val durationFast = 150
    const val durationMedium = 300
    const val durationSlow = 500
    val bouncySpring: SpringSpec<Float> = spring()
    val athleticMicroSpring: SpringSpec<Float> = spring()
    val athleticEntranceSpring: SpringSpec<Float> = spring()
    val athleticScreenTransitionSpec: SpringSpec<Float> = spring()
    val athleticModalSpec: SpringSpec<Float> = spring()
    val exitSpec: TweenSpec<Float> = tween(durationMillis = FAST)
    val microInteractionSpec: TweenSpec<Float> = tween(durationMillis = durationFast)
    val fastColorTransitionSpec: TweenSpec<Color> = tween(durationMillis = durationFast)
    val standardTransitionSpec: TweenSpec<Float> = tween(durationMillis = durationMedium)
    val fastTransitionSpec: TweenSpec<Float> = tween(durationMillis = durationFast)
    val standardSpring: SpringSpec<Float> = spring()
}

val CardElevationGradient: Brush = LiftrixColorsV2.Gradients.CardElevationGradientLight

object DesignSystemAccessibilityColors {
    fun Color.luminance(): Float = (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
}

@Composable
fun LiftrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = LiftrixColorsV2.Teal,
                secondary = LiftrixColorsV2.TealHover
            )
        } else {
            lightColorScheme(
                primary = LiftrixColorsV2.Teal,
                secondary = LiftrixColorsV2.TealHover
            )
        },
        content = content
    )
}
