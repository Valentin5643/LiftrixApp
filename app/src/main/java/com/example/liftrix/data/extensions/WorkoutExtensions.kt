package com.example.liftrix.data.extensions

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toJavaLocalDate
import timber.log.Timber
import java.time.format.DateTimeFormatter

/**
 * Extension functions for workout data processing to eliminate repetitive code.
 * 
 * These extensions provide reusable calculations for:
 * - Volume calculations (weight × reps)
 * - Duration processing
 * - Frequency analysis
 * - Data grouping and aggregation
 */

/**
 * Data class to hold aggregated workout metrics for a single day
 */
data class WorkoutDayMetrics(
    val totalVolume: Float,
    val exerciseCount: Int,
    val workoutCount: Int,
    val totalDurationMinutes: Int,
    val averageDurationMinutes: Int
)

/**
 * Calculate comprehensive daily metrics from a list of workouts
 */
suspend fun List<WorkoutEntity>.calculateDailyMetrics(): Map<LocalDate, WorkoutDayMetrics> {
    val gson = Gson()
    return groupBy { it.date.toKotlinLocalDate() }.mapValues { (_, workouts) ->
        val validWorkouts = workouts.filter { it.startTime != null && it.endTime != null }
        
        var totalVolume = 0f
        var exerciseCount = 0
        
        // Calculate volume and exercise counts
        // Enhanced to properly handle both library and custom exercises (custom exercises
        // are stored as simplified ExerciseLibrary objects after conversion)
        workouts.forEach { workout ->
            val exercises: List<Exercise> = parseExercisesFromJsonLegacy(workout.id, workout.exercisesJson, gson)
            val workoutVolumeResult = calculateWorkoutVolumeEnhanced(workout.id, exercises)
            totalVolume += workoutVolumeResult.totalVolume
            exerciseCount += workoutVolumeResult.validExerciseCount
            
        }
        
        // Calculate duration metrics
        val totalDurationMinutes = validWorkouts.sumOf { workout ->
            val durationMs = workout.endTime!!.toEpochMilli() - workout.startTime!!.toEpochMilli()
            (durationMs / 60_000L).toInt()
        }
        
        val averageDurationMinutes = if (validWorkouts.isNotEmpty()) {
            totalDurationMinutes / validWorkouts.size
        } else 0
        
        val metrics = WorkoutDayMetrics(
            totalVolume = totalVolume,
            exerciseCount = exerciseCount,
            workoutCount = workouts.size,
            totalDurationMinutes = totalDurationMinutes,
            averageDurationMinutes = averageDurationMinutes
        )
        
        
        metrics
    }
}

/**
 * Convert WorkoutDayMetrics to VolumeDataPoint list
 */
fun Map<LocalDate, WorkoutDayMetrics>.toVolumeDataPoints(): List<VolumeDataPoint> {
    val volumeDataPoints = map { (date, metrics) ->
        VolumeDataPoint(
            date = date,
            totalVolume = metrics.totalVolume,
            exerciseCount = metrics.exerciseCount
        )
    }.sortedBy { it.date }
    
    val totalVolumeSum = volumeDataPoints.sumOf { it.totalVolume.toDouble() }.toFloat()
    
    return volumeDataPoints
}

/**
 * Convert WorkoutDayMetrics to DurationDataPoint list
 */
fun Map<LocalDate, WorkoutDayMetrics>.toDurationDataPoints(): List<DurationDataPoint> {
    return map { (date, metrics) ->
        DurationDataPoint(
            date = date,
            durationMinutes = metrics.averageDurationMinutes,
            workoutCount = metrics.workoutCount
        )
    }.sortedBy { it.date }
}

/**
 * Convert WorkoutDayMetrics to FrequencyDataPoint list with intensity calculation
 */
fun Map<LocalDate, WorkoutDayMetrics>.toFrequencyDataPoints(): List<FrequencyDataPoint> {
    val maxWorkoutsPerDay = values.maxOfOrNull { it.workoutCount } ?: 1
    
    return map { (date, metrics) ->
        val intensity = if (maxWorkoutsPerDay > 0) {
            metrics.workoutCount.toFloat() / maxWorkoutsPerDay.toFloat()
        } else 0f
        
        FrequencyDataPoint(
            date = date,
            workoutCount = metrics.workoutCount,
            intensity = intensity.coerceIn(0f, 1f)
        )
    }.sortedBy { it.date }
}

/**
 * Extension to convert Java LocalDate to Kotlin LocalDate
 */
private fun java.time.LocalDate.toKotlinLocalDate(): LocalDate {
    return LocalDate(year, monthValue, dayOfMonth)
}

/**
 * Common query helper for date range formatting
 */
fun LocalDate.toDateString(): String = toJavaLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

/**
 * Helper for getting workouts with proper date filtering and error handling
 */
