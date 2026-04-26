package com.example.liftrix.data.repository

import android.net.Uri
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.mapper.CustomExerciseMapper
import androidx.room.withTransaction
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.remote.legacy.LegacyCustomExerciseFirestoreDataSource
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
    private val legacyDataSource: LegacyCustomExerciseFirestoreDataSource,
    private val mediaUploadService: MediaUploadService
) : CustomExerciseRepository {
    
    // Repository-scoped coroutine scope for structured concurrency
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
    }
    
    override suspend fun createCustomExercise(
        userId: String,
        name: String,
        description: String?,
        exerciseType: ExerciseType,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory>,
        difficulty: Int?,
        instructions: List<String>,
        mainImage: Uri?,
        additionalImages: List<Uri>,
        videoUrl: String?,
        tags: List<String>,
        categories: List<ExerciseCategory>,
        notes: String?
    ): Result<CustomExercise> = withContext(Dispatchers.IO) {
        try {
            // Upload images first if provided
            val mainImageUrl = mainImage?.let { uri ->
                val uploadResult = mediaUploadService.uploadImage(uri, "exercises/$userId")
                uploadResult.fold(
                    onSuccess = { it },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }
            
            val additionalImageUrls = mutableListOf<String>()
            for (imageUri in additionalImages) {
                val uploadResult = mediaUploadService.uploadImage(imageUri, "exercises/$userId")
                uploadResult.fold(
                    onSuccess = { additionalImageUrls.add(it) },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }
            
            return@withContext createCustomExerciseWithUrls(
                userId = userId,
                name = name,
                description = description,
                exerciseType = exerciseType,
                primaryMuscle = primaryMuscle,
                equipment = equipment,
                secondaryMuscles = secondaryMuscles,
                difficulty = difficulty,
                instructions = instructions,
                mainImageUrl = mainImageUrl,
                additionalImageUrls = additionalImageUrls,
                videoUrl = videoUrl,
                tags = tags,
                categories = categories,
                notes = notes
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create custom exercise with image uploads")
            Result.failure(e)
        }
    }
    
    override suspend fun createCustomExerciseWithUrls(
        userId: String,
        name: String,
        description: String?,
        exerciseType: ExerciseType,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory>,
        difficulty: Int?,
        instructions: List<String>,
        mainImageUrl: String?,
        additionalImageUrls: List<String>,
        videoUrl: String?,
        tags: List<String>,
        categories: List<ExerciseCategory>,
        notes: String?
    ): Result<CustomExercise> = withContext(Dispatchers.IO) {
        try {
            // Create entity with all new fields
            val entity = mapper.createEntity(
                userId = userId,
                name = name,
                description = description,
                exerciseType = exerciseType,
                primaryMuscle = primaryMuscle,
                equipment = equipment,
                secondaryMuscles = secondaryMuscles,
                difficulty = difficulty,
                instructions = instructions,
                mainImageUrl = mainImageUrl,
                additionalImageUrls = additionalImageUrls,
                videoUrl = videoUrl,
                tags = tags,
                categories = categories,
                notes = notes,
                isSynced = false
            )
            
            // Insert to local database
            if (OfflineArchitectureFlags.FIX_CUSTOM_EXERCISE_REPOSITORY) {
                dao.upsertLocal(entity)
            } else {
                dao.insertCustomExercise(entity)
            }
            
            // Return the domain model
            val result = mapper.toDomain(entity)
            
            // Sync to Firestore in background (outside transaction)
            try {
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
            if (OfflineArchitectureFlags.FIX_CUSTOM_EXERCISE_REPOSITORY) {
                dao.upsertLocal(updatedEntity)
            } else {
                val rowsUpdated = dao.updateCustomExercise(updatedEntity)
                if (rowsUpdated == 0) {
                    return@withContext Result.failure(IllegalStateException("Failed to update custom exercise"))
                }
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
    
    override fun getCustomExercisesByType(
        userId: String,
        exerciseType: ExerciseType
    ): Flow<List<CustomExercise>> {
        return dao.getCustomExercisesByType(userId, exerciseType)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getCustomExercisesByTag(
        userId: String,
        tag: String
    ): Flow<List<CustomExercise>> {
        return dao.getCustomExercisesByTag(userId, tag)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override suspend fun updateMainImage(
        userId: String,
        exerciseId: CustomExerciseId,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Upload new image
            val uploadResult = mediaUploadService.uploadImage(imageUri, "exercises/$userId")
            val imageUrl = uploadResult.fold(
                onSuccess = { it },
                onFailure = { return@withContext Result.failure(it) }
            )
            
            // Update database
            dao.updateMainImage(exerciseId.value, userId, imageUrl)
            
            Result.success(imageUrl)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update main image")
            Result.failure(e)
        }
    }
    
    override suspend fun updateAdditionalImages(
        userId: String,
        exerciseId: CustomExerciseId,
        imageUris: List<Uri>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val imageUrls = mutableListOf<String>()
            
            // Upload all images
            for (imageUri in imageUris) {
                val uploadResult = mediaUploadService.uploadImage(imageUri, "exercises/$userId")
                val imageUrl = uploadResult.fold(
                    onSuccess = { it },
                    onFailure = { return@withContext Result.failure(it) }
                )
                imageUrls.add(imageUrl)
            }
            
            // Update database with JSON array of URLs
            val imageUrlsJson = if (imageUrls.isNotEmpty()) {
                com.google.gson.Gson().toJson(imageUrls)
            } else null
            
            dao.updateAdditionalImages(exerciseId.value, userId, imageUrlsJson)
            
            Result.success(imageUrls)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update additional images")
            Result.failure(e)
        }
    }
    
    override suspend fun getCustomExerciseCount(userId: String): Int {
        return dao.getCustomExerciseCount(userId)
    }
    
    override suspend fun getCustomExerciseCountByType(
        userId: String,
        exerciseType: ExerciseType
    ): Int {
        return dao.getCustomExerciseCountByType(userId, exerciseType)
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
        if (OfflineArchitectureFlags.FIX_CUSTOM_EXERCISE_REPOSITORY) {
            return
        }

        try {
            legacyDataSource.syncExercise(entity)
            repositoryScope.launch {
                try {
                    dao.markCustomExercisesAsSynced(listOf(entity.id))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to mark exercise as synced")
                }
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
        if (OfflineArchitectureFlags.FIX_CUSTOM_EXERCISE_REPOSITORY) {
            return
        }

        try {
            legacyDataSource.deleteExercise(exerciseId)
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
            if (OfflineArchitectureFlags.FIX_CUSTOM_EXERCISE_REPOSITORY) {
                return@withContext Result.success(Unit)
            }

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
