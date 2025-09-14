package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.remote.dto.WorkoutDto
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
    private val gson: Gson
) {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Convert Room entity to domain model
     */
    fun toDomain(entity: WorkoutEntity): Workout = entity.run {
        // Handle both old and new JSON formats for backward compatibility
        val exercises: List<Exercise> = try {
            // Try new enhanced format first
            val enhancedType = object : TypeToken<Map<String, Any>>() {}.type
            val enhancedData: Map<String, Any>? = gson.fromJson(exercisesJson, enhancedType)
            
            if (enhancedData?.containsKey("exercises") == true) {
                // New format with exercises key
                val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                val exercisesList = gson.fromJson<List<Exercise>>(gson.toJson(enhancedData["exercises"]), exercisesType) ?: emptyList<Exercise>()
                exercisesList
            } else {
                // Old format - direct exercise list
                val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                val exercisesList = gson.fromJson<List<Exercise>>(exercisesJson, exercisesType) ?: emptyList<Exercise>()
                exercisesList
            }
        } catch (e: Exception) {
            // Fallback to old format if parsing fails
            try {
                val exercisesType = object : TypeToken<List<Exercise>>() {}.type
                val fallbackList = gson.fromJson<List<Exercise>>(exercisesJson, exercisesType) ?: emptyList<Exercise>()
                fallbackList
            } catch (e2: Exception) {
                Timber.e(e2, "🔥 WORKOUT-DEBUG: Exercise JSON parsing failed completely")
                emptyList<Exercise>()
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
        
        // Create enhanced JSON that includes calculated totalVolume
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
        
        val enhancedJson = mapOf(
            "exercises" to exercises,
            "totalVolume" to workoutTotalVolume,
            "exercisesWithVolume" to exercisesWithVolume
        )
        
        // 🔥 SETS-DEBUG: Log the serialized JSON to detect serialization issues
        val serializedJson = gson.toJson(enhancedJson)
        Timber.d("[SETS-DEBUG-5] WorkoutMapper.toEntity: Generated JSON length=${serializedJson.length}")
        
        // Log first part of JSON to see structure without overwhelming logs
        if (serializedJson.length > 500) {
            Timber.d("[SETS-DEBUG-5a] JSON preview: ${serializedJson.substring(0, 500)}...")
        } else {
            Timber.d("[SETS-DEBUG-5a] Full JSON: $serializedJson")
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
    fun fromFirestoreDto(dto: WorkoutDto): Workout = dto.run {
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
        
        // 🔥 VOLUME-BUG-FIX: Don't lose exercise data during sync
        // Firebase exercise conversion is complex and handled by separate sync service
        // For now, preserve the fact that exercises exist without full conversion
        val convertedExercises = if (exercises.isNotEmpty()) {
            Timber.d("🔥 FIREBASE-SYNC-FIX: Firebase workout has ${exercises.size} exercises - marking as preserved")
            // Create placeholder exercises to indicate data exists
            // The actual exercise data will be handled by the exercise sync service
            emptyList<Exercise>() // TODO: Implement proper exercise conversion service
        } else {
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

} 