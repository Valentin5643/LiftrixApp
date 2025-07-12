package com.example.liftrix.debug

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

/**
 * Comprehensive debugging utility for Liftrix application
 * Provides debugging tools, performance monitoring, system validation, and error tracking
 * Enhanced with animation tracking, composition monitoring, and comprehensive reporting
 */
object LiftrixDebugger {
    
    private const val TAG = "LiftrixDebugger"
    private const val DEBUG_PREFIX = "🔧 DEBUG:"
    private const val ERROR_PREFIX = "❌ ERROR:"
    private const val WARNING_PREFIX = "⚠️ WARNING:"
    private const val INFO_PREFIX = "ℹ️ INFO:"
    private const val PERFORMANCE_PREFIX = "⚡ PERF:"
    private const val ANIMATION_PREFIX = "🎬 ANIM:"
    private const val MEMORY_PREFIX = "💾 MEMORY:"
    private const val BUILD_PREFIX = "🏗️ BUILD:"
    private const val SYSTEM_PREFIX = "🖥️ SYSTEM:"
    
    private val debugScope = CoroutineScope(Dispatchers.IO)
    private val _debugEvents = MutableStateFlow<List<DebugEvent>>(emptyList())
    val debugEvents: StateFlow<List<DebugEvent>> = _debugEvents.asStateFlow()
    
    // Performance tracking
    private val animationTimes = mutableMapOf<String, Long>()
    private val compositionCounts = mutableMapOf<String, Int>()
    private val performanceMetrics = mutableMapOf<String, PerformanceMetric>()
    private var lastMemoryCheck = 0L
    private var debugEnabled = true
    
    /**
     * Logs build-related information with enhanced context
     */
    fun build(message: String, tag: String = TAG, context: String? = null) {
        val timestamp = Instant.now().toString()
        val contextInfo = context?.let { " [$it]" } ?: ""
        val formattedMessage = "$BUILD_PREFIX [$timestamp]$contextInfo $message"
        
        logEvent(DebugEvent.Build(message, tag, timestamp, context))
        
        Timber.tag(tag).i(formattedMessage)
        Log.i(tag, formattedMessage)
    }
    
    /**
     * Initialize the enhanced debugger
     */
    fun initialize(context: Context) {
        build("Enhanced Liftrix Debugger initialized")
        info("Debug mode: ${com.example.liftrix.BuildConfig.DEBUG}")
        logSystemInfo(context)
        validateSystemCapabilities(context)
    }
    
