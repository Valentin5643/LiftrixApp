package com.example.liftrix.data.service

import com.example.liftrix.data.mapper.ExerciseSetMapper
import com.example.liftrix.data.remote.dto.ExerciseDto
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for converting Firebase ExerciseDto objects to domain Exercise objects
 * Handles the complex conversion logic that requires looking up library exercises
 */
@Singleton
class ExerciseConversionService @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val exerciseSetMapper: ExerciseSetMapper
) {

    /**
     * Convert a list of Firebase ExerciseDto objects to domain Exercise objects
     * This is the main method called by WorkoutMapper for Firebase exercise conversion.
     */
    suspend fun convertFirebaseExercisesToDomain(
        exerciseDtos: List<ExerciseDto>,
        workoutId: WorkoutId
    ): List<Exercise> {
        if (exerciseDtos.isEmpty()) {
            Timber.d("ExerciseConversionService: No exercises to convert")
            return emptyList()
        }

        Timber.d("ExerciseConversionService: Converting ${exerciseDtos.size} Firebase exercises to domain")

        val convertedExercises = mutableListOf<Exercise>()

        exerciseDtos.forEachIndexed { index, dto ->
            try {
                val convertedExercise = convertSingleExercise(dto, workoutId, index)
                if (convertedExercise != null) {
                    convertedExercises.add(convertedExercise)
                    Timber.d("ExerciseConversionService: Successfully converted '${dto.name}' with ${dto.sets.size} sets")
                } else {
                    Timber.w("ExerciseConversionService: Failed to convert '${dto.name}' - library exercise not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "ExerciseConversionService: Error converting exercise '${dto.name}'")
            }
        }

        Timber.d("ExerciseConversionService: Successfully converted ${convertedExercises.size}/${exerciseDtos.size} exercises")
        return convertedExercises
    }

    /**
     * Convert a single Firebase ExerciseDto to domain Exercise
     */
    private suspend fun convertSingleExercise(
        dto: ExerciseDto,
        workoutId: WorkoutId,
        orderIndex: Int
    ): Exercise? {
        // Look up the library exercise by name since ExerciseDto only has name/category
        val libraryExercise = findLibraryExercise(dto.name, dto.category)
            ?: return null

        // Convert the exercise sets
        val convertedSets = dto.sets.mapNotNull { setDto ->
            try {
                exerciseSetMapper.fromFirestoreDto(setDto)
            } catch (e: Exception) {
                Timber.w(e, "ExerciseConversionService: Failed to convert set ${setDto.setNumber} for '${dto.name}'")
                null
            }
        }

        // Normalize set numbers to ensure they are sequential
        val normalizedSets = convertedSets.sortedBy { it.setNumber }
            .mapIndexed { index, set -> set.copy(setNumber = index + 1) }

        return try {
            Exercise.createSafe(
                id = ExerciseId.generate(),
                workoutId = workoutId,
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                targetSets = dto.targetSets,
                targetReps = dto.targetReps,
                targetWeight = dto.targetWeightKg?.let { Weight.fromKilograms(it) },
                sets = normalizedSets,
                notes = dto.notes,
                createdAt = Instant.ofEpochSecond(dto.createdAt.seconds, dto.createdAt.nanoseconds.toLong())
            )
        } catch (e: Exception) {
            Timber.e(e, "ExerciseConversionService: Failed to create domain Exercise for '${dto.name}'")
            null
        }
    }

    /**
     * Find a library exercise by name and category
     * Uses fuzzy matching to handle slight variations in naming
     */
    private suspend fun findLibraryExercise(name: String, category: String): ExerciseLibrary? {
        return try {
            val result = exerciseRepository.getAllExercises().first()
            val allExercises = result.fold(
                onSuccess = { it },
                onFailure = { error ->
                    Timber.e("ExerciseConversionService: Failed to fetch exercise library: $error")
                    return null
                }
            )

            // First try exact name match
            var match = allExercises.find { it.name.equals(name, ignoreCase = true) }
            if (match != null) {
                return match
            }

            // Then try fuzzy matching with category consideration
            val categoryMatches = allExercises.filter {
                it.primaryMuscleGroup.name.equals(category, ignoreCase = true) ||
                it.secondaryMuscleGroups.any { secondary -> secondary.name.equals(category, ignoreCase = true) }
            }

            // Look for best fuzzy match within the category
            match = categoryMatches.maxByOrNull { it.calculateMatchScore(name) }
            if (match != null && match.calculateMatchScore(name) > 0.5) {
                return match
            }

            // Last resort: best fuzzy match across all exercises
            match = allExercises.maxByOrNull { it.calculateMatchScore(name) }
            if (match != null && match.calculateMatchScore(name) > 0.3) {
                Timber.w("ExerciseConversionService: Using fuzzy match '${match.name}' for '${name}'")
                return match
            }

            // If no good match found, create a placeholder warning
            Timber.w("ExerciseConversionService: No suitable library exercise found for '$name' in category '$category'")
            null
        } catch (e: Exception) {
            Timber.e(e, "ExerciseConversionService: Error searching for library exercise '$name'")
            null
        }
    }

    /**
     * Convert domain exercises back to Firebase DTOs
     * Used for uploading local changes to Firebase
     */
    fun convertDomainExercisesToFirebase(exercises: List<Exercise>): List<ExerciseDto> {
        return exercises.map { exercise ->
            ExerciseDto(
                id = exercise.id.value,
                name = exercise.libraryExercise.name,
                category = exercise.libraryExercise.primaryMuscleGroup.name,
                sets = exercise.sets.map { exerciseSetMapper.toFirestoreDto(it) },
                notes = exercise.notes,
                targetSets = exercise.targetSets,
                targetReps = exercise.targetReps,
                targetWeightKg = exercise.targetWeight?.kilograms,
                createdAt = com.google.firebase.Timestamp(exercise.createdAt.epochSecond, exercise.createdAt.nano),
                updatedAt = com.google.firebase.Timestamp.now()
            )
        }
    }

    /**
     * Validate conversion integrity - used for testing and debugging
     */
    suspend fun validateConversion(originalDtos: List<ExerciseDto>, convertedExercises: List<Exercise>): Boolean {
        if (originalDtos.size != convertedExercises.size) {
            Timber.w("ExerciseConversionService: Size mismatch - original: ${originalDtos.size}, converted: ${convertedExercises.size}")
            return false
        }

        var isValid = true
        originalDtos.forEachIndexed { index, dto ->
            val converted = convertedExercises.getOrNull(index)
            if (converted == null) {
                Timber.w("ExerciseConversionService: Missing converted exercise at index $index")
                isValid = false
                return@forEachIndexed
            }

            // Validate key properties are preserved
            if (dto.sets.size != converted.sets.size) {
                Timber.w("ExerciseConversionService: Set count mismatch for '${dto.name}' - original: ${dto.sets.size}, converted: ${converted.sets.size}")
                isValid = false
            }

            if (dto.targetSets != converted.targetSets) {
                Timber.w("ExerciseConversionService: Target sets mismatch for '${dto.name}' - original: ${dto.targetSets}, converted: ${converted.targetSets}")
                isValid = false
            }
        }

        return isValid
    }
}
