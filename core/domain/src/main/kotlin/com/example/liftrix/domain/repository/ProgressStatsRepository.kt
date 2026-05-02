package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Repository interface for progress statistics and analytics data aggregation
 * Provides data for charts, summaries, and progress tracking dashboards
 */
interface ProgressStatsRepository {
    
    /**
     * Get workout volume data (total weight lifted) over time
     * @param userId User identifier
     * @param startDate Start of the period
     * @param endDate End of the period
     * @return Flow of volume data points
     */
    fun getWorkoutVolumeData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<VolumeDataPoint>>
    
    /**
     * Get workout duration data over time
     * @param userId User identifier
     * @param startDate Start of the period
     * @param endDate End of the period
     * @return Flow of duration data points
     */
    fun getWorkoutDurationData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DurationDataPoint>>
    
    /**
     * Get workout frequency data for heatmap visualization
     * @param userId User identifier
     * @param startDate Start of the period
     * @param endDate End of the period
     * @return Flow of frequency data points
     */
    fun getWorkoutFrequencyData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<FrequencyDataPoint>>
    
    /**
     * Get summary statistics for the progress dashboard
     * @param userId User identifier
     * @param startDate Start of the period
     * @param endDate End of the period
     * @return Flow of progress summary
     */
    fun getProgressSummary(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<ProgressSummary>
    
    /**
     * Get exercise-specific volume progression
     * @param userId User identifier
     * @param exerciseLibraryId Exercise identifier
     * @param startDate Start of the period
     * @param endDate End of the period
     * @return Flow of exercise volume data
     */
    fun getExerciseVolumeProgression(
        userId: String,
        exerciseLibraryId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<VolumeDataPoint>>
    
    // Enhanced analytics methods for advanced dashboard features
    
    /**
     * Get volume calendar data for monthly analytics view
     * @param userId User identifier
     * @param month Target month for calendar data
     * @return Flow of volume calendar data with intensity calculations
     */
    suspend fun getVolumeCalendarData(
        userId: String,
        year: Int,
        month: Int
    ): Flow<LiftrixResult<VolumeCalendarData>>
    
    /**
     * Get user's historical maximum daily volume for proportional intensity scaling
     * @param userId User identifier
     * @return Flow of maximum daily volume ever recorded for this user
     */
    suspend fun getUserMaxDailyVolume(userId: String): Flow<LiftrixResult<com.example.liftrix.domain.model.Volume>>
    
    /**
     * Get comprehensive progress metrics for analytics dashboard
     * @param userId User identifier
     * @param timeRange Time range for metrics calculation
     * @return Flow of progress metrics with trends and analysis
     */
    suspend fun getProgressMetrics(
        userId: String,
        timeRange: TimeRange
    ): Flow<LiftrixResult<ProgressMetrics>>
    
    /**
     * Get aggregated dashboard data for widget display
     * @param userId User identifier
     * @param timeRange Time range for data aggregation
     * @return Flow of dashboard data with key metrics
     */
    suspend fun getDashboardData(
        userId: String,
        timeRange: TimeRange
    ): Flow<LiftrixResult<DashboardData>>
    
}

/**
 * Data class representing a volume data point for charts
 */
data class VolumeDataPoint(
    val date: LocalDate,
    val totalVolume: Float, // Total weight lifted in kg
    val exerciseCount: Int
)

/**
 * Data class representing a duration data point for charts
 */
data class DurationDataPoint(
    val date: LocalDate,
    val durationMinutes: Int,
    val workoutCount: Int
)

/**
 * Data class representing a frequency data point for heatmap
 */
data class FrequencyDataPoint(
    val date: LocalDate,
    val workoutCount: Int,
    val intensity: Float // 0.0 to 1.0 for heatmap coloring
)

/**
 * Data class for progress summary statistics
 */
data class ProgressSummary(
    val totalWorkouts: Int,
    val totalVolume: Float,
    val averageDuration: Int, // in minutes
    val currentStreak: Int, // consecutive workout days
    val longestStreak: Int,
    val averageWorkoutsPerWeek: Float,
    val totalActiveTime: Int // total workout time in minutes
)

/**
 * Data class for aggregated dashboard data
 */
data class DashboardData(
    val volumeCalendar: VolumeCalendarData,
    val progressMetrics: ProgressMetrics,
    val keyMetrics: Map<String, Any>,
    val lastUpdated: kotlinx.datetime.Instant
)