    /**
     * Enable or disable debug logging
     */
    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
        info("Debug logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Logs debug information with enhanced context
     */
    fun debug(message: String, tag: String = TAG, context: String? = null, throwable: Throwable? = null) {
        if (!debugEnabled) return
        
        val timestamp = Instant.now().toString()
        val contextInfo = context?.let { " [$it]" } ?: ""
        val formattedMessage = "$DEBUG_PREFIX [$timestamp]$contextInfo $message"
        
        logEvent(DebugEvent.Debug(message, tag, timestamp, context, throwable))
        
        if (throwable != null) {
            Timber.tag(tag).d(throwable, formattedMessage)
            Log.d(tag, formattedMessage, throwable)
        } else {
            Timber.tag(tag).d(formattedMessage)
            Log.d(tag, formattedMessage)
        }
    }
    
    /**
     * Logs error information with enhanced tracking
     */
    fun error(message: String, tag: String = TAG, context: String? = null, throwable: Throwable? = null) {
        val timestamp = Instant.now().toString()
        val contextInfo = context?.let { " [$it]" } ?: ""
        val formattedMessage = "$ERROR_PREFIX [$timestamp]$contextInfo $message"
        
        logEvent(DebugEvent.Error(message, tag, timestamp, context, throwable))
        
        if (throwable != null) {
            Timber.tag(tag).e(throwable, formattedMessage)
            Log.e(tag, formattedMessage, throwable)
        } else {
            Timber.tag(tag).e(formattedMessage)
            Log.e(tag, formattedMessage)
        }
    }
    
    /**
     * Logs warning information with context
     */
    fun warning(message: String, tag: String = TAG, context: String? = null, throwable: Throwable? = null) {
        val timestamp = Instant.now().toString()
        val contextInfo = context?.let { " [$it]" } ?: ""
        val formattedMessage = "$WARNING_PREFIX [$timestamp]$contextInfo $message"
        
        logEvent(DebugEvent.Warning(message, tag, timestamp, context, throwable))
        
        if (throwable != null) {
            Timber.tag(tag).w(throwable, formattedMessage)
            Log.w(tag, formattedMessage, throwable)
        } else {
            Timber.tag(tag).w(formattedMessage)
            Log.w(tag, formattedMessage)
        }
    }
    
    /**
     * Logs informational messages
     */
    fun info(message: String, tag: String = TAG, context: String? = null) {
        val timestamp = Instant.now().toString()
        val contextInfo = context?.let { " [$it]" } ?: ""
        val formattedMessage = "$INFO_PREFIX [$timestamp]$contextInfo $message"
        
        logEvent(DebugEvent.Info(message, tag, timestamp, context))
        
        Timber.tag(tag).i(formattedMessage)
        Log.i(tag, formattedMessage)
    }
    
    /**
     * Logs performance metrics
     */
    fun performance(
        operation: String,
        duration: Long? = null,
        tag: String = TAG,
        context: String? = null,
        metrics: Map<String, Any>? = null
    ) {
        val timestamp = Instant.now().toString()
        val contextInfo = context?.let { " [$it]" } ?: ""
        val durationText = duration?.let { " (${it}ms)" } ?: ""
        val formattedMessage = "$PERFORMANCE_PREFIX [$timestamp]$contextInfo $operation$durationText"
        
        // Store performance metric
        if (duration != null) {
            performanceMetrics[operation] = PerformanceMetric(operation, duration, timestamp, metrics)
        }
        
        logEvent(DebugEvent.Performance(operation, tag, timestamp, context, duration, metrics))
        
        Timber.tag(tag).i(formattedMessage)
        Log.i(tag, formattedMessage)
        
        // Check for performance issues
        if (duration != null && duration > 100) {
            warning("Slow operation detected: $operation took ${duration}ms", tag, context)
        }
    }
    
    /**
     * Tracks animation performance with detailed metrics
     */
    fun trackAnimation(
        animationName: String,
        duration: Long,
        frameCount: Int? = null,
        droppedFrames: Int? = null,
        context: String? = null
    ) {
        animationTimes[animationName] = duration
        val timestamp = Instant.now().toString()
        
        val metrics = mutableMapOf<String, Any>()
        frameCount?.let { metrics["frameCount"] = it }
        droppedFrames?.let { metrics["droppedFrames"] = it }
        
        val formattedMessage = buildString {
            append("$ANIMATION_PREFIX [$timestamp] Animation '$animationName' completed in ${duration}ms")
            frameCount?.let { append(" (${it} frames)") }
            droppedFrames?.let { append(" (${it} dropped)") }
        }
        
        logEvent(DebugEvent.Animation(animationName, TAG, timestamp, context, duration, metrics))
        
        Timber.tag(TAG).i(formattedMessage)
        Log.i(TAG, formattedMessage)
        
        // Performance analysis
        if (duration > 16) {
            warning("Animation '$animationName' exceeded 16ms frame budget: ${duration}ms", TAG, context)
        }
        
        droppedFrames?.let { dropped ->
            if (dropped > 0) {
                warning("Animation '$animationName' dropped $dropped frames", TAG, context)
            }
        }
    }
    
    /**
     * Tracks composition performance with recomposition analysis
     */
    fun trackComposition(
        composableName: String,
        recompositions: Int,
        context: String? = null,
        reason: String? = null
    ) {
        compositionCounts[composableName] = recompositions
        val timestamp = Instant.now().toString()
        
        val reasonText = reason?.let { " (reason: $it)" } ?: ""
        val formattedMessage = "$PERFORMANCE_PREFIX [$timestamp] Composable '$composableName' recomposed $recompositions times$reasonText"
        
        logEvent(DebugEvent.Composition(composableName, TAG, timestamp, context, recompositions, reason))
        
        Timber.tag(TAG).i(formattedMessage)
        Log.i(TAG, formattedMessage)
        
        // Analyze recomposition frequency
        if (recompositions > 10) {
            warning("Excessive recompositions in '$composableName': $recompositions times", TAG, context)
        }
    }
    
    /**
     * Logs memory usage with detailed analysis
     */
    fun logMemoryUsage(force: Boolean = false, context: String? = null) {
        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastMemoryCheck < 30000) return
        lastMemoryCheck = currentTime
        
        debugScope.launch {
            try {
                val runtime = Runtime.getRuntime()
                val totalMemory = runtime.totalMemory() / 1024 / 1024
                val freeMemory = runtime.freeMemory() / 1024 / 1024
                val usedMemory = totalMemory - freeMemory
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                val usagePercentage = (usedMemory.toFloat() / maxMemory * 100).toInt()
                
                val timestamp = Instant.now().toString()
                val contextInfo = context?.let { " [$it]" } ?: ""
                val formattedMessage = "$MEMORY_PREFIX [$timestamp]$contextInfo Memory: ${usedMemory}MB used, ${freeMemory}MB free, ${maxMemory}MB max (${usagePercentage}%)"
                
                val metrics = mapOf(
                    "usedMemory" to usedMemory,
                    "freeMemory" to freeMemory,
                    "maxMemory" to maxMemory,
                    "usagePercentage" to usagePercentage
                )
                
                logEvent(DebugEvent.Memory("Memory usage", TAG, timestamp, context, usedMemory, metrics))
                
                Timber.tag(TAG).i(formattedMessage)
                Log.i(TAG, formattedMessage)
                
                // Memory warnings
                when {
                    usagePercentage > 90 -> error("Critical memory usage: ${usagePercentage}%", TAG, context)
                    usagePercentage > 80 -> warning("High memory usage: ${usagePercentage}%", TAG, context)
                    usagePercentage > 70 -> info("Moderate memory usage: ${usagePercentage}%", TAG, context)
                }
                
            } catch (e: Exception) {
                error("Failed to log memory usage", TAG, context, e)
            }
        }
    }
    
