package com.example.liftrix.ui.progress.components.widgets.heatmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMuscleValue
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide

@Composable
internal fun MuscleHeatmapCanvas(
    gender: MuscleHeatmapGender,
    viewSide: MuscleHeatmapViewSide,
    values: List<MuscleHeatmapMuscleValue>,
    colorMode: MuscleHeatmapColorMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resourceId = remember(gender, viewSide) { muscleHeatmapResource(gender, viewSide) }
    val svg = remember(resourceId, viewSide) { MuscleHeatmapSvgParser.parse(context, resourceId, viewSide) }
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val heatmapColors = remember(colorScheme, isDarkTheme) {
        MuscleHeatmapThemeColors.from(colorScheme, isDarkTheme)
    }
    val intensityByMuscle = remember(values) {
        values.associate { it.muscleGroup to it.normalizedIntensity }
    }

    Canvas(modifier = modifier) {
        val bounds = svg.contentBounds
        val contentWidth = bounds.width.coerceAtLeast(1f)
        val contentHeight = bounds.height.coerceAtLeast(1f)
        val scale = minOf(size.width / contentWidth, size.height / contentHeight)
        val left = (size.width - contentWidth * scale) / 2f
        val top = (size.height - contentHeight * scale) / 2f

        translate(left = left, top = top) {
            scale(scale = scale, pivot = Offset.Zero) {
                translate(left = -bounds.left, top = -bounds.top) {
                    svg.paths.forEach { region ->
                        val fill = region.resolveFill(
                            intensityByMuscle = intensityByMuscle,
                            colorMode = colorMode,
                            neutral = heatmapColors.neutral,
                            base = heatmapColors.base,
                            separator = region.separatorColor(heatmapColors)
                        )
                        if (region.isSeparator) {
                            if (region.isFilledSeparator) {
                                drawPath(region.path, fill)
                            } else {
                                drawPath(region.path, fill, style = Stroke(width = 1.2f / scale))
                            }
                        } else {
                            drawPath(region.path, fill)
                            region.sourceStrokeWidth?.let { strokeWidth ->
                                drawPath(
                                    path = region.path,
                                    color = heatmapColors.separator,
                                    style = Stroke(width = strokeWidth.coerceAtLeast(1f) / scale)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MuscleHeatmapPath.resolveFill(
    intensityByMuscle: Map<MuscleGroup, Float>,
    colorMode: MuscleHeatmapColorMode,
    neutral: Color,
    base: Color,
    separator: Color
): Color {
    if (isSeparator) return separator
    val muscle = muscleGroup ?: return base
    return muscleHeatmapColor(
        intensity = intensityByMuscle[muscle] ?: 0f,
        colorMode = colorMode,
        neutral = neutral
    )
}

private data class MuscleHeatmapThemeColors(
    val neutral: Color,
    val base: Color,
    val separator: Color,
    val outline: Color
) {
    companion object {
        fun from(
            colorScheme: androidx.compose.material3.ColorScheme,
            isDarkTheme: Boolean
        ): MuscleHeatmapThemeColors {
            return if (isDarkTheme) {
                MuscleHeatmapThemeColors(
                    neutral = colorScheme.onSurface.copy(alpha = 0.10f),
                    base = colorScheme.onSurface.copy(alpha = 0.055f),
                    separator = colorScheme.background,
                    outline = colorScheme.onSurface.copy(alpha = 0.15f)
                )
            } else {
                MuscleHeatmapThemeColors(
                    neutral = Color(0xFFE9EEF1),
                    base = Color(0xFFF5F7F8),
                    separator = colorScheme.background,
                    outline = colorScheme.onSurface.copy(alpha = 0.10f)
                )
            }
        }
    }
}

private fun MuscleHeatmapPath.separatorColor(colors: MuscleHeatmapThemeColors): Color {
    return if (id.equals("body-outline", ignoreCase = true)) {
        colors.outline
    } else {
        colors.separator
    }
}
