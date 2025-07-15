package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.Weight
import kotlinx.datetime.DayOfWeek

/**
 * Volume metrics for comprehensive workout volume tracking and analysis
 * 
 * Provides detailed volume analytics including:
 * - Total and average volume calculations
 * - Week-over-week and month-over-month progression tracking
 * - Volume trend analysis with directional indicators
 * - Personal record volume tracking
 * - Day-of-week volume distribution for pattern analysis
 * 
 * Used by:
 * - AnalyticsMapper for volume calculations
 * - VolumeMetricCard for UI display
 * - ProgressMetrics for comprehensive analytics
 * - Dashboard widgets for volume visualization
 */
data class VolumeMetrics(
    val totalVolume: Weight,
    val averageVolumePerWorkout: Weight,
    val weekOverWeekChange: Float, // Percentage change (-1.0 to 1.0+)
    val monthOverMonthChange: Float, // Percentage change (-1.0 to 1.0+)
    val volumeTrend: TrendDirection,
    val personalRecordVolume: Weight,
    val volumeDistributionByDay: Map<DayOfWeek, Weight>
) {
    init {
        require(totalVolume >= Weight.ZERO) { "Total volume cannot be negative: $totalVolume" }
        require(averageVolumePerWorkout >= Weight.ZERO) { "Average volume per workout cannot be negative: $averageVolumePerWorkout" }
        require(weekOverWeekChange >= -1.0f) { "Week over week change cannot be less than -100%: $weekOverWeekChange" }
        require(monthOverMonthChange >= -1.0f) { "Month over month change cannot be less than -100%: $monthOverMonthChange" }
        require(personalRecordVolume >= Weight.ZERO) { "Personal record volume cannot be negative: $personalRecordVolume" }
        require(volumeDistributionByDay.values.all { it >= Weight.ZERO }) { "Volume distribution values cannot be negative" }
    }
    
    companion object {
        /**
         * Creates empty volume metrics with zero values
         */
        fun empty(): VolumeMetrics = VolumeMetrics(
            totalVolume = Weight.ZERO,
            averageVolumePerWorkout = Weight.ZERO,
            weekOverWeekChange = 0.0f,
            monthOverMonthChange = 0.0f,
            volumeTrend = TrendDirection.STABLE,
            personalRecordVolume = Weight.ZERO,
            volumeDistributionByDay = emptyMap()
        )
    }
    
    /**
     * Calculates the percentage change as a formatted string
     */
    fun getWeekOverWeekChangeFormatted(): String = formatPercentageChange(weekOverWeekChange)
    
    /**
     * Calculates the percentage change as a formatted string
     */
    fun getMonthOverMonthChangeFormatted(): String = formatPercentageChange(monthOverMonthChange)
    
    /**
     * Gets the highest volume day from distribution
     */
    fun getHighestVolumeDay(): DayOfWeek? = volumeDistributionByDay.maxByOrNull { (_, weight: Weight) -> weight.kilograms }?.key
    
    /**
     * Gets the lowest volume day from distribution
     */
    fun getLowestVolumeDay(): DayOfWeek? = volumeDistributionByDay.minByOrNull { (_, weight: Weight) -> weight.kilograms }?.key
    
    /**
     * Calculates total volume distribution across all days
     */
    fun getTotalDistributionVolume(): Weight = volumeDistributionByDay.values.fold(Weight.ZERO) { acc, weight -> acc + weight }
    
    /**
     * Checks if personal record was achieved (PR volume equals total volume)
     */
    fun isPersonalRecordAchieved(): Boolean = personalRecordVolume == totalVolume
    
    /**
     * Gets volume efficiency score (0.0 to 1.0) based on consistency
     */
    fun getVolumeEfficiencyScore(): Float {
        if (volumeDistributionByDay.isEmpty()) return 0.0f
        
        val volumes = volumeDistributionByDay.values.map { it.kilograms }
        val average = volumes.average()
        val variance = volumes.map { (it - average).let { diff -> diff * diff } }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        // Lower standard deviation = higher efficiency (more consistent)
        return (1.0 / (1.0 + standardDeviation)).toFloat().coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Formats percentage change with proper sign and percentage symbol
     */
    private fun formatPercentageChange(change: Float): String {
        val percentage = (change * 100).toInt()
        return when {
            percentage > 0 -> "+$percentage%"
            percentage < 0 -> "$percentage%"
            else -> "0%"
        }
    }
}