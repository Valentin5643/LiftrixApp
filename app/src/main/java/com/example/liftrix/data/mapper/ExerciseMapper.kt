package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.remote.dto.ExerciseDto
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between Exercise domain model, Room entities, and Firestore DTOs
 */
@Singleton
class ExerciseMapper @Inject constructor(
    private val exerciseSetMapper: ExerciseSetMapper,
    private val exerciseRepository: ExerciseRepository
) {
    
    // === Firestore DTO Mapping Methods ===
    
    /**
     * Convert domain Exercise to Firestore DTO
     */
    fun toFirestoreDto(exercise: Exercise): ExerciseDto {
        return ExerciseDto(
            id = exercise.id.value,
            name = exercise.libraryExercise.name,
            category = exercise.libraryExercise.primaryMuscleGroup.name,
            sets = exercise.sets.map { exerciseSetMapper.toFirestoreDto(it) },
            notes = exercise.notes,
            targetSets = exercise.targetSets,
            targetReps = exercise.targetReps,
            targetWeightKg = exercise.targetWeight?.kilograms,
            createdAt = com.google.firebase.Timestamp(exercise.createdAt.epochSecond, exercise.createdAt.nano),
            updatedAt = com.google.firebase.Timestamp(System.currentTimeMillis() / 1000, 0)
        )
    }

    /**
     * Convert Firestore DTO to domain Exercise
     * Note: This method requires additional context for full conversion
     */
    fun fromFirestoreDto(dto: ExerciseDto, libraryExercise: ExerciseLibrary, workoutId: WorkoutId): Exercise {
        return Exercise(
            id = ExerciseId(dto.id),
            workoutId = workoutId,
            libraryExercise = libraryExercise,
            orderIndex = 0, // Will need to be set externally
            sets = dto.sets.map { exerciseSetMapper.fromFirestoreDto(it) },
            notes = dto.notes,
            targetSets = dto.targetSets,
            targetReps = dto.targetReps,
            targetWeight = dto.targetWeightKg?.let { Weight.fromKilograms(it) },
            createdAt = java.time.Instant.ofEpochSecond(dto.createdAt.seconds, dto.createdAt.nanoseconds.toLong())
        )
    }
    
    // === Room Entity Mapping Methods ===
    
    /**
     * Convert Room entity with sets to domain Exercise
     */
    suspend fun toDomain(entity: ExerciseEntity, setEntities: List<ExerciseSetEntity>): Exercise {
        // Get the library exercise
        val libraryExercise = getLibraryExerciseById(entity.exerciseLibraryId)
            ?: throw IllegalStateException("Exercise library entry not found: ${entity.exerciseLibraryId}")
        
        val sets = setEntities.map { exerciseSetMapper.toDomain(it) }
        
        return Exercise(
            id = ExerciseId(entity.id.toString()),
            workoutId = WorkoutId(entity.workoutId),
            libraryExercise = libraryExercise,
            orderIndex = entity.orderIndex,
            targetSets = entity.targetSets,
            targetReps = entity.targetReps,
            targetWeight = entity.targetWeightKg?.let { Weight.fromKilograms(it.toDouble()) },
            targetTime = entity.targetTimeSeconds?.let { Duration.ofSeconds(it.toLong()) },
            targetDistance = entity.targetDistanceMeters?.let { Distance.fromMeters(it) },
            sets = sets,
            notes = entity.notes,
            createdAt = Instant.ofEpochMilli(entity.createdAt)
        )
    }
    
    /**
     * Convert domain Exercise to Room entity
     */
    fun toEntity(exercise: Exercise): ExerciseEntity {
        return ExerciseEntity(
            id = 0, // Always use 0 for new exercises to let database auto-generate ID
            workoutId = exercise.workoutId.value,
            exerciseLibraryId = exercise.libraryExercise.id,
            orderIndex = exercise.orderIndex,
            targetSets = exercise.targetSets,
            targetReps = exercise.targetReps,
            targetWeightKg = exercise.targetWeight?.kilograms?.toFloat(),
            targetTimeSeconds = exercise.targetTime?.seconds?.toInt(),
            targetDistanceMeters = exercise.targetDistance?.meters,
            notes = exercise.notes,
            createdAt = exercise.createdAt.toEpochMilli(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Helper method to get library exercise by ID
     */
    private suspend fun getLibraryExerciseById(id: String): ExerciseLibrary? {
        return try {
            val result = exerciseRepository.getAllExercises().first()
            result.getOrNull()?.find { it.id == id }
        } catch (e: Exception) {
            null
        }
    }
}