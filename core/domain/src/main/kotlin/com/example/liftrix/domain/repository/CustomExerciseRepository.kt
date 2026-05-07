package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for custom exercise operations
 */
interface CustomExerciseRepository {
    
    /**
     * Creates a new custom exercise for the user with image upload support
     */
    suspend fun createCustomExercise(
        userId: String,
        name: String,
        description: String? = null,
        exerciseType: ExerciseType,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory> = emptySet(),
        difficulty: Int? = null,
        instructions: List<String> = emptyList(),
        mainImage: Any? = null,
        additionalImages: List<Any> = emptyList(),
        videoUrl: String? = null,
        tags: List<String> = emptyList(),
        categories: List<ExerciseCategory> = emptyList(),
        notes: String? = null
    ): Result<CustomExercise>
    
    /**
     * Creates a new custom exercise with already uploaded image URLs
     */
    suspend fun createCustomExerciseWithUrls(
        userId: String,
        name: String,
        description: String? = null,
        exerciseType: ExerciseType,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory> = emptySet(),
        difficulty: Int? = null,
        instructions: List<String> = emptyList(),
        mainImageUrl: String? = null,
        additionalImageUrls: List<String> = emptyList(),
        videoUrl: String? = null,
        tags: List<String> = emptyList(),
        categories: List<ExerciseCategory> = emptyList(),
        notes: String? = null
    ): Result<CustomExercise>
    
    /**
     * Gets all custom exercises for a user
     */
    fun getAllCustomExercises(userId: String): Flow<List<CustomExercise>>
    
    /**
     * Gets custom exercises by muscle group
     */
    fun getCustomExercisesByMuscleGroup(
        userId: String, 
        muscleGroup: ExerciseCategory
    ): Flow<List<CustomExercise>>
    
    /**
     * Gets custom exercises by equipment
     */
    fun getCustomExercisesByEquipment(
        userId: String,
        equipment: Equipment
    ): Flow<List<CustomExercise>>
    
    /**
     * Searches custom exercises by name
     */
    fun searchCustomExercises(
        userId: String,
        query: String
    ): Flow<List<CustomExercise>>
    
    /**
     * Gets a specific custom exercise by ID
     */
    suspend fun getCustomExercise(
        userId: String,
        exerciseId: CustomExerciseId
    ): Result<CustomExercise>
    
    /**
     * Updates an existing custom exercise
     */
    suspend fun updateCustomExercise(
        userId: String,
        exercise: CustomExercise
    ): Result<CustomExercise>
    
    /**
     * Deletes a custom exercise
     */
    suspend fun deleteCustomExercise(
        userId: String,
        exerciseId: CustomExerciseId
    ): Result<Unit>
    
    /**
     * Gets custom exercises by exercise type
     */
    fun getCustomExercisesByType(
        userId: String,
        exerciseType: ExerciseType
    ): Flow<List<CustomExercise>>
    
    /**
     * Searches custom exercises by tags
     */
    fun getCustomExercisesByTag(
        userId: String,
        tag: String
    ): Flow<List<CustomExercise>>
    
    /**
     * Updates the main image of a custom exercise
     */
    suspend fun updateMainImage(
        userId: String,
        exerciseId: CustomExerciseId,
        imageUri: Any
    ): Result<String>
    
    /**
     * Updates additional images of a custom exercise
     */
    suspend fun updateAdditionalImages(
        userId: String,
        exerciseId: CustomExerciseId,
        imageUris: List<Any>
    ): Result<List<String>>
    
    /**
     * Gets count of custom exercises for a user
     */
    suspend fun getCustomExerciseCount(userId: String): Int
    
    /**
     * Gets count of custom exercises by type for a user
     */
    suspend fun getCustomExerciseCountByType(
        userId: String,
        exerciseType: ExerciseType
    ): Int
    
    /**
     * Checks if a custom exercise name is unique for the user
     */
    suspend fun isExerciseNameUnique(
        userId: String,
        name: String,
        excludeId: CustomExerciseId? = null
    ): Boolean
} 
