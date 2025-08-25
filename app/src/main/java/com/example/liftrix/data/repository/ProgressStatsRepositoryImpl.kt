package com.example.liftrix.data.repository

import com.example.liftrix.data.extensions.getWorkoutsInDateRangeWithMetrics
import com.example.liftrix.data.extensions.toDurationDataPoints
import com.example.liftrix.data.extensions.toFrequencyDataPoints
import com.example.liftrix.data.extensions.toVolumeDataPoints
import com.example.liftrix.data.extensions.calculateProgressSummary
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.AnalyticsMapper
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.DashboardData
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlin.random.Random
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.sync.AnalyticsCalculation
import com.example.liftrix.service.sync.RealtimeSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone as KotlinTimeZone
import kotlinx.datetime.toJavaInstant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.Clock
import java.time.ZoneId

@Singleton
class ProgressStatsRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val analyticsMapper: AnalyticsMapper,
    private val cacheManager: com.example.liftrix.core.cache.CacheManager,
    private val realtimeSyncManager: RealtimeSyncManager
) : ProgressStatsRepository {

    companion object {
        private const val MILLISECONDS_PER_MINUTE = 60_000L
    }

    override fun getWorkoutVolumeData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<VolumeDataPoint>> = flow {
        Timber.d("🔍 REPO-DEBUG: getWorkoutVolumeData() Flow starting for userId=$userId")
        val timeRange = TimeRange(
            java.util.Date.from(java.time.LocalDate.of(startDate.year, startDate.monthNumber, startDate.dayOfMonth).atStartOfDay(ZoneId.systemDefault()).toInstant()),
            java.util.Date.from(java.time.LocalDate.of(endDate.year, endDate.monthNumber, endDate.dayOfMonth).atStartOfDay(ZoneId.systemDefault()).toInstant())
        )
        val cacheKey = com.example.liftrix.core.cache.CacheKeyUtils.createVolumeKey(userId, timeRange)
        
        // Check cache first
        Timber.d("🔍 REPO-DEBUG: Checking repository cache for key=$cacheKey")
        val cachedEntry = cacheManager.get<List<VolumeDataPoint>>(cacheKey)
        if (cachedEntry != null && cachedEntry.isValid()) {
            Timber.d("🔍 REPO-DEBUG: Repository cache HIT, emitting cached data")
            emit(cachedEntry.data)
            return@flow
        }
        
        // Cache miss - compute data
        Timber.d("🔍 REPO-DEBUG: Repository cache MISS, calling DAO.getWorkoutsInDateRangeWithMetrics()")
        val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
            userId, startDate, endDate
        )
        Timber.d("🔍 REPO-DEBUG: DAO call completed, got ${dailyMetrics.size} metrics")
        
        val volumeData = dailyMetrics.toVolumeDataPoints()
        Timber.d("🔍 REPO-DEBUG: Converted to ${volumeData.size} VolumeDataPoints")
        
        // Cache the result for 1 hour
        cacheManager.put(cacheKey, volumeData, 1.hours)
        
        // Trigger sync event for volume data changes
        triggerSyncIfSignificantChange(userId, "volume_data", volumeData.size)
        
        Timber.d("🔍 REPO-DEBUG: About to emit ${volumeData.size} VolumeDataPoints")
        emit(volumeData)
        Timber.d("🔍 REPO-DEBUG: emit() completed successfully")
    }.catch { e ->
        Timber.e(e, "Failed to get workout volume data for user: $userId")
        emit(emptyList())
    }

    override fun getWorkoutDurationData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DurationDataPoint>> = flow {
        val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
            userId, startDate, endDate
        )
        
        val durationData = dailyMetrics.toDurationDataPoints()
        
        emit(durationData)
    }.catch { e ->
        Timber.e(e, "Failed to get workout duration data for user: $userId")
        emit(emptyList())
    }

    override fun getWorkoutFrequencyData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<FrequencyDataPoint>> = flow {
        val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
            userId, startDate, endDate
        )
        
        val frequencyData = dailyMetrics.toFrequencyDataPoints()
        
        emit(frequencyData)
    }.catch { e ->
        Timber.e(e, "Failed to get workout frequency data for user: $userId")
        emit(emptyList())
    }

    override fun getProgressSummary(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<ProgressSummary> = flow {
        Timber.d("ProgressStatsRepository: Loading progress summary for user: $userId, range: $startDate to $endDate")
        
        val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(userId, startDate, endDate)
        Timber.d("ProgressStatsRepository: Found ${dailyMetrics.size} days with metrics")
        
        val summary = dailyMetrics.calculateProgressSummary(startDate, endDate)
        Timber.d("ProgressStatsRepository: Summary calculated - workouts: ${summary.totalWorkouts}, volume: ${summary.totalVolume}")
        
        // Always emit real data - no sample data fallbacks
        emit(summary)
    }.catch { e ->
        // FIXED: Use Flow.catch operator instead of try-catch with emit
        Timber.e(e, "ProgressStatsRepository: Failed to get progress summary for user: $userId")
        
        // Emit empty/zero progress summary when error occurs
        emit(ProgressSummary(
            totalWorkouts = 0,
            totalVolume = 0f,
            averageDuration = 0,
            currentStreak = 0,
            longestStreak = 0,
            averageWorkoutsPerWeek = 0f,
            totalActiveTime = 0
        ))
    }

    override fun getExerciseVolumeProgression(
        userId: String,
        exerciseLibraryId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<VolumeDataPoint>> = flow {
        try {
            val startDateString = startDate.toString()
            val endDateString = endDate.toString()
            
            // Get exercise history for specific exercise
            val exercises = exerciseDao.getExerciseHistoryInDateRange(userId, exerciseLibraryId, startDateString, endDateString)
            
            // Group by workout date and calculate volume
            val volumeData = exercises.groupBy { exercise ->
                // Get workout date for this exercise
                workoutDao.getWorkoutByExerciseId(exercise.id)?.date
            }
                .filterKeys { it != null }
                .map { (date, exerciseList) ->
                    var totalVolume = 0f
                    
                    exerciseList.forEach { exercise ->
                        val sets = exerciseSetDao.getSetsByExercise(exercise.id)
                        sets.forEach { set ->
                            if (set.weightKg != null && set.reps != null) {
                                totalVolume += set.weightKg * set.reps
                            }
                        }
                    }
                    
                    VolumeDataPoint(
                        date = kotlinx.datetime.LocalDate.parse(date!!.toString()),
                        totalVolume = totalVolume,
                        exerciseCount = exerciseList.size
                    )
                }
                .sortedBy { it.date }
            
            emit(volumeData)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get exercise volume progression for user: $userId, exercise: $exerciseLibraryId")
            emit(emptyList())
        }
    }


    /**
     * Calculate current and longest workout streaks from workout dates
     */
    private fun calculateStreaks(workoutDates: List<java.time.LocalDate>): Pair<Int, Int> {
        if (workoutDates.isEmpty()) return Pair(0, 0)
        
        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 1
        
        val today = java.time.LocalDate.now()
        val sortedDates = workoutDates.sorted()
        
        // Calculate longest streak
        for (i in 1 until sortedDates.size) {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(sortedDates[i-1], sortedDates[i])
            if (daysBetween == 1L) {
                tempStreak++
            } else {
                longestStreak = maxOf(longestStreak, tempStreak)
                tempStreak = 1
            }
        }
        longestStreak = maxOf(longestStreak, tempStreak)
        
        // Calculate current streak (from most recent workout to today)
        val lastWorkoutDate = sortedDates.lastOrNull()
        if (lastWorkoutDate != null) {
            val daysSinceLastWorkout = java.time.temporal.ChronoUnit.DAYS.between(lastWorkoutDate, today)
            if (daysSinceLastWorkout <= 1L) {
                // Find consecutive days leading up to today
                var streak = 1
                for (i in sortedDates.size - 2 downTo 0) {
                    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(sortedDates[i], sortedDates[i + 1])
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

    // Enhanced analytics methods implementation
    
    override suspend fun getVolumeCalendarData(
        userId: String,
        year: Int,
        month: Int
    ): Flow<LiftrixResult<VolumeCalendarData>> = flow {
        try {
            Timber.d("🔍 VOLUME-CALENDAR-FIX: getVolumeCalendarData starting for userId=$userId, year=$year, month=$month")
            
            val startDate = LocalDate(year, month, 1)
            // Calculate last day of month
            val endDate = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> LocalDate(year, month, 31)
                4, 6, 9, 11 -> LocalDate(year, month, 30)
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 
                    LocalDate(year, month, 29) else LocalDate(year, month, 28)
                else -> LocalDate(year, month, 31)
            }
            
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Using same data source as working volume chart")
            
            // FIX: Use the same data calculation method as the working volume chart
            // This calls getWorkoutsInDateRangeWithMetrics() which properly calculates volume from exercise sets
            val allDailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(userId, startDate, endDate)
            
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Got ${allDailyMetrics.size} daily metrics")
            
            // Filter to only include dates within the specified month (VolumeCalendarData validation requirement)
            val filteredMetrics = allDailyMetrics.filterKeys { date ->
                date.year == year && date.monthNumber == month
            }
            
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Filtered to ${filteredMetrics.size} metrics within month $month/$year")
            
            // Convert daily metrics to volume calendar format
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Converting ${filteredMetrics.size} daily metrics to calendar format")
            
            val dailyVolumes = try {
                filteredMetrics.mapValues { (date, metrics) ->
                    Timber.d("🔍 VOLUME-CALENDAR-FIX: Processing date=$date, volume=${metrics.totalVolume}kg")
                    com.example.liftrix.domain.model.Volume(metrics.totalVolume.toDouble())
                }
            } catch (e: Exception) {
                Timber.e(e, "🔍 VOLUME-CALENDAR-FIX: Error converting daily metrics to volumes")
                throw e
            }
            
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Converted to ${dailyVolumes.size} daily volumes")
            
            // Use historical maximum for consistent proportional scaling across months
            val maxVolume = try {
                var historicalMaxVolume: com.example.liftrix.domain.model.Volume? = null
                
                // Get user's historical maximum daily volume
                getUserMaxDailyVolume(userId).collect { result ->
                    result.fold(
                        onSuccess = { historicalMaxVolume = it },
                        onFailure = { 
                            Timber.w("Failed to get historical max volume, falling back to monthly max")
                            historicalMaxVolume = dailyVolumes.values.maxByOrNull { it.kilograms } 
                                ?: com.example.liftrix.domain.model.Volume.ZERO
                        }
                    )
                }
                
                val finalMaxVolume = historicalMaxVolume ?: com.example.liftrix.domain.model.Volume.ZERO
                Timber.d("🔍 VOLUME-CALENDAR-FIX: Using max volume: ${finalMaxVolume.kilograms}kg for proportional scaling")
                finalMaxVolume
                
            } catch (e: Exception) {
                Timber.e(e, "🔍 VOLUME-CALENDAR-FIX: Error calculating max volume, using monthly fallback")
                dailyVolumes.values.maxByOrNull { it.kilograms } ?: com.example.liftrix.domain.model.Volume.ZERO
            }
            
            val totalVolume = try {
                dailyVolumes.values.fold(com.example.liftrix.domain.model.Volume.ZERO) { acc, volume -> 
                    com.example.liftrix.domain.model.Volume(acc.kilograms + volume.kilograms) 
                }
            } catch (e: Exception) {
                Timber.e(e, "🔍 VOLUME-CALENDAR-FIX: Error calculating total volume")
                throw e
            }
            
            val averageVolume = try {
                if (dailyVolumes.isNotEmpty()) {
                    com.example.liftrix.domain.model.Volume(totalVolume.kilograms / dailyVolumes.size)
                } else {
                    com.example.liftrix.domain.model.Volume.ZERO
                }
            } catch (e: Exception) {
                Timber.e(e, "🔍 VOLUME-CALENDAR-FIX: Error calculating average volume")
                throw e
            }
            
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Calculated - Max: ${maxVolume.kilograms}kg, Total: ${totalVolume.kilograms}kg, Avg: ${averageVolume.kilograms}kg")
            
            val volumeCalendarData = try {
                VolumeCalendarData(
                    year = year,
                    month = kotlinx.datetime.Month.values()[month - 1],
                    dailyVolumes = dailyVolumes,
                    maxVolume = maxVolume,
                    averageVolume = averageVolume
                )
            } catch (e: Exception) {
                Timber.e(e, "🔍 VOLUME-CALENDAR-FIX: Error creating VolumeCalendarData object")
                throw e
            }
            
            Timber.d("🔍 VOLUME-CALENDAR-FIX: Created calendar data - Max: ${maxVolume.kilograms}kg, Avg: ${averageVolume.kilograms}kg, Days: ${dailyVolumes.size}")
            
            emit(LiftrixResult.success(volumeCalendarData))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get volume calendar data for user: $userId, month: $month")
            emit(LiftrixResult.failure(LiftrixError.DatabaseError("Failed to load volume calendar data")))
        }
    }
    
    override suspend fun getUserMaxDailyVolume(userId: String): Flow<LiftrixResult<com.example.liftrix.domain.model.Volume>> = flow {
        try {
            Timber.d("🔍 MAX-VOLUME: Getting historical maximum daily volume for userId=$userId")
            
            // Get all workout data for the user to find maximum daily volume
            // Using a reasonable time window (last 2 years) for performance
            val currentDate = kotlinx.datetime.Clock.System.now()
            val twoYearsAgo = kotlinx.datetime.LocalDate.fromEpochDays(
                (currentDate.epochSeconds / 86400).toInt() - (365 * 2)
            )
            val today = kotlinx.datetime.LocalDate.fromEpochDays(
                (currentDate.epochSeconds / 86400).toInt()
            )
            
            // Get all daily metrics in the time window
            val allDailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(userId, twoYearsAgo, today)
            
            Timber.d("🔍 MAX-VOLUME: Found ${allDailyMetrics.size} days with workout data")
            
            // Find the maximum daily volume
            val maxDailyVolume = allDailyMetrics.values.maxOfOrNull { it.totalVolume } ?: 0f
            
            Timber.d("🔍 MAX-VOLUME: Historical maximum daily volume: ${maxDailyVolume}kg")
            
            // Apply minimum threshold to ensure reasonable intensity scaling
            // Even users with low volume should see visual differences
            val effectiveMaxVolume = maxOf(maxDailyVolume.toDouble(), 1000.0) // Minimum 1000kg for scaling
            
            val maxVolume = com.example.liftrix.domain.model.Volume.fromKilograms(effectiveMaxVolume)
            
            Timber.d("🔍 MAX-VOLUME: Effective max volume for scaling: ${maxVolume.kilograms}kg")
            
            emit(LiftrixResult.success(maxVolume))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user max daily volume for user: $userId")
            emit(LiftrixResult.failure(LiftrixError.DatabaseError("Failed to load maximum daily volume")))
        }
    }
    
    override suspend fun getProgressMetrics(
        userId: String,
        timeRange: TimeRange
    ): Flow<LiftrixResult<ProgressMetrics>> = flow {
        try {
            val startDateString = timeRange.startDate.toString()
            val endDateString = timeRange.endDate.toString()
            
            // Get workout statistics for the time range
            val workoutStats = workoutDao.getWorkoutStats(userId, startDateString)
            
            // Get detailed workout entities for analysis
            val workoutEntities = workoutDao.getWorkoutsInDateRangeForUser(userId, startDateString, endDateString)
            
            val progressMetrics = analyticsMapper.mapToProgressMetrics(
                workoutStats,
                workoutEntities,
                timeRange,
                userId
            )
            
            emit(LiftrixResult.success(progressMetrics))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get progress metrics for user: $userId, timeRange: $timeRange")
            emit(LiftrixResult.failure(LiftrixError.DatabaseError("Failed to load progress metrics")))
        }
    }
    
    override suspend fun getDashboardData(
        userId: String,
        timeRange: TimeRange
    ): Flow<LiftrixResult<DashboardData>> = flow {
        try {
            val today = kotlinx.datetime.LocalDate.fromEpochDays((kotlinx.datetime.Clock.System.now().epochSeconds / 86400).toInt())
            
            // Get volume calendar data for current month
            val volumeCalendarFlow = getVolumeCalendarData(userId, today.year, today.monthNumber)
            var volumeCalendarData: VolumeCalendarData? = null
            
            volumeCalendarFlow.collect { result ->
                result.fold(
                    onSuccess = { volumeCalendarData = it },
                    onFailure = { 
                        emit(LiftrixResult.failure(it))
                        return@collect
                    }
                )
            }
            
            // Get progress metrics
            val progressMetricsFlow = getProgressMetrics(userId, timeRange)
            var progressMetrics: ProgressMetrics? = null
            
            progressMetricsFlow.collect { result ->
                result.fold(
                    onSuccess = { progressMetrics = it },
                    onFailure = { 
                        emit(LiftrixResult.failure(it))
                        return@collect
                    }
                )
            }
            
            // Create dashboard data
            val dashboardData = DashboardData(
                volumeCalendar = volumeCalendarData!!,
                progressMetrics = progressMetrics!!,
                keyMetrics = createKeyMetrics(progressMetrics!!),
                lastUpdated = kotlinx.datetime.Clock.System.now()
            )
            
            emit(LiftrixResult.success(dashboardData))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get dashboard data for user: $userId, timeRange: $timeRange")
            emit(LiftrixResult.failure(LiftrixError.DatabaseError("Failed to load dashboard data")))
        }
    }
    
    /**
     * Creates key metrics map from progress metrics
     */
    private fun createKeyMetrics(progressMetrics: ProgressMetrics): Map<String, Any> {
        return mapOf(
            "totalVolume" to progressMetrics.volumeMetrics.totalVolume.toString(),
            "workoutCount" to progressMetrics.frequencyMetrics.workoutCount,
            "averageWorkoutsPerWeek" to progressMetrics.frequencyMetrics.averageWorkoutsPerWeek,
            "currentStreak" to progressMetrics.consistencyMetrics.currentStreak,
            "volumeTrend" to progressMetrics.volumeMetrics.volumeTrend.name,
            "consistencyScore" to progressMetrics.frequencyMetrics.consistencyScore,
            "recentPRCount" to progressMetrics.strengthMetrics.recentPRCount
        )
    }

    // Analytics sync methods for AnalyticsSyncWorker
    
    override suspend fun getPendingSyncCalculations(userId: String): List<AnalyticsCalculation> {
        return try {
            // Get analytics calculations that haven't been synced yet
            // This is a placeholder implementation - in a real system this would query
            // a dedicated analytics calculation table
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get pending sync calculations for user: $userId")
            emptyList()
        }
    }
    
    override suspend fun markCalculationsAsSynced(userId: String, calculationIds: List<String>) {
        try {
            // Mark calculations as synced in database
            // This is a placeholder implementation
            Timber.d("Marked ${calculationIds.size} calculations as synced for user: $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark calculations as synced for user: $userId")
        }
    }
    
    override suspend fun queueCalculationForSync(calculation: AnalyticsCalculation) {
        try {
            // Queue calculation for sync
            // This is a placeholder implementation
            Timber.d("Queued calculation for sync: ${calculation.id}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue calculation for sync: ${calculation.id}")
        }
    }
    
    override suspend fun getUnsyncedCalculationsCount(userId: String): Int {
        return try {
            // Get count of unsynced calculations
            // This is a placeholder implementation
            0
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced calculations count for user: $userId")
            0
        }
    }
    
    /**
     * Triggers real-time sync if there are significant changes in the data
     */
    private suspend fun triggerSyncIfSignificantChange(userId: String, dataType: String, changeCount: Int) {
        try {
            // Only trigger sync for significant changes (e.g., new workouts, PRs)
            if (changeCount > 0) {
                Timber.d("ProgressStatsRepository: Triggering sync for $dataType changes (count: $changeCount)")
                
                // Start real-time sync for this user to propagate changes
                realtimeSyncManager.startRealtimeSync(userId)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to trigger sync for $dataType changes")
        }
    }
    
    /**
     * Notifies sync manager about workout data updates
     */
    suspend fun notifyWorkoutDataUpdate(userId: String, workoutId: String) {
        try {
            Timber.d("ProgressStatsRepository: Notifying workout data update - user: $userId, workout: $workoutId")
            
            // Invalidate relevant cache entries
            val volumeCacheKey = com.example.liftrix.core.cache.CacheKeyUtils.createVolumeKey(
                userId, 
                TimeRange.lastMonth()
            )
            cacheManager.invalidate(volumeCacheKey)
            
            // Trigger sync for workout-related widgets
            realtimeSyncManager.startRealtimeSync(userId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify workout data update for user: $userId")
        }
    }
    
    /**
     * Notifies sync manager about personal record updates
     */
    suspend fun notifyPersonalRecordUpdate(userId: String, exerciseId: String, recordType: String) {
        try {
            Timber.d("ProgressStatsRepository: Notifying PR update - user: $userId, exercise: $exerciseId, type: $recordType")
            
            // Trigger immediate sync for strength-related widgets
            realtimeSyncManager.startRealtimeSync(userId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify personal record update for user: $userId")
        }
    }

}