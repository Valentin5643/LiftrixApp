package com.example.liftrix.data.service

import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.RPE
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Equipment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.liftrix.core.data.BuildConfig
import com.example.liftrix.core.security.JsonInputValidator
import com.example.liftrix.core.performance.SerializationPerformanceMonitor
import com.example.liftrix.core.performance.SerializationCacheManager
import com.example.liftrix.core.performance.SerializationOperation
import java.time.Instant

/**
 * Complete kotlinx.serialization-based workout serialization service.
 *
 * This service provides modern JSON serialization with:
 * - Type-safe serialization with compile-time validation
 * - Better performance than Gson for complex objects
 * - Native Kotlin multiplatform support
 * - Reduced reflection usage
 * - Better error messages and debugging
 * - Comprehensive caching and performance monitoring
 * - Full backward compatibility with existing API
 *
 * This service replaces WorkoutJsonSerializationService completely.
 */
@Singleton
class KotlinxWorkoutSerializationService @Inject constructor(
    private val jsonValidator: JsonInputValidator,
    private val performanceMonitor: SerializationPerformanceMonitor,
    private val cacheManager: SerializationCacheManager
) {

    companion object {
        const val SCHEMA_VERSION = 1  // Single version, fresh start
    }

    // Configure kotlinx.serialization Json instance
    private val json = Json {
        ignoreUnknownKeys = true // Handle backward compatibility
        isLenient = true // Handle malformed JSON gracefully
        encodeDefaults = true // Include default values for completeness
        prettyPrint = BuildConfig.DEBUG // Pretty print in debug builds
    }

    /**
     * Serializes exercise data using kotlinx.serialization with caching and monitoring.
     */
    suspend fun serializeExercises(exercises: List<Exercise>): String {
        return try {
            val startTime = System.currentTimeMillis()
            val exerciseIds = exercises.map { it.id.value }
            val lastModified = exercises.maxOfOrNull { it.createdAt.toEpochMilli() } ?: System.currentTimeMillis()

            // 💾 CACHE: Check for cached result first
            val cachedResult = cacheManager.getCachedSerialization(exerciseIds, lastModified)
            if (cachedResult != null) {
                val endTime = System.currentTimeMillis()
                performanceMonitor.recordSerializationMetric(
                    operation = SerializationOperation.SERIALIZE,
                    durationMs = endTime - startTime,
                    dataSize = cachedResult.length,
                    exerciseCount = exercises.size,
                    format = "kotlinx_serialization_cached",
                    success = true
                )
                return cachedResult
            }

            // Convert to serializable DTOs
            val exerciseDtos = exercises.map { exercise ->
                SerializableExercise(
                    id = exercise.id.value,
                    workoutId = exercise.workoutId.value,
                    libraryExerciseName = exercise.libraryExercise.name,
                    orderIndex = exercise.orderIndex,
                    targetSets = exercise.targetSets,
                    targetReps = exercise.targetReps,
                    targetWeightKg = exercise.targetWeight?.kilograms,
                    sets = exercise.sets.map { set ->
                        SerializableExerciseSet(
                            id = set.id.value,
                            setNumber = set.setNumber,
                            repsCount = set.reps?.count,
                            weightKg = set.weight?.kilograms,
                            rpeValue = set.rpe?.value,
                            completedAtEpochMilli = set.completedAt?.toEpochMilli(),
                            notes = set.notes
                        )
                    },
                    notes = exercise.notes,
                    createdAtEpochMilli = exercise.createdAt.toEpochMilli()
                )
            }

            // Create workout data structure
            val workoutData = KotlinxWorkoutData(
                schemaVersion = SCHEMA_VERSION,
                exercises = exerciseDtos,
                metadata = createMetadata(exercises),
                serializationFormat = "kotlinx.serialization"
            )

            val result = json.encodeToString(workoutData)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // 📊 MONITORING: Record performance metrics
            performanceMonitor.recordSerializationMetric(
                operation = SerializationOperation.SERIALIZE,
                durationMs = duration,
                dataSize = result.length,
                exerciseCount = exercises.size,
                format = "kotlinx_serialization",
                success = true
            )

            // 💾 CACHE: Store result for future use
            cacheManager.cacheSerialization(exerciseIds, lastModified, result)

            if (BuildConfig.DEBUG) {
                Timber.d("🚀 KOTLINX-SERIALIZATION: Serialized ${exercises.size} exercises in ${duration}ms, JSON size: ${result.length} chars")
            }

            result
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - System.currentTimeMillis()

            // 📊 MONITORING: Record failure metrics
            performanceMonitor.recordSerializationMetric(
                operation = SerializationOperation.SERIALIZE,
                durationMs = duration,
                dataSize = 0,
                exerciseCount = exercises.size,
                format = "kotlinx_serialization",
                success = false
            )

            Timber.e(e, "❌ KOTLINX-SERIALIZATION-ERROR: Failed to serialize ${exercises.size} exercises")
            throw RuntimeException("Failed to serialize workout data: ${e.message}", e)
        }
    }

    /**
     * Deserializes exercise data with runtime exceptions for unsupported formats.
     */
    fun deserializeExercises(exercisesJson: String): List<Exercise> {
        return try {
            // 🔒 SECURITY: Validate JSON input before parsing
            val validatedJson = when (val validation = jsonValidator.validateJson(exercisesJson)) {
                is JsonInputValidator.ValidationResult.Valid -> validation.json
                is JsonInputValidator.ValidationResult.Invalid -> {
                    throw IllegalArgumentException("Invalid JSON format: ${validation.reason}")
                }
            }

            val startTime = System.currentTimeMillis()

            if (BuildConfig.DEBUG) {
                Timber.d("🔍 JSON-STRUCTURE: About to parse JSON: ${validatedJson.take(500)}...")
            }

            val workoutData = json.decodeFromString<KotlinxWorkoutData>(validatedJson)

            if (BuildConfig.DEBUG) {
                Timber.d("🔍 KOTLINX-DEBUG: Parsed workout data - schema: ${workoutData.schemaVersion}, exercises: ${workoutData.exercises.size}")
                workoutData.exercises.forEachIndexed { index, dto ->
                    Timber.d("🔍 KOTLINX-DEBUG: Exercise $index: name='${dto.libraryExerciseName}', targetWeightKg=${dto.targetWeightKg}, sets=${dto.sets.size}")
                    dto.sets.forEachIndexed { setIndex, setDto ->
                        Timber.d("🔍 KOTLINX-DEBUG: Set $setIndex: repsCount=${setDto.repsCount}, weightKg=${setDto.weightKg}")
                    }
                }
            }

            if (workoutData.schemaVersion != SCHEMA_VERSION) {
                throw UnsupportedOperationException("Unsupported workout format (version ${workoutData.schemaVersion}). Please export and re-import your workouts.")
            }

            val exercises = convertDtosToExercises(workoutData.exercises)

            val endTime = System.currentTimeMillis()
            if (BuildConfig.DEBUG) {
                Timber.d("🚀 KOTLINX-DESERIALIZE-PERF: Deserialized ${exercises.size} exercises in ${endTime - startTime}ms")
            }

            exercises
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is UnsupportedOperationException -> throw e
                else -> throw RuntimeException("Failed to load workout data: ${e.message}", e)
            }
        }
    }

    /**
     * Synchronous version of serializeExercises for backward compatibility.
     */
    fun serializeExercisesSync(exercises: List<Exercise>): String {
        return try {
            val startTime = System.currentTimeMillis()

            // Convert to serializable DTOs
            val exerciseDtos = exercises.map { exercise ->
                SerializableExercise(
                    id = exercise.id.value,
                    workoutId = exercise.workoutId.value,
                    libraryExerciseName = exercise.libraryExercise.name,
                    orderIndex = exercise.orderIndex,
                    targetSets = exercise.targetSets,
                    targetReps = exercise.targetReps,
                    targetWeightKg = exercise.targetWeight?.kilograms,
                    sets = exercise.sets.map { set ->
                        SerializableExerciseSet(
                            id = set.id.value,
                            setNumber = set.setNumber,
                            repsCount = set.reps?.count,
                            weightKg = set.weight?.kilograms,
                            rpeValue = set.rpe?.value,
                            completedAtEpochMilli = set.completedAt?.toEpochMilli(),
                            notes = set.notes
                        )
                    },
                    notes = exercise.notes,
                    createdAtEpochMilli = exercise.createdAt.toEpochMilli()
                )
            }

            val workoutData = KotlinxWorkoutData(
                schemaVersion = SCHEMA_VERSION,
                exercises = exerciseDtos,
                metadata = createMetadata(exercises),
                serializationFormat = "kotlinx.serialization"
            )

            val result = json.encodeToString(workoutData)
            val endTime = System.currentTimeMillis()

            if (BuildConfig.DEBUG) {
                Timber.d("🚀 KOTLINX-SERIALIZATION-SYNC: Serialized ${exercises.size} exercises in ${endTime - startTime}ms, JSON size: ${result.length} chars")
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "❌ KOTLINX-SERIALIZATION-ERROR-SYNC: Failed to serialize ${exercises.size} exercises")
            throw RuntimeException("Failed to serialize workout data: ${e.message}", e)
        }
    }

    /**
     * Async wrapper for serializeExercises.
     */
    suspend fun serializeExercisesAsync(exercises: List<Exercise>): String = withContext(Dispatchers.IO) {
        serializeExercises(exercises)
    }

    /**
     * Async wrapper for deserializeExercises.
     */
    suspend fun deserializeExercisesAsync(exercisesJson: String): List<Exercise> = withContext(Dispatchers.IO) {
        deserializeExercises(exercisesJson)
    }

    /**
     * Validates JSON schema integrity and performance characteristics.
     */
    fun validateExerciseJson(exercisesJson: String): KotlinxValidationResult {
        return try {
            val startTime = System.currentTimeMillis()
            val exercises = deserializeExercises(exercisesJson)
            val endTime = System.currentTimeMillis()

            KotlinxValidationResult.Valid(
                schemaVersion = SCHEMA_VERSION,
                exerciseCount = exercises.size,
                deserializationTimeMs = endTime - startTime,
                jsonSizeBytes = exercisesJson.length
            )
        } catch (e: Exception) {
            KotlinxValidationResult.Invalid("Validation failed: ${e.message}")
        }
    }


    // Private helper methods


    private fun convertDtosToExercises(exerciseDtos: List<SerializableExercise>): List<Exercise> {
        return exerciseDtos.mapNotNull { dto ->
            try {
                // Create ExerciseLibrary from serialized name
                val libraryExercise = ExerciseLibrary(
                    id = "lib_${dto.libraryExerciseName.hashCode()}",
                    name = dto.libraryExerciseName,
                    primaryMuscleGroup = ExerciseCategory.OTHER, // Default for now
                    equipment = Equipment.BODYWEIGHT_ONLY, // Default for now
                    secondaryMuscleGroups = emptyList(),
                    movementPattern = "unknown",
                    difficultyLevel = 5,
                    instructions = null,
                    isCompound = false,
                    searchableTerms = emptyList()
                )

                // Convert serializable sets to domain sets, filtering out empty sets
                val sets = dto.sets.mapNotNull { setDto ->
                    if (BuildConfig.DEBUG) {
                        Timber.d("🔍 SET-CONVERSION: Converting setDto - repsCount=${setDto.repsCount}, weightKg=${setDto.weightKg}")
                    }

                    val reps = setDto.repsCount?.let { Reps(it) }
                    val weight = setDto.weightKg?.let { Weight.fromKilograms(it) }
                    val completedAt = setDto.completedAtEpochMilli?.let { Instant.ofEpochMilli(it) }

                    // Skip sets that have no metrics (empty/placeholder sets)
                    if (reps == null && weight == null) {
                        if (BuildConfig.DEBUG) {
                            Timber.d("🔍 SKIP-SET: Skipping empty set ${setDto.setNumber} - no metrics")
                        }
                        return@mapNotNull null
                    }

                    // Safety net: if we have reps but no weight, this might be a bodyweight exercise
                    // or a data entry issue. For now, we'll allow it through since reps alone is a valid metric.
                    if (BuildConfig.DEBUG && weight == null && reps != null) {
                        Timber.w("🔍 NULL-WEIGHT: Set ${setDto.setNumber} has reps but weightKg=null in JSON - likely bodyweight or data issue")
                    }

                    if (BuildConfig.DEBUG) {
                        Timber.d("🔍 DOMAIN-VALUES: Creating ExerciseSet - reps=$reps, weight=$weight, completedAt=$completedAt")
                    }

                    try {
                        ExerciseSet(
                            id = ExerciseSetId(setDto.id),
                            setNumber = setDto.setNumber,
                            reps = reps,
                            weight = weight,
                            rpe = setDto.rpeValue?.let { RPE(it) },
                            completedAt = completedAt,
                            notes = setDto.notes
                        )
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Timber.e("🔍 SET-ERROR: Failed to create ExerciseSet - reps=$reps, weight=$weight, error=${e.message}")
                        }
                        null
                    }
                }

                val domainExercise = Exercise(
                    id = ExerciseId(dto.id),
                    workoutId = WorkoutId(dto.workoutId),
                    libraryExercise = libraryExercise,
                    orderIndex = dto.orderIndex,
                    targetSets = dto.targetSets,
                    targetReps = dto.targetReps,
                    targetWeight = dto.targetWeightKg?.let { Weight.fromKilograms(it) },
                    sets = sets,
                    notes = dto.notes,
                    createdAt = Instant.ofEpochMilli(dto.createdAtEpochMilli)
                )

                if (BuildConfig.DEBUG) {
                    Timber.d("🔍 DOMAIN-CONVERSION: Exercise '${dto.libraryExerciseName}' -> targetWeight=${domainExercise.targetWeight?.kilograms}, ${sets.size} sets")
                    sets.forEach { set ->
                        Timber.d("🔍 DOMAIN-CONVERSION: Set ${set.setNumber}: reps=${set.reps?.count}, weight=${set.weight?.kilograms}kg")
                    }
                }

                domainExercise
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert exercise DTO: ${dto.libraryExerciseName}")
                null
            }
        }
    }


    private fun createMetadata(exercises: List<Exercise>): KotlinxWorkoutMetadata {
        val totalVolume = exercises.mapNotNull { it.getTotalVolume() }
            .fold(Weight.ZERO) { acc, weight -> acc + weight }

        return KotlinxWorkoutMetadata(
            totalVolumeKg = totalVolume.kilograms,
            totalSets = exercises.sumOf { it.sets.size },
            exerciseCount = exercises.size,
            createdAt = System.currentTimeMillis(),
            format = "kotlinx_serialization_v4"
        )
    }

}