    /**
     * Validates system capabilities and logs findings
     */
    fun validateSystemCapabilities(context: Context) {
        val timestamp = Instant.now().toString()
        
        try {
            build("Validating system capabilities")
            
            // Check available memory
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            info("Available heap memory: ${maxMemory}MB", TAG, "SystemValidation")
            
            // Check processor info
            val processors = runtime.availableProcessors()
            info("Available processors: $processors", TAG, "SystemValidation")
            
            // Check Android version compatibility
            val apiLevel = Build.VERSION.SDK_INT
            info("Android API level: $apiLevel", TAG, "SystemValidation")
            
            if (apiLevel < 26) {
                warning("Running on older Android version (API $apiLevel)", TAG, "SystemValidation")
            }
            
            build("System capabilities validation completed")
            
        } catch (e: Exception) {
            error("System capabilities validation failed", TAG, "SystemValidation", e)
        }
    }
    
    /**
     * Logs system information
     */
    private fun logSystemInfo(context: Context) {
        val timestamp = Instant.now().toString()
        
        try {
            val systemInfo = buildString {
                appendLine("=== SYSTEM INFORMATION ===")
                appendLine("Android Version: ${Build.VERSION.RELEASE}")
                appendLine("API Level: ${Build.VERSION.SDK_INT}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Architecture: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                appendLine("Build Type: ${Build.TYPE}")
                appendLine("Build Tags: ${Build.TAGS}")
            }
            
            info(systemInfo, TAG, "SystemInfo")
            
        } catch (e: Exception) {
            error("Failed to log system information", TAG, "SystemInfo", e)
        }
    }
    
    /**
     * Creates a comprehensive debug report
     */
    fun createEnhancedDebugReport(context: Context): EnhancedDebugReport {
        val timestamp = Instant.now()
        
        val buildInfo = EnhancedBuildInfo(
            debug = com.example.liftrix.BuildConfig.DEBUG,
            versionName = com.example.liftrix.BuildConfig.VERSION_NAME,
            versionCode = com.example.liftrix.BuildConfig.VERSION_CODE,
            buildType = com.example.liftrix.BuildConfig.BUILD_TYPE,
            compileSdkVersion = Build.VERSION.SDK_INT,
            buildTimestamp = timestamp
        )
        
        val systemInfo = EnhancedSystemInfo(
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024,
            buildTags = Build.TAGS,
            buildType = Build.TYPE
        )
        
        val performanceInfo = EnhancedPerformanceInfo(
            animationMetrics = animationTimes.toMap(),
            compositionMetrics = compositionCounts.toMap(),
            performanceMetrics = performanceMetrics.toMap(),
            currentMemoryUsage = getCurrentMemoryUsage(),
            memoryPressure = getMemoryPressure()
        )
        
        return EnhancedDebugReport(
            timestamp = timestamp,
            buildInfo = buildInfo,
            systemInfo = systemInfo,
            performanceInfo = performanceInfo,
            recentEvents = _debugEvents.value.takeLast(100),
            debugEnabled = debugEnabled
        )
    }
    
    /**
     * Exports enhanced debug report as formatted string
     */
    fun exportEnhancedDebugReport(context: Context): String {
        return createEnhancedDebugReport(context).format()
    }
    
    /**
     * Clears all debug data
     */
    fun clearDebugData() {
        _debugEvents.value = emptyList()
        animationTimes.clear()
        compositionCounts.clear()
        performanceMetrics.clear()
        info("Debug data cleared")
    }
    
    /**
     * Clears all debug events (alias for clearDebugData)
     */
    fun clearDebugEvents() {
        clearDebugData()
    }
    
    /**
     * Exports debug session as formatted string
     */
    fun exportDebugSession(): String {
        return try {
            // Create a simple debug report without context
            buildString {
                appendLine("=== LIFTRIX DEBUG SESSION EXPORT ===")
                appendLine("Generated: ${Instant.now()}")
                appendLine("Debug Enabled: $debugEnabled")
                appendLine()
                
                appendLine("=== BUILD INFORMATION ===")
                appendLine("Debug Mode: ${com.example.liftrix.BuildConfig.DEBUG}")
                appendLine("Version: ${com.example.liftrix.BuildConfig.VERSION_NAME} (${com.example.liftrix.BuildConfig.VERSION_CODE})")
                appendLine("Build Type: ${com.example.liftrix.BuildConfig.BUILD_TYPE}")
                appendLine()
                
                appendLine("=== PERFORMANCE SUMMARY ===")
                appendLine("Current Memory Usage: ${getCurrentMemoryUsage()}MB")
                appendLine("Memory Pressure: ${getMemoryPressure()}")
                appendLine("Animations Tracked: ${animationTimes.size}")
                appendLine("Compositions Tracked: ${compositionCounts.size}")
                appendLine("Operations Tracked: ${performanceMetrics.size}")
                appendLine()
                
                if (animationTimes.isNotEmpty()) {
                    appendLine("=== ANIMATION PERFORMANCE ===")
                    val avgAnimTime = animationTimes.values.average()
                    appendLine("Average Duration: ${avgAnimTime.toInt()}ms")
                    val slowAnimations = animationTimes.filter { it.value > 16 }
                    if (slowAnimations.isNotEmpty()) {
                        appendLine("Slow Animations (>16ms):")
                        slowAnimations.entries.sortedByDescending { it.value }.take(10).forEach {
                            appendLine("  ${it.key}: ${it.value}ms")
                        }
                    }
                    appendLine()
                }
                
                if (compositionCounts.isNotEmpty()) {
                    appendLine("=== COMPOSITION PERFORMANCE ===")
                    val avgCompositions = compositionCounts.values.average()
                    appendLine("Average Recompositions: ${avgCompositions.toInt()}")
                    val excessiveCompositions = compositionCounts.filter { it.value > 10 }
                    if (excessiveCompositions.isNotEmpty()) {
                        appendLine("Excessive Recompositions (>10):")
                        excessiveCompositions.entries.sortedByDescending { it.value }.take(10).forEach {
                            appendLine("  ${it.key}: ${it.value} times")
                        }
                    }
                    appendLine()
                }
                
                if (_debugEvents.value.isNotEmpty()) {
                    appendLine("=== RECENT DEBUG EVENTS ===")
                    _debugEvents.value.takeLast(30).forEach { event ->
                        val prefix = when (event) {
                            is DebugEvent.Debug -> "🔧"
                            is DebugEvent.Info -> "ℹ️"
                            is DebugEvent.Warning -> "⚠️"
                            is DebugEvent.Error -> "❌"
                            is DebugEvent.Performance -> "⚡"
                            is DebugEvent.Animation -> "🎬"
                            is DebugEvent.Composition -> "🔄"
                            is DebugEvent.Memory -> "💾"
                            is DebugEvent.Build -> "🏗️"
                            is DebugEvent.System -> "🖥️"
                            is DebugEvent.Validation -> "✅"
                        }
                        val contextInfo = when (event) {
                            is DebugEvent.Debug -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Error -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Warning -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Info -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Performance -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Animation -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Composition -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Memory -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Build -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.System -> event.context?.let { "[$it] " } ?: ""
                            is DebugEvent.Validation -> event.context?.let { "[$it] " } ?: ""
                        }
                        appendLine("$prefix [${event.timestamp}] $contextInfo${event.message}")
                    }
                    appendLine()
                }
                
                appendLine("=== END DEBUG SESSION EXPORT ===")
            }
        } catch (e: Exception) {
            error("Failed to export debug session", throwable = e)
            "Failed to export debug session: ${e.message}"
        }
    }
    
    /**
     * Tracks animation start time
     */
    fun trackAnimationStart(animationName: String): Long {
        val startTime = System.currentTimeMillis()
        debug("Animation '$animationName' started", context = "AnimationTracking")
        return startTime
    }
    
    /**
     * Tracks animation end time and logs performance
     */
    fun trackAnimationEnd(animationName: String, startTime: Long) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        trackAnimation(animationName, duration, context = "AnimationTracking")
    }
    
