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
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

@Singleton
class ProgressStatsRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val analyticsMapper: AnalyticsMapper
) : ProgressStatsRepository {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        private const val MILLISECONDS_PER_MINUTE = 60_000L
    }

    override fun getWorkoutVolumeData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<VolumeDataPoint>> = flow {
        try {
            val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
                userId, startDate, endDate, exerciseDao, exerciseSetDao
            )
            
            val volumeData = dailyMetrics.toVolumeDataPoints()
            
            emit(volumeData)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout volume data for user: $userId")
            emit(emptyList())
        }
    }

    override fun getWorkoutDurationData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DurationDataPoint>> = flow {
        try {
            val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
                userId, startDate, endDate, exerciseDao, exerciseSetDao
            )
            
            val durationData = dailyMetrics.toDurationDataPoints()
            
            emit(durationData)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout duration data for user: $userId")
            emit(emptyList())
        }
    }

    override fun getWorkoutFrequencyData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<FrequencyDataPoint>> = flow {
        try {
            val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
                userId, startDate, endDate, exerciseDao, exerciseSetDao
            )
            
            val frequencyData = dailyMetrics.toFrequencyDataPoints()
            
            emit(frequencyData)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout frequency data for user: $userId")
            emit(emptyList())
        }
    }

    override fun getProgressSummary(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<ProgressSummary> = flow {
        try {
            val dailyMetrics = workoutDao.getWorkoutsInDateRangeWithMetrics(
                userId, startDate, endDate, exerciseDao, exerciseSetDao
            )
            
            val summary = dailyMetrics.calculateProgressSummary(startDate, endDate)
            
            emit(summary)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get progress summary for user: $userId")
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
    }

    override fun getExerciseVolumeProgression(
        userId: String,
        exerciseLibraryId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<VolumeDataPoint>> = flow {
        try {
            val startDateString = startDate.toJavaLocalDate().format(DATE_FORMATTER)
            val endDateString = endDate.toJavaLocalDate().format(DATE_FORMATTER)
            
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
                        date = kotlinx.datetime.LocalDate.parse(date!!.format(DATE_FORMATTER)),
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
            val startDate = LocalDate(year, month, 1)
            // Calculate last day of month
            val endDate = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> LocalDate(year, month, 31)
                4, 6, 9, 11 -> LocalDate(year, month, 30)
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 
                    LocalDate(year, month, 29) else LocalDate(year, month, 28)
                else -> LocalDate(year, month, 31)
            }
            
            val dailyVolumes = workoutDao.getDailyVolumesByDateRange(userId, startDate.toString(), endDate.toString())
            val volumeCalendarData = analyticsMapper.mapToVolumeCalendarData(
                dailyVolumes,
                year,
                kotlinx.datetime.Month.values()[month - 1]
            )
            
            emit(LiftrixResult.Success(volumeCalendarData))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get volume calendar data for user: $userId, month: $month")
            emit(LiftrixResult.Error(LiftrixError.DatabaseError("Failed to load volume calendar data")))
        }
    }
    
    override suspend fun getProgressMetrics(
        userId: String,
        timeRange: TimeRange
    ): Flow<LiftrixResult<ProgressMetrics>> = flow {
        try {
            val startDateString = timeRange.startDate.toJavaLocalDate().format(DATE_FORMATTER)
            val endDateString = timeRange.endDate.toJavaLocalDate().format(DATE_FORMATTER)
            
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
            
            emit(LiftrixResult.Success(progressMetrics))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get progress metrics for user: $userId, timeRange: $timeRange")
            emit(LiftrixResult.Error(LiftrixError.DatabaseError("Failed to load progress metrics")))
        }
    }
    
    override suspend fun getDashboardData(
        userId: String,
        timeRange: TimeRange
    ): Flow<LiftrixResult<DashboardData>> = flow {
        try {
            val today = kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
            
            // Get volume calendar data for current month
            val volumeCalendarFlow = getVolumeCalendarData(userId, today.year, today.monthNumber)
            var volumeCalendarData: VolumeCalendarData? = null
            
            volumeCalendarFlow.collect { result ->
                when (result) {
                    is LiftrixResult.Success -> volumeCalendarData = result.data
                    is LiftrixResult.Error -> {
                        emit(result)
                        return@collect
                    }
                }
            }
            
            // Get progress metrics
            val progressMetricsFlow = getProgressMetrics(userId, timeRange)
            var progressMetrics: ProgressMetrics? = null
            
            progressMetricsFlow.collect { result ->
                when (result) {
                    is LiftrixResult.Success -> progressMetrics = result.data
                    is LiftrixResult.Error -> {
                        emit(result)
                        return@collect
                    }
                }
            }
            
            // Create dashboard data
            val dashboardData = DashboardData(
                volumeCalendar = volumeCalendarData!!,
                progressMetrics = progressMetrics!!,
                keyMetrics = createKeyMetrics(progressMetrics!!),
                lastUpdated = kotlinx.datetime.Clock.System.now()
            )
            
            emit(LiftrixResult.Success(dashboardData))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get dashboard data for user: $userId, timeRange: $timeRange")
            emit(LiftrixResult.Error(LiftrixError.DatabaseError("Failed to load dashboard data")))
        }
    }
    
    /**
     * Creates key metrics map from progress metrics
     */
    private fun createKeyMetrics(progressMetrics: ProgressMetrics): Map<String, Any> {
        return mapOf(
            "totalVolume" to progressMetrics.volumeMetrics.totalVolume.format(),
            "workoutCount" to progressMetrics.frequencyMetrics.workoutCount,
            "averageWorkoutsPerWeek" to progressMetrics.frequencyMetrics.averageWorkoutsPerWeek,
            "currentStreak" to progressMetrics.consistencyMetrics.currentStreak,
            "overallProgressScore" to progressMetrics.calculateOverallProgressScore(),
            "primaryTrendDirection" to progressMetrics.getPrimaryTrendDirection().name,
            "consistencyScore" to progressMetrics.frequencyMetrics.getConsistencyScore(),
            "recentPRCount" to progressMetrics.strengthMetrics.recentPRCount
        )
    }

}