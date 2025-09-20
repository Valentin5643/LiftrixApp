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
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonDeserializationContext
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.example.liftrix.core.json.ExerciseJsonParser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.liftrix.BuildConfig
import com.example.liftrix.core.security.JsonInputValidator
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
    // 🔥 SAFE GSON: Handle completedAt/createdAt as either String or {} object + nested structures
    // Note: SafeExerciseDeserializer now used manually in parseExercisesFromJsonLegacy to avoid type registration issues
    val gson = GsonBuilder()
        .registerTypeAdapter(String::class.java, SafeDateAdapter())
        .registerTypeAdapter(java.time.Instant::class.java, SafeInstantAdapter())
        .create()
    return groupBy { it.date.toKotlinLocalDate() }.mapValues { (_, workouts) ->
        val validWorkouts = workouts.filter { it.startTime != null && it.endTime != null }
        
        var totalVolume = 0f
        var exerciseCount = 0
        
        // Calculate volume and exercise counts
        // Enhanced to properly handle both library and custom exercises (custom exercises
        // are stored as simplified ExerciseLibrary objects after conversion)
        workouts.forEach { workout ->
            Timber.d("🔍 PROGRESS-DEBUG: Processing workout ${workout.id}, date=${workout.date}, exercisesJson.length=${workout.exercisesJson?.length ?: 0}")
            
            // Note: JSON parsing should ideally be moved to background thread in repository layer
            val exercises: List<Exercise> = parseExercisesFromJsonLegacy(workout.id, workout.exercisesJson, gson)
            Timber.d("🔍 PROGRESS-DEBUG: Parsed ${exercises.size} exercises from workout ${workout.id}")
            
            val workoutVolumeResult = calculateWorkoutVolumeEnhanced(workout.id, exercises)
            Timber.d("🔍 PROGRESS-DEBUG: Workout ${workout.id} volume calculation: ${workoutVolumeResult.totalVolume}kg from ${workoutVolumeResult.validExerciseCount} exercises (${workoutVolumeResult.validSets}/${workoutVolumeResult.totalSets} valid sets)")
            
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
    Timber.d("🔍 PROGRESS-DEBUG: getWorkoutsInDateRangeWithMetrics called with userId=$userId, startDate=$startDate, endDate=$endDate")
    
    val startDateString = startDate.toDateString()
    val endDateString = endDate.toDateString()
    Timber.d("🔍 PROGRESS-DEBUG: Date range converted to strings: $startDateString to $endDateString")
    
    val workouts = try {
        kotlinx.coroutines.withTimeout(5000) { // 5 second timeout for database call
            getWorkoutsInDateRangeForUser(
                userId = userId,
                startDate = startDateString,
                endDate = endDateString
            )
        }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        Timber.e("Database query timed out after 5s for user: $userId, dates: $startDateString to $endDateString")
        throw RuntimeException("Database query timed out after 5 seconds. Check database performance and indexes.")
    }
    
    Timber.d("🔍 PROGRESS-DEBUG: DAO returned ${workouts.size} workouts")
    workouts.forEachIndexed { index, workout ->
        Timber.d("🔍 PROGRESS-DEBUG: Workout $index: id=${workout.id}, date=${workout.date}, createdAt=${workout.createdAt}, status=${workout.status}, exercisesJson.length=${workout.exercisesJson?.length ?: 0}")
    }
    
    if (workouts.isEmpty()) {
        Timber.d("🔍 PROGRESS-DEBUG: No workouts found, returning empty map")
        return emptyMap()
    }
    
    val dailyMetrics = workouts.calculateDailyMetrics()
    
    Timber.d("🔍 PROGRESS-DEBUG: Daily metrics calculated: ${dailyMetrics.size} days")
    dailyMetrics.forEach { (date, metrics) ->
        Timber.d("🔍 PROGRESS-DEBUG: Date $date: volume=${metrics.totalVolume}kg, exercises=${metrics.exerciseCount}, workouts=${metrics.workoutCount}")
    }
    
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
 * Async wrapper for parseExercisesFromJsonLegacy that ensures JSON parsing happens on background thread
 */
suspend fun parseExercisesFromJsonLegacyAsync(
    workoutId: String,
    exercisesJson: String?,
    gson: Gson,
    jsonValidator: JsonInputValidator
): List<Exercise> = withContext(Dispatchers.IO) {
    if (exercisesJson.isNullOrBlank()) return@withContext emptyList()

    // 🔒 SECURITY: Validate JSON input before parsing
    when (val validation = jsonValidator.validateJson(exercisesJson)) {
        is JsonInputValidator.ValidationResult.Valid -> {
            parseExercisesFromJsonLegacy(workoutId, validation.json, gson)
        }
        is JsonInputValidator.ValidationResult.Invalid -> {
            Timber.e("WorkoutExtensions: JSON validation failed for workout $workoutId: ${validation.reason}")
            emptyList()
        }
    }
}

/**
 * Enhanced JSON parsing for exercises with comprehensive error handling and fallbacks.
 * Now supports both library exercises and custom exercises stored in SearchableExercise format.
 * WARNING: This method performs heavy JSON parsing and should be called from background thread
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
    
    // Attempt 3: Use defensive parser for wrapped format
    try {
        val exercises = ExerciseJsonParser.parseWrappedExercises(exercisesJson, SearchableExercise::class.java, "exercises")
        if (exercises.isNotEmpty()) return exercises
    } catch (e: Exception) {
    }
    
    return emptyList()
}

/**
 * Async wrapper for parseExercisesFromJson that ensures JSON parsing happens on background thread
 */
suspend fun parseExercisesFromJsonAsync(
    workoutId: String,
    exercisesJson: String?,
    gson: Gson,
    jsonValidator: JsonInputValidator
): List<SearchableExercise> = withContext(Dispatchers.IO) {
    if (exercisesJson.isNullOrBlank()) return@withContext emptyList()

    // 🔒 SECURITY: Validate JSON input before parsing
    when (val validation = jsonValidator.validateJson(exercisesJson)) {
        is JsonInputValidator.ValidationResult.Valid -> {
            parseExercisesFromJson(workoutId, validation.json, gson)
        }
        is JsonInputValidator.ValidationResult.Invalid -> {
            Timber.e("WorkoutExtensions: JSON validation failed for workout $workoutId: ${validation.reason}")
            emptyList()
        }
    }
}

/**
 * Legacy JSON parsing for exercises with comprehensive error handling and fallbacks.
 * Maintains backward compatibility with the original Exercise model format.
 * WARNING: This method performs heavy JSON parsing and should be called from background thread
 */
private fun parseExercisesFromJsonLegacy(workoutId: String, exercisesJson: String, gson: Gson): List<Exercise> {
    if (exercisesJson.isBlank()) {
        if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Empty JSON for workout $workoutId")
        return emptyList()
    }

    // 🔥 ENHANCED DEBUG: Log the raw JSON before parsing
    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-RAW JSON for workout $workoutId (length=${exercisesJson.length}): ${exercisesJson.take(500)}")
    
    // 🔥 FIX: Add schema detection to route Kotlinx workouts to correct parser
    return try {
        if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Starting JSON parsing for workout $workoutId")
        
        // Parse JSON element first to detect format
        val element = com.google.gson.JsonParser.parseString(exercisesJson)
        
        // 🔥 NEW: Check for Kotlinx serialization schema
        if (element.isJsonObject) {
            val jsonObject = element.asJsonObject
            if (jsonObject.has("schemaVersion")) {
                val schemaVersion = jsonObject.get("schemaVersion").asInt
                if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: ✅ Detected Kotlinx workout (schemaVersion=$schemaVersion) - routing to KotlinxWorkoutSerializationService")
                
                // Route to production KotlinxWorkoutSerializationService for proper parsing
                try {
                    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Creating KotlinxWorkoutSerializationService for production parsing")
                    
                    // Create the production service with proper dependencies
                    // Note: Using basic implementations for the dependencies since this is a parsing-only operation
                    val jsonValidator = com.example.liftrix.core.security.JsonInputValidator()
                    val performanceMonitor = com.example.liftrix.core.performance.SerializationPerformanceMonitor()
                    val cacheManager = com.example.liftrix.core.performance.SerializationCacheManager(performanceMonitor)
                    
                    val kotlinxService = com.example.liftrix.data.service.KotlinxWorkoutSerializationService(
                        jsonValidator = jsonValidator,
                        performanceMonitor = performanceMonitor,
                        cacheManager = cacheManager
                    )
                    
                    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Calling kotlinxService.deserializeExercises() with JSON")
                    
                    // Use the production deserializeExercises method
                    val exercises = kotlinxService.deserializeExercises(exercisesJson)
                    
                    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: ✅ Kotlinx production service parsing succeeded with ${exercises.size} exercises")
                    return exercises
                    
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Timber.e(e, "PROGRESS-PARSE-DEBUG: ❌ Kotlinx production service parsing failed, falling back to Gson")
                    // Fall through to Gson parsing
                }
            }
        }
        if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: JSON parsed as element type: ${when {
            element.isJsonObject -> "JsonObject"
            element.isJsonArray -> "JsonArray" 
            element.isJsonPrimitive -> "JsonPrimitive"
            element.isJsonNull -> "JsonNull"
            else -> "Unknown"
        }}")
        
        if (element.isJsonObject) {
            val jsonObject = element.asJsonObject
            if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: JsonObject has keys: ${jsonObject.keySet().joinToString(", ")}")
            
            // Check if this is the wrapped format with "exercises" key (same as feed parsing)
            if (jsonObject.has("exercises")) {
                if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Found wrapped format with 'exercises' key - using successful feed approach")
                val exercisesElement = jsonObject.get("exercises")
                if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Exercises element type: ${when {
                    exercisesElement.isJsonArray -> "JsonArray"
                    exercisesElement.isJsonObject -> "JsonObject"
                    exercisesElement.isJsonPrimitive -> "JsonPrimitive"
                    exercisesElement.isJsonNull -> "JsonNull"
                    else -> "Unknown"
                }}")
                
                if (exercisesElement.isJsonArray) {
                    val exercisesArray = exercisesElement.asJsonArray
                    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Parsing exercises array with ${exercisesArray.size()} items")
                    
                    // 🔥 FIX: Use direct element-by-element parsing to bypass Gson type registration issues
                    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: About to parse ${exercisesArray.size()} exercises manually")
                    
                    val exercises = try {
                        val exerciseDeserializer = SafeExerciseDeserializer()
                        exercisesArray.mapNotNull { exerciseElement ->
                            try {
                                // Create a JsonDeserializationContext using gson's context
                                val context = object : JsonDeserializationContext {
                                    override fun <T> deserialize(json: JsonElement?, typeOfT: Type?): T {
                                        return gson.fromJson(json, typeOfT)
                                    }
                                }
                                exerciseDeserializer.deserialize(exerciseElement, Exercise::class.java, context)
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-DEBUG: Failed to parse individual exercise: ${e.message}")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Timber.e(e, "PROGRESS-PARSE-DEBUG: Exception in manual exercise parsing")
                        emptyList()
                    }
                    
                    if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: gson.fromJson returned ${exercises.size} exercises")

                    if (exercises.isNotEmpty()) {
                        if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Starting validation of ${exercises.size} exercises")
                        
                        // Validate exercises have valid data
                        val validExercises = exercises.filter { exercise ->
                            val hasValidSets = exercise.sets.isNotEmpty() && exercise.sets.any { set ->
                                set.weight != null && set.reps != null
                            }
                            if (!hasValidSets) {
                                if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-FILTER: Exercise '${exercise.libraryExercise.name}' has no valid sets (${exercise.sets.size} sets total)")
                            } else {
                                if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-FILTER: Exercise '${exercise.libraryExercise.name}' is valid with ${exercise.sets.size} sets")
                            }
                            hasValidSets
                        }

                        if (validExercises.isNotEmpty()) {
                            if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-SUCCESS: ✅ Wrapped format parsing succeeded with ${validExercises.size} valid exercises (${exercises.size} total parsed)")
                            return validExercises
                        } else {
                            if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: All ${exercises.size} parsed exercises were filtered out due to invalid sets")
                        }
                    } else {
                        if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: Wrapped format returned empty exercises array")
                    }
                } else {
                    if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: 'exercises' key is not an array")
                }
            } else {
                if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: Object format without 'exercises' key")
            }
        } else if (element.isJsonArray) {
            // Direct array format
            val exercisesArray = element.asJsonArray
            if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-DEBUG: Detected direct array format with ${exercisesArray.size()} items")
            
            val exercises = try {
                val exerciseDeserializer = SafeExerciseDeserializer()
                exercisesArray.mapNotNull { exerciseElement ->
                    try {
                        val context = object : JsonDeserializationContext {
                            override fun <T> deserialize(json: JsonElement?, typeOfT: Type?): T {
                                return gson.fromJson(json, typeOfT)
                            }
                        }
                        exerciseDeserializer.deserialize(exerciseElement, Exercise::class.java, context)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-DEBUG: Failed to parse individual exercise in direct array: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Timber.e(e, "PROGRESS-PARSE-DEBUG: Exception in direct array parsing")
                emptyList()
            }
            
            if (exercises.isNotEmpty()) {
                if (BuildConfig.DEBUG) Timber.d("PROGRESS-PARSE-SUCCESS: ✅ Direct array parsing succeeded with ${exercises.size} exercises")
                return exercises
            } else {
                if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: Direct array parsing returned empty list")
            }
        } else {
            if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: JSON is neither object nor array - type: ${element.javaClass.simpleName}")
        }
        
        if (BuildConfig.DEBUG) Timber.w("PROGRESS-PARSE-WARNING: ⚠️ All parsing attempts failed for workout $workoutId - returning empty list (will cause 0 volume)")
        emptyList()
        
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Timber.e(e, "PROGRESS-PARSE-ERROR: Exception during parsing for workout $workoutId")
        emptyList()
    }
}

/**
 * Safe Gson adapter that handles date fields that can be either String or {} object
 */
private class SafeDateAdapter : JsonDeserializer<String?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): String? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> json.asString
            json.isJsonObject && json.asJsonObject.entrySet().isEmpty() -> null // treat {} as null
            json.isJsonObject -> null // treat any object as null for now
            else -> json.toString()
        }
    }
}

/**
 * Safe Gson adapter that handles Instant fields that can be either String or {} object
 */
private class SafeInstantAdapter : JsonDeserializer<java.time.Instant?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): java.time.Instant? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                try {
                    java.time.Instant.parse(json.asString)
                } catch (e: Exception) {
                    null
                }
            }
            json.isJsonObject && json.asJsonObject.entrySet().isEmpty() -> null // treat {} as null
            json.isJsonObject -> null // treat any object as null for now
            else -> null
        }
    }
}

