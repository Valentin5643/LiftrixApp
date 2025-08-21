package com.example.liftrix.service

import com.example.liftrix.core.cache.MemoizationCache
import com.example.liftrix.core.cache.createCacheKey
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.VolumeMetrics
import com.example.liftrix.domain.model.analytics.FrequencyMetrics
import com.example.liftrix.domain.model.analytics.StrengthMetrics
import com.example.liftrix.domain.model.analytics.ConsistencyMetrics
import com.example.liftrix.domain.model.analytics.RecoveryMetrics
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.StreakType
import com.example.liftrix.domain.model.analytics.RiskLevel
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Analytics engine service for calculating comprehensive fitness metrics
 * 
 * Provides centralized analytics calculation functionality including:
 * - Progress metrics aggregation from workout data
 * - Time-based analytics with configurable ranges
 * - Performance trend analysis
 * - Comprehensive error handling with LiftrixResult pattern
 * 
 * Technical Implementation:
 * - Uses coroutines for async processing
 * - Follows Clean Architecture patterns
 * - Integrates with repository layer for data access
 * - Optimized for performance with large datasets
 * 
 * Performance Targets:
 * - Metrics calculation: <2s for quarterly data
 * - Real-time updates: <500ms for current metrics
 * - Memory efficient processing for large datasets
 */
