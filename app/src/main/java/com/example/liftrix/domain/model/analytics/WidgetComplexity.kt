package com.example.liftrix.domain.model.analytics

/**
 * Widget complexity levels affecting calculation requirements and refresh rates
 * 
 * Defines complexity tiers that determine:
 * - Data calculation intensity and processing time
 * - Automatic refresh frequency intervals
 * - Caching strategies and memory usage
 * - UI rendering performance characteristics
 * - Background processing priority levels
 * 
 * Complexity directly impacts system performance and user experience,
 * with smart refresh intervals optimized for each level.
 */
enum class WidgetComplexity(
    val displayName: String,
    val description: String,
    val minRefreshIntervalMinutes: Int,
    val maxRefreshIntervalMinutes: Int,
    val defaultRefreshIntervalMinutes: Int,
    val calculationTimeoutMs: Long,
    val cacheTtlMinutes: Int,
    val backgroundPriority: BackgroundPriority
) {
    /**
     * Simple widgets with minimal calculation requirements
     * Fast data retrieval, high refresh frequency, immediate updates
     */
    SIMPLE(
        displayName = "Simple",
        description = "Fast calculations with minimal processing requirements",
        minRefreshIntervalMinutes = 1,
        maxRefreshIntervalMinutes = 30,
        defaultRefreshIntervalMinutes = 5,
        calculationTimeoutMs = 500L,
        cacheTtlMinutes = 10,
        backgroundPriority = BackgroundPriority.HIGH
    ),
    
    /**
     * Moderate widgets requiring balanced calculation complexity
     * Medium processing time, standard refresh rates, balanced performance
     */
    MODERATE(
        displayName = "Moderate", 
        description = "Balanced calculations with moderate processing requirements",
        minRefreshIntervalMinutes = 5,
        maxRefreshIntervalMinutes = 120,
        defaultRefreshIntervalMinutes = 30,
        calculationTimeoutMs = 2000L,
        cacheTtlMinutes = 30,
        backgroundPriority = BackgroundPriority.MEDIUM
    ),
    
    /**
     * Complex widgets with intensive calculation requirements
     * Longer processing time, lower refresh frequency, background processing
     */
    COMPLEX(
        displayName = "Complex",
        description = "Intensive calculations requiring significant processing time",
        minRefreshIntervalMinutes = 30,
        maxRefreshIntervalMinutes = 720,
        defaultRefreshIntervalMinutes = 120,
        calculationTimeoutMs = 10000L,
        cacheTtlMinutes = 120,
        backgroundPriority = BackgroundPriority.LOW
    );
    
    /**
     * Calculates optimal refresh interval based on user activity patterns
     * 
     * @param userActivityLevel Activity level (0.0 = inactive, 1.0 = very active)
     * @param networkCondition Current network condition
     * @return Optimized refresh interval in minutes
     */
    fun getOptimalRefreshInterval(
        userActivityLevel: Float = 0.5f,
        networkCondition: NetworkCondition = NetworkCondition.GOOD
    ): Int {
        val baseInterval = defaultRefreshIntervalMinutes
        
        // Adjust based on user activity (more active = more frequent updates)
        val activityMultiplier = 1.0f - (userActivityLevel * 0.3f)
        
        // Adjust based on network condition
        val networkMultiplier = when (networkCondition) {
            NetworkCondition.POOR -> 1.5f
            NetworkCondition.GOOD -> 1.0f
            NetworkCondition.EXCELLENT -> 0.8f
        }
        
        val adjustedInterval = (baseInterval * activityMultiplier * networkMultiplier).toInt()
        return adjustedInterval.coerceIn(minRefreshIntervalMinutes, maxRefreshIntervalMinutes)
    }
    
    /**
     * Gets the maximum allowed widgets of this complexity for performance
     */
    fun getMaxAllowedWidgets(): Int = when (this) {
        SIMPLE -> 8      // Lightweight, allow many
        MODERATE -> 4    // Balanced, moderate limit
        COMPLEX -> 2     // Resource intensive, strict limit
    }
    
    /**
     * Checks if real-time updates are supported for this complexity
     */
    fun supportsRealTimeUpdates(): Boolean = this == SIMPLE
    
    /**
     * Gets memory usage estimate in MB for caching
     */
    fun getEstimatedMemoryUsageMB(): Float = when (this) {
        SIMPLE -> 0.5f    // Minimal data structures
        MODERATE -> 2.0f  // Chart data and calculations
        COMPLEX -> 8.0f   // Large datasets and analytics
    }
    
    /**
     * Gets CPU usage priority for background processing
     */
    fun getCpuPriority(): Int = when (this) {
        SIMPLE -> 1       // High priority, quick execution
        MODERATE -> 2     // Medium priority
        COMPLEX -> 3      // Low priority, background processing
    }
    
    /**
     * Determines if widget should use background thread for calculations
     */
    fun requiresBackgroundProcessing(): Boolean = this != SIMPLE
    
    /**
     * Gets recommended batch size for data processing
     */
    fun getBatchSize(): Int = when (this) {
        SIMPLE -> 50      // Small batches for quick results
        MODERATE -> 100   // Balanced batch processing
        COMPLEX -> 500    // Large batches for efficiency
    }
    
    companion object {
        /**
         * Gets complexity from refresh interval (reverse lookup)
         */
        fun fromRefreshInterval(intervalMinutes: Int): WidgetComplexity {
            return values().minByOrNull { 
                kotlin.math.abs(it.defaultRefreshIntervalMinutes - intervalMinutes) 
            } ?: MODERATE
        }
        
        /**
         * Gets complexities ordered by performance impact (lightest first)
         */
        fun getByPerformanceImpact(): List<WidgetComplexity> = listOf(SIMPLE, MODERATE, COMPLEX)
        
        /**
         * Calculates total memory usage for a widget mix
         */
        fun calculateTotalMemoryUsage(complexityCounts: Map<WidgetComplexity, Int>): Float {
            return complexityCounts.entries.sumOf { (complexity, count): Map.Entry<WidgetComplexity, Int> ->
                (complexity.getEstimatedMemoryUsageMB() * count).toDouble()
            }.toFloat()
        }
        
        /**
         * Validates widget mix for performance constraints
         */
        fun validateWidgetMix(complexityCounts: Map<WidgetComplexity, Int>): ValidationResult {
            val totalMemory = calculateTotalMemoryUsage(complexityCounts)
            val complexWidgets = complexityCounts[COMPLEX] ?: 0
            
            return when {
                totalMemory > 50.0f -> ValidationResult.MEMORY_LIMIT_EXCEEDED
                complexWidgets > COMPLEX.getMaxAllowedWidgets() -> ValidationResult.COMPLEX_WIDGET_LIMIT_EXCEEDED
                else -> ValidationResult.VALID
            }
        }
    }
}

/**
 * Background processing priority levels
 */
enum class BackgroundPriority(val displayName: String) {
    HIGH("High Priority"),
    MEDIUM("Medium Priority"), 
    LOW("Low Priority")
}

/**
 * Network condition states for refresh optimization
 */
enum class NetworkCondition(val displayName: String) {
    POOR("Poor Connection"),
    GOOD("Good Connection"),
    EXCELLENT("Excellent Connection")
}

/**
 * Widget mix validation results
 */
enum class ValidationResult(val message: String) {
    VALID("Widget configuration is valid"),
    MEMORY_LIMIT_EXCEEDED("Total memory usage exceeds 50MB limit"),
    COMPLEX_WIDGET_LIMIT_EXCEEDED("Too many complex widgets for optimal performance")
}