package com.example.liftrix.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * LiftrixChartStyle - Modern chart styling with bezier curves and Material 3 theming
 *
 * Comprehensive chart configuration system providing:
 * - Bezier curve line rendering with smooth animations
 * - Support for both V1 (Persian Green/Tiffany Blue) and V2 (Teal-based) color systems
 * - Material 3 design tokens integration
 * - Mobile-optimized touch targets and accessibility
 * - Performance-optimized 60fps rendering
 */
@Immutable
object LiftrixChartStyle {

    /**
     * Chart colors supporting both V1 (5-color) and V2 (Teal-based) systems
     */
    @Stable
    object ChartColors {
        // V1 Colors (Legacy - now mapped to V2 colors)
        object V1 {
            val primaryLine: Color = LiftrixColors.PersianGreen
            val secondaryLine: Color = LiftrixColors.TiffanyBlue
            val accentLine: Color = LiftrixColors.Jet
            val gridColor: Color = LiftrixColors.PersianGreen.copy(alpha = 0.12f)
            val labelColor: Color = LiftrixColors.Night.copy(alpha = 0.8f)
            val backgroundFill: Color = LiftrixColors.Snow
            val surfaceFill: Color = LiftrixColors.Snow.copy(alpha = 0.95f)
            
            // Data point markers
            val dataPointPrimary: Color = LiftrixColors.PersianGreen
            val dataPointSecondary: Color = LiftrixColors.TiffanyBlue
            val dataPointHighlight: Color = LiftrixColors.Night
            val dataPointBackground: Color = LiftrixColors.Snow
            
            // Interactive states
            val hoverColor: Color = LiftrixColors.PersianGreen.copy(alpha = 0.08f)
            val selectedColor: Color = LiftrixColors.PersianGreen.copy(alpha = 0.16f)
            val pressedColor: Color = LiftrixColors.PersianGreen.copy(alpha = 0.24f)
        }
        
        // V2 Colors (Modern - Teal-based system)
        object V2 {
            val primaryLine: Color = LiftrixColorsV2.Teal
            val secondaryLine: Color = LiftrixColorsV2.TealHover
            val accentLine: Color = LiftrixColorsV2.TealLight
            val gridColor: Color = LiftrixColorsV2.Teal.copy(alpha = 0.12f)
            val labelColor: Color = LiftrixColorsV2.Dark.TextPrimary.copy(alpha = 0.8f)
            val backgroundFill: Color = Color.Transparent
            val surfaceFill: Color = Color.Transparent
            
            // Data point markers
            val dataPointPrimary: Color = LiftrixColorsV2.Teal
            val dataPointSecondary: Color = LiftrixColorsV2.TealHover
            val dataPointHighlight: Color = LiftrixColorsV2.TealDark
            val dataPointBackground: Color = Color.White
            
            // Interactive states
            val hoverColor: Color = LiftrixColorsV2.Teal.copy(alpha = 0.08f)
            val selectedColor: Color = LiftrixColorsV2.Teal.copy(alpha = 0.16f)
            val pressedColor: Color = LiftrixColorsV2.Teal.copy(alpha = 0.24f)
        }
        
        // Legacy properties for backward compatibility (default to V1)
        val primaryLine: Color get() = V1.primaryLine
        val secondaryLine: Color get() = V1.secondaryLine
        val accentLine: Color get() = V1.accentLine
        val gridColor: Color get() = V1.gridColor
        val labelColor: Color get() = V1.labelColor
        val backgroundFill: Color get() = V1.backgroundFill
        val surfaceFill: Color get() = V1.surfaceFill
        val dataPointPrimary: Color get() = V1.dataPointPrimary
        val dataPointSecondary: Color get() = V1.dataPointSecondary
        val dataPointHighlight: Color get() = V1.dataPointHighlight
        val dataPointBackground: Color get() = V1.dataPointBackground
        val hoverColor: Color get() = V1.hoverColor
        val selectedColor: Color get() = V1.selectedColor
        val pressedColor: Color get() = V1.pressedColor
        