@Singleton
class AnalyticsEngine @Inject constructor(
    private val workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
    private val calorieCalculator: com.example.liftrix.domain.model.analytics.CalorieCalculator,
    private val progressStatsRepository: com.example.liftrix.domain.repository.ProgressStatsRepository,
    private val performanceBenchmark: PerformanceBenchmark
) {
    
    companion object {
        private const val MAX_CALCULATION_TIME_MS = 5000L // 5 second timeout
        private const val MEMORY_PRESSURE_THRESHOLD = 0.8f // Clear cache when 80% memory used
        private const val CACHE_REDUCTION_FACTOR = 0.5f // Reduce cache to 50% when under pressure
    }
    
    // Memoization caches for expensive calculations with memory pressure handling
    private val progressMetricsCache = MemoizationCache<String, ProgressMetrics>(
        maxSize = 50,
        defaultTtl = 10.minutes
    )
    
    private val workoutMetricsCache = MemoizationCache<String, com.example.liftrix.domain.model.analytics.WorkoutMetrics>(
        maxSize = 100,
        defaultTtl = 30.minutes
    )
    
    private val volumeCalendarCache = MemoizationCache<String, com.example.liftrix.domain.model.analytics.VolumeCalendarData>(
        maxSize = 30,
        defaultTtl = 15.minutes
    )
    
    /**
     * Monitors memory pressure and reduces cache sizes when memory is constrained
     */
    private suspend fun handleMemoryPressure() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()
        
        if (memoryUsageRatio > MEMORY_PRESSURE_THRESHOLD) {
            Timber.w("Memory pressure detected: ${(memoryUsageRatio * 100).toInt()}% used. Clearing expired cache entries.")
            
            // Clean up expired entries instead of trimming
            progressMetricsCache.cleanupExpired()
            workoutMetricsCache.cleanupExpired()
            volumeCalendarCache.cleanupExpired()
            
            // If still too much memory, clear caches
            if (memoryUsageRatio > 0.9f) {
                progressMetricsCache.clear()
                workoutMetricsCache.clear()
                volumeCalendarCache.clear()
                Timber.w("Critical memory pressure. All caches cleared.")
            }
            
            // Force garbage collection
            System.gc()
            
            val progressSize = progressMetricsCache.size()
            val workoutSize = workoutMetricsCache.size()
            val calendarSize = volumeCalendarCache.size()
            Timber.d("Cache cleanup completed. Progress: $progressSize, Workout: $workoutSize, Calendar: $calendarSize")
        }
    }
    
    /**
     * Clears all analytics caches to free memory
     */
    suspend fun clearAllCaches() {
        Timber.d("Clearing all analytics caches")
        progressMetricsCache.clear()
        workoutMetricsCache.clear()
        volumeCalendarCache.clear()
        System.gc()
    }
    
    /**
     * Calculates comprehensive progress metrics for a user within a time range
     * 
     * @param userId The user ID to calculate metrics for
     * @param timeRange The time range to calculate metrics within
     * @return LiftrixResult containing ProgressMetrics or error
     */
    suspend fun calculateProgressMetrics(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<ProgressMetrics> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Validate inputs first
            if (userId.isBlank()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be blank for analytics calculation")
                    )
                )
            }
            
            if (!timeRange.isValid()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "timeRange",
                        violations = listOf("Invalid time range for analytics: $timeRange")
                    )
                )
            }
            
            // Check memory pressure before expensive calculation
            handleMemoryPressure()
            
            // Use memoization for expensive calculation
            val cacheKey = createCacheKey("progress_metrics", userId, timeRange.toString())
            val metrics = progressMetricsCache.memoize(cacheKey, ttl = 10.minutes) {
                Timber.d("Calculating progress metrics for user: $userId, timeRange: $timeRange")
                val startTime = System.currentTimeMillis()
                
                // Calculate metrics based on time range duration
                // Benchmark the calculation for performance tracking
                val result = performanceBenchmark.measureWidgetCalculation("progress_metrics") {
                    when {
                        timeRange.getDurationInDays() <= 7 -> calculateWeeklyMetrics(userId, timeRange)
                        timeRange.getDurationInDays() <= 30 -> calculateMonthlyMetrics(userId, timeRange)
                        timeRange.getDurationInDays() <= 90 -> calculateQuarterlyMetrics(userId, timeRange)
                        else -> calculateYearlyMetrics(userId, timeRange)
                    }
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                Timber.d("Analytics calculation completed in ${executionTime}ms")
                
                // Check execution time performance and log to benchmark
                if (executionTime > MAX_CALCULATION_TIME_MS) {
                    Timber.w("Analytics calculation took ${executionTime}ms, exceeding target of ${MAX_CALCULATION_TIME_MS}ms")
                }
                
                // Log performance verification periodically
                if (System.currentTimeMillis() % 10 == 0L) {
                    val report = performanceBenchmark.generatePerformanceReport()
                    Timber.d("Performance Report:\n$report")
                }
                
                result
            }
            
            liftrixSuccess(metrics)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during analytics calculation for user: $userId")
            liftrixFailure(
                LiftrixError.CalculationError(
                    errorMessage = "Analytics calculation failed: ${e.message}",
                    operation = "calculateProgressMetrics"
                )
            )
        }
    }
    
    /**
     * Calculates weekly progress metrics using real workout data
     */
    private suspend fun calculateWeeklyMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
        return calculateRealProgressMetrics(userId, timeRange)
    }
    
    /**
     * Calculates monthly progress metrics using real workout data
     */
    private suspend fun calculateMonthlyMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
        return calculateRealProgressMetrics(userId, timeRange)
    }
    
    /**
     * Calculates quarterly progress metrics using real workout data
     */
    private suspend fun calculateQuarterlyMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
        return calculateRealProgressMetrics(userId, timeRange)
    }
    
    /**
     * Calculates yearly progress metrics using real workout data
     */
    private suspend fun calculateYearlyMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
        return calculateRealProgressMetrics(userId, timeRange)
    }
    
    /**
     * Calculates real progress metrics from repository data
     */
    private suspend fun calculateRealProgressMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
        return try {
            // Get real progress metrics from repository
            val result = progressStatsRepository.getProgressMetrics(userId, timeRange)
            
            var progressMetrics: ProgressMetrics? = null
            result.collect { liftrixResult ->
                liftrixResult.fold(
                    onSuccess = { data ->
                        progressMetrics = data
                    },
                    onFailure = { throwable ->
                        val error = if (throwable is LiftrixError) throwable else LiftrixError.UnknownError(throwable.message ?: "Unknown error")
                        Timber.w("Failed to get progress metrics from repository: $error")
                        // Fall back to minimal metrics
                        progressMetrics = createMinimalProgressMetrics(userId, timeRange)
                    }
                )
            }
            
            progressMetrics ?: createMinimalProgressMetrics(userId, timeRange)
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating real progress metrics")
            createMinimalProgressMetrics(userId, timeRange)
        }
    }
    
    /**
     * Creates minimal progress metrics when real data is unavailable
     */
    private fun createMinimalProgressMetrics(userId: String, timeRange: TimeRange): ProgressMetrics {
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
    
    /**
     * Calculates comprehensive metrics for a specific workout
     * 
     * @param workoutId The ID of the workout to calculate metrics for
     * @return LiftrixResult containing calculated WorkoutMetrics or error information
     */
    suspend fun calculateWorkoutMetrics(workoutId: com.example.liftrix.domain.model.WorkoutId): LiftrixResult<com.example.liftrix.domain.model.analytics.WorkoutMetrics> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check memory pressure before expensive calculation
            handleMemoryPressure()
            
            // Use memoization for expensive workout calculations
            val cacheKey = createCacheKey("workout_metrics", workoutId.value)
            val metrics = workoutMetricsCache.memoize(cacheKey, ttl = 30.minutes) {
                Timber.d("Calculating workout metrics for workoutId: ${workoutId.value}")
                
                // Query actual workout data from database
                val workoutEntity = workoutDao.getWorkoutById(workoutId.value)
                if (workoutEntity == null) {
                    throw LiftrixError.NotFoundError(
                        errorMessage = "Workout not found",
                        resourceType = "Workout",
                        resourceId = workoutId.value
                    )
                }
                
                // Calculate actual calories burned using CalorieCalculator
                val caloriesBurned = try {
                    calorieCalculator.calculateWorkoutCalories(
                        exercisesJson = workoutEntity.exercisesJson,
                        durationMinutes = workoutEntity.endTime?.let { end ->
                            workoutEntity.startTime?.let { start ->
                                java.time.Duration.between(start, end).toMinutes()
                            }
                        } ?: 60,
                        userId = workoutEntity.userId
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to calculate calories for workout ${workoutId.value}, using estimate")
                    // Fallback to simple estimate based on duration
                    val duration = workoutEntity.endTime?.let { end ->
                        workoutEntity.startTime?.let { start ->
                            java.time.Duration.between(start, end).toMinutes()
                        }
                    } ?: 60
                    (duration * 6.5).toInt() // Rough estimate: 6.5 calories per minute
                }
                
                // Calculate total volume from exercises JSON
                val totalVolume = try {
                    extractTotalVolumeFromExercisesJson(workoutEntity.exercisesJson)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to extract volume from exercises JSON")
                    com.example.liftrix.domain.model.Weight.ZERO
                }
                
                // Calculate other metrics
                val sessionDuration = workoutEntity.endTime?.let { end ->
                    workoutEntity.startTime?.let { start ->
                        java.time.Duration.between(start, end)
                    }
                } ?: java.time.Duration.ofMinutes(60)
                
                com.example.liftrix.domain.model.analytics.WorkoutMetrics(
                    workoutId = workoutId.value,
                    userId = workoutEntity.userId,
                    date = kotlinx.datetime.LocalDate.fromEpochDays(workoutEntity.date.toEpochDay().toInt()),
                    totalVolume = totalVolume,
                    sessionDuration = sessionDuration,
                    caloriesBurned = caloriesBurned,
                    exerciseCount = extractExerciseCount(workoutEntity.exercisesJson),
                    totalSets = extractTotalSets(workoutEntity.exercisesJson),
                    completedSets = extractCompletedSets(workoutEntity.exercisesJson),
                    totalReps = com.example.liftrix.domain.model.Reps(extractTotalReps(workoutEntity.exercisesJson)),
                    completionPercentage = calculateCompletionPercentage(workoutEntity.exercisesJson),
                    averageIntensity = calculateAverageIntensity(workoutEntity.exercisesJson),
                    volumeEfficiency = calculateVolumeEfficiency(totalVolume, sessionDuration),
                    categories = extractExerciseCategories(workoutEntity.exercisesJson)
                )
            }
            
            liftrixSuccess(metrics)
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating workout metrics for workoutId: ${workoutId.value}")
            liftrixFailure(
                LiftrixError.CalculationError(
                    errorMessage = "Failed to calculate workout metrics: ${e.message}",
                    operation = "calculateWorkoutMetrics"
                )
            )
        }
    }
    
    /**
     * Generates volume calendar data for the specified month and year
     * 
     * @param userId The user ID to generate calendar for
     * @param year The year for calendar generation
     * @param month The month for calendar generation
     * @return LiftrixResult containing VolumeCalendarData or error information
     */
    suspend fun generateVolumeCalendar(
        userId: String,
        year: Int,
        month: kotlinx.datetime.Month
    ): LiftrixResult<com.example.liftrix.domain.model.analytics.VolumeCalendarData> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Use memoization for expensive volume calendar generation
            val cacheKey = createCacheKey("volume_calendar", userId, year, month.value)
            val calendarData = volumeCalendarCache.memoize(cacheKey, ttl = 15.minutes) {
                Timber.d("Generating volume calendar for user: $userId, year: $year, month: $month")
                
                // Use real repository data for volume calendar generation
                val result = progressStatsRepository.getVolumeCalendarData(userId, year, month.value)
                
                var data: com.example.liftrix.domain.model.analytics.VolumeCalendarData? = null
                var errorOccurred: LiftrixError? = null
                result.collect { liftrixResult ->
                    liftrixResult.fold(
                        onSuccess = { resultData ->
                            data = resultData
                        },
                        onFailure = { throwable ->
                            errorOccurred = if (throwable is LiftrixError) throwable else LiftrixError.UnknownError(throwable.message ?: "Unknown error")
                        }
                    )
                }
                
                // Check if error occurred during collection
                errorOccurred?.let { error ->
                    throw error
                }
                
                data ?: com.example.liftrix.domain.model.analytics.VolumeCalendarData(
                    year = year,
                    month = month,
                    dailyVolumes = emptyMap(),
                    maxVolume = com.example.liftrix.domain.model.Volume.ZERO,
                    averageVolume = com.example.liftrix.domain.model.Volume.ZERO
                )
            }
            
            liftrixSuccess(calendarData)
            
        } catch (e: Exception) {
            Timber.e(e, "Error generating volume calendar for user: $userId")
            liftrixFailure(
                LiftrixError.CalculationError(
                    errorMessage = "Failed to generate volume calendar: ${e.message}",
                    operation = "generateVolumeCalendar"
                )
            )
        }
    }

    /**
     * Helper methods for extracting workout data from exercises JSON
     */
    private fun extractTotalVolumeFromExercisesJson(exercisesJson: String): com.example.liftrix.domain.model.Weight {
        return try {
            // Try to extract from enhanced JSON format first
            if (exercisesJson.contains("\"totalVolume\"")) {
                val volumeRegex = "\"totalVolume\"\\s*:\\s*([0-9.]+)".toRegex()
                val matchResult = volumeRegex.find(exercisesJson)
                val volumeKg = matchResult?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                com.example.liftrix.domain.model.Weight(volumeKg)
            } else {
                // Fallback: estimate from exercises
                com.example.liftrix.domain.model.Weight.ZERO
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract total volume from exercises JSON")
            com.example.liftrix.domain.model.Weight.ZERO
        }
    }
    
    private fun extractExerciseCount(exercisesJson: String): Int {
        return try {
            val exercisePattern = "\\{[^}]*\"name\"[^}]*\\}".toRegex()
            exercisePattern.findAll(exercisesJson).count()
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract exercise count")
            0
        }
    }
    
    private fun extractTotalSets(exercisesJson: String): Int {
        return try {
            val setsPattern = "\"sets\"\\s*:\\s*\\[[^\\]]*\\]".toRegex()
            setsPattern.findAll(exercisesJson).sumOf { match ->
                val setPattern = "\\{[^}]*\\}".toRegex()
                setPattern.findAll(match.value).count()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract total sets")
            0
        }
    }
    
    private fun extractCompletedSets(exercisesJson: String): Int {
        return try {
            val completedPattern = "\"isCompleted\"\\s*:\\s*true".toRegex()
            completedPattern.findAll(exercisesJson).count()
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract completed sets")
            0
        }
    }
    
    private fun extractTotalReps(exercisesJson: String): Int {
        return try {
            val repsPattern = "\"reps\"\\s*:\\s*(\\d+)".toRegex()
            repsPattern.findAll(exercisesJson)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .sum()
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract total reps")
            0
        }
    }
    
    private fun calculateCompletionPercentage(exercisesJson: String): Double {
        val totalSets = extractTotalSets(exercisesJson)
        val completedSets = extractCompletedSets(exercisesJson)
        return if (totalSets > 0) {
            (completedSets.toDouble() / totalSets.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    private fun calculateAverageIntensity(exercisesJson: String): Float {
        return try {
            val intensityPattern = "\"intensity\"\\s*:\\s*([0-9.]+)".toRegex()
            val intensities = intensityPattern.findAll(exercisesJson)
                .mapNotNull { it.groupValues.getOrNull(1)?.toFloatOrNull() }
                .toList()
            
            if (intensities.isNotEmpty()) {
                intensities.average().toFloat()
            } else {
                0.75f // Default intensity estimate
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate average intensity")
            0.75f
        }
    }
    
    private fun calculateVolumeEfficiency(volume: com.example.liftrix.domain.model.Weight, duration: java.time.Duration): Float {
        val minutes = duration.toMinutes()
        return if (minutes > 0) {
            (volume.kilograms / minutes).toFloat()
        } else {
            0f
        }
    }
    
    private fun extractExerciseCategories(exercisesJson: String): Set<com.example.liftrix.domain.model.ExerciseCategory> {
        return try {
            val categoryPattern = "\"category\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            categoryPattern.findAll(exercisesJson)
                .mapNotNull { match ->
                    try {
                        com.example.liftrix.domain.model.ExerciseCategory.valueOf(match.groupValues[1])
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                .toSet()
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract exercise categories")
            emptySet()
        }
    }

    /**
     * Clears all memoization caches for data invalidation
     * 
     * Should be called when user data changes or when fresh calculations are needed
     */
    suspend fun clearMemoizationCaches() {
        progressMetricsCache.clear()
        workoutMetricsCache.clear()
        volumeCalendarCache.clear()
        Timber.d("Cleared all AnalyticsEngine memoization caches")
    }
    
    /**
     * Invalidates specific cache entries for a user
     * 
     * @param userId The user ID to invalidate cache for
     */
    suspend fun invalidateUserCache(userId: String) {
        // Clear progress metrics cache for user
        progressMetricsCache.clear() // Simple approach - clear all since keys contain userId
        
        // Clear workout metrics cache for user workouts
        workoutMetricsCache.clear() // Simple approach - clear all since workout metrics are user-specific
        
        // Clear volume calendar cache for user
        volumeCalendarCache.clear() // Simple approach - clear all since keys contain userId
        
        Timber.d("Invalidated caches for user: $userId")
    }
    
    /**
     * Gets cache statistics for monitoring
     */
    suspend fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "progressMetricsCache" to progressMetricsCache.getStats(),
            "workoutMetricsCache" to workoutMetricsCache.getStats(),
            "volumeCalendarCache" to volumeCalendarCache.getStats()
        )
    }
    
    /**
     * Validates calculated metrics for consistency
     */
    private fun validateMetrics(metrics: ProgressMetrics): Boolean {
        return metrics.isValid() &&
               metrics.totalWorkouts <= 1000 && // Reasonable upper bound
               metrics.totalVolume <= 1_000_000 && // Reasonable upper bound
               metrics.averageDuration <= 300 && // Max 5 hours
               metrics.strengthGain <= 200 && // Max 200% gain
               metrics.workoutFrequency <= 10.0 // Max 10 workouts per week
    }
}