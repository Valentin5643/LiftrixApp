package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * ChartGradients - Centralized gradient definitions for Liftrix charts
 *
 * Provides reusable gradient shaders optimized for chart visualizations:
 * - Persian Green/Tiffany Blue gradient system compliance
 * - Performance-optimized gradient caching
 * - Accessibility-compliant color combinations
 * - Mobile-optimized rendering with hardware acceleration
 * - Dark theme gradient variants
 */
@Immutable
object ChartGradients {

    /**
     * Primary chart area gradients using Persian Green
     */
    @Stable
    object Primary {
        /**
         * Main area fill gradient - Persian Green fade to transparent
         */
        val areaFill: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.PersianGreen.copy(alpha = 0.35f),
                LiftrixColors.PersianGreen.copy(alpha = 0.15f),
                LiftrixColors.PersianGreen.copy(alpha = 0.05f),
                Color.Transparent
            ),
            startY = 0f,
            endY = Float.POSITIVE_INFINITY
        )

        /**
         * Linear progression gradient - Persian Green to Tiffany Blue
         */
        val linearProgression: Brush = Brush.linearGradient(
            colors = listOf(
                LiftrixColors.PersianGreen,
                LiftrixColors.TiffanyBlue
            )
        )

        /**
         * Radial highlight gradient for data point emphasis
         */
        val radialHighlight: Brush = Brush.radialGradient(
            colors = listOf(
                LiftrixColors.PersianGreen.copy(alpha = 0.6f),
                LiftrixColors.PersianGreen.copy(alpha = 0.3f),
                Color.Transparent
            ),
            radius = 40f
        )

        /**
         * Subtle background wash - very light Persian Green tint
         */
        val backgroundWash: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.PersianGreen.copy(alpha = 0.02f),
                Color.Transparent,
                LiftrixColors.PersianGreen.copy(alpha = 0.01f)
            )
        )
    }

    /**
     * Secondary chart gradients using Tiffany Blue
     */
    @Stable
    object Secondary {
        /**
         * Secondary area fill - Tiffany Blue fade
         */
        val areaFill: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue.copy(alpha = 0.3f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.12f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.04f),
                Color.Transparent
            )
        )

        /**
         * Reverse linear progression - Tiffany Blue to Persian Green
         */
        val reverseProgression: Brush = Brush.linearGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue,
                LiftrixColors.PersianGreen
            )
        )

        /**
         * Soft radial glow for secondary emphasis
         */
        val softGlow: Brush = Brush.radialGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue.copy(alpha = 0.4f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.2f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.08f),
                Color.Transparent
            ),
            radius = 60f
        )
    }

    /**
     * Accent gradients using Jet for subtle differentiation
     */
    @Stable
    object Accent {
        /**
         * Neutral area fill using Jet
         */
        val neutralFill: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.Jet.copy(alpha = 0.25f),
                LiftrixColors.Jet.copy(alpha = 0.10f),
                LiftrixColors.Jet.copy(alpha = 0.03f),
                Color.Transparent
            )
        )

        /**
         * Grid line gradient for subtle chart grids
         */
        val gridLines: Brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                LiftrixColors.Jet.copy(alpha = 0.08f),
                Color.Transparent
            )
        )
    }

    /**
     * Dark theme gradient variants
     */
    @Stable
    object Dark {
        /**
         * Primary area fill for dark theme
         */
        val primaryAreaFill: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.PersianGreen.copy(alpha = 0.4f),
                LiftrixColors.PersianGreen.copy(alpha = 0.2f),
                LiftrixColors.PersianGreen.copy(alpha = 0.08f),
                Color.Transparent
            )
        )

        /**
         * Secondary area fill for dark theme
         */
        val secondaryAreaFill: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue.copy(alpha = 0.35f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.18f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.06f),
                Color.Transparent
            )
        )

        /**
         * Dark theme background gradient
         */
        val backgroundGradient: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.Night,
                LiftrixColors.Jet.copy(alpha = 0.3f),
                LiftrixColors.Night
            )
        )
    }

    /**
     * Performance analytics gradients with heat map colors
     */
    @Stable
    object Performance {
        /**
         * Heat map gradient from low to high performance
         */
        val heatMap: Brush = Brush.linearGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue.copy(alpha = 0.3f),  // Low
                LiftrixColors.PersianGreen.copy(alpha = 0.6f), // Medium
                LiftrixColors.PersianGreen,                     // High
                LiftrixColors.TiffanyBlue                       // Peak
            )
        )

        /**
         * Volume progression gradient
         */
        val volumeProgression: Brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                LiftrixColors.PersianGreen.copy(alpha = 0.1f),
                LiftrixColors.PersianGreen.copy(alpha = 0.3f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.2f),
                Color.Transparent
            )
        )
    }

    /**
     * Interactive state gradients for touch feedback
     */
    @Stable
    object Interactive {
        /**
         * Hover state gradient
         */
        val hover: Brush = Brush.radialGradient(
            colors = listOf(
                LiftrixColors.PersianGreen.copy(alpha = 0.12f),
                LiftrixColors.PersianGreen.copy(alpha = 0.06f),
                Color.Transparent
            ),
            radius = 48f
        )

        /**
         * Pressed state gradient
         */
        val pressed: Brush = Brush.radialGradient(
            colors = listOf(
                LiftrixColors.PersianGreen.copy(alpha = 0.24f),
                LiftrixColors.PersianGreen.copy(alpha = 0.12f),
                Color.Transparent
            ),
            radius = 32f
        )

        /**
         * Selection highlight gradient
         */
        val selection: Brush = Brush.linearGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue.copy(alpha = 0.2f),
                LiftrixColors.TiffanyBlue.copy(alpha = 0.1f),
                Color.Transparent
            )
        )
    }

    /**
     * Specialized chart type gradients
     */
    @Stable
    object ChartTypes {
        /**
         * Pie chart segment gradient
         */
        val pieSegment: Brush = Brush.radialGradient(
            colors = listOf(
                LiftrixColors.PersianGreen,
                LiftrixColors.PersianGreen.copy(alpha = 0.9f),
                LiftrixColors.PersianGreen.copy(alpha = 0.7f)
            )
        )

        /**
         * Bar chart gradient fill
         */
        val barFill: Brush = Brush.verticalGradient(
            colors = listOf(
                LiftrixColors.TiffanyBlue,
                LiftrixColors.PersianGreen.copy(alpha = 0.8f)
            )
        )

        /**
         * Line chart marker gradient
         */
        val lineMarker: Brush = Brush.radialGradient(
            colors = listOf(
                LiftrixColors.Snow,
                LiftrixColors.PersianGreen.copy(alpha = 0.9f),
                LiftrixColors.PersianGreen
            ),
            radius = 8f
        )
    }
}

