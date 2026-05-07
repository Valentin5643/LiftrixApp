package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.dao.DailyVolumeResult
import com.example.liftrix.data.local.dao.MonthlyWorkoutCount
import com.example.liftrix.data.local.dao.WorkoutStatsResult
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.Volume
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.VolumeMetrics
import com.example.liftrix.domain.model.analytics.FrequencyMetrics
import com.example.liftrix.domain.model.analytics.StrengthMetrics
import com.example.liftrix.domain.model.analytics.ConsistencyMetrics
import com.example.liftrix.domain.model.analytics.RecoveryMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.StreakType
import com.example.liftrix.domain.model.analytics.RiskLevel
import com.example.liftrix.domain.model.analytics.PersonalRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaLocalDate
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics data mapper for transforming database results to domain analytics models
 * 
 * Handles complex mapping operations for:
 * - Volume calendar data with intensity calculations
 * - Progress metrics with trend analysis
 * - Dashboard data aggregation
 * - Performance-optimized data transformations
 * 
 * Used by ProgressStatsRepositoryImpl for analytics calculations
 */
@Singleton
class AnalyticsMapper @Inject constructor() {
    
    /**
     * Maps daily volume results to VolumeCalendarData with intensity calculations
     * 
     * @param dailyVolumes List of daily volume results from database
     * @param year Target year for calendar
     * @param month Target month for calendar
     * @return VolumeCalendarData with daily volumes and intensity metrics
     */
    fun mapToVolumeCalendarData(
        dailyVolumes: List<DailyVolumeResult>,
        year: Int,
        month: Month
    ): VolumeCalendarData {
        try {
            val volumeMap = dailyVolumes.associate { result ->
                val date = LocalDate.parse(result.date)
                date to Volume.fromKilograms(result.total_volume)
            }
            
            val maxVolume = volumeMap.values.maxByOrNull { it.kilograms } ?: Volume.ZERO
            val averageVolume = if (volumeMap.isNotEmpty()) {
                val totalVolume = volumeMap.values.fold(Volume.ZERO) { acc, volume -> acc + volume }
                Volume.fromKilograms(totalVolume.kilograms / volumeMap.size)
            } else Volume.ZERO
            
            return VolumeCalendarData(
                year = year,
                month = month,
                dailyVolumes = volumeMap,
                maxVolume = maxVolume,
                averageVolume = averageVolume
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to map daily volumes to VolumeCalendarData")
            return VolumeCalendarData.empty(year, month)
        }
    }
    
    /**
     * Maps workout statistics and entities to comprehensive ProgressMetrics
     * 
     * @param workoutStats Database workout statistics
     * @param workoutEntities List of workout entities for detailed analysis
     * @param timeRange Time range for metrics calculation
     * @param userId User identifier
     * @return ProgressMetrics with comprehensive analytics
     */
    fun mapToProgressMetrics(
        workoutStats: WorkoutStatsResult,
        workoutEntities: List<WorkoutEntity>,
        timeRange: TimeRange,
        userId: String
    ): ProgressMetrics {
        try {
            val volumeMetrics = calculateVolumeMetrics(workoutEntities, timeRange)
            val frequencyMetrics = calculateFrequencyMetrics(workoutEntities, timeRange)
            val strengthMetrics = calculateStrengthMetrics(workoutEntities, timeRange)
            val consistencyMetrics = calculateConsistencyMetrics(workoutEntities, timeRange)
            val recoveryMetrics = calculateRecoveryMetrics(workoutEntities, timeRange)
            
            return ProgressMetrics(
                userId = userId,
                timeRange = timeRange,
                volumeMetrics = volumeMetrics,
                frequencyMetrics = frequencyMetrics,
                strengthMetrics = strengthMetrics,
                consistencyMetrics = consistencyMetrics,
                recoveryMetrics = recoveryMetrics
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to map workout data to ProgressMetrics")
            return createEmptyProgressMetrics(userId, timeRange)
        }
    }
    
    /**
     * Calculates volume metrics from workout entities
     */
    private fun calculateVolumeMetrics(
        workoutEntities: List<WorkoutEntity>,
        timeRange: TimeRange
    ): VolumeMetrics {
        val completedWorkouts = workoutEntities.filter { it.status == WorkoutStatus.COMPLETED }
        
        val totalVolume = completedWorkouts.sumOf { workout ->
            extractTotalVolumeFromJson(workout.exercisesJson)
        }
        
        val averageVolumePerWorkout = if (completedWorkouts.isNotEmpty()) {
            totalVolume / completedWorkouts.size
        } else 0.0
        
        // Calculate week-over-week and month-over-month changes
        val weekOverWeekChange = calculateVolumeChange(completedWorkouts, 7)
        val monthOverMonthChange = calculateVolumeChange(completedWorkouts, 30)
        
        val volumeTrend = when {
            weekOverWeekChange > 0.05 -> TrendDirection.UP
            weekOverWeekChange < -0.05 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
        
        val personalRecordVolume = completedWorkouts.maxOfOrNull { workout ->
            extractTotalVolumeFromJson(workout.exercisesJson)
        } ?: 0.0
        
        val volumeDistributionByDay = calculateVolumeDistributionByDay(completedWorkouts)
        
        return VolumeMetrics(
            totalVolume = Weight.fromKilograms(totalVolume),
            averageVolumePerWorkout = Weight.fromKilograms(averageVolumePerWorkout),
            weekOverWeekChange = weekOverWeekChange.toFloat(),
            monthOverMonthChange = monthOverMonthChange.toFloat(),
            volumeTrend = volumeTrend,
            personalRecordVolume = Weight.fromKilograms(personalRecordVolume),
            volumeDistributionByDay = volumeDistributionByDay
        )
    }
    
    /**
     * Calculates frequency metrics from workout entities
     */
    private fun calculateFrequencyMetrics(
        workoutEntities: List<WorkoutEntity>,
        timeRange: TimeRange
    ): FrequencyMetrics {
        val completedWorkouts = workoutEntities.filter { it.status == WorkoutStatus.COMPLETED }
        val workoutCount = completedWorkouts.size
        
        val daysInRange = timeRange.getDurationInDays()
        val weeksInRange = daysInRange / 7.0f
        val averageWorkoutsPerWeek = if (weeksInRange > 0) workoutCount / weeksInRange else 0.0f
        
        val weekOverWeekChange = calculateFrequencyChange(completedWorkouts, 7)
        
        // Calculate consistency score based on workout distribution
        val consistencyScore = calculateConsistencyScore(completedWorkouts, timeRange)
        
        // Calculate gaps between workouts
        val gaps = calculateWorkoutGaps(completedWorkouts)
        val longestGap = gaps.maxOrNull() ?: 0
        val shortestGap = gaps.minOrNull() ?: 0
        
        return FrequencyMetrics(
            workoutCount = workoutCount,
            averageWorkoutsPerWeek = averageWorkoutsPerWeek,
            weekOverWeekChange = weekOverWeekChange.toFloat(),
            targetFrequencyAchievement = calculateTargetFrequencyAchievement(averageWorkoutsPerWeek),
            consistencyScore = consistencyScore,
            longestGap = longestGap,
            shortestGap = shortestGap
        )
    }
    
    /**
     * Calculates strength metrics from workout entities
     */
    private fun calculateStrengthMetrics(
        workoutEntities: List<WorkoutEntity>,
        timeRange: TimeRange
    ): StrengthMetrics {
        val completedWorkouts = workoutEntities.filter { it.status == WorkoutStatus.COMPLETED }
        
        return StrengthMetrics(
            personalRecords = emptyList(), // PR calculation handled by specialized service
            strengthProgression = 0.0f, // Strength progression calculated separately
            recentPRCount = 0, // PR counting handled by PR detection service
            volumeLoadProgression = 0.0f, // Volume calculations handled by analytics service
            oneRepMaxEstimates = emptyMap() // 1RM estimation handled by calculation service
        )
    }
    
    /**
     * Calculates consistency metrics from workout entities
     */
    private fun calculateConsistencyMetrics(
        workoutEntities: List<WorkoutEntity>,
        timeRange: TimeRange
    ): ConsistencyMetrics {
        val completedWorkouts = workoutEntities.filter { it.status == WorkoutStatus.COMPLETED }
        val workoutDates = completedWorkouts.map { 
            java.time.LocalDate.parse(it.date.toString()) 
        }.sorted()
        
        val (currentStreak, longestStreak) = calculateStreaks(workoutDates)
        val averageRestDays = calculateAverageRestDays(workoutDates)
        
        return ConsistencyMetrics(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            averageRestDays = averageRestDays,
            workoutDaysInPeriod = workoutDates.size,
            totalDaysInPeriod = timeRange.getDurationInDays(),
            streakType = StreakType.WORKOUT_DAYS
        )
    }
    
    /**
     * Calculates recovery metrics from workout entities
     */
    private fun calculateRecoveryMetrics(
        workoutEntities: List<WorkoutEntity>,
        timeRange: TimeRange
    ): RecoveryMetrics {
        val completedWorkouts = workoutEntities.filter { it.status == WorkoutStatus.COMPLETED }
        val workoutDates = completedWorkouts.map { 
            java.time.LocalDate.parse(it.date.toString()) 
        }.sorted()
        
        val averageRestDays = calculateAverageRestDays(workoutDates)
        
        return RecoveryMetrics(
            averageRestDaysBetweenWorkouts = averageRestDays,
            optimalRestDayRange = 1..3, // Standard rest day range
            recoveryPatternScore = calculateRecoveryPatternScore(averageRestDays),
            overreachingRisk = RiskLevel.LOW, // Risk assessment handled by specialized service
            underrecoveryDays = 0, // Underrecovery calculation handled by recovery service
            recommendedRestDays = calculateRecommendedRestDays(averageRestDays)
        )
    }
    
    /**
     * Extracts total volume from workout's exercises JSON
     */
    private fun extractTotalVolumeFromJson(exercisesJson: String?): Double {
        return try {
            if (exercisesJson.isNullOrBlank()) return 0.0
            
            // Parse JSON to extract totalVolume
            // This is a simplified extraction - in production, use proper JSON parsing
            val volumeRegex = "\"totalVolume\"\\s*:\\s*([0-9.]+)".toRegex()
            val matchResult = volumeRegex.find(exercisesJson)
            matchResult?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract volume from exercises JSON")
            0.0
        }
    }
    
    /**
     * Calculates volume change over specified period
     */
    private fun calculateVolumeChange(workouts: List<WorkoutEntity>, daysPeriod: Int): Double {
        val today = java.time.LocalDate.now()
        val cutoffDate = today.minusDays(daysPeriod.toLong())
        
        val recentWorkouts = workouts.filter { 
            java.time.LocalDate.parse(it.date.toString()) >= cutoffDate 
        }
        val olderWorkouts = workouts.filter { 
            java.time.LocalDate.parse(it.date.toString()) < cutoffDate 
        }
        
        val recentVolume = recentWorkouts.sumOf { extractTotalVolumeFromJson(it.exercisesJson) }
        val olderVolume = olderWorkouts.sumOf { extractTotalVolumeFromJson(it.exercisesJson) }
        
        return if (olderVolume > 0) {
            (recentVolume - olderVolume) / olderVolume
        } else 0.0
    }
    
    /**
     * Calculates volume distribution by day of week
     */
    private fun calculateVolumeDistributionByDay(workouts: List<WorkoutEntity>): Map<DayOfWeek, Weight> {
        val distribution = mutableMapOf<DayOfWeek, Double>()
        
        workouts.forEach { workout ->
            val date = workout.date
            val dayOfWeek = date.dayOfWeek
            val volume = extractTotalVolumeFromJson(workout.exercisesJson)
            
            distribution[dayOfWeek] = distribution.getOrDefault(dayOfWeek, 0.0) + volume
        }
        
        return distribution.mapValues { (_, volume) -> Weight.fromKilograms(volume) }
    }
    
    /**
     * Calculates frequency change over specified period
     */
    private fun calculateFrequencyChange(workouts: List<WorkoutEntity>, daysPeriod: Int): Double {
        val today = java.time.LocalDate.now()
        val cutoffDate = today.minusDays(daysPeriod.toLong())
        
        val recentCount = workouts.count { 
            java.time.LocalDate.parse(it.date.toString()) >= cutoffDate 
        }
        val olderCount = workouts.count { 
            java.time.LocalDate.parse(it.date.toString()) < cutoffDate 
        }
        
        return if (olderCount > 0) {
            (recentCount - olderCount).toDouble() / olderCount
        } else 0.0
    }
    
    /**
     * Calculates consistency score based on workout distribution
     */
    private fun calculateConsistencyScore(workouts: List<WorkoutEntity>, timeRange: TimeRange): Float {
        if (workouts.isEmpty()) return 0.0f
        
        val workoutDates = workouts.map { java.time.LocalDate.parse(it.date.toString()) }.sorted()
        val gaps = calculateWorkoutGaps(workouts)
        
        // Consistency is higher when gaps are more uniform
        val averageGap = gaps.average()
        val gapVariance = gaps.map { (it - averageGap).let { diff -> diff * diff } }.average()
        val standardDeviation = kotlin.math.sqrt(gapVariance)
        
        // Lower standard deviation = higher consistency
        return (1.0 / (1.0 + standardDeviation)).toFloat().coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Calculates gaps between consecutive workouts
     */
    private fun calculateWorkoutGaps(workouts: List<WorkoutEntity>): List<Int> {
        val workoutDates = workouts.map { java.time.LocalDate.parse(it.date.toString()) }.sorted()
        if (workoutDates.size < 2) return emptyList()
        
        return workoutDates.zipWithNext { date1, date2 ->
            (date2.toEpochDay() - date1.toEpochDay()).toInt()
        }
    }
    
    /**
     * Calculates target frequency achievement rate
     */
    private fun calculateTargetFrequencyAchievement(averageWorkoutsPerWeek: Float): Float {
        val targetWorkoutsPerWeek = 3.0f // Standard target
        return (averageWorkoutsPerWeek / targetWorkoutsPerWeek).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Calculates workout streaks
     */
    private fun calculateStreaks(workoutDates: List<java.time.LocalDate>): Pair<Int, Int> {
        if (workoutDates.isEmpty()) return Pair(0, 0)
        
        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 1
        
        val today = java.time.LocalDate.now()
        
        // Calculate longest streak
        for (i in 1 until workoutDates.size) {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(workoutDates[i-1], workoutDates[i])
            if (daysBetween == 1L) {
                tempStreak++
            } else {
                longestStreak = maxOf(longestStreak, tempStreak)
                tempStreak = 1
            }
        }
        longestStreak = maxOf(longestStreak, tempStreak)
        
        // Calculate current streak
        val lastWorkoutDate = workoutDates.lastOrNull()
        if (lastWorkoutDate != null) {
            val daysSinceLastWorkout = java.time.temporal.ChronoUnit.DAYS.between(lastWorkoutDate, today)
            if (daysSinceLastWorkout <= 1L) {
                // Find consecutive days leading up to today
                var streak = 1
                for (i in workoutDates.size - 2 downTo 0) {
                    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(workoutDates[i], workoutDates[i + 1])
                    if (daysBetween == 1L) {
                        streak++
                    } else {
                        break
                    }
                }
                currentStreak = streak
            }
        }
        
        return Pair(currentStreak, longestStreak)
    }
    
    /**
     * Calculates average rest days between workouts
     */
    private fun calculateAverageRestDays(workoutDates: List<java.time.LocalDate>): Float {
        if (workoutDates.size < 2) return 0.0f
        
        val gaps = workoutDates.zipWithNext { date1, date2 ->
            java.time.temporal.ChronoUnit.DAYS.between(date1, date2).toInt()
        }
        
        return gaps.average().toFloat()
    }
    
    /**
     * Calculates recovery pattern score
     */
    private fun calculateRecoveryPatternScore(averageRestDays: Float): Float {
        // Optimal rest is 1-3 days
        return when {
            averageRestDays in 1.0f..3.0f -> 1.0f
            averageRestDays in 0.5f..1.0f || averageRestDays in 3.0f..5.0f -> 0.7f
            else -> 0.3f
        }
    }
    
    /**
     * Calculates recommended rest days based on current pattern
     */
    private fun calculateRecommendedRestDays(averageRestDays: Float): Int {
        return when {
            averageRestDays < 1.0f -> 2 // Increase rest
            averageRestDays > 3.0f -> 1 // Decrease rest
            else -> averageRestDays.toInt() // Maintain current
        }
    }
    
    /**
     * Creates empty progress metrics as fallback
     */
    private fun createEmptyProgressMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
        return ProgressMetrics(
            userId = userId,
            timeRange = timeRange,
            volumeMetrics = VolumeMetrics(
                totalVolume = Weight.ZERO,
                averageVolumePerWorkout = Weight.ZERO,
                weekOverWeekChange = 0.0f,
                monthOverMonthChange = 0.0f,
                volumeTrend = TrendDirection.STABLE,
                personalRecordVolume = Weight.ZERO,
                volumeDistributionByDay = emptyMap()
            ),
            frequencyMetrics = FrequencyMetrics(
                workoutCount = 0,
                averageWorkoutsPerWeek = 0.0f,
                weekOverWeekChange = 0.0f,
                targetFrequencyAchievement = 0.0f,
                consistencyScore = 0.0f,
                longestGap = 0,
                shortestGap = 0
            ),
            strengthMetrics = StrengthMetrics(
                personalRecords = emptyList(),
                strengthProgression = 0.0f,
                recentPRCount = 0,
                volumeLoadProgression = 0.0f,
                oneRepMaxEstimates = emptyMap()
            ),
            consistencyMetrics = ConsistencyMetrics(
                currentStreak = 0,
                longestStreak = 0,
                averageRestDays = 0.0f,
                workoutDaysInPeriod = 0,
                totalDaysInPeriod = timeRange.getDurationInDays(),
                streakType = StreakType.WORKOUT_DAYS
            ),
            recoveryMetrics = RecoveryMetrics(
                averageRestDaysBetweenWorkouts = 0.0f,
                optimalRestDayRange = 1..3,
                recoveryPatternScore = 0.0f,
                overreachingRisk = RiskLevel.LOW,
                underrecoveryDays = 0,
                recommendedRestDays = 1
            )
        )
    }
}