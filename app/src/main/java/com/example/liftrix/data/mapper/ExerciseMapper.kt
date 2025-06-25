package com.example.liftrix.data.mapper

import com.example.liftrix.data.remote.dto.ExerciseDto
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between Exercise domain model and Firestore DTO
 */
@Singleton
class ExerciseMapper @Inject constructor(
    private val exerciseSetMapper: ExerciseSetMapper
) {
    

    
    /**
     * Convert domain Exercise to Firestore DTO
     */
    fun toFirestoreDto(exercise: Exercise): ExerciseDto {
        return ExerciseDto(
            id = exercise.id.value,
            name = exercise.name,
            category = exercise.category.name,
            sets = exercise.sets.map { exerciseSetMapper.toFirestoreDto(it) },
            notes = exercise.notes,
            targetSets = exercise.targetSets,
            targetReps = exercise.targetReps?.count,
            targetWeightKg = exercise.targetWeight?.kilograms,
            createdAt = com.google.firebase.Timestamp(exercise.createdAt.epochSecond, exercise.createdAt.nano),
            updatedAt = com.google.firebase.Timestamp(exercise.updatedAt.epochSecond, exercise.updatedAt.nano)
        )
    }

    /**
     * Convert Firestore DTO to domain Exercise
     */
    fun fromFirestoreDto(dto: ExerciseDto): Exercise {
        return Exercise(
            id = ExerciseId(dto.id),
            name = dto.name,
            category = ExerciseCategory.valueOf(dto.category),
            sets = dto.sets.map { exerciseSetMapper.fromFirestoreDto(it) },
            notes = dto.notes,
            targetSets = dto.targetSets,
            targetReps = dto.targetReps?.let { Reps(it) },
            targetWeight = dto.targetWeightKg?.let { Weight(it) },
            createdAt = java.time.Instant.ofEpochSecond(dto.createdAt.seconds, dto.createdAt.nanoseconds.toLong()),
            updatedAt = java.time.Instant.ofEpochSecond(dto.updatedAt.seconds, dto.updatedAt.nanoseconds.toLong())
        )
    }
} 