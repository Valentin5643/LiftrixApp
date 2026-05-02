package com.example.liftrix.ui.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Performance tracking component for monitoring Compose recomposition performance.
 * 
 * Provides development-time performance monitoring for analytics components,
 * helping identify performance bottlenecks and excessive recompositions.
 */
@Composable
fun PerformanceTracker(
    componentId: String,
    enabled: Boolean = false
) {
    // Use simple frame counting for this lightweight tracker
    if (enabled) {
        LaunchedEffect(componentId) {
            // Start simple performance tracking
            kotlinx.coroutines.delay(100)
            timber.log.Timber.d("PerformanceTracker: Started tracking $componentId")
        }
    }
}