        /**
         * Get chart colors based on theme version
         */
        fun getColorsForVersion(themeVersion: ThemeVersion): ChartColorPalette {
            return when (themeVersion) {
                ThemeVersion.V1 -> ChartColorPalette(
                    primaryLine = V1.primaryLine,
                    secondaryLine = V1.secondaryLine,
                    accentLine = V1.accentLine,
                    gridColor = V1.gridColor,
                    labelColor = V1.labelColor,
                    backgroundFill = V1.backgroundFill,
                    surfaceFill = V1.surfaceFill,
                    dataPointPrimary = V1.dataPointPrimary,
                    dataPointSecondary = V1.dataPointSecondary,
                    dataPointHighlight = V1.dataPointHighlight,
                    dataPointBackground = V1.dataPointBackground,
                    hoverColor = V1.hoverColor,
                    selectedColor = V1.selectedColor,
                    pressedColor = V1.pressedColor
                )
                ThemeVersion.V2 -> ChartColorPalette(
                    primaryLine = V2.primaryLine,
                    secondaryLine = V2.secondaryLine,
                    accentLine = V2.accentLine,
                    gridColor = V2.gridColor,
                    labelColor = V2.labelColor,
                    backgroundFill = V2.backgroundFill,
                    surfaceFill = V2.surfaceFill,
                    dataPointPrimary = V2.dataPointPrimary,
                    dataPointSecondary = V2.dataPointSecondary,
                    dataPointHighlight = V2.dataPointHighlight,
                    dataPointBackground = V2.dataPointBackground,
                    hoverColor = V2.hoverColor,
                    selectedColor = V2.selectedColor,
                    pressedColor = V2.pressedColor
                )
            }
        }
    }

    /**
     * Chart dimensions optimized for mobile touch targets
     */
    @Stable
    object ChartDimensions {
        val defaultHeight = 280.dp
        val compactHeight = 200.dp
        val expandedHeight = 360.dp
        
        val strokeWidthThin = 2.dp
        val strokeWidthMedium = 3.dp
        val strokeWidthThick = 4.dp
        
        val dataPointRadius = 4.dp
        val dataPointRadiusLarge = 6.dp
        val touchTargetSize = 44.dp  // Minimum touch target per Material guidelines
        
        val cornerRadius = 12.dp
        val chartPadding = 16.dp
        val labelPadding = 8.dp
    }

    /**
     * Typography for chart labels and legends
     */
    @Stable
    object ChartTypography {
        val titleTextSize = 16.sp
        val labelTextSize = 14.sp  // Updated to meet 14sp minimum requirement
        val valueTextSize = 14.sp
        val captionTextSize = 14.sp  // Updated to meet 14sp minimum requirement
        val axisLabelSize = 14.sp  // Updated to meet 14sp minimum requirement
    }

    /**
     * Animation configurations for smooth chart interactions
     */
    @Stable
    object ChartAnimations {
        const val defaultDuration = 300
        const val fastDuration = 150
        const val slowDuration = 500
        
        const val bezierControlPointRatio = 0.4f  // Control point distance for bezier curves
        const val smoothingFactor = 0.25f  // Smoothing factor for curve interpolation
    }

    /**
     * Line chart configuration with bezier curve support
     */
    @Immutable
    data class LineChartConfig(
        val strokeWidth: Float,
        val color: Color,
        val useBezierCurves: Boolean = true,
        val showDataPoints: Boolean = true,
        val showGradientFill: Boolean = true,
        val gradientBrush: Brush? = null,
        val showGrid: Boolean = false,
        val animationDuration: Int = ChartAnimations.defaultDuration
    )

    /**
     * Create primary line chart configuration
     */
    @Stable
    fun primaryLineChart(): LineChartConfig = LineChartConfig(
        strokeWidth = ChartDimensions.strokeWidthMedium.value,
        color = ChartColors.primaryLine,
        useBezierCurves = true,
        showDataPoints = true,
        showGradientFill = true,
        showGrid = false,
        animationDuration = ChartAnimations.defaultDuration
    )

