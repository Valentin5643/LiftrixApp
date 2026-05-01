package com.example.liftrix.domain.model.analytics

/**
 * Enumeration representing trend directions for analytics calculations
 * 
 * Used throughout the analytics system to indicate the direction of various metrics
 * including volume trends, strength progression, frequency changes, and other
 * time-based analytical measurements.
 * 
 * Provides standardized representation of trend data for:
 * - UI display with appropriate colors and icons
 * - Comparative analytics between time periods
 * - Progress evaluation and goal tracking
 * - Trend analysis and projection calculations
 */
enum class TrendDirection(
    val displayName: String,
    val symbol: String,
    val isPositive: Boolean
) {
    /**
     * Upward trend indicating improvement or increase
     * Used for positive changes in metrics like volume, strength, frequency
     */
    UP("Increasing", "↗", true),
    
    /**
     * Downward trend indicating decline or decrease
     * Used for negative changes in metrics (may be positive for rest days)
     */
    DOWN("Decreasing", "↘", false),
    
    /**
     * Stable trend indicating little to no change
     * Used when metrics remain consistent within acceptable variance
     */
    STABLE("Stable", "→", true),
    
    /**
     * Unknown trend when insufficient data is available
     * Used during initial periods or when data is incomplete
     */
    UNKNOWN("Unknown", "?", true);
    
    /**
     * Gets the appropriate color indicator for UI display
     */
    fun getColorIndicator(): String = when (this) {
        UP -> "green"
        DOWN -> "red"
        STABLE -> "yellow"
        UNKNOWN -> "gray"
    }
    
    /**
     * Checks if this trend direction represents improvement
     * Context-dependent: DOWN might be positive for rest days/recovery
     */
    fun isImprovement(context: TrendContext = TrendContext.PERFORMANCE): Boolean = when (context) {
        TrendContext.PERFORMANCE -> this == UP || this == STABLE
        TrendContext.RECOVERY -> this == DOWN || this == STABLE  // More rest can be good
        TrendContext.FREQUENCY -> this == UP || this == STABLE
        TrendContext.VOLUME -> this == UP || this == STABLE
    }
    
    /**
     * Gets a percentage-based description of the trend
     */
    fun getPercentageDescription(percentage: Float): String {
        val absPercentage = kotlin.math.abs(percentage)
        val percentStr = String.format("%.1f%%", absPercentage)
        
        return when (this) {
            UP -> "+$percentStr"
            DOWN -> "-$percentStr"
            STABLE -> "±$percentStr"
            UNKNOWN -> "N/A"
        }
    }
    
    /**
     * Calculates the intensity level of the trend based on percentage change
     */
    fun getIntensityLevel(percentage: Float): TrendIntensity {
        val absPercentage = kotlin.math.abs(percentage)
        return when {
            absPercentage < 5.0f -> TrendIntensity.MINIMAL
            absPercentage < 15.0f -> TrendIntensity.MODERATE
            absPercentage < 30.0f -> TrendIntensity.SIGNIFICANT
            else -> TrendIntensity.DRAMATIC
        }
    }
    
    companion object {
        /**
         * Determines trend direction from percentage change
         */
        fun fromPercentageChange(percentage: Float, threshold: Float = 2.0f): TrendDirection = when {
            percentage > threshold -> UP
            percentage < -threshold -> DOWN
            else -> STABLE
        }
        
        /**
         * Determines trend direction from comparing two values
         */
        fun fromComparison(
            currentValue: Double,
            previousValue: Double,
            threshold: Double = 0.02 // 2% threshold
        ): TrendDirection {
            if (previousValue == 0.0) return UNKNOWN
            
            val percentageChange = ((currentValue - previousValue) / previousValue) * 100.0
            return fromPercentageChange(percentageChange.toFloat(), (threshold * 100).toFloat())
        }
        
        /**
         * Gets all positive trend directions
         */
        fun getPositiveTrends(): List<TrendDirection> = values().filter { it.isPositive }
        
        /**
         * Gets all trend directions that indicate change
         */
        fun getChangeTrends(): List<TrendDirection> = listOf(UP, DOWN)
    }
}

/**
 * Context for trend interpretation - different metrics have different "positive" directions
 */
enum class TrendContext(val displayName: String) {
    /**
     * Performance metrics where UP is generally positive
     */
    PERFORMANCE("Performance"),
    
    /**
     * Recovery metrics where DOWN might be positive (less rest needed)
     */
    RECOVERY("Recovery"),
    
    /**
     * Frequency metrics where UP is generally positive
     */
    FREQUENCY("Frequency"),
    
    /**
     * Volume metrics where UP is generally positive
     */
    VOLUME("Volume")
}

/**
 * Intensity level of trend changes
 */
enum class TrendIntensity(
    val displayName: String,
    val description: String
) {
    /**
     * Minimal change - within normal variance
     */
    MINIMAL("Minimal", "Small change within normal variance"),
    
    /**
     * Moderate change - noticeable but not dramatic
     */
    MODERATE("Moderate", "Noticeable change in trend"),
    
    /**
     * Significant change - substantial shift in metrics
     */
    SIGNIFICANT("Significant", "Substantial change requiring attention"),
    
    /**
     * Dramatic change - major shift that may need investigation
     */
    DRAMATIC("Dramatic", "Major change requiring immediate review")
}