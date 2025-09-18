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
    val gson = GsonBuilder()
        .registerTypeAdapter(String::class.java, SafeDateAdapter())
        .registerTypeAdapter(java.time.Instant::class.java, SafeInstantAdapter())
        .registerTypeAdapter(Exercise::class.java, SafeExerciseDeserializer())
        .create()
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
    
    // Attempt 3: Use defensive parser for wrapped format
    try {
        val exercises = ExerciseJsonParser.parseWrappedExercises(exercisesJson, SearchableExercise::class.java, "exercises")
        if (exercises.isNotEmpty()) return exercises
    } catch (e: Exception) {
    }
    
    return emptyList()
}

/**
 * Legacy JSON parsing for exercises with comprehensive error handling and fallbacks.
 * Maintains backward compatibility with the original Exercise model format.
 */
private fun parseExercisesFromJsonLegacy(workoutId: String, exercisesJson: String, gson: Gson): List<Exercise> {
    if (exercisesJson.isBlank()) {
        Timber.d("PROGRESS-PARSE-DEBUG: Empty JSON for workout $workoutId")
        return emptyList()
    }
    
    // 🔥 ENHANCED DEBUG: Log the raw JSON before parsing
    Timber.d("PROGRESS-PARSE-RAW JSON for workout $workoutId (length=${exercisesJson.length}): ${exercisesJson.take(500)}")
    
    // 🔥 FIX: Use the same successful approach as feed parsing
    return try {
        // Parse JSON element first to detect format
        val element = com.google.gson.JsonParser.parseString(exercisesJson)
        
        if (element.isJsonObject) {
            val jsonObject = element.asJsonObject
            
            // Check if this is the wrapped format with "exercises" key (same as feed parsing)
            if (jsonObject.has("exercises")) {
                Timber.d("PROGRESS-PARSE-DEBUG: Found wrapped format with 'exercises' key - using successful feed approach")
                val exercisesElement = jsonObject.get("exercises")
                
                if (exercisesElement.isJsonArray) {
                    Timber.d("PROGRESS-PARSE-DEBUG: Parsing exercises array with ${exercisesElement.asJsonArray.size()} items")
                    
                    // 🔥 FIX: Use the SafeExerciseDeserializer for proper nested structure handling
                    val listType = object : TypeToken<List<Exercise>>() {}.type
                    val exercises = gson.fromJson<List<Exercise>>(exercisesElement, listType) ?: emptyList()

                    if (exercises.isNotEmpty()) {
                        // Validate exercises have valid data
                        val validExercises = exercises.filter { exercise ->
                            val hasValidSets = exercise.sets.isNotEmpty() && exercise.sets.any { set ->
                                set.weight != null && set.reps != null
                            }
                            if (!hasValidSets) {
                                Timber.w("PROGRESS-PARSE-FILTER: Exercise '${exercise.libraryExercise.name}' has no valid sets (${exercise.sets.size} sets total)")
                            }
                            hasValidSets
                        }

                        if (validExercises.isNotEmpty()) {
                            Timber.d("PROGRESS-PARSE-SUCCESS: ✅ Wrapped format parsing succeeded with ${validExercises.size} valid exercises (${exercises.size} total parsed)")
                            return validExercises
                        } else {
                            Timber.w("PROGRESS-PARSE-WARNING: All ${exercises.size} parsed exercises were filtered out due to invalid sets")
                        }
                    } else {
                        Timber.w("PROGRESS-PARSE-WARNING: Wrapped format returned empty exercises array")
                    }
                } else {
                    Timber.w("PROGRESS-PARSE-WARNING: 'exercises' key is not an array")
                }
            } else {
                Timber.w("PROGRESS-PARSE-WARNING: Object format without 'exercises' key")
            }
        } else if (element.isJsonArray) {
            // Direct array format
            Timber.d("PROGRESS-PARSE-DEBUG: Detected direct array format")
            val listType = object : TypeToken<List<Exercise>>() {}.type
            val exercises = gson.fromJson<List<Exercise>>(exercisesJson, listType) ?: emptyList()
            
            if (exercises.isNotEmpty()) {
                Timber.d("PROGRESS-PARSE-SUCCESS: ✅ Direct array parsing succeeded with ${exercises.size} exercises")
                return exercises
            }
        }
        
        Timber.w("PROGRESS-PARSE-WARNING: ⚠️ All parsing attempts failed for workout $workoutId - returning empty list (will cause 0 volume)")
        emptyList()
        
    } catch (e: Exception) {
        Timber.e(e, "PROGRESS-PARSE-ERROR: Exception during parsing for workout $workoutId")
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
        val name = jsonObject.get("name")?.asString ?: ""

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

                ExerciseLibrary(
                    id = libObj.get("id")?.asString ?: id,
                    name = libObj.get("name")?.asString ?: name,
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
                Timber.d("SafeExerciseDeserializer: No sets found for exercise $name")
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
        val weight = try {
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
            Timber.d("parseExerciseSet: Failed to parse weight for set $setNumber: ${e.message}")
            null
        }

        // Handle nested reps structure: {"reps": {"count": 20}}
        val reps = try {
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
            Timber.d("parseExerciseSet: Failed to parse reps for set $setNumber: ${e.message}")
            null
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
            Timber.d("parseExerciseSet: Failed to parse completedAt for set $setNumber: ${e.message}")
            null
        }

        // Parse completed status - if completedAt exists, set is completed
        val isCompleted = completedAt != null || setObject.get("completed")?.asBoolean == true

        Timber.d("parseExerciseSet: Set $setNumber parsed - weight=$weight, reps=$reps, completed=$isCompleted")

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