    /**
     * Tracks composition performance (alias for trackComposition)
     */
    fun trackCompositionPerformance(composableName: String, recompositions: Int, reason: String? = null) {
        trackComposition(composableName, recompositions, context = "CompositionTracking", reason = reason)
    }
    
    /**
     * Validates data model structure
     */
    fun validateDataModel(modelName: String, data: Any): ValidationResult {
        val timestamp = Instant.now().toString()
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            debug("Validating data model: $modelName", context = "DataValidation")
            
            // Basic validation checks
            when (data) {
                is Map<*, *> -> {
                    if (data.isEmpty()) warnings.add("Empty map")
                    data.keys.forEach { key ->
                        if (key == null) issues.add("Null key found in map")
                    }
                }
                is Collection<*> -> {
                    if (data.isEmpty()) warnings.add("Empty collection")
                    if (data.contains(null)) warnings.add("Null elements in collection")
                }
                is String -> {
                    if (data.isEmpty()) warnings.add("Empty string")
                    if (data.isBlank()) warnings.add("Blank string")
                }
                null -> issues.add("Data model is null")
            }
            
            val isValid = issues.isEmpty()
            val result = ValidationResult(isValid, issues, warnings)
            
            logEvent(DebugEvent.Validation(
                validationType = "DataModel_$modelName",
                isValid = isValid,
                tag = TAG,
                timestamp = timestamp,
                context = "DataValidation",
                details = mapOf("issues" to issues, "warnings" to warnings)
            ))
            
            if (isValid) {
                info("Data model '$modelName' validation passed", context = "DataValidation")
            } else {
                warning("Data model '$modelName' validation failed: ${issues.joinToString()}", context = "DataValidation")
            }
            
            return result
            
        } catch (e: Exception) {
            error("Data model validation failed for '$modelName'", throwable = e, context = "DataValidation")
            return ValidationResult(false, listOf("Validation error: ${e.message}"))
        }
    }
    
    /**
     * Validates application state
     */
    fun validateApplicationState(): ValidationResult {
        val timestamp = Instant.now().toString()
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            debug("Validating application state", context = "AppValidation")
            
            // Check memory state
            val memoryUsage = getCurrentMemoryUsage()
            val memoryPressure = getMemoryPressure()
            
            when (memoryPressure) {
                "CRITICAL" -> issues.add("Critical memory pressure detected")
                "HIGH" -> warnings.add("High memory pressure detected")
                "MODERATE" -> warnings.add("Moderate memory pressure detected")
            }
            
            // Check debug events
            val eventCount = _debugEvents.value.size
            if (eventCount > 800) {
                warnings.add("High number of debug events: $eventCount")
            }
            
            // Check performance metrics
            val slowAnimations = animationTimes.filter { it.value > 50 }
            if (slowAnimations.isNotEmpty()) {
                warnings.add("${slowAnimations.size} slow animations detected")
            }
            
            val excessiveCompositions = compositionCounts.filter { it.value > 20 }
            if (excessiveCompositions.isNotEmpty()) {
                warnings.add("${excessiveCompositions.size} composables with excessive recompositions")
            }
            
            val isValid = issues.isEmpty()
            val result = ValidationResult(isValid, issues, warnings)
            
            logEvent(DebugEvent.Validation(
                validationType = "ApplicationState",
                isValid = isValid,
                tag = TAG,
                timestamp = timestamp,
                context = "AppValidation",
                details = mapOf(
                    "memoryUsage" to memoryUsage,
                    "memoryPressure" to memoryPressure,
                    "eventCount" to eventCount,
                    "slowAnimations" to slowAnimations.size,
                    "excessiveCompositions" to excessiveCompositions.size
                )
            ))
            
            if (isValid) {
                info("Application state validation passed", context = "AppValidation")
            } else {
                warning("Application state validation failed: ${issues.joinToString()}", context = "AppValidation")
            }
            
            return result
            
        } catch (e: Exception) {
            error("Application state validation failed", throwable = e, context = "AppValidation")
            return ValidationResult(false, listOf("Validation error: ${e.message}"))
        }
    }
    
    /**
     * Validates build configuration
     */
    fun validateBuildConfiguration(): ValidationResult {
        val timestamp = Instant.now().toString()
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            build("Starting build configuration validation")
            
            // Check if debug build
            if (com.example.liftrix.BuildConfig.DEBUG) {
                build("Running in DEBUG mode")
                info("Debug features enabled")
            } else {
                build("Running in RELEASE mode")
                warnings.add("Running in RELEASE mode - some debug features may be disabled")
            }
            
            // Validate Timber configuration
            try {
                Timber.d("Timber validation test")
                build("Timber logging is properly configured")
            } catch (e: Exception) {
                issues.add("Timber logging configuration issue: ${e.message}")
                error("Timber validation failed", throwable = e)
            }
            
            // Check system compatibility
            val apiLevel = android.os.Build.VERSION.SDK_INT
            if (apiLevel < 26) {
                warnings.add("Running on older Android version (API $apiLevel)")
            }
            
            val isValid = issues.isEmpty()
            val result = ValidationResult(isValid, issues, warnings)
            
            logEvent(DebugEvent.Validation(
                validationType = "BuildConfiguration",
                isValid = isValid,
                tag = TAG,
                timestamp = timestamp,
                context = "BuildValidation",
                details = mapOf(
                    "debugMode" to com.example.liftrix.BuildConfig.DEBUG,
                    "apiLevel" to apiLevel,
                    "issues" to issues,
                    "warnings" to warnings
                )
            ))
            
            if (isValid) {
                build("Build configuration validation passed")
            } else {
                warning("Build configuration validation failed: ${issues.joinToString()}", context = "BuildValidation")
            }
            
            return result
            
        } catch (e: Exception) {
            error("Build configuration validation failed", throwable = e, context = "BuildValidation")
            return ValidationResult(false, listOf("Validation error: ${e.message}"))
        }
    }
    
    /**
     * Gets performance summary
     */
    fun getPerformanceSummary(): String {
        return buildString {
            appendLine("=== PERFORMANCE SUMMARY ===")
            
            if (animationTimes.isNotEmpty()) {
                val avgAnimTime = animationTimes.values.average()
                val slowAnimations = animationTimes.filter { it.value > 16 }
                appendLine("Animations:")
                appendLine("  Total tracked: ${animationTimes.size}")
                appendLine("  Average duration: ${avgAnimTime.toInt()}ms")
                appendLine("  Slow animations (>16ms): ${slowAnimations.size}")
            }
            
            if (compositionCounts.isNotEmpty()) {
                val avgCompositions = compositionCounts.values.average()
                val excessiveCompositions = compositionCounts.filter { it.value > 10 }
                appendLine("Compositions:")
                appendLine("  Total tracked: ${compositionCounts.size}")
                appendLine("  Average recompositions: ${avgCompositions.toInt()}")
                appendLine("  Excessive recompositions (>10): ${excessiveCompositions.size}")
            }
            
            if (performanceMetrics.isNotEmpty()) {
                val avgDuration = performanceMetrics.values.mapNotNull { it.duration }.average()
                appendLine("Operations:")
                appendLine("  Total tracked: ${performanceMetrics.size}")
                appendLine("  Average duration: ${avgDuration.toInt()}ms")
            }
            
            appendLine("Memory:")
            appendLine("  Current usage: ${getCurrentMemoryUsage()}MB")
            appendLine("  Memory pressure: ${getMemoryPressure()}")
        }
    }
    
    private fun logEvent(event: DebugEvent) {
        val currentEvents = _debugEvents.value.toMutableList()
        currentEvents.add(event)
        
        // Keep only last 1000 events
        if (currentEvents.size > 1000) {
            currentEvents.removeAt(0)
        }
        
        _debugEvents.value = currentEvents
    }
    
    private fun getCurrentMemoryUsage(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getMemoryPressure(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val percentage = (usedMemory.toFloat() / maxMemory * 100).toInt()
            
            when {
                percentage > 90 -> "CRITICAL"
                percentage > 80 -> "HIGH"
                percentage > 70 -> "MODERATE"
                percentage > 50 -> "LOW"
                else -> "NORMAL"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}



/**
 * Enhanced build information
 */
data class EnhancedBuildInfo(
    val debug: Boolean,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val compileSdkVersion: Int,
    val buildTimestamp: Instant
)

/**
 * Enhanced system information
 */
data class EnhancedSystemInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val device: String,
    val architecture: String,
    val availableProcessors: Int,
    val maxMemory: Long,
    val buildTags: String,
    val buildType: String
)

/**
 * Enhanced performance information
 */
data class EnhancedPerformanceInfo(
    val animationMetrics: Map<String, Long>,
    val compositionMetrics: Map<String, Int>,
    val performanceMetrics: Map<String, PerformanceMetric>,
    val currentMemoryUsage: Long,
    val memoryPressure: String
)

/**
 * Enhanced debug report
 */
data class EnhancedDebugReport(
    val timestamp: Instant,
    val buildInfo: EnhancedBuildInfo,
    val systemInfo: EnhancedSystemInfo,
    val performanceInfo: EnhancedPerformanceInfo,
    val recentEvents: List<DebugEvent>,
    val debugEnabled: Boolean
) {
    fun format(): String {
        return buildString {
            appendLine("=== ENHANCED LIFTRIX DEBUG REPORT ===")
            appendLine("Generated: $timestamp")
            appendLine("Debug Enabled: $debugEnabled")
            appendLine()
            
            appendLine("=== BUILD INFORMATION ===")
            appendLine("Debug Mode: ${buildInfo.debug}")
            appendLine("Version: ${buildInfo.versionName} (${buildInfo.versionCode})")
            appendLine("Build Type: ${buildInfo.buildType}")
            appendLine("Compile SDK: ${buildInfo.compileSdkVersion}")
            appendLine("Build Timestamp: ${buildInfo.buildTimestamp}")
            appendLine()
            
            appendLine("=== SYSTEM INFORMATION ===")
            appendLine("Android Version: ${systemInfo.androidVersion} (API ${systemInfo.apiLevel})")
            appendLine("Device: ${systemInfo.device}")
            appendLine("Architecture: ${systemInfo.architecture}")
            appendLine("Processors: ${systemInfo.availableProcessors}")
            appendLine("Max Memory: ${systemInfo.maxMemory}MB")
            appendLine("Build Tags: ${systemInfo.buildTags}")
            appendLine("Build Type: ${systemInfo.buildType}")
            appendLine()
            
            appendLine("=== PERFORMANCE SUMMARY ===")
            appendLine("Current Memory Usage: ${performanceInfo.currentMemoryUsage}MB")
            appendLine("Memory Pressure: ${performanceInfo.memoryPressure}")
            appendLine("Animations Tracked: ${performanceInfo.animationMetrics.size}")
            appendLine("Compositions Tracked: ${performanceInfo.compositionMetrics.size}")
            appendLine("Operations Tracked: ${performanceInfo.performanceMetrics.size}")
            appendLine()
            
            if (performanceInfo.animationMetrics.isNotEmpty()) {
                appendLine("=== ANIMATION PERFORMANCE ===")
                val avgAnimTime = performanceInfo.animationMetrics.values.average()
                appendLine("Average Duration: ${avgAnimTime.toInt()}ms")
                val slowAnimations = performanceInfo.animationMetrics.filter { it.value > 16 }
                if (slowAnimations.isNotEmpty()) {
                    appendLine("Slow Animations (>16ms):")
                    slowAnimations.entries.sortedByDescending { it.value }.take(10).forEach {
                        appendLine("  ${it.key}: ${it.value}ms")
                    }
                }
                appendLine()
            }
            
            if (performanceInfo.compositionMetrics.isNotEmpty()) {
                appendLine("=== COMPOSITION PERFORMANCE ===")
                val avgCompositions = performanceInfo.compositionMetrics.values.average()
                appendLine("Average Recompositions: ${avgCompositions.toInt()}")
                val excessiveCompositions = performanceInfo.compositionMetrics.filter { it.value > 10 }
                if (excessiveCompositions.isNotEmpty()) {
                    appendLine("Excessive Recompositions (>10):")
                    excessiveCompositions.entries.sortedByDescending { it.value }.take(10).forEach {
                        appendLine("  ${it.key}: ${it.value} times")
                    }
                }
                appendLine()
            }
            
            if (performanceInfo.performanceMetrics.isNotEmpty()) {
                appendLine("=== OPERATION PERFORMANCE ===")
                val avgDuration = performanceInfo.performanceMetrics.values.mapNotNull { it.duration }.average()
                appendLine("Average Operation Duration: ${avgDuration.toInt()}ms")
                val slowOperations = performanceInfo.performanceMetrics.filter { it.value.duration > 100 }
                if (slowOperations.isNotEmpty()) {
                    appendLine("Slow Operations (>100ms):")
                    slowOperations.entries.sortedByDescending { it.value.duration }.take(10).forEach {
                        appendLine("  ${it.key}: ${it.value.duration}ms")
                    }
                }
                appendLine()
            }
            
            if (recentEvents.isNotEmpty()) {
                appendLine("=== RECENT DEBUG EVENTS ===")
                recentEvents.takeLast(30).forEach { event ->
                    val prefix = when (event) {
                        is DebugEvent.Debug -> "🔧"
                        is DebugEvent.Info -> "ℹ️"
                        is DebugEvent.Warning -> "⚠️"
                        is DebugEvent.Error -> "❌"
                        is DebugEvent.Performance -> "⚡"
                        is DebugEvent.Animation -> "🎬"
                        is DebugEvent.Composition -> "🔄"
                        is DebugEvent.Memory -> "💾"
                        is DebugEvent.Build -> "🏗️"
                        is DebugEvent.System -> "🖥️"
                        is DebugEvent.Validation -> "✅"
                    }
                    val contextInfo = when (event) {
                        is DebugEvent.Debug -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Error -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Warning -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Info -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Performance -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Animation -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Composition -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Memory -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Build -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.System -> event.context?.let { "[$it] " } ?: ""
                        is DebugEvent.Validation -> event.context?.let { "[$it] " } ?: ""
                    }
                    appendLine("$prefix [${event.timestamp}] $contextInfo${event.message}")
                }
                appendLine()
            }
            
            appendLine("=== END ENHANCED DEBUG REPORT ===")
        }
    }
}

/**
 * Composable for tracking composition performance
 */
@Composable
fun rememberEnhancedCompositionTracker(
    composableName: String,
    context: String? = null
): EnhancedCompositionTracker {
    return remember(composableName, context) {
        EnhancedCompositionTracker(composableName, context)
    }
}

/**
 * Enhanced composition tracker
 */
class EnhancedCompositionTracker(
    private val composableName: String,
    private val context: String? = null
) {
    private var recompositionCount = 0
    private var lastRecompositionTime = System.currentTimeMillis()
    
    fun trackRecomposition(reason: String? = null) {
        recompositionCount++
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRecomposition = currentTime - lastRecompositionTime
        lastRecompositionTime = currentTime
        
        LiftrixDebugger.trackComposition(
            composableName = composableName,
            recompositions = recompositionCount,
            context = context,
            reason = reason ?: "Unknown"
        )
        
        // Log rapid recomposition
        if (timeSinceLastRecomposition < 16) {
            LiftrixDebugger.warning(
                "Rapid recomposition in '$composableName' (${timeSinceLastRecomposition}ms since last)",
                context = context
            )
        }
    }
    
    fun reset() {
        recompositionCount = 0
        lastRecompositionTime = System.currentTimeMillis()
    }
} 