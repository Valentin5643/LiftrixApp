package com.example.liftrix.ui.common.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
// Chart theming for custom Canvas-based charts

/**
 * Centralized chart theme provider for Vico charts integration with Material 3 theming.
 * 
 * Provides consistent theming across all analytics visualizations following LiftrixColors
 * design system with Material 3 principles and accessibility compliance.
 * 
 * Features:
 * - Material 3 color scheme integration
 * - LiftrixColors gradient support
 * - Dark/Light theme adaptation
 * - 60fps performance optimization
 * - Accessibility-compliant contrast ratios
 * - Smooth animation configurations
 */
object ChartThemeProvider {
    
    /**
     * Creates a comprehensive Vico chart theme with Material 3 integration
     * 
     * @return CartesianChartTheme configured with LiftrixColors and Material 3 theming
     */
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
    
    /**
     * Creates line chart theme with LiftrixColors primary gradient
     */
    @Composable
    fun createLineLayerTheme(): LineChartTheme {
        return LineChartTheme(
            lineColor = LiftrixColorsV2.Teal,
            lineWidth = 3.dp,
            pointColor = LiftrixColorsV2.Teal,
            pointSize = 8.dp,
            areaFillColor = LiftrixColorsV2.Teal.copy(alpha = 0.2f)
        )
    }
    
    /**
     * Creates column/bar chart theme with LiftrixColors theming
     */
    @Composable
    fun createColumnLayerTheme(): ColumnChartTheme {
        return ColumnChartTheme(
            columnColor = LiftrixColorsV2.Teal,
            columnWidth = 16.dp,
            cornerRadius = 6.dp
        )
    }
    
    /**
     * Creates axis theme with Material 3 typography and colors
     */
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
    
    /**
     * Creates LiftrixColors gradient shader for line charts
     */
    private fun createLiftrixGradientShader(): Color {
        return LiftrixColorsV2.Teal
    }
    
    /**
     * Creates LiftrixColors area gradient shader for filled areas
     */
    private fun createLiftrixAreaGradientShader(): Color {
        return LiftrixColorsV2.Teal.copy(alpha = 0.2f)
    }
    
    /**
     * Creates heatmap-specific color scheme for frequency visualization
     */
    @Composable
    fun createHeatMapColorScheme(): HeatMapColorScheme {
        return HeatMapColorScheme(
            noDataColor = MaterialTheme.colorScheme.surface,
            lowIntensityColor = LiftrixColorsV2.Teal.copy(alpha = 0.2f),
            mediumIntensityColor = LiftrixColorsV2.Teal.copy(alpha = 0.6f),
            highIntensityColor = LiftrixColorsV2.Teal,
            strokeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            textColor = MaterialTheme.colorScheme.onSurface
        )
    }
    
