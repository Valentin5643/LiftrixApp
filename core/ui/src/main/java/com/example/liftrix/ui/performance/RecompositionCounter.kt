package com.example.liftrix.ui.performance

import androidx.compose.runtime.*
import com.example.liftrix.core.ui.BuildConfig
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enhanced recomposition tracking for PERF-002 performance validation.
 * Provides detailed recomposition counting and performance analysis for
 * components to ensure optimal rendering efficiency.
 */
class RecompositionCounter {
    
    private val compositionCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val recompositionTimes = ConcurrentHashMap<String, MutableList<Long>>()
    
    /**
     * Track recompositions for a composable component
     * @param key Unique identifier for the component
     * @param content Composable content to track
     */
    @Composable
    fun TrackRecomposition(
        key: String,
        content: @Composable () -> Unit
    ) {
        if (!BuildConfig.DEBUG) {
            content()
            return
        }
        
        val count = compositionCounts.getOrPut(key) { AtomicInteger(0) }
        val times = recompositionTimes.getOrPut(key) { mutableListOf() }
        
        // Record recomposition
        val currentTime = System.currentTimeMillis()
        count.incrementAndGet()
        times.add(currentTime)
        
        if (count.get() > 1) {
            Timber.d("RecompositionCounter: $key recomposed ${count.get()} times")
        }
        
        content()
    }
    
    /**
     * Get the current composition count for a component
     * @param key Component identifier
     * @return Number of times component has been composed
     */
    fun getCompositionCount(key: String): Int {
        return compositionCounts[key]?.get() ?: 0
    }
    
    /**
     * Get recomposition timing information
     * @param key Component identifier
     * @return List of timestamps when component was recomposed
     */
    fun getRecompositionTimes(key: String): List<Long> {
        return recompositionTimes[key]?.toList() ?: emptyList()
    }
    
    /**
     * Reset tracking for a specific component
     * @param key Component identifier
     */
    fun reset(key: String) {
        compositionCounts.remove(key)
        recompositionTimes.remove(key)
    }
    
    /**
     * Reset all tracking data
     */
    fun resetAll() {
        compositionCounts.clear()
        recompositionTimes.clear()
    }
}

/**
 * Legacy composable function for backward compatibility
 * Enhanced to work with the new RecompositionCounter class
 */
@Composable
fun RecompositionCounter(
    componentName: String,
    reason: String = "recomposition"
) {
    if (BuildConfig.DEBUG) {
        SideEffect {
            Timber.d("Recomposition: $componentName - $reason")
        }
    }
}

/**
 * Global recomposition counter for easy access during testing
 */
object GlobalRecompositionCounter {
    private val instance = RecompositionCounter()
    
    @Composable
    fun Track(key: String, content: @Composable () -> Unit) {
        instance.TrackRecomposition(key, content)
    }
    
    fun getCount(key: String): Int = instance.getCompositionCount(key)
    fun reset(key: String) = instance.reset(key)
    fun resetAll() = instance.resetAll()
}
