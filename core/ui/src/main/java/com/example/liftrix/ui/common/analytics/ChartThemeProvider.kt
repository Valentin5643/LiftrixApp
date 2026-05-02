package com.example.liftrix.ui.common.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2

object ChartThemeProvider {
    @Composable
    fun createLiftrixChartTheme(): LiftrixChartTheme {
        val colorScheme = MaterialTheme.colorScheme
        return remember(colorScheme) {
            LiftrixChartTheme(
                primaryColor = LiftrixColorsV2.Teal,
                secondaryColor = LiftrixColorsV2.TealHover,
                tertiaryColor = LiftrixColorsV2.TealLight,
                surfaceColor = colorScheme.surface,
                onSurfaceColor = colorScheme.onSurface,
                outlineColor = colorScheme.outline,
                lineWidth = 3.dp,
                pointSize = 8.dp,
                columnWidth = 16.dp,
                cornerRadius = 6.dp
            )
        }
    }

    @Composable
    fun createLineLayerTheme(): LineChartTheme = LineChartTheme(
        lineColor = LiftrixColorsV2.Teal,
        lineWidth = 3.dp,
        pointColor = LiftrixColorsV2.Teal,
        pointSize = 8.dp,
        areaFillColor = LiftrixColorsV2.Teal.copy(alpha = 0.2f)
    )

    @Composable
    fun createAxisTheme(): AxisTheme {
        val colorScheme = MaterialTheme.colorScheme
        return AxisTheme(
            lineColor = colorScheme.outline,
            labelColor = colorScheme.onSurface,
            tickColor = colorScheme.outline,
            gridColor = colorScheme.outline.copy(alpha = 0.3f)
        )
    }

    @Composable
    fun createHeatMapColorScheme(): HeatMapColorScheme = HeatMapColorScheme(
        noDataColor = MaterialTheme.colorScheme.surface,
        lowIntensityColor = LiftrixColorsV2.Teal.copy(alpha = 0.2f),
        mediumIntensityColor = LiftrixColorsV2.Teal.copy(alpha = 0.6f),
        highIntensityColor = LiftrixColorsV2.Teal,
        strokeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        textColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun createMultiLineChartTheme(): MultiLineChartTheme = MultiLineChartTheme(
        primaryLineColor = LiftrixColorsV2.Teal,
        secondaryLineColor = LiftrixColorsV2.TealHover,
        tertiaryLineColor = LiftrixColorsV2.TealLight,
        strokeWidth = 2.5f,
        pointSize = 6f,
        animationDuration = 800,
        enableSmoothCurves = true
    )

    @Composable
    fun createPerformanceOptimizedTheme(): ChartPerformanceConfig = ChartPerformanceConfig(
        targetFps = 60,
        enableHardwareAcceleration = true,
        maxDataPoints = 100,
        animationInterpolator = AnimationInterpolator.EASE_OUT,
        enableDataPointCaching = true,
        renderingMode = RenderingMode.HARDWARE
    )

    @Composable
    fun createAccessibilityConfig(): AccessibilityConfig {
        val colorScheme = MaterialTheme.colorScheme
        return AccessibilityConfig(
            minimumTouchTargetSize = 48.dp,
            contentDescriptionEnabled = true,
            highContrastMode = false,
            labelTextSize = 14f,
            enableVibration = true,
            focusColor = colorScheme.primary,
            enableSonification = false
        )
    }
}

data class LiftrixChartTheme(
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val surfaceColor: Color,
    val onSurfaceColor: Color,
    val outlineColor: Color,
    val lineWidth: Dp,
    val pointSize: Dp,
    val columnWidth: Dp,
    val cornerRadius: Dp
)

data class LineChartTheme(
    val lineColor: Color,
    val lineWidth: Dp,
    val pointColor: Color,
    val pointSize: Dp,
    val areaFillColor: Color
)

data class AxisTheme(
    val lineColor: Color,
    val labelColor: Color,
    val tickColor: Color,
    val gridColor: Color
)

data class HeatMapColorScheme(
    val noDataColor: Color,
    val lowIntensityColor: Color,
    val mediumIntensityColor: Color,
    val highIntensityColor: Color,
    val strokeColor: Color,
    val textColor: Color
)

data class MultiLineChartTheme(
    val primaryLineColor: Color,
    val secondaryLineColor: Color,
    val tertiaryLineColor: Color,
    val strokeWidth: Float,
    val pointSize: Float,
    val animationDuration: Int,
    val enableSmoothCurves: Boolean
)

data class ChartPerformanceConfig(
    val targetFps: Int,
    val enableHardwareAcceleration: Boolean,
    val maxDataPoints: Int,
    val animationInterpolator: AnimationInterpolator,
    val enableDataPointCaching: Boolean,
    val renderingMode: RenderingMode
)

enum class AnimationInterpolator {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BOUNCE
}

enum class RenderingMode {
    SOFTWARE,
    HARDWARE
}

data class AccessibilityConfig(
    val minimumTouchTargetSize: Dp,
    val contentDescriptionEnabled: Boolean,
    val highContrastMode: Boolean,
    val labelTextSize: Float,
    val enableVibration: Boolean,
    val focusColor: Color,
    val enableSonification: Boolean
)
