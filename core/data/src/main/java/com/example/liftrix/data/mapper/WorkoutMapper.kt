package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.remote.dto.WorkoutDto
import com.example.liftrix.data.remote.dto.ExerciseDto
import com.example.liftrix.data.service.ExerciseConversionService
import com.example.liftrix.data.service.KotlinxWorkoutSerializationService
import com.example.liftrix.domain.model.*
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.liftrix.core.data.BuildConfig
import com.example.liftrix.core.security.JsonInputValidator
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.service.CanonicalWorkoutJsonAdapter

@Singleton
class WorkoutMapper @Inject constructor(
    private val exerciseMapper: ExerciseMapper,
    private val exerciseConversionService: ExerciseConversionService,
    private val kotlinxSerializer: KotlinxWorkoutSerializationService,
    private val canonicalJsonAdapter: CanonicalWorkoutJsonAdapter,
    private val gson: Gson,
    private val jsonValidator: JsonInputValidator
) {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Data class to represent the enhanced workout JSON structure
     */
    data class WorkoutJsonWrapper(
        val schema_version: Int?,
        val exercises: List<Exercise>?,
        val totalVolume: Double?,
        val exercisesWithVolume: List<Any>?,
        val created_at: Long?,
        val format: String?
    )

    /**
     * Convert Room entity to domain model
     * Note: This method should be called from a background thread context
     */
    fun toDomain(entity: WorkoutEntity): Workout = entity.run {
        // 🚨 SCHEMA-CONFLICT: Log the incoming JSON to detect schema conflicts between WorkoutMapper vs WorkoutJsonSerializationService
        if (BuildConfig.DEBUG) {
            Timber.d("[CONFLICT] WorkoutMapper.toDomain: Processing workout '${entity.name}' (ID: ${entity.id})")
            Timber.d("[CONFLICT] JSON length: ${exercisesJson?.length ?: 0}")
        }
        if (!exercisesJson.isNullOrBlank()) {
            if (BuildConfig.DEBUG) {
                val jsonPreview = if (exercisesJson.length > 300) exercisesJson.substring(0, 300) + "..." else exercisesJson
                Timber.d("[CONFLICT] JSON preview: $jsonPreview")
            }

            // Detect schema version from JSON structure - UNIFIED V1 as current format
            val schemaVersion = when {
                exercisesJson.contains("\"schemaVersion\":1") -> 1  // Current kotlinx.serialization format
                exercisesJson.contains("\"schemaVersion\":3") -> 3  // Legacy v3 format (deprecated)
                exercisesJson.contains("\"schema_version\":2") -> 2  // Legacy v2 format (deprecated)
                exercisesJson.contains("\"exercises\":") -> 1       // Default to current format
                else -> 0
            }

            // 🚀 MIGRATION: Track format usage for migration metrics
            val isCurrentFormat = schemaVersion == 1
            val isLegacyV3Format = schemaVersion == 3
            val isLegacyV2Format = schemaVersion == 2

            if (BuildConfig.DEBUG) {
                Timber.d("[CONFLICT] Detected version: $schemaVersion")
                Timber.d("[CONFLICT] Current format (v1): $isCurrentFormat, Legacy V3 format: $isLegacyV3Format, Legacy V2 format: $isLegacyV2Format")
                Timber.d("[CONFLICT] WorkoutMapper now using kotlinx.serialization for all formats")
            }

            // All formats now handled by kotlinx.serialization
        }

        // 🚀 PRODUCTION-READY: Handle multiple JSON schema versions with version bridge pattern

        // 🔒 SECURITY: Validate JSON input before parsing
        val validatedJson = when (val validation = jsonValidator.validateJson(exercisesJson)) {
            is JsonInputValidator.ValidationResult.Valid -> validation.json
            is JsonInputValidator.ValidationResult.Invalid -> {
                Timber.e("WorkoutMapper: JSON validation failed: ${validation.reason}")
                return@run Workout(
                    userId = userId,
                    id = WorkoutId(id),
                    name = name,
                    date = date,
                    exercises = emptyList(),
                    status = status,
                    startTime = startTime,
                    endTime = endTime,
                    notes = notes,
                    templateId = templateId?.let(::WorkoutId),
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            }
        }

        val exercises: List<Exercise> = try {
            if (canonicalJsonAdapter.isCanonicalJson(validatedJson)) {
                if (BuildConfig.DEBUG) Timber.d("[CANONICAL] Parsing canonical workout JSON (${validatedJson.length} chars)")
                canonicalJsonAdapter.deserializeToDomain(validatedJson, WorkoutId(id))
            } else {
                // Use kotlinx.serialization for all workout deserialization
                if (BuildConfig.DEBUG) Timber.d("[KOTLINX] Using kotlinx.serialization for workout deserialization (${validatedJson.length} chars)")
                parseWithKotlinxSerialization(validatedJson)
            }
        } catch (e: Exception) {
            if (canonicalJsonAdapter.isCanonicalJson(validatedJson)) {
                try {
                    canonicalJsonAdapter.deserializeToDomain(validatedJson, WorkoutId(id))
                } catch (canonicalError: Exception) {
                    Timber.e(canonicalError, "[CANONICAL] Failed to parse canonical workout JSON for ${entity.id}")
                    emptyList()
                }
            } else {
                // Final fallback to empty list with detailed error logging
                Timber.e(e, "[SCHEMA-DEBUG-4] 🔥 SCHEMA-ERROR: All exercise JSON parsing failed for workout ${entity.id}")
                if (BuildConfig.DEBUG) {
                    Timber.e("[SCHEMA-DEBUG-4a] 🔥 SCHEMA-ERROR: JSON content: ${exercisesJson?.take(200)}...")
                    Timber.e("[SCHEMA-DEBUG-4b] 🔥 SCHEMA-ERROR: Exception type: ${e.javaClass.simpleName}")
                    Timber.e("[SCHEMA-DEBUG-4c] 🔥 SCHEMA-ERROR: This suggests either corrupted JSON or incompatible data structure")
                }
                emptyList()
            }
        }

        // 🔥 SCHEMA-DEBUG: Log final result
        if (BuildConfig.DEBUG) {
            Timber.d("[SCHEMA-DEBUG-5] WorkoutMapper.toDomain final result: ${exercises.size} exercises")
            if (exercises.isEmpty()) {
                Timber.w("[SCHEMA-DEBUG-5a] ⚠️ WARNING: toDomain returning ZERO exercises! This will cause volume calculations to be 0!")
            } else {
                exercises.forEachIndexed { index, exercise ->
                    Timber.d("[SCHEMA-DEBUG-5b] Final exercise $index: '${exercise.libraryExercise.name}' with ${exercise.sets.size} sets")
                }
            }
        }

        Workout(
            userId = userId,
            id = WorkoutId(id),
            name = name,
            date = date,
            exercises = exercises,
            status = status,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            templateId = templateId?.let(::WorkoutId),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Async wrapper for toDomain that ensures JSON parsing happens on background thread
     */
    suspend fun toDomainAsync(entity: WorkoutEntity): Workout = withContext(Dispatchers.IO) {
        toDomain(entity)
    }

    /**
     * Convert domain model to Room entity
     * Note: This method should be called from a background thread context
     */
    fun toEntity(workout: Workout, isSynced: Boolean = false): WorkoutEntity = workout.run {
        // 🔥 SETS-DEBUG: Log workout exercises before serialization
        if (BuildConfig.DEBUG) {
            Timber.d("[SETS-DEBUG-4] WorkoutMapper.toEntity: Workout '$name' has ${exercises.size} exercises")

            if (exercises.isEmpty()) {
                Timber.w("[SETS-DEBUG-4-WARNING] ⚠️ WORKOUT HAS NO EXERCISES! This will save exercises=[] and cause 0 volume!")
                Timber.w("[SETS-DEBUG-4-WARNING] Workout ID: ${id.value}, Name: '$name', Status: $status")
            } else {
                exercises.forEach { exercise ->
                    Timber.d("[SETS-DEBUG-4a] Exercise '${exercise.libraryExercise.name}' has ${exercise.sets.size} sets, type=${exercise.exerciseType}")
                    exercise.sets.forEach { set ->
                        Timber.d("[SETS-DEBUG-4b] ExerciseSet ${set.setNumber}: reps=${set.reps}, weight=${set.weight}, completed=${set.completedAt}")
                    }
                }
            }
        }
        
        val serializedJson = if (OfflineArchitectureFlags.ENABLE_CANONICAL_JSON_FORMAT) {
            canonicalJsonAdapter.serializeFromDomain(exercises)
        } else {
            // 🚀 KOTLINX-SERIALIZATION: Use kotlinx.serialization service for consistent serialization
            kotlinxSerializer.serializeExercisesSync(exercises)
        }
        if (BuildConfig.DEBUG) {
            Timber.d("[KOTLINX] WorkoutMapper.toEntity: Generated JSON length=${serializedJson.length}")
            Timber.d("[KOTLINX] This JSON uses kotlinx.serialization with single schema version")
        }

        // Log first part of JSON to see structure without overwhelming logs
        if (BuildConfig.DEBUG) {
            if (serializedJson.length > 500) {
                Timber.d("[UNIFIED] JSON preview: ${serializedJson.substring(0, 500)}...")
            } else {
                Timber.d("[UNIFIED] Full JSON: $serializedJson")
            }
        }
        
        WorkoutEntity(
            id = id.value,
            userId = userId,
            name = name,
            date = date,
            exercisesJson = serializedJson,
            status = status,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            templateId = templateId?.value,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isSynced = isSynced,
            syncVersion = System.currentTimeMillis()
        )
    }

    /**
     * Async wrapper for toEntity that ensures JSON serialization happens on background thread
     */
    suspend fun toEntityAsync(workout: Workout, isSynced: Boolean = false): WorkoutEntity = withContext(Dispatchers.IO) {
        toEntity(workout, isSynced)
    }

    /**
     * Direct entity to Firestore DTO conversion - bypasses domain layer
     *
     * This method preserves raw exercise data from the entity without parsing,
     * preventing data loss during sync operations when schema versions mismatch.
     * Use this for sync operations where preserving data integrity is critical.
     */
    fun entityToFirestoreDto(entity: WorkoutEntity, userId: String): WorkoutDto = entity.run {
        // 🔥 SYNC-FIX: Log the bypass operation
        if (BuildConfig.DEBUG) {
            Timber.d("[SYNC-BYPASS] entityToFirestoreDto: Converting '${entity.name}' directly from entity to DTO")
            Timber.d("[SYNC-BYPASS] Entity has ${exercisesJson?.length ?: 0} chars of exercise JSON")
        }

        // 🔥 SYNC-FIX: Try to extract exercises using the robust parsing we know works
        val exercises: List<ExerciseDto> = try {
            if (exercisesJson.isNullOrBlank()) {
                if (BuildConfig.DEBUG) Timber.d("[SYNC-BYPASS] No exercise JSON to parse")
                emptyList()
            } else {
                val trimmedJson = exercisesJson.trim()

                when {
                    trimmedJson.startsWith("[") -> {
                        if (BuildConfig.DEBUG) Timber.d("[SYNC-BYPASS] Found exercises in JSON array format")
                        val listType = object : TypeToken<List<ExerciseDto>>() {}.type
                        gson.fromJson<List<ExerciseDto>>(trimmedJson, listType) ?: emptyList()
                    }
                    else -> {
                        val dataType = object : TypeToken<Map<String, Any>>() {}.type
                        val data: Map<String, Any>? = gson.fromJson(exercisesJson, dataType)

                        when {
                            data?.containsKey("exercises") == true -> {
                                if (BuildConfig.DEBUG) Timber.d("[SYNC-BYPASS] Found exercises in JSON object format")
                                val exercisesRaw = data["exercises"]
                                if (exercisesRaw is List<*>) {
                                    exercisesRaw.mapNotNull { exerciseMap ->
                                        try {
                                            val exerciseJson = gson.toJson(exerciseMap)
                                            gson.fromJson(exerciseJson, ExerciseDto::class.java)
                                        } catch (e: Exception) {
                                            Timber.w("[SYNC-BYPASS] Failed to convert exercise: ${e.message}")
                                            null
                                        }
                                    }
                                } else {
                                    Timber.w("[SYNC-BYPASS] Exercises field is not a list: ${exercisesRaw?.javaClass?.simpleName}")
                                    emptyList()
                                }
                            }
                            else -> {
                                if (BuildConfig.DEBUG) {
                                    Timber.d("[SYNC-BYPASS] JSON doesn't contain exercises field, treating as empty")
                                }
                                emptyList()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[SYNC-BYPASS] Failed to parse exercises JSON, using empty list")
            emptyList()
        }

        if (BuildConfig.DEBUG) {
            Timber.d("[SYNC-BYPASS] Successfully parsed ${exercises.size} exercises for Firestore upload")
            if (exercises.isNotEmpty()) {
                exercises.forEachIndexed { index, exerciseDto ->
                    Timber.d("[SYNC-BYPASS] Exercise $index: '${exerciseDto.name}' with ${exerciseDto.sets.size} sets")
                }
            }
        }

        WorkoutDto(
            id = id,
            name = name,
            date = Timestamp(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)),
            exercises = exercises,
            status = status.name,
            startTime = startTime?.let { Timestamp(it.epochSecond, it.nano) },
            endTime = endTime?.let { Timestamp(it.epochSecond, it.nano) },
            notes = notes,
            templateId = templateId,
            createdAt = Timestamp(createdAt.epochSecond, createdAt.nano),
            updatedAt = Timestamp(updatedAt.epochSecond, updatedAt.nano),
            userId = userId,
            version = syncVersion
        )
    }

    /**
     * Convert domain model to Firestore DTO
     */
    fun toFirestoreDto(workout: Workout, userId: String): WorkoutDto = workout.run {
        WorkoutDto(
            id = id.value,
            name = name,
            date = Timestamp(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)),
            exercises = exercises.map(exerciseMapper::toFirestoreDto),
            status = status.name,
            startTime = startTime?.let { Timestamp(it.epochSecond, it.nano) },
            endTime = endTime?.let { Timestamp(it.epochSecond, it.nano) },
            notes = notes,
            templateId = templateId?.value,
            createdAt = Timestamp(createdAt.epochSecond, createdAt.nano),
            updatedAt = Timestamp(updatedAt.epochSecond, updatedAt.nano),
            userId = userId,
            version = System.currentTimeMillis()
        )
    }

    /**
     * Convert Firestore DTO to domain model with robust timestamp handling
     */
    suspend fun fromFirestoreDto(dto: WorkoutDto): Workout = dto.run {
        val parsedStartTime = convertToInstant(startTime)
        val parsedEndTime = convertToInstant(endTime)
        
        // Ensure valid time range for domain model validation
        val validatedEndTime = if (parsedStartTime != null && parsedEndTime != null) {
            if (parsedEndTime <= parsedStartTime) {
                // Add 1 second to ensure end time is after start time
                parsedStartTime.plusSeconds(1)
            } else {
                parsedEndTime
            }
        } else {
            parsedEndTime
        }
        
        // 🔥 VOLUME-BUG-FIX: Properly convert Firebase exercise data using conversion service
        val convertedExercises = if (exercises.isNotEmpty()) {
            if (BuildConfig.DEBUG) Timber.d("🔥 FIREBASE-SYNC-FIX: Converting Firebase workout with ${exercises.size} exercises")
            try {
                exerciseConversionService.convertFirebaseExercisesToDomain(exercises, WorkoutId(id))
            } catch (e: Exception) {
                Timber.e(e, "🔥 FIREBASE-SYNC-ERROR: Failed to convert exercises for workout $id")
                emptyList<Exercise>()
            }
        } else {
            if (BuildConfig.DEBUG) Timber.d("🔥 FIREBASE-SYNC-FIX: Firebase workout has no exercises")
            emptyList()
        }
        
        Workout(
            userId = userId,
            id = WorkoutId(id),
            name = name,
            date = convertToLocalDate(date) ?: LocalDate.now(),
            exercises = convertedExercises,
            status = WorkoutStatus.valueOf(status),
            startTime = parsedStartTime,
            endTime = validatedEndTime,
            notes = notes,
            templateId = templateId?.let(::WorkoutId),
            createdAt = convertToInstant(createdAt) ?: Instant.now(),
            updatedAt = convertToInstant(updatedAt) ?: convertToInstant(createdAt) ?: Instant.now()
        )
    }

    /**
     * Convert Firestore DTO to Room entity with robust timestamp handling
     */
    fun firestoreDtoToEntity(dto: WorkoutDto, isSynced: Boolean = true): WorkoutEntity {
        // 🔥 NETWORK-DEFENSIVE: Log comprehensive sync state for debugging
        if (BuildConfig.DEBUG) {
            Timber.d("🔥 FIREBASE-DTO-TO-ENTITY: Processing workout ${dto.id}")
            Timber.d("🔥 FIREBASE-DTO-TO-ENTITY: Remote has ${dto.exercises.size} exercises")
            Timber.d("🔥 FIREBASE-DTO-TO-ENTITY: Remote startTime=${dto.startTime}, endTime=${dto.endTime}")
        }
        
        // 🔥 WARNING: This method creates entities with empty exercises by design
        // It should only be called when we want to overwrite local data or for new workouts
        val exercisesJson = if (dto.exercises.isNotEmpty()) {
            Timber.w("🔥 FIREBASE-DTO-LOSS: Converting Firebase workout with ${dto.exercises.size} exercises to EMPTY local format!")
            Timber.w("🔥 FIREBASE-DTO-LOSS: This will cause 0 volume if it overwrites a completed workout!")
            // Create placeholder structure that indicates Firebase had data but we lost it
            gson.toJson(mapOf(
                "exercises" to emptyList<Exercise>(),
                "totalVolume" to 0.0,
                "exercisesWithVolume" to emptyList<Any>(),
                "firebaseExerciseCount" to dto.exercises.size, // Track that Firebase had data
                "dataLossWarning" to "Firebase exercises were not converted - check sync service"
            ))
        } else {
            if (BuildConfig.DEBUG) Timber.d("🔥 FIREBASE-DTO-EMPTY: Firebase workout has no exercises - creating empty structure")
            gson.toJson(mapOf(
                "exercises" to emptyList<Exercise>(),
                "totalVolume" to 0.0,
                "exercisesWithVolume" to emptyList<Any>(),
                "source" to "firebase_empty"
            ))
        }
        
        return WorkoutEntity(
            id = dto.id,
            userId = dto.userId,
            name = dto.name,
            date = convertToLocalDate(dto.date) ?: LocalDate.now(),
            exercisesJson = exercisesJson,
            status = WorkoutStatus.valueOf(dto.status),
            startTime = convertToInstant(dto.startTime),
            endTime = convertToInstant(dto.endTime),
            notes = dto.notes,
            templateId = dto.templateId,
            createdAt = convertToInstant(dto.createdAt) ?: Instant.now(),
            updatedAt = convertToInstant(dto.updatedAt) ?: convertToInstant(dto.createdAt) ?: Instant.now(),
            isSynced = isSynced,
            syncVersion = dto.version
        )
    }

    /**
     * Update entity sync status
     */
    fun updateEntitySyncStatus(entity: WorkoutEntity, isSynced: Boolean): WorkoutEntity {
        return entity.copy(
            isSynced = isSynced,
            syncVersion = System.currentTimeMillis()
        )
    }

    /**
     * Merge remote changes with local entity - DEFENSIVE SYNC to handle network failures
     */
    fun mergeRemoteChanges(localEntity: WorkoutEntity, remoteDto: WorkoutDto): WorkoutEntity {
        return if (remoteDto.version > localEntity.syncVersion) {
            // 🔥 NETWORK-DEFENSIVE: Check for network failure indicators
            val remoteLooksIncomplete = remoteDto.exercises.isEmpty() && 
                (remoteDto.startTime == null || remoteDto.endTime == null)
            
            // Check if local entity has exercise data
            val localHasExercises = try {
                val localExercisesJson = localEntity.exercisesJson
                localExercisesJson.isNotBlank() && 
                !localExercisesJson.contains("\"exercises\":[]") &&
                !localExercisesJson.contains("\"totalVolume\":0.0")
            } catch (e: Exception) {
                false
            }
            
            when {
                // 🔥 NETWORK-FAILURE-PROTECTION: Remote looks incomplete but local has data
                remoteLooksIncomplete && localHasExercises -> {
                    Timber.w("🔥 FIREBASE-MERGE-DEFENSIVE: Remote data looks incomplete (network failure?) - preserving ALL local data")
                    localEntity.copy(
                        isSynced = false, // Mark as NOT synced since remote fetch failed
                        // Keep everything else local - don't trust incomplete remote data
                        syncVersion = localEntity.syncVersion // Don't update version
                    )
                }
                
                // 🔥 SMART-MERGE: Remote has data but local exercises should be preserved
                localHasExercises -> {
                    if (BuildConfig.DEBUG) Timber.d("🔥 FIREBASE-MERGE-SMART: Local has exercise data - merging metadata only")
                    localEntity.copy(
                        name = remoteDto.name,
                        status = WorkoutStatus.valueOf(remoteDto.status),
                        startTime = convertToInstant(remoteDto.startTime),
                        endTime = convertToInstant(remoteDto.endTime),
                        notes = remoteDto.notes,
                        templateId = remoteDto.templateId,
                        updatedAt = convertToInstant(remoteDto.updatedAt) ?: localEntity.updatedAt,
                        isSynced = true,
                        syncVersion = remoteDto.version
                        // 🔥 CRITICAL: Keep existing exercisesJson - don't overwrite with Firebase data
                    )
                }
                
                // 🔥 FULL-REPLACE: Local has no exercises, safe to use remote data
                else -> {
                    if (BuildConfig.DEBUG) Timber.d("🔥 FIREBASE-MERGE-FULL: Local has no exercises - using remote data")
                    firestoreDtoToEntity(remoteDto, isSynced = true)
                }
            }
        } else {
            // Local version is newer or same, keep local but mark as synced
            localEntity.copy(isSynced = true)
        }
    }
    
    /**
     * Safe merge that never destroys local exercise data during network failures
     */
    fun safeMergeRemoteChanges(localEntity: WorkoutEntity, remoteDto: WorkoutDto, networkSuccess: Boolean = true): WorkoutEntity {
        if (!networkSuccess) {
            Timber.w("🔥 NETWORK-FAILURE: Skipping merge due to network failure - preserving local data")
            return localEntity.copy(isSynced = false)
        }
        
        return mergeRemoteChanges(localEntity, remoteDto)
    }

    private fun Timestamp.toInstant(): Instant {
        return Instant.ofEpochSecond(seconds, nanoseconds.toLong())
    }
    
    /**
     * Convert Any timestamp type (Timestamp or Long epoch millis) to Instant
     * Handles backward compatibility with existing Firestore documents
     */
    private fun convertToInstant(value: Any?): Instant? {
        return when (value) {
            is Timestamp -> value.toInstant()
            is Long -> Instant.ofEpochMilli(value)
            is Number -> Instant.ofEpochMilli(value.toLong())
            null -> null
            else -> {
                Timber.w("WorkoutMapper: Unexpected timestamp type: ${value::class.simpleName}, value: $value")
                null
            }
        }
    }
    
    /**
     * Convert Any timestamp type to LocalDate
     * Handles backward compatibility with existing Firestore documents
     */
    private fun convertToLocalDate(value: Any?): LocalDate? {
        return when (value) {
            is Timestamp -> value.toDate().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            is Long -> Instant.ofEpochMilli(value).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            is Number -> Instant.ofEpochMilli(value.toLong()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            null -> null
            else -> {
                Timber.w("WorkoutMapper: Unexpected date type: ${value::class.simpleName}, value: $value")
                null
            }
        }
    }


    /**
     * Parse serializable exercise data back to domain Exercise object
     */
    private fun parseSerializableExercise(exerciseData: Map<String, Any?>): Exercise? {
        return try {
            val id = ExerciseId(exerciseData["id"] as? String ?: return null)
            val workoutId = WorkoutId(exerciseData["workoutId"] as? String ?: return null)

            val libraryExerciseData = exerciseData["libraryExercise"] as? Map<String, Any?> ?: return null
            val libraryExercise = ExerciseLibrary(
                id = libraryExerciseData["id"] as? String ?: return null,
                name = libraryExerciseData["name"] as? String ?: return null,
                primaryMuscleGroup = (libraryExerciseData["primaryMuscleGroup"] as? String)?.let { ExerciseCategory.valueOf(it) } ?: ExerciseCategory.OTHER,
                equipment = Equipment.BODYWEIGHT_ONLY, // Default for now
                secondaryMuscleGroups = emptyList(),
                movementPattern = "unknown",
                difficultyLevel = 5,
                instructions = null,
                isCompound = false,
                searchableTerms = emptyList()
            )

            val orderIndex = (exerciseData["orderIndex"] as? Number)?.toInt() ?: return null

            // Parse sets data
            val setsData = exerciseData["sets"] as? List<Map<String, Any?>> ?: emptyList()
            val sets = setsData.mapNotNull { setData ->
                try {
                    val setId = ExerciseSetId(setData["id"] as? String ?: return@mapNotNull null)
                    val setNumber = (setData["setNumber"] as? Number)?.toInt() ?: return@mapNotNull null
                    val reps = (setData["reps"] as? Number)?.toInt()?.let { Reps(it) }
                    val weight = (setData["weight"] as? Number)?.toDouble()?.let { Weight.fromKilograms(it) }
                    val rpe = (setData["rpe"] as? Number)?.toInt()?.let { RPE(it) }
                    val completedAt = (setData["completedAt"] as? Number)?.toLong()?.let { Instant.ofEpochMilli(it) }
                    val notes = setData["notes"] as? String

                    ExerciseSet(
                        id = setId,
                        setNumber = setNumber,
                        reps = reps,
                        weight = weight,
                        rpe = rpe,
                        completedAt = completedAt,
                        notes = notes
                    )
                } catch (e: Exception) {
                    Timber.w(e, "[CONFLICT] Failed to parse set data: $setData")
                    null
                }
            }

            val targetWeight = (exerciseData["targetWeight"] as? Number)?.toDouble()?.let { Weight.fromKilograms(it) }
            val createdAt = (exerciseData["createdAt"] as? Number)?.toLong()?.let { Instant.ofEpochMilli(it) } ?: Instant.now()

            Exercise.createSafe(
                id = id,
                workoutId = workoutId,
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                targetSets = exerciseData["targetSets"] as? Int,
                targetReps = exerciseData["targetReps"] as? Int,
                targetWeight = targetWeight,
                sets = sets,
                notes = exerciseData["notes"] as? String,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Timber.w(e, "[CONFLICT] Failed to parse serializable exercise: $exerciseData")
            null
        }
    }


    /**
     * Parse JSON using kotlinx.serialization only
     */
    private fun parseWithKotlinxSerialization(json: String): List<Exercise> {
        return try {
            if (BuildConfig.DEBUG) {
                Timber.d("🔍 PARSE-DEBUG: Attempting kotlinx deserialization on JSON: ${json.take(300)}...")
            }
            kotlinxSerializer.deserializeExercises(json)
        } catch (e: Exception) {
            Timber.e(e, "❌ KOTLINX-PARSE-ERROR: Failed to parse exercises with kotlinx.serialization")
            if (BuildConfig.DEBUG) {
                Timber.e("❌ KOTLINX-PARSE-ERROR: Exception message: ${e.message}")
                Timber.e("❌ KOTLINX-PARSE-ERROR: JSON that failed: ${json.take(500)}...")
            }
            throw RuntimeException("Unable to load workout data. This workout uses an unsupported format. Please export and re-import your workouts.", e)
        }
    }



}