/**
 * Safe Exercise deserializer that handles nested Weight/Reps structures from WorkoutMapper
 */
private class SafeExerciseDeserializer : JsonDeserializer<Exercise> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Exercise {
        val jsonObject = json.asJsonObject

        // Extract basic exercise info
        val id = jsonObject.get("id")?.asString ?: ""
        // Fix: Look for libraryExerciseName first, then name as fallback
        val name = jsonObject.get("libraryExerciseName")?.asString 
            ?: jsonObject.get("name")?.asString 
            ?: ""
        
        if (BuildConfig.DEBUG) {
            Timber.d("SafeExerciseDeserializer: Exercise ID: $id, extracted name: '$name' from fields: libraryExerciseName=${jsonObject.get("libraryExerciseName")?.asString}, name=${jsonObject.get("name")?.asString}")
        }

        // Handle libraryExercise - properly parse all fields instead of using defaults
        val libraryExercise = try {
            val libElement = jsonObject.get("libraryExercise")
            if (libElement?.isJsonObject == true) {
                val libObj = libElement.asJsonObject

                // Parse equipment from string to enum
                val equipmentStr = libObj.get("equipment")?.asString ?: "BARBELL"
                val equipment = try {
                    Equipment.valueOf(equipmentStr.uppercase())
                } catch (e: Exception) {
                    Timber.w("SafeExerciseDeserializer: Unknown equipment '$equipmentStr', using BARBELL")
                    Equipment.BARBELL
                }

                // Parse primary muscle group - extract from movementPattern or use shoulders for arnold press
                val primaryMuscleGroup = try {
                    val exerciseId = libObj.get("id")?.asString ?: ""
                    when {
                        exerciseId.contains("shoulders") -> ExerciseCategory.SHOULDERS
                        exerciseId.contains("chest") -> ExerciseCategory.CHEST
                        exerciseId.contains("back") -> ExerciseCategory.BACK
                        exerciseId.contains("legs") -> ExerciseCategory.LEGS
                        exerciseId.contains("arms") -> ExerciseCategory.ARMS
                        exerciseId.contains("bicep") -> ExerciseCategory.BICEPS
                        exerciseId.contains("tricep") -> ExerciseCategory.TRICEPS
                        exerciseId.contains("core") -> ExerciseCategory.CORE
                        else -> ExerciseCategory.OTHER
                    }
                } catch (e: Exception) {
                    ExerciseCategory.OTHER
                }

                // Parse secondary muscle groups if available
                val secondaryMuscleGroups = try {
                    val secondaryArray = libObj.getAsJsonArray("secondaryMuscleGroups")
                    secondaryArray?.map { element ->
                        try {
                            ExerciseCategory.valueOf(element.asString.uppercase())
                        } catch (e: Exception) {
                            null
                        }
                    }?.filterNotNull() ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                // Fix: Look for libraryExerciseName first, then name as fallback for library exercise name
                val libraryExerciseName = libObj.get("libraryExerciseName")?.asString 
                    ?: libObj.get("name")?.asString 
                    ?: name
                
                if (BuildConfig.DEBUG) {
                    Timber.d("SafeExerciseDeserializer: Library exercise name resolution - libraryExerciseName: ${libObj.get("libraryExerciseName")?.asString}, name: ${libObj.get("name")?.asString}, fallback: $name, final: $libraryExerciseName")
                }
                
                ExerciseLibrary(
                    id = libObj.get("id")?.asString ?: id,
                    name = libraryExerciseName,
                    primaryMuscleGroup = primaryMuscleGroup,
                    equipment = equipment,
                    secondaryMuscleGroups = secondaryMuscleGroups,
                    movementPattern = libObj.get("movementPattern")?.asString ?: "compound",
                    difficultyLevel = libObj.get("difficultyLevel")?.asInt ?: 3,
                    instructions = libObj.get("instructions")?.asString ?: "",
                    isCompound = libObj.get("isCompound")?.asBoolean ?: false,
                    searchableTerms = try {
                        val termsArray = libObj.getAsJsonArray("searchableTerms")
                        termsArray?.map { it.asString } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                )
            } else {
                // Fallback to simple structure
                ExerciseLibrary(
                    id = id,
                    name = name,
                    primaryMuscleGroup = ExerciseCategory.OTHER,
                    equipment = Equipment.BODYWEIGHT_ONLY,
                    secondaryMuscleGroups = emptyList(),
                    movementPattern = "unknown",
                    difficultyLevel = 3,
                    instructions = "",
                    isCompound = false,
                    searchableTerms = emptyList()
                )
            }
        } catch (e: Exception) {
            Timber.w("SafeExerciseDeserializer: Failed to parse libraryExercise, using fallback: ${e.message}")
            ExerciseLibrary(
                id = id,
                name = name,
                primaryMuscleGroup = ExerciseCategory.OTHER,
                equipment = Equipment.BODYWEIGHT_ONLY,
                secondaryMuscleGroups = emptyList(),
                movementPattern = "unknown",
                difficultyLevel = 3,
                instructions = "",
                isCompound = false,
                searchableTerms = emptyList()
            )
        }
        
        // Parse sets with proper handling of nested Weight/Reps structures
        val sets = try {
            val setsArray = jsonObject.getAsJsonArray("sets")
            if (setsArray != null && setsArray.size() > 0) {
                setsArray.mapIndexedNotNull { index, setElement ->
                    try {
                        parseExerciseSet(setElement.asJsonObject, index + 1)
                    } catch (e: Exception) {
                        Timber.w("SafeExerciseDeserializer: Failed to parse set ${index + 1} for exercise $name: ${e.message}")
                        null
                    }
                }
            } else {
                if (BuildConfig.DEBUG) Timber.d("SafeExerciseDeserializer: No sets found for exercise $name")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.w("SafeExerciseDeserializer: Failed to parse sets for exercise $name: ${e.message}")
            emptyList()
        }
        
        return Exercise(
            id = ExerciseId(id),
            workoutId = WorkoutId(""), // Will be set by parent
            libraryExercise = libraryExercise,
            orderIndex = 0,
            sets = sets,
            notes = jsonObject.get("notes")?.asString,
            createdAt = java.time.Instant.now() // Default to now since we can't parse from JSON reliably
        )
    }
    
    private fun parseExerciseSet(setObject: com.google.gson.JsonObject, setNumber: Int): ExerciseSet {
        // Handle nested weight structure: {"weight": {"kilograms": 50.0}}
        var weight = try {
            val weightElement = setObject.get("weight")
            when {
                weightElement?.isJsonObject == true -> {
                    val weightObj = weightElement.asJsonObject
                    val kg = weightObj.get("kilograms")?.asDouble
                    if (kg != null && kg > 0) Weight(kg) else null
                }
                weightElement?.isJsonPrimitive == true -> {
                    val kg = weightElement.asDouble
                    if (kg > 0) Weight(kg) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Failed to parse weight for set $setNumber: ${e.message}")
            null
        }

        // 🔥 FIX: Add fallbacks for various weight field names after existing weight parsing
        if (weight == null) {
            try {
                // Try multiple weight field variants for maximum compatibility
                val weightKg = setObject.get("weightKg")?.asDouble
                    ?: setObject.get("actualWeight")?.asDouble
                    ?: setObject.get("targetWeight")?.asDouble
                
                if (weightKg != null && weightKg > 0) {
                    weight = Weight(weightKg)
                    val fieldUsed = when {
                        setObject.has("weightKg") -> "weightKg"
                        setObject.has("actualWeight") -> "actualWeight"
                        setObject.has("targetWeight") -> "targetWeight"
                        else -> "unknown"
                    }
                    if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Set $setNumber found weight via $fieldUsed fallback: $weightKg kg")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Failed to parse weight fallbacks for set $setNumber: ${e.message}")
            }
        }

        // Handle nested reps structure: {"reps": {"count": 20}}
        var reps = try {
            val repsElement = setObject.get("reps")
            when {
                repsElement?.isJsonObject == true -> {
                    val repsObj = repsElement.asJsonObject
                    val count = repsObj.get("count")?.asInt
                    if (count != null && count > 0) Reps(count) else null
                }
                repsElement?.isJsonPrimitive == true -> {
                    val count = repsElement.asInt
                    if (count > 0) Reps(count) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Failed to parse reps for set $setNumber: ${e.message}")
            null
        }

        // 🔥 FIX: Add fallbacks for various reps field names after existing reps parsing
        if (reps == null) {
            try {
                // Try multiple reps field variants for maximum compatibility
                val repsCount = setObject.get("repsCount")?.asInt
                    ?: setObject.get("actualReps")?.asInt
                    ?: setObject.get("targetReps")?.asInt
                    ?: setObject.get("repsValue")?.asInt
                
                if (repsCount != null && repsCount > 0) {
                    reps = Reps(repsCount)
                    val fieldUsed = when {
                        setObject.has("repsCount") -> "repsCount"
                        setObject.has("actualReps") -> "actualReps"
                        setObject.has("targetReps") -> "targetReps"
                        setObject.has("repsValue") -> "repsValue"
                        else -> "unknown"
                    }
                    if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Set $setNumber found reps via $fieldUsed fallback: $repsCount")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Failed to parse reps fallbacks for set $setNumber: ${e.message}")
            }
        }

        // Handle completedAt
        val completedAt = try {
            val completedElement = setObject.get("completedAt")
            when {
                completedElement?.isJsonPrimitive == true && completedElement.asJsonPrimitive.isString -> {
                    java.time.Instant.parse(completedElement.asString)
                }
                completedElement?.isJsonObject == true && completedElement.asJsonObject.entrySet().isEmpty() -> {
                    // Handle empty {} objects as null
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.d("parseExerciseSet: Failed to parse completedAt for set $setNumber: ${e.message}")
            null
        }

        // Parse completed status - if completedAt exists, set is completed
        val isCompleted = completedAt != null || setObject.get("completed")?.asBoolean == true

        // 🔥 FIX: Enhanced debug logging to show which field names are being used
        if (BuildConfig.DEBUG) {
            val availableFields = setObject.keySet().joinToString(", ")
            val weightSource = when {
                weight != null && setObject.has("weight") -> "weight (nested/primitive)"
                weight != null && setObject.has("weightKg") -> "weightKg (fallback)"
                weight != null && setObject.has("actualWeight") -> "actualWeight (fallback)"
                weight != null && setObject.has("targetWeight") -> "targetWeight (fallback)"
                else -> "none"
            }
            val repsSource = when {
                reps != null && setObject.has("reps") -> "reps (nested/primitive)"
                reps != null && setObject.has("repsCount") -> "repsCount (fallback)"
                reps != null && setObject.has("actualReps") -> "actualReps (fallback)"
                reps != null && setObject.has("targetReps") -> "targetReps (fallback)"
                reps != null && setObject.has("repsValue") -> "repsValue (fallback)"
                else -> "none"
            }
            Timber.d("parseExerciseSet: Set $setNumber parsed - weight=$weight (from $weightSource), reps=$reps (from $repsSource), completed=$isCompleted")
            Timber.d("parseExerciseSet: Set $setNumber available fields: $availableFields")
        }

        return ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = setNumber,
            reps = reps,
            weight = weight,
            time = null,
            distance = null,
            rpe = null,
            completedAt = if (isCompleted) completedAt ?: java.time.Instant.now() else null,
            notes = setObject.get("notes")?.asString?.takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Calculate total volume from workout JSON using the same logic as FeedRepositoryImpl
 * This provides live calculation to avoid stale metadata issues
 */
fun WorkoutEntity.calculateLiveVolume(): Double {
    return try {
        // Use same parser as FeedRepositoryImpl
        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(String::class.java, SafeDateAdapter())
            .registerTypeAdapter(java.time.Instant::class.java, SafeInstantAdapter())
            .create()

        val exercises = parseExercisesFromWorkoutJson(exercisesJson, gson)

        // Sum volume from all completed sets
        exercises.sumOf { exercise ->
            exercise.effectiveSets.sumOf { set ->
                val weight = set.effectiveWeight
                val reps = set.effectiveReps

                if (set.isEffectivelyCompleted && weight != null && reps != null) {
                    weight * reps
                } else {
                    0.0
                }
            }
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Timber.w(e, "Failed to calculate live volume for workout $id")
        0.0
    }
}


/**
 * Helper data classes matching FeedRepositoryImpl structure
 */
private data class WorkoutExerciseJson(
    val name: String? = null,
    val libraryExerciseName: String? = null,
    val sets: List<WorkoutSetJson>? = emptyList(),
    val libraryExercise: LibraryExerciseJson? = null
) {
    val effectiveName: String get() = libraryExerciseName ?: name ?: libraryExercise?.name ?: libraryExercise?.libraryExerciseName ?: "Unknown Exercise"
    val effectiveSets: List<WorkoutSetJson> get() = sets ?: emptyList()
}

private data class LibraryExerciseJson(
    val name: String? = null,
    val libraryExerciseName: String? = null,
    val sets: List<WorkoutSetJson>? = null
)

private data class WorkoutSetJson(
    val actualWeight: Double? = null,
    val targetWeight: Double? = null,
    val weight: WeightJson? = null,
    val weightKg: Double? = null,
    val weightLbs: Double? = null,

    val actualReps: Int? = null,
    val targetReps: Int? = null,
    val reps: RepsJson? = null,
    val repsValue: Int? = null,
    val repsCount: Int? = null,

    val completed: Boolean = false,
    val completedAt: String? = null,
    val completedAtEpochMilli: Long? = null
) {
    val effectiveWeight: Double? get() =
        actualWeight ?: targetWeight ?:
        weight?.kilograms ?: weightKg ?:
        weightLbs?.let { it / 2.20462 }

    val effectiveReps: Int? get() =
        actualReps ?: targetReps ?:
        reps?.count ?: repsValue ?: repsCount

    val isEffectivelyCompleted: Boolean get() =
        completed || completedAt != null || completedAtEpochMilli != null
}

private data class WeightJson(
    val kilograms: Double? = null,
    val pounds: Double? = null
)

private data class RepsJson(
    val count: Int? = null
)

/**
 * Parse exercises from workout JSON with same logic as FeedRepositoryImpl
 */
private fun parseExercisesFromWorkoutJson(exercisesJson: String, gson: com.google.gson.Gson): List<WorkoutExerciseJson> {
    return try {
        // Handle wrapper format: {"exercises": [...], "totalVolume": 123.45}
        val jsonElement = gson.fromJson(exercisesJson, com.google.gson.JsonElement::class.java)

        val exercisesList = if (jsonElement.isJsonObject && jsonElement.asJsonObject.has("exercises")) {
            // Wrapper format
            jsonElement.asJsonObject.getAsJsonArray("exercises")
        } else if (jsonElement.isJsonArray) {
            // Direct array format
            jsonElement.asJsonArray
        } else {
            return emptyList()
        }

        exercisesList.map { exerciseElement ->
            gson.fromJson(exerciseElement, WorkoutExerciseJson::class.java)
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Timber.w(e, "Failed to parse workout exercises JSON")
        emptyList()
    }
}

