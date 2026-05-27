package com.example.liftrix.ui.progress.components.widgets.heatmap

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.liftrix.domain.model.analytics.MuscleHeatmapWidgetData
import com.example.liftrix.ui.progress.components.widgets.FolderStyleWidget

@Composable
fun MuscleHeatmapWidget(
    data: MuscleHeatmapWidgetData?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: () -> Unit = {},
    aspectRatio: Float = 1.1f
) {
    FolderStyleWidget(
        title = "Muscle Heatmap",
        icon = Icons.Default.AccessibilityNew,
        onClick = onClick,
        modifier = modifier,
        isLoading = isLoading || data?.isLoading == true,
        error = data?.error?.message,
        aspectRatio = aspectRatio
    )
}