    /**
     * Create secondary line chart configuration
     */
    @Stable
    fun secondaryLineChart(): LineChartConfig = LineChartConfig(
        strokeWidth = ChartDimensions.strokeWidthThin.value,
        color = ChartColors.secondaryLine,
        useBezierCurves = true,
        showDataPoints = false,
        showGradientFill = false,
        showGrid = false,
        animationDuration = ChartAnimations.fastDuration
    )

    /**
     * Pie chart configuration
     */
    @Immutable
    data class PieChartConfig(
        val centerRadius: Float = 0.3f,
        val strokeWidth: Float = 0.dp.value,
        val showLabels: Boolean = true,
        val showPercentages: Boolean = true,
        val animationDuration: Int = ChartAnimations.defaultDuration,
        val cornerRadius: Float = 4.dp.value
    )

    /**
     * Create default pie chart configuration
     */
    @Stable
    fun defaultPieChart(): PieChartConfig = PieChartConfig(
        centerRadius = 0.4f,
        strokeWidth = 2.dp.value,
        showLabels = true,
        showPercentages = true,
        animationDuration = ChartAnimations.defaultDuration,
        cornerRadius = 8.dp.value
    )

    /**
     * Chart gradients supporting both V1 and V2 design systems
     */
    @Stable
    object ChartGradients {
        // V1 Gradients (Persian Green/Tiffany Blue)
        object V1 {
            val primaryGradient: Brush = Brush.verticalGradient(
                colors = listOf(
                    LiftrixColors.PersianGreen.copy(alpha = 0.3f),
                    LiftrixColors.PersianGreen.copy(alpha = 0.1f),
                    Color.Transparent
                )
            )

            val secondaryGradient: Brush = Brush.verticalGradient(
                colors = listOf(
                    LiftrixColors.TiffanyBlue.copy(alpha = 0.3f),
                    LiftrixColors.TiffanyBlue.copy(alpha = 0.1f),
                    Color.Transparent
                )
            )