suspend fun WorkoutDao.getWorkoutsInDateRangeWithMetrics(
    userId: String,
    startDate: LocalDate,
    endDate: LocalDate
): Map<LocalDate, WorkoutDayMetrics> {
    val workouts = try {
        kotlinx.coroutines.withTimeout(5000) { // 5 second timeout for database call
            getWorkoutsInDateRangeForUser(
                userId = userId,
                startDate = startDate.toDateString(),
                endDate = endDate.toDateString()
            )
        }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        Timber.e("Database query timed out after 5s for user: $userId, dates: ${startDate.toDateString()} to ${endDate.toDateString()}")
        throw RuntimeException("Database query timed out after 5 seconds. Check database performance and indexes.")
    }
    
    if (workouts.isEmpty()) {
        return emptyMap()
    }
    
    val dailyMetrics = workouts.calculateDailyMetrics()
    
    return dailyMetrics
}

/**
 * Calculate progress summary from daily metrics
 */
fun Map<LocalDate, WorkoutDayMetrics>.calculateProgressSummary(
    startDate: LocalDate,
    endDate: LocalDate
): ProgressSummary {
    val totalWorkouts = values.sumOf { it.workoutCount }
    val totalVolume = values.sumOf { it.totalVolume.toDouble() }.toFloat()
    val totalActiveTime = values.sumOf { it.totalDurationMinutes }
    
    val averageDuration = if (values.isNotEmpty()) {
        values.sumOf { it.averageDurationMinutes } / values.size
    } else 0
    
    // Calculate streaks from dates with workouts
    val workoutDates = filter { it.value.workoutCount > 0 }.keys.sorted()
    val (currentStreak, longestStreak) = calculateStreaks(workoutDates)
    
    // Calculate average workouts per week
    val daysBetween = kotlin.math.abs(endDate.toEpochDays() - startDate.toEpochDays())
    val weeks = if (daysBetween > 0) daysBetween / 7.0 else 1.0
    val averageWorkoutsPerWeek = if (weeks > 0) totalWorkouts.toFloat() / weeks.toFloat() else 0f
    
    return ProgressSummary(
        totalWorkouts = totalWorkouts,
        totalVolume = totalVolume,
        averageDuration = averageDuration,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        averageWorkoutsPerWeek = averageWorkoutsPerWeek,
        totalActiveTime = totalActiveTime
    )
}

/**
 * Calculate current and longest workout streaks from workout dates
 */
private fun calculateStreaks(workoutDates: List<LocalDate>): Pair<Int, Int> {
    if (workoutDates.isEmpty()) return Pair(0, 0)
    
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var longestStreak: Int = 1
    var currentStreak: Int = 0
    var tempStreak: Int = 1
    
    // Calculate longest streak
    for (i in 1 until workoutDates.size) {
        val previousDate = workoutDates[i - 1]
        val currentDate = workoutDates[i]
        val daysDiff: Long = (currentDate.toEpochDays() - previousDate.toEpochDays()).toLong()
        
        if (daysDiff == 1L) {
            tempStreak++
        } else {
            longestStreak = maxOf(longestStreak, tempStreak)
            tempStreak = 1
        }
    }
    longestStreak = maxOf(longestStreak, tempStreak)
    
    // Calculate current streak (from most recent date backwards)
    val mostRecentWorkout = workoutDates.last()
    val daysSinceLastWorkout: Long = (today.toEpochDays() - mostRecentWorkout.toEpochDays()).toLong()
    
    if (daysSinceLastWorkout <= 1L) { // Today or yesterday
        currentStreak = 1
        
        // Count backwards for consecutive days
        for (i in workoutDates.size - 2 downTo 0) {
            val currentDate = workoutDates[i + 1]
            val previousDate = workoutDates[i]
            val daysDiff: Long = (currentDate.toEpochDays() - previousDate.toEpochDays()).toLong()
            
            if (daysDiff == 1L) {
                currentStreak++
            } else {
                break
            }
        }
    }
    
    return Pair(currentStreak, longestStreak)
}

/**
 * Result of workout volume calculation with validation metrics
 */
data class WorkoutVolumeResult(
    val totalVolume: Float,
    val validExerciseCount: Int,
    val totalSets: Int,
    val validSets: Int
)

/**
 * Enhanced calculate total volume for a workout that properly handles custom exercises.
 * Custom exercises are stored as ExerciseLibrary objects after conversion, but with 
 * simplified metadata. This function provides robust volume calculation regardless
 * of whether the exercise was originally a library or custom exercise.
 */
private fun calculateWorkoutVolumeEnhanced(workoutId: String, exercises: List<Exercise>): WorkoutVolumeResult {
    if (exercises.isEmpty()) return WorkoutVolumeResult(0f, 0, 0, 0)
    
    var totalVolume = 0f
    var validExerciseCount = 0
    var totalSets = 0
    var validSets = 0
    
    exercises.forEach { exercise ->
        if (exercise.sets.isEmpty()) return@forEach
        
        var exerciseVolume = 0f
        var exerciseValidSets = 0
        
        exercise.sets.forEach { set ->
            totalSets++
            if (set.weight != null && set.reps != null) {
                val setVolume = (set.weight.kilograms * set.reps.count).toFloat()
                exerciseVolume += setVolume
                exerciseValidSets++
                validSets++
            }
        }
        
        if (exerciseValidSets > 0) {
            validExerciseCount++
            totalVolume += exerciseVolume
            
            // Enhanced logging to identify custom vs library exercises
            val exerciseType = identifyExerciseType(exercise.libraryExercise)
        }
    }
    
    return WorkoutVolumeResult(totalVolume, validExerciseCount, totalSets, validSets)
}

