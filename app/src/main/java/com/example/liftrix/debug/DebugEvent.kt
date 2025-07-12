package com.example.liftrix.debug

/**
 * Sealed class representing different types of debug events
 * Used for tracking and reporting debug information throughout the application
 */
sealed class DebugEvent {
    abstract val message: String
    abstract val tag: String
    abstract val timestamp: String
    
    data class Debug(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val throwable: Throwable? = null
    ) : DebugEvent()
    
    data class Error(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val throwable: Throwable? = null
    ) : DebugEvent()
    
    data class Warning(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val throwable: Throwable? = null
    ) : DebugEvent()
    
    data class Info(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null
    ) : DebugEvent()
    
    data class Performance(
        val operation: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val duration: Long? = null,
        val metrics: Map<String, Any>? = null
    ) : DebugEvent() {
        override val message: String = "Performance: $operation${duration?.let { " (${it}ms)" } ?: ""}"
    }
    
    data class Animation(
        val animationName: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val duration: Long? = null,
        val metrics: Map<String, Any>? = null
    ) : DebugEvent() {
        override val message: String = "Animation: $animationName${duration?.let { " (${it}ms)" } ?: ""}"
    }
    
    data class Composition(
        val composableName: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val recompositions: Int,
        val reason: String? = null
    ) : DebugEvent() {
        override val message: String = "Composition: $composableName recomposed $recompositions times"
    }
    
    data class Memory(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val memoryUsage: Long? = null,
        val metrics: Map<String, Any>? = null
    ) : DebugEvent()
    
    data class Build(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null
    ) : DebugEvent()
    
    data class System(
        override val message: String,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val systemInfo: Map<String, Any>? = null
    ) : DebugEvent()
    
    data class Validation(
        val validationType: String,
        val isValid: Boolean,
        override val tag: String,
        override val timestamp: String,
        val context: String? = null,
        val details: Map<String, Any>? = null
    ) : DebugEvent() {
        override val message: String = "Validation: $validationType - ${if (isValid) "PASSED" else "FAILED"}"
    }
}

/**
 * Data class for performance metrics
 */
data class PerformanceMetric(
    val operation: String,
    val duration: Long,
    val timestamp: String,
    val additionalMetrics: Map<String, Any>? = null
)

/**
 * Data class for validation results
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val details: Map<String, Any>? = null
)

/**
 * Alias for backward compatibility
 */
typealias BuildValidationResult = ValidationResult
typealias ApplicationValidationResult = ValidationResult 