            val accentGradient: Brush = Brush.verticalGradient(
                colors = listOf(
                    LiftrixColors.Jet.copy(alpha = 0.2f),
                    LiftrixColors.Jet.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )

            val backgroundGradient: Brush = Brush.radialGradient(
                colors = listOf(
                    LiftrixColors.PersianGreen.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )
        }
        
        // V2 Gradients (Teal-based)
        object V2 {
            val primaryGradient: Brush = Brush.verticalGradient(
                colors = listOf(
                    LiftrixColorsV2.Teal.copy(alpha = 0.3f),
                    LiftrixColorsV2.Teal.copy(alpha = 0.1f),
                    Color.Transparent
                )
            )

            val secondaryGradient: Brush = Brush.verticalGradient(
                colors = listOf(
                    LiftrixColorsV2.TealHover.copy(alpha = 0.3f),
                    LiftrixColorsV2.TealHover.copy(alpha = 0.1f),
                    Color.Transparent
                )
            )

            val accentGradient: Brush = Brush.verticalGradient(
                colors = listOf(
                    LiftrixColorsV2.TealLight.copy(alpha = 0.2f),
                    LiftrixColorsV2.TealLight.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )

            val backgroundGradient: Brush = Brush.radialGradient(
                colors = listOf(
                    LiftrixColorsV2.Teal.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )
        }
        
        // Legacy properties for backward compatibility (default to V1)
        val primaryGradient: Brush get() = V1.primaryGradient
        val secondaryGradient: Brush get() = V1.secondaryGradient
        val accentGradient: Brush get() = V1.accentGradient
        val backgroundGradient: Brush get() = V1.backgroundGradient
        
        /**
         * Get gradients based on theme version
         */
        fun getGradientsForVersion(themeVersion: ThemeVersion): ChartGradientPalette {
            return when (themeVersion) {
                ThemeVersion.V1 -> ChartGradientPalette(
                    primaryGradient = V1.primaryGradient,
                    secondaryGradient = V1.secondaryGradient,
                    accentGradient = V1.accentGradient,
                    backgroundGradient = V1.backgroundGradient
                )
                ThemeVersion.V2 -> ChartGradientPalette(
                    primaryGradient = V2.primaryGradient,
                    secondaryGradient = V2.secondaryGradient,
                    accentGradient = V2.accentGradient,
                    backgroundGradient = V2.backgroundGradient
                )
            }
        }
    }

    /**
     * Chart container styling
     */
    @Stable
    object ChartContainer {
        val shape = RoundedCornerShape(ChartDimensions.cornerRadius)
        val shadowElevation = 2.dp
        val borderWidth = 1.dp
        val borderColor = LiftrixColors.PersianGreen.copy(alpha = 0.1f)
        
        val backgroundColor = Color.Transparent
        val surfaceColor = Color.Transparent
    }

    /**
     * Bezier curve path creation utilities
     */
    object BezierCurves {
        /**
         * Calculate control points for smooth bezier curve between data points
         * Now handles edge cases with null safety
         */
        fun calculateControlPoints(
            prevX: Float, prevY: Float,
            currentX: Float, currentY: Float,
            nextX: Float?, nextY: Float?
        ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
            val controlPointRatio = ChartAnimations.bezierControlPointRatio
            
            // Handle edge case where points are too close or invalid
            val deltaX = currentX - prevX
            val deltaY = currentY - prevY
            
            // If points are at the same position, return the same point as control points
            if (deltaX == 0f && deltaY == 0f) {
                return Pair(
                    Pair(prevX, prevY),
                    Pair(currentX, currentY)
                )
            }
            
            val control1X = prevX + deltaX * controlPointRatio
            val control1Y = prevY + deltaY * controlPointRatio
            
            val control2X = currentX - deltaX * controlPointRatio
            val control2Y = currentY - deltaY * controlPointRatio
            
            return Pair(
                Pair(control1X, control1Y),
                Pair(control2X, control2Y)
            )
        }

        /**
         * Apply smoothing to data points for more natural curves
         */
        fun smoothDataPoints(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            if (points.size <= 2) return points
            
            val smoothed = mutableListOf<Pair<Float, Float>>()
            smoothed.add(points.first()) // Keep first point unchanged
            
            for (i in 1 until points.size - 1) {
                val prevPoint = points[i - 1]
                val currentPoint = points[i]
                val nextPoint = points[i + 1]
                
                val smoothedX = currentPoint.first
                val smoothedY = (prevPoint.second + currentPoint.second + nextPoint.second) / 3f * 
                    (1f - ChartAnimations.smoothingFactor) + currentPoint.second * ChartAnimations.smoothingFactor
                
                smoothed.add(Pair(smoothedX, smoothedY))
            }
            
            smoothed.add(points.last()) // Keep last point unchanged
            return smoothed
        }
    }

    /**
     * Performance optimization for chart rendering
     */
    @Stable
    object Performance {
        const val maxDataPointsBeforeOptimization = 100
        const val renderingFrameTarget = 16  // Target 60fps (16ms per frame)
        
        /**
         * Optimize data points for performance while maintaining visual quality and curve continuity
         * Uses Douglas-Peucker algorithm to reduce points while preserving curve shape
         */
        fun optimizeDataPoints(
            points: List<Pair<Float, Float>>,
            canvasWidth: Float
        ): List<Pair<Float, Float>> {
            if (points.size <= maxDataPointsBeforeOptimization) return points
            
            val pixelDensity = canvasWidth / points.size
            return if (pixelDensity < 2f) {
                // High density - use Douglas-Peucker algorithm to preserve curve shape
                douglasPeucker(points, epsilon = 2f)
            } else {
                points
            }
        }
        
        /**
         * Douglas-Peucker algorithm for point reduction while preserving curve shape
         * This maintains curve continuity better than simply skipping points
         */
        private fun douglasPeucker(
            points: List<Pair<Float, Float>>,
            epsilon: Float
        ): List<Pair<Float, Float>> {
            if (points.size <= 2) return points
            
            // Find the point with maximum distance from line between first and last
            var maxDistance = 0f
            var maxIndex = 0
            
            val first = points.first()
            val last = points.last()
            
            for (i in 1 until points.size - 1) {
                val distance = perpendicularDistance(points[i], first, last)
                if (distance > maxDistance) {
                    maxDistance = distance
                    maxIndex = i
                }
            }
            
            // If max distance is greater than epsilon, recursively simplify
            return if (maxDistance > epsilon) {
                val leftSegment = douglasPeucker(points.subList(0, maxIndex + 1), epsilon)
                val rightSegment = douglasPeucker(points.subList(maxIndex, points.size), epsilon)
                
                // Combine segments without duplicating the middle point
                leftSegment.dropLast(1) + rightSegment
            } else {
                // Return just the endpoints
                listOf(first, last)
            }
        }
        
        /**
         * Calculate perpendicular distance from point to line
         */
        private fun perpendicularDistance(
            point: Pair<Float, Float>,
            lineStart: Pair<Float, Float>,
            lineEnd: Pair<Float, Float>
        ): Float {
            val dx = lineEnd.first - lineStart.first
            val dy = lineEnd.second - lineStart.second
            
            if (dx == 0f && dy == 0f) {
                // Line start and end are the same point
                val pdx = point.first - lineStart.first
                val pdy = point.second - lineStart.second
                return kotlin.math.sqrt(pdx * pdx + pdy * pdy)
            }
            
            val normalized = ((point.first - lineStart.first) * dx + 
                            (point.second - lineStart.second) * dy) / (dx * dx + dy * dy)
            
            val closestX = lineStart.first + normalized * dx
            val closestY = lineStart.second + normalized * dy
            
            val distX = point.first - closestX
            val distY = point.second - closestY
            
            return kotlin.math.sqrt(distX * distX + distY * distY)
        }
    }
}

/**
 * ModernChartRenderer - Encapsulates chart rendering operations
 * 
 * This object provides the main API for rendering charts with bezier curves and gradients
 * following the Liftrix design system.
 */
@Stable
object ModernChartRenderer {
    
    /**
     * Draw bezier curve line with gradient fill
     * Handles edge cases: 0, 1, or 2 data points
     */
    fun DrawScope.drawBezierLineChart(
        dataPoints: List<Pair<Float, Float>>,
        config: LiftrixChartStyle.LineChartConfig
    ) {
        // Handle edge cases for minimal data points
        when (dataPoints.size) {
            0 -> return  // No data to draw
            1 -> {
                // Draw single point
                val point = dataPoints.first()
                drawCircle(
                    color = config.color,
                    radius = LiftrixChartStyle.ChartDimensions.dataPointRadiusLarge.toPx(),
                    center = androidx.compose.ui.geometry.Offset(point.first, point.second)
                )
                return
            }
            // 2 or more points continue to normal processing
        }
        
        val optimizedPoints = LiftrixChartStyle.Performance.optimizeDataPoints(dataPoints, size.width)
        val smoothedPoints = if (config.useBezierCurves) {
            LiftrixChartStyle.BezierCurves.smoothDataPoints(optimizedPoints)
        } else {
            optimizedPoints
        }
        
        val path = androidx.compose.ui.graphics.Path()
        
        // Create line path
        smoothedPoints.forEachIndexed { index, (x, y) ->
            when (index) {
                0 -> path.moveTo(x, y)
                else -> {
                    if (config.useBezierCurves && index > 0) {
                        val prevPoint = smoothedPoints[index - 1]
                        val controlPoints = LiftrixChartStyle.BezierCurves.calculateControlPoints(
                            prevPoint.first, prevPoint.second,
                            x, y,
                            smoothedPoints.getOrNull(index + 1)?.first,
                            smoothedPoints.getOrNull(index + 1)?.second
                        )
                        
                        path.cubicTo(
                            controlPoints.first.first, controlPoints.first.second,
                            controlPoints.second.first, controlPoints.second.second,
                            x, y
                        )
                    } else {
                        path.lineTo(x, y)
                    }
                }
            }
        }
        
        // Draw gradient fill
        if (config.showGradientFill) {
            val fillPath = androidx.compose.ui.graphics.Path().apply {
                addPath(path)
                lineTo(smoothedPoints.last().first, size.height)
                lineTo(smoothedPoints.first().first, size.height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = config.gradientBrush ?: LiftrixChartStyle.ChartGradients.primaryGradient
            )
        }
        
        // Draw the line
        drawPath(
            path = path,
            color = config.color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = config.strokeWidth,
                cap = StrokeCap.Round
            )
        )
        
        // Draw data points
        if (config.showDataPoints) {
            smoothedPoints.forEach { (x, y) ->
                drawCircle(
                    color = config.color,
                    radius = LiftrixChartStyle.ChartDimensions.dataPointRadius.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
                // Draw point background for better visibility
                drawCircle(
                    color = LiftrixChartStyle.ChartColors.dataPointBackground,
                    radius = LiftrixChartStyle.ChartDimensions.dataPointRadius.toPx() - 1.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
        }
    }
    
    /**
     * Draw data point labels for peaks and valleys
     */
    fun DrawScope.drawDataPointLabels(
        dataPoints: List<Pair<Float, Float>>,
        values: List<Float>,
        config: LiftrixChartStyle.LineChartConfig
    ) {
        if (dataPoints.size < 3) return
        
        // Identify peaks and valleys
        dataPoints.forEachIndexed { index, point ->
            if (index == 0 || index == dataPoints.size - 1) return@forEachIndexed
            
            val prevY = dataPoints[index - 1].second
            val currentY = point.second
            val nextY = dataPoints[index + 1].second
            
            val isPeak = currentY < prevY && currentY < nextY  // Lower Y = higher value
            val isValley = currentY > prevY && currentY > nextY  // Higher Y = lower value
            
            if (isPeak || isValley) {
                val value = values.getOrNull(index) ?: return@forEachIndexed
                
                // Use native canvas for text drawing
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val textPaint = android.graphics.Paint().apply {
                        color = config.color.toArgb()
                        textSize = LiftrixChartStyle.ChartTypography.valueTextSize.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    
                    val labelY = if (isPeak) {
                        point.second - LiftrixChartStyle.ChartDimensions.labelPadding.toPx()
                    } else {
                        point.second + LiftrixChartStyle.ChartDimensions.labelPadding.toPx() + 
                            LiftrixChartStyle.ChartTypography.valueTextSize.toPx()
                    }
                    
                    nativeCanvas.drawText(
                        String.format("%.1f", value),
                        point.first,
                        labelY,
                        textPaint
                    )
                }
            }
        }
    }
}

/**
 * Extension functions for backward compatibility
 */

/**
 * Draw bezier curve line with gradient fill - Delegates to ModernChartRenderer
 */
fun DrawScope.drawBezierLineChart(
    dataPoints: List<Pair<Float, Float>>,
    config: LiftrixChartStyle.LineChartConfig
) {
    with(ModernChartRenderer) {
        drawBezierLineChart(dataPoints, config)
    }
}

/**
 * Composable function to provide chart style context
 */
@Composable
fun rememberLiftrixChartStyle(): LiftrixChartStyle {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        LiftrixChartStyle
    }
}

/**
 * Data class representing chart color palette for theme version switching
 */
@Immutable
data class ChartColorPalette(
    val primaryLine: Color,
    val secondaryLine: Color,
    val accentLine: Color,
    val gridColor: Color,
    val labelColor: Color,
    val backgroundFill: Color,
    val surfaceFill: Color,
    val dataPointPrimary: Color,
    val dataPointSecondary: Color,
    val dataPointHighlight: Color,
    val dataPointBackground: Color,
    val hoverColor: Color,
    val selectedColor: Color,
    val pressedColor: Color
)

/**
 * Data class representing chart gradient palette for theme version switching
 */
@Immutable
data class ChartGradientPalette(
    val primaryGradient: Brush,
    val secondaryGradient: Brush,
    val accentGradient: Brush,
    val backgroundGradient: Brush
)

/**
 * Extension functions for theme-aware chart styling
 */
@Composable
fun rememberChartColorsForVersion(themeVersion: ThemeVersion): ChartColorPalette {
    return remember(themeVersion) {
        LiftrixChartStyle.ChartColors.getColorsForVersion(themeVersion)
    }
}

@Composable
fun rememberChartGradientsForVersion(themeVersion: ThemeVersion): ChartGradientPalette {
    return remember(themeVersion) {
        LiftrixChartStyle.ChartGradients.getGradientsForVersion(themeVersion)
    }
}
