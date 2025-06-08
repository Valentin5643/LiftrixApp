package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.remote.dto.ExerciseSetDto
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between ExerciseSet domain model, Room entity, and Firestore DTO
 */
@Singleton
class ExerciseSetMapper @Inject constructor() {
    
    /**
     * JSON representation for Room storage
     */
    data class ExerciseSetJson(
        val setNumber: Int,
        val weightKg: Double,
        val reps: Int,
        val isCompleted: Boolean,
        val restTimeSeconds: Int?,
        val notes: String?,
        val completedAt: String?
    )
    
    /**
     * Converts domain model to Room entity
     */
    fun toEntity(exerciseSet: ExerciseSet, exerciseId: String): ExerciseSetEntity {
        return ExerciseSetEntity(
            id = UUID.randomUUID().toString(),
            exerciseId = exerciseId,
            setNumber = exerciseSet.setNumber,
            weightKg = exerciseSet.weight.kilograms,
            reps = exerciseSet.reps.count,
            isCompleted = exerciseSet.isCompleted,
            restTimeSeconds = exerciseSet.restTimeSeconds,
            notes = exerciseSet.notes,
            completedAt = exerciseSet.completedAt
        )
    }
    
    /**
     * Converts Room entity to domain model
     */
    fun fromEntity(entity: ExerciseSetEntity): ExerciseSet {
        return ExerciseSet(
            setNumber = entity.setNumber,
            weight = Weight.fromKilograms(entity.weightKg),
            reps = Reps.of(entity.reps),
            isCompleted = entity.isCompleted,
            restTimeSeconds = entity.restTimeSeconds,
            notes = entity.notes,
            completedAt = entity.completedAt
        )
    }
    
    /**
     * Converts domain model to JSON for Room storage
     */
    fun toJson(exerciseSet: ExerciseSet): ExerciseSetJson {
        return ExerciseSetJson(
            setNumber = exerciseSet.setNumber,
            weightKg = exerciseSet.weight.kilograms,
            reps = exerciseSet.reps.count,
            isCompleted = exerciseSet.isCompleted,
            restTimeSeconds = exerciseSet.restTimeSeconds,
            notes = exerciseSet.notes,
            completedAt = exerciseSet.completedAt?.toString()
        )
    }
    
    /**
     * Converts JSON to domain model
     */
    fun fromJson(json: ExerciseSetJson): ExerciseSet {
        return ExerciseSet(
            setNumber = json.setNumber,
            weight = Weight.fromKilograms(json.weightKg),
            reps = Reps.of(json.reps),
            isCompleted = json.isCompleted,
            restTimeSeconds = json.restTimeSeconds,
            notes = json.notes,
            completedAt = json.completedAt?.let { Instant.parse(it) }
        )
    }
    
    /**
     * Convert domain ExerciseSet to Firestore DTO
     */
    fun toFirestoreDto(exerciseSet: ExerciseSet): ExerciseSetDto {
        return ExerciseSetDto(
            setNumber = exerciseSet.setNumber,
            reps = exerciseSet.reps.count,
            weightKg = exerciseSet.weight.kilograms,
            isCompleted = exerciseSet.isCompleted,
            restTimeSeconds = exerciseSet.restTimeSeconds,
            notes = exerciseSet.notes,
            completedAt = exerciseSet.completedAt?.let { 
                com.google.firebase.Timestamp(it.epochSecond, it.nano) 
            }
        )
    }

    /**
     * Convert Firestore DTO to domain ExerciseSet
     */
    fun fromFirestoreDto(dto: ExerciseSetDto): ExerciseSet {
        return ExerciseSet(
            setNumber = dto.setNumber,
            reps = Reps(dto.reps),
            weight = Weight(dto.weightKg),
            isCompleted = dto.isCompleted,
            restTimeSeconds = dto.restTimeSeconds,
            notes = dto.notes,
            completedAt = dto.completedAt?.let { 
                java.time.Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) 
            }
        )
    }
} 