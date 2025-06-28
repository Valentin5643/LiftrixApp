package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ProgressStatsRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao
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
            val startDateString = startDate.toJavaLocalDate().format(DATE_FORMATTER)
            val endDateString = endDate.toJavaLocalDate().format(DATE_FORMATTER)
            
            // Get all workouts in date range for user
            val workouts = workoutDao.getWorkoutsInDateRangeForUser(userId, startDateString, endDateString)
            
            // Group by date and calculate volume
            val volumeData = workouts.groupBy { it.date }
                .map { (date, dailyWorkouts) ->
                    var totalVolume = 0f
                    var exerciseCount = 0
                    
                    dailyWorkouts.forEach { workout ->
                        // Get exercises for this workout
                        val exercises = exerciseDao.getExercisesByWorkoutId(workout.id)
                        exerciseCount += exercises.size
                        
                        // Calculate volume for each exercise
                        exercises.forEach { exercise ->
                            val sets = exerciseSetDao.getSetsByExercise(exercise.id)
                            sets.forEach { set ->
                                if (set.weightKg != null && set.reps != null) {
                                    totalVolume += set.weightKg * set.reps
                                }
                            }
                        }
                    }
                    
                    VolumeDataPoint(
                        date = kotlinx.datetime.LocalDate.parse(date.format(DATE_FORMATTER)),
                        totalVolume = totalVolume,
                        exerciseCount = exerciseCount
                    )
                }
                .sortedBy { it.date }
            
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
            val startDateString = startDate.toJavaLocalDate().format(DATE_FORMATTER)
            val endDateString = endDate.toJavaLocalDate().format(DATE_FORMATTER)
            
            // Get all workouts in date range for user
            val workouts = workoutDao.getWorkoutsInDateRangeForUser(userId, startDateString, endDateString)
            
            // Group by date and calculate duration
            val durationData = workouts.groupBy { it.date }
                .map { (date, dailyWorkouts) ->
                    val totalDuration = dailyWorkouts.sumOf { workout ->
                        if (workout.startTime != null && workout.endTime != null) {
                            val durationMs = workout.endTime.toEpochMilli() - workout.startTime.toEpochMilli()
                            (durationMs / MILLISECONDS_PER_MINUTE).toInt()
                        } else {
                            0
                        }
                    }
                    
                    DurationDataPoint(
                        date = kotlinx.datetime.LocalDate.parse(date.format(DATE_FORMATTER)),
                        durationMinutes = totalDuration,
                        workoutCount = dailyWorkouts.size
                    )
                }
                .sortedBy { it.date }
            
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
            val startDateString = startDate.toJavaLocalDate().format(DATE_FORMATTER)
            val endDateString = endDate.toJavaLocalDate().format(DATE_FORMATTER)
            
            // Get all workouts in date range for user
            val workouts = workoutDao.getWorkoutsInDateRangeForUser(userId, startDateString, endDateString)
            
            // Group by date and calculate frequency with intensity
            val workoutsByDate = workouts.groupBy { it.date }
            val maxWorkoutsPerDay = workoutsByDate.values.maxOfOrNull { it.size } ?: 1
            
            val frequencyData = workoutsByDate.map { (date, dailyWorkouts) ->
                val intensity = if (maxWorkoutsPerDay > 0) {
                    dailyWorkouts.size.toFloat() / maxWorkoutsPerDay.toFloat()
                } else {
                    0f
                }
                
                FrequencyDataPoint(
                    date = kotlinx.datetime.LocalDate.parse(date.format(DATE_FORMATTER)),
                    workoutCount = dailyWorkouts.size,
                    intensity = intensity.coerceIn(0f, 1f)
                )
            }.sortedBy { it.date }
            
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
            val startDateString = startDate.toJavaLocalDate().format(DATE_FORMATTER)
            val endDateString = endDate.toJavaLocalDate().format(DATE_FORMATTER)
            
            // Get all workouts in date range for user
            val workouts = workoutDao.getWorkoutsInDateRangeForUser(userId, startDateString, endDateString)
            
            // Calculate total volume
            var totalVolume = 0f
            workouts.forEach { workout ->
                val exercises = exerciseDao.getExercisesByWorkoutId(workout.id)
                exercises.forEach { exercise ->
                    val sets = exerciseSetDao.getSetsByExercise(exercise.id)
                    sets.forEach { set ->
                        if (set.weightKg != null && set.reps != null) {
                            totalVolume += set.weightKg * set.reps
                        }
                    }
                }
            }
            
            // Calculate average duration
            val completedWorkouts = workouts.filter { it.startTime != null && it.endTime != null }
            val averageDuration = if (completedWorkouts.isNotEmpty()) {
                completedWorkouts.map { workout ->
                    val durationMs = workout.endTime!!.toEpochMilli() - workout.startTime!!.toEpochMilli()
                    (durationMs / MILLISECONDS_PER_MINUTE).toInt()
                }.average().toInt()
            } else {
                0
            }
            
            // Calculate total active time
            val totalActiveTime = completedWorkouts.sumOf { workout ->
                val durationMs = workout.endTime!!.toEpochMilli() - workout.startTime!!.toEpochMilli()
                (durationMs / MILLISECONDS_PER_MINUTE).toInt()
            }
            
            // Calculate streaks
            val workoutDates = workouts.map { it.date }.distinct().sorted()
            val (currentStreak, longestStreak) = calculateStreaks(workoutDates)
            
            // Calculate average workouts per week
            val daysBetween = abs(endDate.toEpochDays() - startDate.toEpochDays())
            val weeks = if (daysBetween > 0) daysBetween / 7.0 else 1.0
            val averageWorkoutsPerWeek = if (weeks > 0) workouts.size.toFloat() / weeks.toFloat() else 0f
            
            val summary = ProgressSummary(
                totalWorkouts = workouts.size,
                totalVolume = totalVolume,
                averageDuration = averageDuration,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                averageWorkoutsPerWeek = averageWorkoutsPerWeek,
                totalActiveTime = totalActiveTime
            )
            
            emit(summary)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get progress summary for user: $userId")
            emit(ProgressSummary(0, 0f, 0, 0, 0, 0f, 0))
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
            if (daysSinceLastWorkout <= 1) {
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
}