package com.example.liftrix.ui.progress.components.widgets.heatmap

import androidx.compose.ui.graphics.Color
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.ui.theme.ChartColorsV2
import com.example.liftrix.ui.theme.LiftrixColorsV2

internal fun muscleHeatmapColor(
    intensity: Float,
    colorMode: MuscleHeatmapColorMode,
    neutral: Color
): Color {
    if (intensity <= 0f) return neutral

    val clamped = intensity.coerceIn(0f, 1f)
    return when (colorMode) {
        MuscleHeatmapColorMode.APP_GRADIENT -> ChartColorsV2.Heatmap.getInterpolatedHeatmapColor(clamped)
        MuscleHeatmapColorMode.MONOCHROME_INTENSITY -> LiftrixColorsV2.Teal.copy(alpha = 0.24f + clamped * 0.76f)
    }
}
