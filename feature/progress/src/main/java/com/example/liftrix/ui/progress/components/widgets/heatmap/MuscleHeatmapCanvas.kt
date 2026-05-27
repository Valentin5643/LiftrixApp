package com.example.liftrix.ui.progress.components.widgets.heatmap

import androidx.compose.foundation.Canvas
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
                            neutral = colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            base = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            separator = colorScheme.outlineVariant
                        )
                        if (region.isSeparator) {
                            if (region.isFilledSeparator) {
                                drawPath(region.path, fill)
                            } else {
                                drawPath(region.path, fill, style = Stroke(width = 1.2f / scale))
                            }
                        } else {
                            drawPath(region.path, fill)
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