/**
 * Kotlinx.serialization data structures for workout serialization.
 */
@Serializable
data class KotlinxWorkoutData(
    val schemaVersion: Int,
    val exercises: List<SerializableExercise>,
    val metadata: KotlinxWorkoutMetadata,
    val serializationFormat: String
)

@Serializable
data class KotlinxWorkoutMetadata(
    val totalVolumeKg: Double,
    val totalSets: Int,
    val exerciseCount: Int,
    val createdAt: Long,
    val format: String
)

@Serializable
data class SerializableExercise(
    val id: String,
    val workoutId: String,
    val libraryExerciseName: String,
    val orderIndex: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    @kotlinx.serialization.SerialName("targetWeightKg") val targetWeightKg: Double? = null,
    val sets: List<SerializableExerciseSet> = emptyList(),
    val notes: String? = null,
    val createdAtEpochMilli: Long
)

@Serializable
data class SerializableExerciseSet(
    val id: String,
    val setNumber: Int,
    val repsCount: Int? = null,
    val weightKg: Double? = null,
    val rpeValue: Int? = null,
    val completedAtEpochMilli: Long? = null,
    val notes: String? = null
)

/**
 * JSON validation result with performance metrics.
 */
sealed class KotlinxValidationResult {
    data class Valid(
        val schemaVersion: Int,
        val exerciseCount: Int,
        val deserializationTimeMs: Long,
        val jsonSizeBytes: Int
    ) : KotlinxValidationResult()

    data class Invalid(val reason: String) : KotlinxValidationResult()
}
