package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.remote.dto.ExerciseSetDto
import com.example.liftrix.domain.model.*
import com.google.firebase.Timestamp
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between ExerciseSet domain model, Room entity, and Firestore DTO
 * Supports flexible metrics (weight, time, distance, RPE)
 */
@Singleton
class ExerciseSetMapper @Inject constructor() {
    
    // === Room Entity Mapping Methods ===
    
    /**
     * Convert Room entity to domain ExerciseSet
     */
    fun toDomain(entity: ExerciseSetEntity): ExerciseSet {
        return ExerciseSet(
            id = ExerciseSetId(entity.id.toString()),
            setNumber = entity.setNumber,
            reps = entity.reps?.let { Reps.of(it) },
            weight = entity.weightKg?.let { Weight.fromKilograms(it.toDouble()) },
            time = entity.timeSeconds?.let { Duration.ofSeconds(it.toLong()) },
            distance = entity.distanceMeters?.let { Distance.fromMeters(it) },
            rpe = entity.rpe?.let { RPE.fromInt(it) },
            completedAt = entity.completedAt?.let { Instant.ofEpochMilli(it) },
            notes = entity.notes
        )
    }
    
    /**
     * Convert domain ExerciseSet to Room entity
     */
    fun toEntity(set: ExerciseSet, exerciseId: Long): ExerciseSetEntity {
        return ExerciseSetEntity(
            id = if (set.id.value.isNotBlank() && set.id.value != "0") set.id.value.toLong() else 0,
            exerciseId = exerciseId,
            setNumber = set.setNumber,
            reps = set.reps?.count,
            weightKg = set.weight?.kilograms?.toFloat(),
            timeSeconds = set.time?.seconds?.toInt(),
            distanceMeters = set.distance?.meters,
            rpe = set.rpe?.value,
            notes = set.notes,
            completedAt = set.completedAt?.toEpochMilli()
        )
    }
    
    // === Firestore DTO Mapping Methods ===
    
    /**
     * Convert domain ExerciseSet to Firestore DTO
     */
    fun toFirestoreDto(exerciseSet: ExerciseSet): ExerciseSetDto {
        return ExerciseSetDto(
            setNumber = exerciseSet.setNumber,
            reps = exerciseSet.reps?.count ?: 0,
            weightKg = exerciseSet.weight?.kilograms ?: 0.0,
            timeSeconds = exerciseSet.time?.seconds?.toInt(),
            distanceMeters = exerciseSet.distance?.meters,
            rpe = exerciseSet.rpe?.value,
            isCompleted = exerciseSet.isCompleted,
            notes = exerciseSet.notes,
            completedAt = exerciseSet.completedAt?.let { 
                Timestamp(it.epochSecond, it.nano) 
            }
        )
    }

    /**
     * Convert Firestore DTO to domain ExerciseSet
     */
    fun fromFirestoreDto(dto: ExerciseSetDto): ExerciseSet {
        return ExerciseSet(
            id = ExerciseSetId.generate(), // Generate new ID for Firestore data
            setNumber = dto.setNumber,
            reps = if (dto.reps > 0) Reps.of(dto.reps) else null,
            weight = if (dto.weightKg > 0) Weight.fromKilograms(dto.weightKg) else null,
            time = dto.timeSeconds?.let { Duration.ofSeconds(it.toLong()) },
            distance = dto.distanceMeters?.let { Distance.fromMeters(it) },
            rpe = dto.rpe?.let { RPE.fromInt(it) },
            completedAt = dto.completedAt?.let { 
                Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) 
            },
            notes = dto.notes
        )
    }
    
    // === Legacy Support (for backward compatibility) ===
    
    /**
     * JSON representation for legacy Room storage
     */
    data class ExerciseSetJson(
        val setNumber: Int,
        val weightKg: Double?,
        val reps: Int?,
        val timeSeconds: Int?,
        val distanceMeters: Float?,
        val rpe: Int?,
        val isCompleted: Boolean,
        val notes: String?,
        val completedAt: String?
    )
    
    /**
     * Converts domain model to JSON for legacy Room storage
     */
    fun toJson(exerciseSet: ExerciseSet): ExerciseSetJson {
        return ExerciseSetJson(
            setNumber = exerciseSet.setNumber,
            weightKg = exerciseSet.weight?.kilograms,
            reps = exerciseSet.reps?.count,
            timeSeconds = exerciseSet.time?.seconds?.toInt(),
            distanceMeters = exerciseSet.distance?.meters,
            rpe = exerciseSet.rpe?.value,
            isCompleted = exerciseSet.isCompleted,
            notes = exerciseSet.notes,
            completedAt = exerciseSet.completedAt?.toString()
        )
    }
    
    /**
     * Converts JSON to domain model for legacy Room storage
     */
    fun fromJson(json: ExerciseSetJson): ExerciseSet {
        return ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = json.setNumber,
            reps = json.reps?.let { Reps.of(it) },
            weight = json.weightKg?.let { Weight.fromKilograms(it) },
            time = json.timeSeconds?.let { Duration.ofSeconds(it.toLong()) },
            distance = json.distanceMeters?.let { Distance.fromMeters(it) },
            rpe = json.rpe?.let { RPE.fromInt(it) },
            completedAt = json.completedAt?.let { Instant.parse(it) },
            notes = json.notes
        )
    }
}