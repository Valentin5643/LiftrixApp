package com.example.liftrix.ui.progress.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun ProgressWidgetPerformanceMarker(
    componentId: String,
    enabled: Boolean
) {
    if (enabled) {
        LaunchedEffect(componentId) {
            delay(100)
            Timber.d("Progress performance marker: $componentId")
        }
    }
}
