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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toJavaLocalDate
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
        workouts.forEach { workout ->
            timber.log.Timber.d("🔍 Processing workout: ${workout.id}, name: ${workout.name}, userId: ${workout.userId}")
            
            // Parse exercises from JSON
            timber.log.Timber.d("🔍 Raw exercisesJson for workout ${workout.id}: ${workout.exercisesJson.take(200)}...")
            
            val exercises: List<Exercise> = try {
                // First, try to determine the JSON structure
                if (workout.exercisesJson.trim().startsWith("{")) {
                    // Object format: { "exercises": [...] } or { "someKey": [...] }
                    timber.log.Timber.d("🔍 Detected object JSON format for workout ${workout.id}")
                    val enhancedType = object : TypeToken<Map<String, Any>>() {}.type
                    val enhancedData: Map<String, Any>? = gson.fromJson(workout.exercisesJson, enhancedType)
                    
                    if (enhancedData?.containsKey("exercises") == true) {
                        // New format with exercises key
                        timber.log.Timber.d("🔍 Using object JSON format with 'exercises' key for workout ${workout.id}")
                        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                        gson.fromJson(gson.toJson(enhancedData["exercises"]), exercisesType) ?: emptyList()
                    } else {
                        // Object format but no exercises key - might be wrapped differently
                        timber.log.Timber.d("🔍 Object format detected but no 'exercises' key found. Keys: ${enhancedData?.keys}")
                        // Try to find any array in the object
                        val firstArrayValue = enhancedData?.values?.firstOrNull { it is List<*> }
                        if (firstArrayValue != null) {
                            val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                            gson.fromJson(gson.toJson(firstArrayValue), exercisesType) ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }
                } else {
                    // Array format: [...]
                    timber.log.Timber.d("🔍 Detected array JSON format for workout ${workout.id}")
                    val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                    gson.fromJson(workout.exercisesJson, exercisesType) ?: emptyList()
                }
            } catch (e: Exception) {
                // Fallback parsing attempts
                timber.log.Timber.w(e, "⚠️ Initial JSON parsing failed for workout ${workout.id}, trying fallback approaches")
                
                // Try direct array parsing
                try {
                    val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                    gson.fromJson(workout.exercisesJson, exercisesType) ?: emptyList()
                } catch (e2: Exception) {
                    timber.log.Timber.e(e2, "❌ All JSON parsing attempts failed for workout ${workout.id}")
                    timber.log.Timber.e("❌ JSON content (first 500 chars): ${workout.exercisesJson.take(500)}")
                    emptyList()
                }
            }
            
            timber.log.Timber.d("📋 Found ${exercises.size} exercises from JSON for workout: ${workout.id}")
            exerciseCount += exercises.size
            
            exercises.forEachIndexed { exerciseIndex, exercise ->
                timber.log.Timber.d("💪 Processing exercise $exerciseIndex: ${exercise.id}, libraryId: ${exercise.libraryExercise.id}")
                timber.log.Timber.d("🏋️ Found ${exercise.sets.size} sets for exercise: ${exercise.id}")
                
                var exerciseVolume = 0f
                exercise.sets.forEachIndexed { setIndex, set ->
                    val setVolume = if (set.weight != null && set.reps != null) {
                        val volume = (set.weight.kilograms * set.reps.count).toFloat()
                        timber.log.Timber.d("📊 Set $setIndex: weight=${set.weight.kilograms}kg, reps=${set.reps.count}, volume=${volume}kg")
                        volume
                    } else {
                        timber.log.Timber.w("⚠️ Set $setIndex has null data: weight=${set.weight?.kilograms}, reps=${set.reps?.count}")
                        0f
                    }
                    exerciseVolume += setVolume
                }
                
                timber.log.Timber.d("💪 Exercise ${exercise.id} total volume: ${exerciseVolume}kg")
                totalVolume += exerciseVolume
            }
            
            timber.log.Timber.d("🏋️ Workout ${workout.id} total volume so far: ${totalVolume}kg")
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
        
        timber.log.Timber.d("📈 Final metrics: totalVolume=${metrics.totalVolume}kg, exerciseCount=${metrics.exerciseCount}, workoutCount=${metrics.workoutCount}")
        
        metrics
    }
}

/**
 * Convert WorkoutDayMetrics to VolumeDataPoint list
 */
fun Map<LocalDate, WorkoutDayMetrics>.toVolumeDataPoints(): List<VolumeDataPoint> {
    return map { (date, metrics) ->
        VolumeDataPoint(
            date = date,
            totalVolume = metrics.totalVolume,
            exerciseCount = metrics.exerciseCount
        )
    }.sortedBy { it.date }
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
    timber.log.Timber.d("🔍 WorkoutExtensions: Querying workouts for user: $userId, range: ${startDate.toDateString()} to ${endDate.toDateString()}")
    
    val workouts = getWorkoutsInDateRangeForUser(
        userId = userId,
        startDate = startDate.toDateString(),
        endDate = endDate.toDateString()
    )
    
    timber.log.Timber.d("📊 WorkoutExtensions: Found ${workouts.size} workouts for user: $userId")
    
    if (workouts.isEmpty()) {
        timber.log.Timber.w("⚠️ WorkoutExtensions: No workouts found for user: $userId in range: ${startDate.toDateString()} to ${endDate.toDateString()}")
        return emptyMap()
    }
    
    val metrics = workouts.calculateDailyMetrics()
    timber.log.Timber.d("✅ WorkoutExtensions: Calculated metrics for ${metrics.size} days")
    
    return metrics
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