    /**
     * Creates radial progress chart theme
     */
    @Composable
    fun createRadialProgressTheme(): RadialProgressTheme {
        val colorScheme = MaterialTheme.colorScheme
        
        return RadialProgressTheme(
            progressColor = LiftrixColorsV2.Teal,
            trackColor = colorScheme.outline.copy(alpha = 0.2f),
            backgroundColor = colorScheme.surface,
            strokeWidth = 8.dp,
            animationDuration = 1000,
            centerTextColor = colorScheme.onSurface,
            centerSubtextColor = colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
    
    /**
     * Creates multi-line chart theme for strength progression
     */
    @Composable
    fun createMultiLineChartTheme(): MultiLineChartTheme {
        return MultiLineChartTheme(
            primaryLineColor = LiftrixColorsV2.Teal,
            secondaryLineColor = LiftrixColorsV2.TealHover,
            tertiaryLineColor = LiftrixColorsV2.TealLight,
            strokeWidth = 2.5f,
            pointSize = 6f,
            animationDuration = 800,
            enableSmoothCurves = true
        )
    }
    
    /**
     * Performance-optimized chart configuration
     */
    @Composable
    fun createPerformanceOptimizedTheme(): ChartPerformanceConfig {
        return ChartPerformanceConfig(
            targetFps = 60,
            enableHardwareAcceleration = true,
            maxDataPoints = 100,
            animationInterpolator = AnimationInterpolator.EASE_OUT,
            enableDataPointCaching = true,
            renderingMode = RenderingMode.HARDWARE
        )
    }
    
    /**
     * Accessibility-compliant chart configuration
     */
    @Composable
    fun createAccessibilityConfig(): AccessibilityConfig {
        val colorScheme = MaterialTheme.colorScheme
        
        return AccessibilityConfig(
            minimumTouchTargetSize = 48.dp,
            contentDescriptionEnabled = true,
            highContrastMode = false, // Can be enabled based on system settings
            labelTextSize = 14f,
            enableVibration = true,
            focusColor = colorScheme.primary,
            enableSonification = false // Audio feedback for data points
        )
    }
}

/**
 * Chart helper utilities
 */
private object ChartUtils {
    fun createColorTheme(primaryColor: Color, secondaryColor: Color): Map<String, Color> {
        return mapOf(
            "primary" to primaryColor,
            "secondary" to secondaryColor
        )
    }
}

/**
 * Heatmap color scheme configuration
 */
data class HeatMapColorScheme(
    val noDataColor: Color,
    val lowIntensityColor: Color,
    val mediumIntensityColor: Color,
    val highIntensityColor: Color,
    val strokeColor: Color,
    val textColor: Color
)

/**
 * Radial progress chart theme configuration
 */
data class RadialProgressTheme(
    val progressColor: Color,
    val trackColor: Color,
    val backgroundColor: Color,
    val strokeWidth: androidx.compose.ui.unit.Dp,
    val animationDuration: Int,
    val centerTextColor: Color,
    val centerSubtextColor: Color
)

/**
 * Multi-line chart theme configuration
 */
data class MultiLineChartTheme(
    val primaryLineColor: Color,
    val secondaryLineColor: Color,
    val tertiaryLineColor: Color,
    val strokeWidth: Float,
    val pointSize: Float,
    val animationDuration: Int,
    val enableSmoothCurves: Boolean
)

/**
 * Performance optimization configuration
 */
data class ChartPerformanceConfig(
    val targetFps: Int,
    val enableHardwareAcceleration: Boolean,
    val maxDataPoints: Int,
    val animationInterpolator: AnimationInterpolator,
    val enableDataPointCaching: Boolean,
    val renderingMode: RenderingMode
)

/**
 * Animation interpolator types
 */
enum class AnimationInterpolator {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BOUNCE
}

/**
 * Chart rendering modes
 */
enum class RenderingMode {
    SOFTWARE,
    HARDWARE
}

/**
 * Liftrix chart theme configuration
 */
data class LiftrixChartTheme(
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val surfaceColor: Color,
    val onSurfaceColor: Color,
    val outlineColor: Color,
    val lineWidth: androidx.compose.ui.unit.Dp,
    val pointSize: androidx.compose.ui.unit.Dp,
    val columnWidth: androidx.compose.ui.unit.Dp,
    val cornerRadius: androidx.compose.ui.unit.Dp
)

/**
 * Line chart specific theme
 */
data class LineChartTheme(
    val lineColor: Color,
    val lineWidth: androidx.compose.ui.unit.Dp,
    val pointColor: Color,
    val pointSize: androidx.compose.ui.unit.Dp,
    val areaFillColor: Color
)

/**
 * Column chart specific theme
 */
data class ColumnChartTheme(
    val columnColor: Color,
    val columnWidth: androidx.compose.ui.unit.Dp,
    val cornerRadius: androidx.compose.ui.unit.Dp
)

/**
 * Axis theme configuration
 */
data class AxisTheme(
    val lineColor: Color,
    val labelColor: Color,
    val tickColor: Color,
    val gridColor: Color
)

/**
 * Accessibility configuration
 */
data class AccessibilityConfig(
    val minimumTouchTargetSize: androidx.compose.ui.unit.Dp,
    val contentDescriptionEnabled: Boolean,
    val highContrastMode: Boolean,
    val labelTextSize: Float,
    val enableVibration: Boolean,
    val focusColor: Color,
    val enableSonification: Boolean
)