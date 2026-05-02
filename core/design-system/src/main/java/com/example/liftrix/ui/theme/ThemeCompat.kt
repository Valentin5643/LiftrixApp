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
import androidx.compose.ui.unit.dp

object LiftrixColors {
    val Primary = LiftrixColorsV2.Teal
    val Secondary = LiftrixColorsV2.TealHover
    val TiffanyBlue = LiftrixColorsV2.Teal
    val PersianGreen = LiftrixColorsV2.TealDark
    val Snow = Color(0xFFFFFAFA)
    val SurfaceLight = Color(0xFFF7F9FA)
    val OnSurface = Color(0xFF1D1B20)
    val Error = Color(0xFFBA1A1A)
    val PrimaryContainer = LiftrixColorsV2.TealSurface
    val TertiaryContainer = LiftrixColorsV2.TealLight.copy(alpha = 0.2f)
    val Night = Color(0xFF111418)
    val Jet = Color(0xFF2D3136)
}

enum class ThemeVersion {
    V1,
    V2
}

val PrimaryGradient: Brush = Brush.linearGradient(
    colors = listOf(LiftrixColorsV2.Teal, LiftrixColorsV2.TealHover)
)

object LiftrixAnimations {
    const val durationFast = 150
    const val durationMedium = 300
    const val durationSlow = 500
    val bouncySpring: SpringSpec<Float> = spring()
    val microInteractionSpec: TweenSpec<Float> = tween(durationMillis = durationFast)
    val standardTransitionSpec: TweenSpec<Float> = tween(durationMillis = durationMedium)
    val fastTransitionSpec: TweenSpec<Float> = tween(durationMillis = durationFast)
    val standardSpring: SpringSpec<Float> = spring()
}

object LiftrixTokens {
    object TouchTarget {
        val IconSmall = 16.dp
        val IconMedium = 24.dp
        val IconLarge = 32.dp
        val Minimum = 48.dp
    }

    object SemanticColors {
        val Success = Color(0xFF2E7D32)
        val Error = Color(0xFFBA1A1A)
        val Warning = Color(0xFFFFA000)
        val Info = Color(0xFF1976D2)
    }

    object CornerRadius {
        val Medium = 12.dp
        val Large = 16.dp
    }

    object Elevation {
        object Level1 { const val value = 1f }
        object Level4 { const val value = 8f }
        object Level5 { const val value = 12f }
    }
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