/**
 * Helper function to identify if an ExerciseLibrary object was originally a custom exercise.
 * Custom exercises typically have simplified metadata after conversion.
 */
private fun identifyExerciseType(exerciseLibrary: ExerciseLibrary): String {
    return when {
        // Custom exercises often have "Custom Exercise" as movementPattern
        exerciseLibrary.movementPattern == "Custom Exercise" -> "Custom"
        // Custom exercises may have simplified metadata
        exerciseLibrary.secondaryMuscleGroups.isEmpty() && exerciseLibrary.difficultyLevel == 3 -> "Custom (likely)"
        else -> "Library"
    }
}

/**
 * Legacy calculate total volume for a workout with comprehensive validation and logging
 */
private fun calculateWorkoutVolume(workoutId: String, exercises: List<Exercise>): WorkoutVolumeResult {
    if (exercises.isEmpty()) return WorkoutVolumeResult(0f, 0, 0, 0)
    
    var totalVolume = 0f
    var validExerciseCount = 0
    var totalSets = 0
    var validSets = 0
    
    exercises.forEach { exercise ->
        if (exercise.sets.isEmpty()) return@forEach
        
        var exerciseVolume = 0f
        var exerciseValidSets = 0
        
        exercise.sets.forEach { set ->
            totalSets++
            if (set.weight != null && set.reps != null) {
                val setVolume = (set.weight.kilograms * set.reps.count).toFloat()
                exerciseVolume += setVolume
                exerciseValidSets++
                validSets++
            }
        }
        
        if (exerciseValidSets > 0) {
            validExerciseCount++
            totalVolume += exerciseVolume
        }
    }
    
    return WorkoutVolumeResult(totalVolume, validExerciseCount, totalSets, validSets)
}

/**
 * Enhanced JSON parsing for exercises with comprehensive error handling and fallbacks.
 * Now supports both library exercises and custom exercises stored in SearchableExercise format.
 */
private fun parseExercisesFromJson(workoutId: String, exercisesJson: String, gson: Gson): List<SearchableExercise> {
    if (exercisesJson.isBlank()) return emptyList()
    
    // Attempt 1: Parse as SearchableExercise list (new format)
    try {
        val searchableType = object : TypeToken<List<SearchableExercise>>() {}.type
        val exercises = gson.fromJson<List<SearchableExercise>>(exercisesJson, searchableType)
        if (exercises != null && exercises.isNotEmpty()) return exercises
    } catch (e: Exception) {
    }
    
    // Attempt 2: Parse as legacy Exercise list and convert
    try {
        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
        val legacyExercises = gson.fromJson<List<Exercise>>(exercisesJson, exercisesType)
        if (legacyExercises != null && legacyExercises.isNotEmpty()) {
            return legacyExercises.map { exercise ->
                SearchableExercise.LibraryExercise(exercise.libraryExercise)
            }
        }
    } catch (e: Exception) {
    }
    
    // Attempt 3: Object with "exercises" key
    try {
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val dataMap = gson.fromJson<Map<String, Any>>(exercisesJson, mapType)
        
        if (dataMap?.containsKey("exercises") == true) {
            val searchableType = object : TypeToken<List<SearchableExercise>>() {}.type
            val exercises = gson.fromJson<List<SearchableExercise>>(gson.toJson(dataMap["exercises"]), searchableType)
            if (exercises != null && exercises.isNotEmpty()) return exercises
        }
    } catch (e: Exception) {
    }
    
    return emptyList()
}

/**
 * Legacy JSON parsing for exercises with comprehensive error handling and fallbacks.
 * Maintains backward compatibility with the original Exercise model format.
 */
private fun parseExercisesFromJsonLegacy(workoutId: String, exercisesJson: String, gson: Gson): List<Exercise> {
    if (exercisesJson.isBlank()) return emptyList()
    
    // Attempt 1: Direct array parsing
    try {
        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
        val exercises = gson.fromJson<List<Exercise>>(exercisesJson, exercisesType)
        if (exercises != null && exercises.isNotEmpty()) return exercises
    } catch (e: Exception) {
        // Fall through to attempt 2
    }
    
    // Attempt 2: Object with "exercises" key
    try {
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val dataMap = gson.fromJson<Map<String, Any>>(exercisesJson, mapType)
        
        if (dataMap?.containsKey("exercises") == true) {
            val exercisesType = object : TypeToken<List<Exercise>>() {}.type
            val exercises = gson.fromJson<List<Exercise>>(gson.toJson(dataMap["exercises"]), exercisesType)
            if (exercises != null && exercises.isNotEmpty()) return exercises
        }
    } catch (e: Exception) {
        // Fall through to return empty
    }
    
    return emptyList()
}