/**
 * Dynamic gradient creation utilities
 */
object GradientFactory {

    /**
     * Create custom area fill gradient with specified color
     */
    @Stable
    fun createAreaFill(
        baseColor: Color,
        startAlpha: Float = 0.35f,
        endAlpha: Float = 0.0f
    ): Brush = Brush.verticalGradient(
        colors = listOf(
            baseColor.copy(alpha = startAlpha),
            baseColor.copy(alpha = startAlpha * 0.5f),
            baseColor.copy(alpha = startAlpha * 0.2f),
            baseColor.copy(alpha = endAlpha)
        )
    )

    /**
     * Create custom linear progression gradient
     */
    @Stable
    fun createLinearProgression(
        startColor: Color,
        endColor: Color,
        angle: Float = 0f
    ): Brush {
        val start = Offset(0f, 0f)
        val end = Offset(
            kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat(),
            kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
        )
        return Brush.linearGradient(
            colors = listOf(startColor, endColor),
            start = start,
            end = end
        )
    }

    /**
     * Create custom radial highlight with specified radius
     */
    @Stable
    fun createRadialHighlight(
        centerColor: Color,
        radius: Float,
        centerAlpha: Float = 0.6f
    ): Brush = Brush.radialGradient(
        colors = listOf(
            centerColor.copy(alpha = centerAlpha),
            centerColor.copy(alpha = centerAlpha * 0.5f),
            centerColor.copy(alpha = centerAlpha * 0.2f),
            Color.Transparent
        ),
        radius = radius
    )
}

/**
 * Gradient utilities for responsive design
 */
object ResponsiveGradients {

    /**
     * Get appropriate gradient based on screen size
     */
    @Composable
    fun adaptiveAreaFill(
        baseGradient: Brush,
        compactGradient: Brush = baseGradient
    ): Brush {
        val density = LocalDensity.current
        return remember(density) {
            // Use compact gradient for small screens
            if (density.density < 2.0f) compactGradient else baseGradient
        }
    }

    /**
     * Create gradient with responsive opacity based on screen size
     */
    @Composable
    fun responsiveOpacityGradient(
        baseColor: Color,
        compactScreenWidthDp: Dp = 360.dp
    ): Brush {
        val density = LocalDensity.current
        val isCompact = remember(density) {
            // Simple responsive logic - adjust based on your needs
            density.density < 2.5f
        }

        return remember(isCompact, baseColor) {
            val alphaMultiplier = if (isCompact) 0.8f else 1.0f
            Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0.35f * alphaMultiplier),
                    baseColor.copy(alpha = 0.15f * alphaMultiplier),
                    baseColor.copy(alpha = 0.05f * alphaMultiplier),
                    Color.Transparent
                )
            )
        }
    }
}

/**
 * Gradient performance optimization utilities
 */
object GradientOptimizations {

    /**
     * Cache frequently used gradients to improve performance
     */
    private val gradientCache = mutableMapOf<String, Brush>()

    /**
     * Get cached gradient or create and cache new one
     */
    fun getCachedGradient(
        key: String,
        gradientProvider: () -> Brush
    ): Brush {
        return gradientCache.getOrPut(key) {
            gradientProvider()
        }
    }

    /**
     * Clear gradient cache (call when theme changes)
     */
    fun clearCache() {
        gradientCache.clear()
    }

    /**
     * Get optimized gradient for large datasets
     */
    @Stable
    fun getOptimizedGradient(
        dataPointCount: Int,
        normalGradient: Brush,
        simplifiedGradient: Brush
    ): Brush {
        return if (dataPointCount > 200) {
            // Use simplified gradient for better performance with large datasets
            simplifiedGradient
        } else {
            normalGradient
        }
    }
}

/**
 * Extension functions for gradient manipulation
 */

/**
 * Create gradient with adjusted alpha values
 */
fun Brush.withAlpha(alphaMultiplier: Float): Brush {
    // Compose doesn't expose internal gradient types directly
    // Return the original brush for now - complex alpha modification would require
    // recreating the gradient with modified colors
    return this
}

/**
 * Blend two gradients together
 */
@Stable
fun blendGradients(
    gradient1: Brush,
    gradient2: Brush,
    blendRatio: Float = 0.5f
): Brush {
    // Simplified implementation - actual blending would require color interpolation
    return if (blendRatio < 0.5f) gradient1 else gradient2
}