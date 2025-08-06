package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.mapper.CustomExerciseMapper
import androidx.room.withTransaction
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for custom exercises with local-first storage and Firestore sync
 */
@Singleton
class CustomExerciseRepositoryImpl @Inject constructor(
    private val database: LiftrixDatabase,
    private val dao: CustomExerciseDao,
    private val mapper: CustomExerciseMapper,
    private val firestore: FirebaseFirestore
) : CustomExerciseRepository {
    
    // Repository-scoped coroutine scope for structured concurrency
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val COLLECTION_CUSTOM_EXERCISES = "custom_exercises"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_NAME = "name"
        private const val FIELD_CREATED_AT = "createdAt"
    }
    
    override suspend fun createCustomExercise(
        userId: String,
        name: String,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory>,
        difficulty: Int?,
        notes: String?
    ): Result<CustomExercise> = withContext(Dispatchers.IO) {
        try {
            // Create entity
            val entity = mapper.createEntity(
                userId = userId,
                name = name,
                primaryMuscle = primaryMuscle,
                equipment = equipment,
                secondaryMuscles = secondaryMuscles,
                difficulty = difficulty,
                notes = notes,
                isSynced = false
            )
            
            // Insert to local database
            dao.insertCustomExercise(entity)
            
            // Return the domain model
            val result = mapper.toDomain(entity)
            
            // Sync to Firestore in background (outside transaction)
            try {
                val entity = mapper.createEntity(
                    userId = userId,
                    name = name,
                    primaryMuscle = primaryMuscle,
                    equipment = equipment,
                    secondaryMuscles = secondaryMuscles,
                    difficulty = difficulty,
                    notes = notes,
                    isSynced = false
                )
                syncToFirestore(entity)
            } catch (e: Exception) {
                Timber.w(e, "Failed to sync custom exercise to Firestore, will retry later")
            }
            
            Result.success(result)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create custom exercise")
            Result.failure(e)
        }
    }
    
    override fun getAllCustomExercises(userId: String): Flow<List<CustomExercise>> {
        return dao.getAllCustomExercisesForUser(userId)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getCustomExercisesByMuscleGroup(
        userId: String,
        muscleGroup: ExerciseCategory
    ): Flow<List<CustomExercise>> {
        return dao.getCustomExercisesByMuscleGroup(userId, muscleGroup)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getCustomExercisesByEquipment(
        userId: String,
        equipment: Equipment
    ): Flow<List<CustomExercise>> {
        return dao.getCustomExercisesByEquipment(userId, equipment)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun searchCustomExercises(
        userId: String,
        query: String
    ): Flow<List<CustomExercise>> {
        return dao.searchCustomExercises(userId, query)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override suspend fun getCustomExercise(
        userId: String,
        exerciseId: CustomExerciseId
    ): Result<CustomExercise> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getCustomExerciseById(exerciseId.value, userId)
            if (entity != null) {
                Result.success(mapper.toDomain(entity))
            } else {
                Result.failure(NoSuchElementException("Custom exercise not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get custom exercise")
            Result.failure(e)
        }
    }
    
    override suspend fun updateCustomExercise(
        userId: String,
        exercise: CustomExercise
    ): Result<CustomExercise> = withContext(Dispatchers.IO) {
        try {
            // Get existing entity to preserve sync information
            val existingEntity = dao.getCustomExerciseById(exercise.id.value, userId)
                ?: return@withContext Result.failure(NoSuchElementException("Custom exercise not found"))
            
            // Update entity with new data
            val updatedEntity = mapper.updateEntity(existingEntity, exercise)
            
            // Update in local database
            val rowsUpdated = dao.updateCustomExercise(updatedEntity)
            if (rowsUpdated == 0) {
                return@withContext Result.failure(IllegalStateException("Failed to update custom exercise"))
            }
            
            // Convert to domain model
            val updatedDomain = mapper.toDomain(updatedEntity)
            
            // Sync to Firestore in background
            try {
                syncToFirestore(updatedEntity)
            } catch (e: Exception) {
                Timber.w(e, "Failed to sync updated custom exercise to Firestore")
            }
            
            Result.success(updatedDomain)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update custom exercise")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteCustomExercise(
        userId: String,
        exerciseId: CustomExerciseId
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Delete from local database
            val rowsDeleted = dao.deleteCustomExercise(exerciseId.value, userId)
            if (rowsDeleted == 0) {
                return@withContext Result.failure(NoSuchElementException("Custom exercise not found"))
            }
            
            // Delete from Firestore in background
            try {
                deleteFromFirestore(userId, exerciseId.value)
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete custom exercise from Firestore")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete custom exercise")
            Result.failure(e)
        }
    }
    
    override suspend fun isExerciseNameUnique(
        userId: String,
        name: String,
        excludeId: CustomExerciseId?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val exists = dao.doesCustomExerciseNameExist(userId, name)
            if (!exists) {
                return@withContext true
            }
            
            // If excludeId is provided, check if the existing exercise is the one being excluded
            excludeId?.let { id ->
                val existingExercise = dao.getCustomExerciseById(id.value, userId)
                return@withContext existingExercise?.name?.equals(name, ignoreCase = true) == true
            }
            
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to check exercise name uniqueness")
            false
        }
    }
    
    /**
     * Syncs a custom exercise to Firestore
     */
    private suspend fun syncToFirestore(entity: com.example.liftrix.data.local.entity.CustomExerciseEntity) {
        try {
            val firestoreData = mapOf(
                "id" to entity.id,
                FIELD_USER_ID to entity.userId,
                FIELD_NAME to entity.name,
                "primaryMuscleGroup" to entity.primaryMuscleGroup.name,
                "equipment" to entity.equipment.name,
                "secondaryMuscleGroups" to (entity.secondaryMuscleGroups?.map { it.name } ?: emptyList()),
                "difficulty" to entity.difficulty,
                "notes" to entity.notes,
                FIELD_CREATED_AT to entity.createdAt,
                "updatedAt" to entity.updatedAt,
                "syncVersion" to entity.syncVersion
            )
            
            firestore.collection(COLLECTION_CUSTOM_EXERCISES)
                .document(entity.id)
                .set(firestoreData)
                .addOnSuccessListener {
                    Timber.d("Successfully synced custom exercise ${entity.id} to Firestore")
                    // Mark as synced in local database using repository scope for structured concurrency
                    repositoryScope.launch {
                        try {
                            dao.markCustomExercisesAsSynced(listOf(entity.id))
                            Timber.d("Marked exercise ${entity.id} as synced")
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to mark exercise as synced")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "Failed to sync custom exercise ${entity.id} to Firestore")
                }
                
        } catch (e: Exception) {
            Timber.e(e, "Error syncing custom exercise to Firestore")
            throw e
        }
    }
    
    /**
     * Deletes a custom exercise from Firestore
     */
    private suspend fun deleteFromFirestore(userId: String, exerciseId: String) {
        try {
            firestore.collection(COLLECTION_CUSTOM_EXERCISES)
                .document(exerciseId)
                .delete()
                .addOnSuccessListener {
                    Timber.d("Successfully deleted custom exercise $exerciseId from Firestore")
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "Failed to delete custom exercise $exerciseId from Firestore")
                }
                
        } catch (e: Exception) {
            Timber.e(e, "Error deleting custom exercise from Firestore")
            throw e
        }
    }
    
    /**
     * Syncs unsynced custom exercises to Firestore
     */
    suspend fun syncUnsyncedExercises(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val unsyncedExercises = dao.getUnsyncedCustomExercises(userId)
            
            for (exercise in unsyncedExercises) {
                try {
                    syncToFirestore(exercise)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to sync exercise ${exercise.id}")
                }
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync unsynced exercises")
            Result.failure(e)
        }
    }
} 