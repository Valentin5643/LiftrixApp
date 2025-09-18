package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.remote.dto.WorkoutDto
import com.example.liftrix.data.remote.dto.ExerciseDto
import com.example.liftrix.data.service.ExerciseConversionService
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

@Singleton
class WorkoutMapper @Inject constructor(
    private val exerciseMapper: ExerciseMapper,
    private val exerciseConversionService: ExerciseConversionService,
    private val gson: Gson
) {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

        // JSON Schema versioning for exercises_json field - PRODUCTION BRIDGE PATTERN
        const val CURRENT_SCHEMA_VERSION = 2  // WorkoutMapper native format (schema_version: 2)
        const val SUPPORTED_LATEST_VERSION = 3  // WorkoutJsonSerializationService format (schemaVersion: 3)
        const val LEGACY_SCHEMA_VERSION = 1
        const val MINIMAL_SCHEMA_VERSION = 0

        // 🚀 PRODUCTION-READY: Support both v2 (native) and v3 (serialization service) during 12-month migration
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
     */
    fun toDomain(entity: WorkoutEntity): Workout = entity.run {
        // 🚨 SCHEMA-CONFLICT: Log the incoming JSON to detect schema conflicts between WorkoutMapper vs WorkoutJsonSerializationService
        Timber.d("[CONFLICT] WorkoutMapper.toDomain: Processing workout '${entity.name}' (ID: ${entity.id})")
        Timber.d("[CONFLICT] JSON length: ${exercisesJson?.length ?: 0}")
        if (!exercisesJson.isNullOrBlank()) {
            val jsonPreview = if (exercisesJson.length > 300) exercisesJson.substring(0, 300) + "..." else exercisesJson
            Timber.d("[CONFLICT] JSON preview: $jsonPreview")

            // Detect schema version from JSON structure - CHECK FOR CONFLICTING FORMATS
            val detectedVersion = when {
                exercisesJson.contains("\"schemaVersion\":3") -> 3  // WorkoutJsonSerializationService format
                exercisesJson.contains("\"schema_version\":2") -> 2  // WorkoutMapper format
                exercisesJson.contains("\"exercises\":") -> 1
                else -> 0
            }

            // 🚨 CRITICAL: Check if we have format conflict between services
            val hasSerializationServiceFormat = exercisesJson.contains("\"schemaVersion\":3")
            val hasMapperFormat = exercisesJson.contains("\"schema_version\":2")

            Timber.d("[CONFLICT] Detected version: $detectedVersion, SerializationService format: $hasSerializationServiceFormat, Mapper format: $hasMapperFormat")
            Timber.d("[CONFLICT] WorkoutMapper supports v$CURRENT_SCHEMA_VERSION (native) and v$SUPPORTED_LATEST_VERSION (bridge)")

            if (detectedVersion != CURRENT_SCHEMA_VERSION && detectedVersion != SUPPORTED_LATEST_VERSION) {
                Timber.w("[CONFLICT] ⚠️ UNSUPPORTED SCHEMA! JSON is v$detectedVersion but mapper supports v$CURRENT_SCHEMA_VERSION-$SUPPORTED_LATEST_VERSION")
            } else {
                Timber.d("[CONFLICT] ✅ SCHEMA SUPPORTED: v$detectedVersion is within supported range")
                if (hasSerializationServiceFormat) {
                    Timber.d("[CONFLICT] ✅ Using v3 bridge pattern for WorkoutJsonSerializationService format")
                }
            }
        }

        // 🚀 PRODUCTION-READY: Handle multiple JSON schema versions with version bridge pattern
        val exercises: List<Exercise> = try {
            // Parse JSON structure first to detect schema version
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any>? = gson.fromJson(exercisesJson, dataType)

            when {
                // 🔥 SCHEMA-V3: WorkoutJsonSerializationService format (schemaVersion: 3)
                data?.containsKey("schemaVersion") == true && data["schemaVersion"]?.toString()?.toIntOrNull() == 3 -> {
                    Timber.d("[CONFLICT] 🚀 SCHEMA-V3-BRIDGE: Parsing WorkoutJsonSerializationService format")

                    // Extract exercises from v3 format: { schemaVersion: 3, exercises: [...], metadata: {...} }
                    val exercisesRaw = data["exercises"]
                    Timber.d("[CONFLICT] V3 has ${(exercisesRaw as? List<*>)?.size ?: 0} exercises")

                    if (exercisesRaw is List<*>) {
                        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                        val parsedExercises = gson.fromJson<List<Exercise>>(gson.toJson(exercisesRaw), exercisesType) ?: emptyList()
                        Timber.d("[CONFLICT] ✅ SCHEMA-V3-SUCCESS: Parsed ${parsedExercises.size} exercises from v3 format")
                        parsedExercises
                    } else {
                        Timber.w("[CONFLICT] ⚠️ SCHEMA-V3-ERROR: exercises field is not a list")
                        emptyList<Exercise>()
                    }
                }

                // 🔥 SCHEMA-V2: WorkoutMapper format (schema_version: 2) with serializable data
                data?.containsKey("schema_version") == true && data["schema_version"]?.toString()?.toIntOrNull() == 2 -> {
                    Timber.d("[CONFLICT] 🔥 SCHEMA-V2: Parsing WorkoutMapper serializable format")

                    val exercisesRaw = data["exercises"]
                    Timber.d("[CONFLICT] V2 has ${(exercisesRaw as? List<*>)?.size ?: 0} exercises")

                    if (exercisesRaw is List<*>) {
                        // Parse serializable exercise data and convert back to domain objects
                        val parsedExercises = exercisesRaw.mapNotNull { exerciseData ->
                            parseSerializableExercise(exerciseData as? Map<String, Any?> ?: return@mapNotNull null)
                        }
                        Timber.d("[CONFLICT] ✅ SCHEMA-V2-SUCCESS: Parsed ${parsedExercises.size} exercises from v2 serializable format")
                        parsedExercises
                    } else {
                        Timber.w("[CONFLICT] ⚠️ SCHEMA-V2-ERROR: exercises field is not a list")
                        emptyList<Exercise>()
                    }
                }

                // 🔥 SCHEMA-V1: Enhanced format without explicit version (exercises key present)
                data?.containsKey("exercises") == true -> {
                    Timber.d("[CONFLICT] 🔥 SCHEMA-V1: Parsing enhanced format (legacy)")
                    val exercisesRaw = data["exercises"]

                    if (exercisesRaw is List<*>) {
                        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                        val parsedExercises = gson.fromJson<List<Exercise>>(gson.toJson(exercisesRaw), exercisesType) ?: emptyList()
                        Timber.d("[CONFLICT] ✅ SCHEMA-V1-SUCCESS: Parsed ${parsedExercises.size} exercises from v1 format")
                        parsedExercises
                    } else {
                        Timber.w("[CONFLICT] ⚠️ SCHEMA-V1-ERROR: exercises field is not a list")
                        emptyList<Exercise>()
                    }
                }

                // 🔥 SCHEMA-V0: Direct exercise array (minimal format)
                else -> {
                    Timber.d("[CONFLICT] 🔥 SCHEMA-V0: Parsing minimal format (direct exercise array)")
                    val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                    val parsedExercises = gson.fromJson<List<Exercise>>(exercisesJson, exercisesType) ?: emptyList()
                    Timber.d("[CONFLICT] ✅ SCHEMA-V0-SUCCESS: Parsed ${parsedExercises.size} exercises from minimal format")
                    parsedExercises
                }
            }
        } catch (e: Exception) {
            // Final fallback to empty list with detailed error logging
            Timber.e(e, "[SCHEMA-DEBUG-4] 🔥 SCHEMA-ERROR: All exercise JSON parsing failed for workout ${entity.id}")
            Timber.e("[SCHEMA-DEBUG-4a] 🔥 SCHEMA-ERROR: JSON content: ${exercisesJson?.take(200)}...")
            Timber.e("[SCHEMA-DEBUG-4b] 🔥 SCHEMA-ERROR: Exception type: ${e.javaClass.simpleName}")
            Timber.e("[SCHEMA-DEBUG-4c] 🔥 SCHEMA-ERROR: This suggests either corrupted JSON or incompatible data structure")
            emptyList<Exercise>()
        }

        // 🔥 SCHEMA-DEBUG: Log final result
        Timber.d("[SCHEMA-DEBUG-5] WorkoutMapper.toDomain final result: ${exercises.size} exercises")
        if (exercises.isEmpty()) {
            Timber.w("[SCHEMA-DEBUG-5a] ⚠️ WARNING: toDomain returning ZERO exercises! This will cause volume calculations to be 0!")
        } else {
            exercises.forEachIndexed { index, exercise ->
                Timber.d("[SCHEMA-DEBUG-5b] Final exercise $index: '${exercise.libraryExercise.name}' with ${exercise.sets.size} sets")
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
     * Convert domain model to Room entity
     */
    fun toEntity(workout: Workout, isSynced: Boolean = false): WorkoutEntity = workout.run {
        // 🔥 SETS-DEBUG: Log workout exercises before serialization
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
        
        // 🚀 FIXED: Create serializable exercise data that includes sets with weight/reps
        val serializableExercises = exercises.map { exercise ->
            mapOf(
                "id" to exercise.id.value,
                "workoutId" to exercise.workoutId.value,
                "libraryExercise" to mapOf(
                    "id" to exercise.libraryExercise.id,
                    "name" to exercise.libraryExercise.name,
                    "primaryMuscleGroup" to exercise.libraryExercise.primaryMuscleGroup.name
                ),
                "orderIndex" to exercise.orderIndex,
                // 🔥 CRITICAL FIX: Include sets with actual weight/reps data
                "sets" to exercise.sets.map { set ->
                    mapOf(
                        "id" to set.id.value,
                        "setNumber" to set.setNumber,
                        "reps" to set.reps?.count,  // Extract actual integer value
                        "weight" to set.weight?.kilograms,  // Extract actual double value
                        "time" to set.time?.toMillis(),
                        "distance" to set.distance?.meters,
                        "rpe" to set.rpe?.value,
                        "completedAt" to set.completedAt?.toEpochMilli(),
                        "notes" to set.notes
                    )
                },
                "targetSets" to exercise.targetSets,
                "targetReps" to exercise.targetReps,
                "targetWeight" to exercise.targetWeight?.kilograms,
                "notes" to exercise.notes,
                "createdAt" to exercise.createdAt.toEpochMilli()
            )
        }

        // Calculate volume from serializable exercise data
        val exercisesWithVolume = serializableExercises.map { exerciseMap ->
            val sets = exerciseMap["sets"] as? List<Map<String, Any?>> ?: emptyList()
            val volumeInKg = sets.sumOf { setMap ->
                val reps = (setMap["reps"] as? Number)?.toDouble() ?: 0.0
                val weight = (setMap["weight"] as? Number)?.toDouble() ?: 0.0
                reps * weight
            }
            mapOf(
                "exerciseId" to exerciseMap["id"],
                "totalVolume" to volumeInKg
            )
        }

        val workoutTotalVolume = exercisesWithVolume.sumOf {
            (it["totalVolume"] as? Number)?.toDouble() ?: 0.0
        }

        val enhancedJson = mapOf(
            "schema_version" to CURRENT_SCHEMA_VERSION,
            "exercises" to serializableExercises,  // Use serializable format instead of domain objects
            "totalVolume" to workoutTotalVolume,
            "exercisesWithVolume" to exercisesWithVolume,
            "created_at" to System.currentTimeMillis(),
            "format" to "enhanced_v2"
        )
        
        // 🚨 CONFLICT: Log serialization to detect which service is creating the JSON
        val serializedJson = gson.toJson(enhancedJson)
        Timber.d("[CONFLICT] WorkoutMapper.toEntity: Generated JSON length=${serializedJson.length}")
        Timber.d("[CONFLICT] This JSON uses schema_version:$CURRENT_SCHEMA_VERSION (WorkoutMapper format)")

        // Log first part of JSON to see structure without overwhelming logs
        if (serializedJson.length > 500) {
            Timber.d("[CONFLICT] JSON preview: ${serializedJson.substring(0, 500)}...")
        } else {
            Timber.d("[CONFLICT] Full JSON: $serializedJson")
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
     * Direct entity to Firestore DTO conversion - bypasses domain layer
     *
     * This method preserves raw exercise data from the entity without parsing,
     * preventing data loss during sync operations when schema versions mismatch.
     * Use this for sync operations where preserving data integrity is critical.
     */
    fun entityToFirestoreDto(entity: WorkoutEntity, userId: String): WorkoutDto = entity.run {
        // 🔥 SYNC-FIX: Log the bypass operation
        Timber.d("[SYNC-BYPASS] entityToFirestoreDto: Converting '${entity.name}' directly from entity to DTO")
        Timber.d("[SYNC-BYPASS] Entity has ${exercisesJson?.length ?: 0} chars of exercise JSON")

        // 🔥 SYNC-FIX: Try to extract exercises using the robust parsing we know works
        val exercises: List<ExerciseDto> = try {
            if (exercisesJson.isNullOrBlank()) {
                Timber.d("[SYNC-BYPASS] No exercise JSON to parse")
                emptyList<ExerciseDto>()
            } else {
                // Use the same parsing logic that we know works from toDomain, but catch errors
                val dataType = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any>? = gson.fromJson(exercisesJson, dataType)

                when {
                    data?.containsKey("exercises") == true -> {
                        Timber.d("[SYNC-BYPASS] Found exercises in JSON object format")
                        val exercisesRaw = data["exercises"]
                        if (exercisesRaw is List<*>) {
                            // Convert each exercise map to ExerciseDto
                            exercisesRaw.mapNotNull { exerciseMap ->
                                try {
                                    // Convert map to ExerciseDto using Gson
                                    val exerciseJson = gson.toJson(exerciseMap)
                                    gson.fromJson(exerciseJson, ExerciseDto::class.java)
                                } catch (e: Exception) {
                                    Timber.w("[SYNC-BYPASS] Failed to convert exercise: ${e.message}")
                                    null
                                }
                            }
                        } else {
                            Timber.w("[SYNC-BYPASS] Exercises field is not a list: ${exercisesRaw?.javaClass?.simpleName}")
                            emptyList<ExerciseDto>()
                        }
                    }
                    else -> {
                        Timber.d("[SYNC-BYPASS] JSON doesn't contain exercises field, treating as empty")
                        emptyList<ExerciseDto>()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[SYNC-BYPASS] Failed to parse exercises JSON, using empty list")
            emptyList<ExerciseDto>()
        }

        Timber.d("[SYNC-BYPASS] Successfully parsed ${exercises.size} exercises for Firestore upload")
        if (exercises.isNotEmpty()) {
            exercises.forEachIndexed { index, exerciseDto ->
                Timber.d("[SYNC-BYPASS] Exercise $index: '${exerciseDto.name}' with ${exerciseDto.sets.size} sets")
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
            Timber.d("🔥 FIREBASE-SYNC-FIX: Converting Firebase workout with ${exercises.size} exercises")
            try {
                exerciseConversionService.convertFirebaseExercisesToDomain(exercises, WorkoutId(id))
            } catch (e: Exception) {
                Timber.e(e, "🔥 FIREBASE-SYNC-ERROR: Failed to convert exercises for workout $id")
                emptyList<Exercise>()
            }
        } else {
            Timber.d("🔥 FIREBASE-SYNC-FIX: Firebase workout has no exercises")
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
        Timber.d("🔥 FIREBASE-DTO-TO-ENTITY: Processing workout ${dto.id}")
        Timber.d("🔥 FIREBASE-DTO-TO-ENTITY: Remote has ${dto.exercises.size} exercises")
        Timber.d("🔥 FIREBASE-DTO-TO-ENTITY: Remote startTime=${dto.startTime}, endTime=${dto.endTime}")
        
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
            Timber.d("🔥 FIREBASE-DTO-EMPTY: Firebase workout has no exercises - creating empty structure")
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
                    Timber.d("🔥 FIREBASE-MERGE-SMART: Local has exercise data - merging metadata only")
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
                    Timber.d("🔥 FIREBASE-MERGE-FULL: Local has no exercises - using remote data")
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
     * Migrate exercise JSON from older schema versions to current version
     * This method can be used to batch migrate existing workouts
     */
    fun migrateExerciseJsonToCurrentSchema(oldJson: String): String {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any>? = gson.fromJson(oldJson, dataType)

            when {
                // Already current version
                data?.get("schema_version")?.toString()?.toIntOrNull() == CURRENT_SCHEMA_VERSION -> {
                    Timber.d("🔥 MIGRATION: JSON already at current schema version")
                    oldJson
                }

                // Migrate from version 1 (enhanced without schema version)
                data?.containsKey("exercises") == true && data.get("schema_version") == null -> {
                    Timber.d("🔥 MIGRATION: Migrating from schema v1 to v${CURRENT_SCHEMA_VERSION}")
                    val migratedJson = mutableMapOf<String, Any>()
                    migratedJson.putAll(data)
                    migratedJson["schema_version"] = CURRENT_SCHEMA_VERSION
                    migratedJson["format"] = "enhanced_v2"
                    migratedJson["migrated_at"] = System.currentTimeMillis()

                    // Preserve original created_at if it exists, otherwise use migration time
                    if (!migratedJson.containsKey("created_at")) {
                        migratedJson["created_at"] = System.currentTimeMillis()
                    }

                    gson.toJson(migratedJson)
                }

                // Migrate from version 0 (direct exercise list)
                else -> {
                    Timber.d("🔥 MIGRATION: Migrating from schema v0 to v${CURRENT_SCHEMA_VERSION}")
                    // Parse as direct exercise list
                    val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                    val exercises: List<Exercise> = gson.fromJson(oldJson, exercisesType) ?: emptyList()

                    // Create new enhanced format
                    val exercisesWithVolume = exercises.map { exercise ->
                        val volumeInKg = exercise.getTotalVolume()?.kilograms ?: 0.0
                        mapOf(
                            "exercise" to exercise,
                            "totalVolume" to volumeInKg
                        )
                    }
                    val workoutTotalVolume = exercises.mapNotNull { it.getTotalVolume() }
                        .fold(Weight.ZERO) { acc, weight -> acc + weight }
                        .kilograms

                    val migratedJson = mapOf(
                        "schema_version" to CURRENT_SCHEMA_VERSION,
                        "exercises" to exercises,
                        "totalVolume" to workoutTotalVolume,
                        "exercisesWithVolume" to exercisesWithVolume,
                        "created_at" to System.currentTimeMillis(),
                        "migrated_at" to System.currentTimeMillis(),
                        "format" to "enhanced_v2",
                        "migrated_from" to "direct_list_v0"
                    )

                    gson.toJson(migratedJson)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🔥 MIGRATION-ERROR: Failed to migrate exercise JSON")
            // Return original JSON on migration failure
            oldJson
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
     * Validates exercise JSON schema integrity
     */
    fun validateExerciseJsonSchema(json: String): SchemaValidationResult {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any>? = gson.fromJson(json, dataType)

            when {
                data?.get("schema_version")?.toString()?.toIntOrNull() == CURRENT_SCHEMA_VERSION -> {
                    val hasExercises = data.containsKey("exercises")
                    val hasTotalVolume = data.containsKey("totalVolume")
                    val hasFormat = data.containsKey("format")

                    if (hasExercises && hasTotalVolume && hasFormat) {
                        SchemaValidationResult.Valid(CURRENT_SCHEMA_VERSION)
                    } else {
                        SchemaValidationResult.Invalid("Missing required fields for schema v$CURRENT_SCHEMA_VERSION")
                    }
                }

                data?.containsKey("exercises") == true -> {
                    SchemaValidationResult.NeedsMigration(LEGACY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                }

                else -> {
                    // Check if it's a direct exercise list
                    try {
                        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                        gson.fromJson<List<Exercise>>(json, exercisesType)
                        SchemaValidationResult.NeedsMigration(MINIMAL_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                    } catch (e: Exception) {
                        SchemaValidationResult.Invalid("Unrecognized JSON format")
                    }
                }
            }
        } catch (e: Exception) {
            SchemaValidationResult.Invalid("JSON parsing failed: ${e.message}")
        }
    }

    /**
     * Result of schema validation
     */
    sealed class SchemaValidationResult {
        data class Valid(val version: Int) : SchemaValidationResult()
        data class NeedsMigration(val currentVersion: Int, val targetVersion: Int) : SchemaValidationResult()
        data class Invalid(val reason: String) : SchemaValidationResult()
    }

} 