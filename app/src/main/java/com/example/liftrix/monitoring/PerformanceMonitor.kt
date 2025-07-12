package com.example.liftrix.monitoring

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor(
    private val firebasePerformance: FirebasePerformance
) {

    companion object {
        // Trace names for screen transitions
        private const val TRACE_SCREEN_TRANSITION = "screen_transition"
        private const val TRACE_ANIMATION_PERFORMANCE = "animation_performance"
        private const val TRACE_USER_INTERACTION = "user_interaction"
        
        // Attribute keys
        private const val ATTR_FROM_SCREEN = "from_screen"
        private const val ATTR_TO_SCREEN = "to_screen"
        private const val ATTR_ANIMATION_TYPE = "animation_type"
        private const val ATTR_INTERACTION_TYPE = "interaction_type"
        private const val ATTR_COMPONENT_TYPE = "component_type"
        
        // Metric keys
        private const val METRIC_DURATION_MS = "duration_ms"
        private const val METRIC_FRAME_COUNT = "frame_count"
        private const val METRIC_RESPONSE_TIME_MS = "response_time_ms"
    }

    /**
     * Track screen transition performance including navigation timing
     * 
     * @param fromScreen Source screen identifier
     * @param toScreen Destination screen identifier
     * @return Result indicating success or failure
     */
    suspend fun trackScreenTransition(fromScreen: String, toScreen: String): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace(TRACE_SCREEN_TRANSITION)
            trace.putAttribute(ATTR_FROM_SCREEN, fromScreen)
            trace.putAttribute(ATTR_TO_SCREEN, toScreen)
            trace.start()
            
            // Note: Trace should be stopped when transition completes
            // This is a demonstration - in real usage, return the trace for later stopping
            trace.stop()
            
            Timber.d("Screen transition tracked: $fromScreen -> $toScreen")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track screen transition: $fromScreen -> $toScreen")
            Result.failure(exception)
        }
    }

    /**
     * Track animation performance metrics including duration and smoothness
     * 
     * @param animationType Type of animation being tracked
     * @param duration Animation duration in milliseconds
     * @return Result indicating success or failure
     */
    suspend fun trackAnimationPerformance(animationType: String, duration: Long): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace(TRACE_ANIMATION_PERFORMANCE)
            trace.putAttribute(ATTR_ANIMATION_TYPE, animationType)
            trace.putMetric(METRIC_DURATION_MS, duration)
            trace.start()
            trace.stop()
            
            Timber.d("Animation performance tracked: $animationType (${duration}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track animation performance: $animationType")
            Result.failure(exception)
        }
    }

    /**
     * Track user interaction performance including response times
     * 
     * @param interactionType Type of user interaction (tap, swipe, etc.)
     * @param componentType UI component being interacted with
     * @param responseTimeMs Time taken to respond to interaction
     * @return Result indicating success or failure
     */
    suspend fun trackUserInteraction(
        interactionType: String,
        componentType: String,
        responseTimeMs: Long
    ): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace(TRACE_USER_INTERACTION)
            trace.putAttribute(ATTR_INTERACTION_TYPE, interactionType)
            trace.putAttribute(ATTR_COMPONENT_TYPE, componentType)
            trace.putMetric(METRIC_RESPONSE_TIME_MS, responseTimeMs)
            trace.start()
            trace.stop()
            
            Timber.d("User interaction tracked: $interactionType on $componentType (${responseTimeMs}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track user interaction: $interactionType")
            Result.failure(exception)
        }
    }

    /**
     * Create and start a custom trace for manual performance monitoring
     * 
     * @param traceName Name of the trace
     * @return Trace object for manual control, or null if creation failed
     */
    fun startCustomTrace(traceName: String): Trace? {
        return try {
            val trace = firebasePerformance.newTrace(traceName)
            trace.start()
            Timber.d("Custom trace started: $traceName")
            trace
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to start custom trace: $traceName")
            null
        }
    }

    /**
     * Stop a custom trace and record its metrics
     * 
     * @param trace The trace to stop
     * @param attributes Optional attributes to add before stopping
     * @param metrics Optional metrics to add before stopping
     * @return Result indicating success or failure
     */
    fun stopCustomTrace(
        trace: Trace,
        attributes: Map<String, String> = emptyMap(),
        metrics: Map<String, Long> = emptyMap()
    ): Result<Unit> {
        return try {
            // Add attributes
            attributes.forEach { (key, value) ->
                trace.putAttribute(key, value)
            }
            
            // Add metrics
            metrics.forEach { (key, value) ->
                trace.putMetric(key, value)
            }
            
            trace.stop()
            Timber.d("Custom trace stopped with ${attributes.size} attributes and ${metrics.size} metrics")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to stop custom trace")
            Result.failure(exception)
        }
    }

    /**
     * Track app startup performance
     * 
     * @param startupTimeMs Total app startup time in milliseconds
     * @param coldStart Whether this was a cold start
     * @return Result indicating success or failure
     */
    suspend fun trackAppStartup(startupTimeMs: Long, coldStart: Boolean): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("app_startup")
            trace.putAttribute("startup_type", if (coldStart) "cold" else "warm")
            trace.putMetric("startup_time_ms", startupTimeMs)
            trace.start()
            trace.stop()
            
            Timber.d("App startup tracked: ${if (coldStart) "cold" else "warm"} start (${startupTimeMs}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track app startup")
            Result.failure(exception)
        }
    }
} 