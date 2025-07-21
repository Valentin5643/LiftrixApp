package com.example.liftrix.ui.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.liftrix.BuildConfig
import javax.inject.Inject

/**
 * Performance tracking component for monitoring Compose recomposition performance.
 * 
 * Provides development-time performance monitoring for analytics components,
 * helping identify performance bottlenecks and excessive recompositions.
 */
@Composable
fun PerformanceTracker(
    componentId: String,
    enabled: Boolean = BuildConfig.DEBUG
) {
    val monitor = remember { PerformanceMonitor() }
    
    if (enabled) {
        LaunchedEffect(componentId) {
            monitor.startFrameRateTracking(componentId)
        }
    }
}