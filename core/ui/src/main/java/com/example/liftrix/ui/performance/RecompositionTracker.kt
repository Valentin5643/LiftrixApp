package com.example.liftrix.ui.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.liftrix.core.ui.BuildConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recomposition tracking utility for development builds to optimize Compose performance.
 * 
 * Tracks unnecessary recompositions, provides debugging information for performance
 * optimization, and helps identify components that need remember() or derivedStateOf() 
 * optimizations. Only active in debug builds to avoid production overhead.
 * 
 * Key features:
 * - Automatic recomposition counting for development builds
 * - Excessive recomposition detection with configurable thresholds
 * - Component-specific tracking with detailed logging
 * - Integration with performance monitoring for comprehensive analysis
 * - Zero production overhead with debug-only instrumentation
 */
@Singleton
class RecompositionTracker @Inject constructor() {
    
    private val recompositionCounts = mutableMapOf<String, Int>()
    private val componentStartTimes = mutableMapOf<String, Long>()
    private var isEnabled = BuildConfig.DEBUG
    
    /**
     * Log recomposition for a specific component
     * @param componentName Name of the component
     * @param reason Optional reason for recomposition
     */
    fun logRecomposition(componentName: String, reason: String? = null) {
        if (!isEnabled) return
        
        val currentCount = recompositionCounts.getOrDefault(componentName, 0) + 1
        recompositionCounts[componentName] = currentCount
        
        if (currentCount == 1) {
            componentStartTimes[componentName] = System.currentTimeMillis()
            Timber.d("RecompositionTracker: First composition - $componentName")
        } else {
            val reasonText = reason?.let { " (reason: $it)" } ?: ""
            Timber.v("RecompositionTracker: Recomposition #$currentCount - $componentName$reasonText")
        }
        
        // Alert for excessive recompositions
        if (currentCount > 10) {
            val duration = System.currentTimeMillis() - (componentStartTimes[componentName] ?: 0)
            Timber.w("RecompositionTracker: EXCESSIVE RECOMPOSITIONS - " +
                    "$componentName recomposed $currentCount times in ${duration}ms")
        }
    }
    
    /**
     * Get recomposition count for a component
     * @param componentName Component name
     * @return Number of recompositions
     */
    fun getRecompositionCount(componentName: String): Int {
        return recompositionCounts.getOrDefault(componentName, 0)
    }
    
    /**
     * Get all tracked components and their recomposition counts
     */
    fun getAllRecompositionCounts(): Map<String, Int> {
        return recompositionCounts.toMap()
    }
    
    /**
     * Reset tracking for a specific component
     * @param componentName Component to reset
     */
    fun resetComponent(componentName: String) {
        recompositionCounts.remove(componentName)
        componentStartTimes.remove(componentName)
        Timber.d("RecompositionTracker: Reset tracking for $componentName")
    }
    
    /**
     * Reset all tracking data
     */
    fun resetAll() {
        recompositionCounts.clear()
        componentStartTimes.clear()
        Timber.d("RecompositionTracker: Reset all tracking data")
    }
    
    /**
     * Generate performance report for all tracked components
     */
    fun generatePerformanceReport(): String {
        if (!isEnabled) return "Recomposition tracking disabled in release builds"
        
        val report = StringBuilder()
        report.appendLine("=== Recomposition Performance Report ===")
        
        if (recompositionCounts.isEmpty()) {
            report.appendLine("No components tracked")
            return report.toString()
        }
        
        // Sort by recomposition count descending
        val sortedComponents = recompositionCounts.toList().sortedByDescending { it.second }
        
        sortedComponents.forEach { (componentName, count) ->
            val duration = System.currentTimeMillis() - (componentStartTimes[componentName] ?: 0)
            val status = when {
                count <= 3 -> "✓ Good"
                count <= 10 -> "⚠ Moderate"
                else -> "❌ Poor"
            }
            
            report.appendLine("$status $componentName: $count recompositions (${duration}ms)")
        }
        
        return report.toString()
    }
    
