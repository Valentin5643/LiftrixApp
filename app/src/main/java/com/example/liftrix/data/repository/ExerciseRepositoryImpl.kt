package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseWeightMemoryDao
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.ExerciseWeightMemoryEntity
import com.example.liftrix.data.mapper.ExerciseMapper
import com.example.liftrix.data.mapper.ExerciseSetMapper
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for exercise-related operations with enhanced support for
 * flexible metrics, weight memory, and exercise history tracking
 */
@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val exerciseWeightMemoryDao: ExerciseWeightMemoryDao,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val exerciseMapper: ExerciseMapper,
    private val exerciseSetMapper: ExerciseSetMapper
) : ExerciseRepository {

    override suspend fun saveExercise(exercise: Exercise): Result<Exercise> {
        return try {
            val entity = exerciseMapper.toEntity(exercise)
            val id = exerciseDao.insertExercise(entity)
            
            // Save all sets
            val setEntities = exercise.sets.map { exerciseSetMapper.toEntity(it, id) }
            exerciseSetDao.insertSets(setEntities)
            
            Result.success(exercise.copy(id = ExerciseId(id.toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExercisesByWorkout(workoutId: WorkoutId): Result<List<Exercise>> {
        return try {
            val exerciseEntities = exerciseDao.getExercisesByWorkout(workoutId.value.toLong())
            val exercises = exerciseEntities.map { entity ->
                val sets = exerciseSetDao.getSetsByExercise(entity.id)
                exerciseMapper.toDomain(entity, sets)
            }
            Result.success(exercises)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateExercise(exercise: Exercise): Result<Exercise> {
        return try {
            val entity = exerciseMapper.toEntity(exercise)
            exerciseDao.updateExercise(entity)
            
            // Replace all sets for the exercise
            val setEntities = exercise.sets.map { 
                exerciseSetMapper.toEntity(it, exercise.id.value.toLong()) 
            }
            exerciseSetDao.replaceAllSetsForExercise(exercise.id.value.toLong(), setEntities)
            
            Result.success(exercise)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExercise(exerciseId: ExerciseId): Result<Unit> {
        return try {
            exerciseDao.deleteExerciseById(exerciseId.value.toLong())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveExerciseSet(exerciseId: ExerciseId, set: ExerciseSet): Result<ExerciseSet> {
        return try {
            val entity = exerciseSetMapper.toEntity(set, exerciseId.value.toLong())
            val id = exerciseSetDao.insertSet(entity)
            Result.success(set.copy(id = ExerciseSetId(id.toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateExerciseSet(set: ExerciseSet): Result<ExerciseSet> {
        return try {
            val entity = exerciseSetMapper.toEntity(set, 0) // exerciseId will be preserved in update
            exerciseSetDao.updateSet(entity)
            Result.success(set)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExerciseSet(setId: ExerciseSetId): Result<Unit> {
        return try {
            exerciseSetDao.deleteSetById(setId.value.toLong())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLastUsedWeight(userId: UserId, exerciseLibraryId: String): Result<Weight?> {
        return try {
            val memory = exerciseWeightMemoryDao.getLastWeight(userId.value, exerciseLibraryId)
            val weight = memory?.lastWeightKg?.let { Weight.fromKilograms(it.toDouble()) }
            Result.success(weight)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateWeightMemory(userId: UserId, exerciseLibraryId: String, weight: Weight): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            val entity = ExerciseWeightMemoryEntity(
                userId = userId.value,
                exerciseLibraryId = exerciseLibraryId,
                lastWeightKg = weight.kilograms.toFloat(),
                lastUsedAt = timestamp,
                usageCount = 1
            )
            exerciseWeightMemoryDao.updateWeight(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExerciseHistory(userId: UserId, exerciseLibraryId: String, limit: Int): Result<List<ExerciseSet>> {
        return try {
            val setEntities = exerciseSetDao.getExerciseHistory(userId.value, exerciseLibraryId, limit)
            val sets = setEntities.map { exerciseSetMapper.toDomain(it) }
            Result.success(sets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExerciseWithSets(exerciseId: ExerciseId): Result<Exercise?> {
        return try {
            val entity = exerciseDao.getExerciseById(exerciseId.value.toLong())
            if (entity == null) {
                Result.success(null)
            } else {
                val sets = exerciseSetDao.getSetsByExercise(entity.id)
                val exercise = exerciseMapper.toDomain(entity, sets)
                Result.success(exercise)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExercisesByType(workoutId: WorkoutId, exerciseType: ExerciseType): Result<List<Exercise>> {
        return try {
            val allExercises = getExercisesByWorkout(workoutId).getOrThrow()
            val filteredExercises = allExercises.filter { it.exerciseType == exerciseType }
            Result.success(filteredExercises)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}