    /**
     * Enable or disable tracking (for testing purposes)
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled && BuildConfig.DEBUG
    }
}

/**
 * Composable that tracks recompositions for performance analysis
 * @param componentName Name of the component being tracked
 * @param reason Optional reason for recomposition tracking
 */
@Composable
fun RecompositionCounter(
    componentName: String,
    reason: String? = null
) {
    if (BuildConfig.DEBUG) {
        var count by remember { mutableStateOf(0) }
        
        SideEffect {
            count++
            if (count > 1) {
                val reasonText = reason?.let { " (reason: $it)" } ?: ""
                Timber.v("RecompositionCounter: $componentName recomposed #$count$reasonText")
            }
        }
    }
}

/**
 * Advanced recomposition tracker with detailed analysis
 */
object RecompositionAnalyzer {
    
    private val trackingData = mutableMapOf<String, RecompositionData>()
    
    /**
     * Start tracking a component's recomposition patterns
     * @param componentName Component identifier
     */
    fun startTracking(componentName: String) {
        if (BuildConfig.DEBUG) {
            trackingData[componentName] = RecompositionData(
                componentName = componentName,
                startTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Record a recomposition event
     * @param componentName Component identifier
     * @param trigger What triggered the recomposition
     */
    fun recordRecomposition(componentName: String, trigger: String = "unknown") {
        if (BuildConfig.DEBUG) {
            trackingData[componentName]?.let { data ->
                data.recompositions.add(
                    RecompositionEvent(
                        timestamp = System.currentTimeMillis(),
                        trigger = trigger
                    )
                )
                
                // Analyze patterns for optimization suggestions
                analyzeRecompositionPattern(data)
            }
        }
    }
    
    /**
     * Analyze recomposition patterns and provide optimization suggestions
     */
    private fun analyzeRecompositionPattern(data: RecompositionData) {
        val recentEvents = data.recompositions.takeLast(5)
        if (recentEvents.size >= 5) {
            val timeDiffs = recentEvents.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val avgInterval = timeDiffs.average()
            
            if (avgInterval < 100) { // Less than 100ms between recompositions
                Timber.w("RecompositionAnalyzer: Rapid recompositions detected in ${data.componentName} " +
                        "(avg ${avgInterval.toInt()}ms) - Consider using remember() or derivedStateOf()")
            }
        }
    }
    
    /**
     * Get optimization suggestions for a component
     */
    fun getOptimizationSuggestions(componentName: String): List<String> {
        val data = trackingData[componentName] ?: return emptyList()
        val suggestions = mutableListOf<String>()
        
        val totalRecompositions = data.recompositions.size
        val duration = System.currentTimeMillis() - data.startTime
        
        when {
            totalRecompositions > 20 -> {
                suggestions.add("Consider using remember() for expensive calculations")
                suggestions.add("Check if state changes are necessary")
                suggestions.add("Use derivedStateOf() for computed values")
            }
            totalRecompositions > 10 -> {
                suggestions.add("Review state dependencies")
                suggestions.add("Consider memoization with remember()")
            }
        }
        
        // Check for rapid successive recompositions
        val rapidRecompositions = data.recompositions.windowed(3) { window ->
            window.last().timestamp - window.first().timestamp < 200
        }.count { it }
        
        if (rapidRecompositions > 2) {
            suggestions.add("Rapid recompositions detected - check for unstable state")
        }
        
        return suggestions
    }
}

/**
 * Data class for tracking recomposition events
 */
private data class RecompositionData(
    val componentName: String,
    val startTime: Long,
    val recompositions: MutableList<RecompositionEvent> = mutableListOf()
)

/**
 * Individual recomposition event
 */
private data class RecompositionEvent(
    val timestamp: Long,
    val trigger: String
)

/**
 * Utility composable for debugging recomposition reasons
 */
@Composable
fun RecompositionDebugger(
    componentName: String,
    vararg keys: Any?
) {
    if (BuildConfig.DEBUG) {
        var previousKeys by remember { mutableStateOf(keys.toList()) }
        
        LaunchedEffect(*keys) {
            val changedKeys = keys.zip(previousKeys) { new, old -> new != old }
                .mapIndexedNotNull { index, changed -> 
                    if (changed) "key$index: ${previousKeys.getOrNull(index)} -> ${keys[index]}" 
                    else null 
                }
            
            if (changedKeys.isNotEmpty()) {
                Timber.d("RecompositionDebugger: $componentName recomposed due to: ${changedKeys.joinToString()}")
            }
            
            previousKeys = keys.toList()
        }
    }
}
