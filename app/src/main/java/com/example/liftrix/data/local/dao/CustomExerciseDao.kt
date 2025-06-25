package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for custom exercises with user-scoped operations
 */
@Dao
interface CustomExerciseDao {
    
    /**
     * Insert a new custom exercise
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomExercise(customExercise: CustomExerciseEntity): Long
    
    /**
     * Get all custom exercises for a specific user
     */
    @Query("SELECT * FROM custom_exercises WHERE user_id = :userId ORDER BY name ASC")
    fun getAllCustomExercisesForUser(userId: String): Flow<List<CustomExerciseEntity>>
    
    /**
     * Get a specific custom exercise by ID and user ID
     */
    @Query("SELECT * FROM custom_exercises WHERE id = :customExerciseId AND user_id = :userId")
    suspend fun getCustomExerciseById(customExerciseId: String, userId: String): CustomExerciseEntity?
    
    /**
     * Search custom exercises by name for a specific user
     */
    @Query("""
        SELECT * FROM custom_exercises 
        WHERE user_id = :userId 
        AND name LIKE '%' || :searchQuery || '%'
        ORDER BY name ASC
    """)
    fun searchCustomExercises(userId: String, searchQuery: String): Flow<List<CustomExerciseEntity>>
    
    /**
     * Get custom exercises by muscle group for a specific user
     */
    @Query("""
        SELECT * FROM custom_exercises 
        WHERE user_id = :userId 
        AND (primary_muscle_group = :muscleGroup 
             OR secondary_muscle_groups LIKE '%' || :muscleGroup || '%')
        ORDER BY name ASC
    """)
    fun getCustomExercisesByMuscleGroup(userId: String, muscleGroup: ExerciseCategory): Flow<List<CustomExerciseEntity>>
    
    /**
     * Get custom exercises by equipment for a specific user
     */
    @Query("SELECT * FROM custom_exercises WHERE user_id = :userId AND equipment = :equipment ORDER BY name ASC")
    fun getCustomExercisesByEquipment(userId: String, equipment: Equipment): Flow<List<CustomExerciseEntity>>
    
    /**
     * Update an existing custom exercise
     */
    @Update
    suspend fun updateCustomExercise(customExercise: CustomExerciseEntity): Int
    
    /**
     * Delete a custom exercise by ID (user-scoped)
     */
    @Query("DELETE FROM custom_exercises WHERE id = :customExerciseId AND user_id = :userId")
    suspend fun deleteCustomExercise(customExerciseId: String, userId: String): Int
    
    /**
     * Delete a custom exercise entity
     */
    @Delete
    suspend fun deleteCustomExercise(customExercise: CustomExerciseEntity): Int
    
    /**
     * Check if a custom exercise name exists for a user
     */
    @Query("SELECT EXISTS(SELECT 1 FROM custom_exercises WHERE user_id = :userId AND name = :name)")
    suspend fun doesCustomExerciseNameExist(userId: String, name: String): Boolean
    
    /**
     * Get unsynced custom exercises for a user
     */
    @Query("SELECT * FROM custom_exercises WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedCustomExercises(userId: String): List<CustomExerciseEntity>
    
    /**
     * Mark custom exercises as synced
     */
    @Query("UPDATE custom_exercises SET is_synced = 1 WHERE id IN (:exerciseIds)")
    suspend fun markCustomExercisesAsSynced(exerciseIds: List<String>): Int
    
    /**
     * Get count of custom exercises for a user
     */
    @Query("SELECT COUNT(*) FROM custom_exercises WHERE user_id = :userId")
    suspend fun getCustomExerciseCount(userId: String): Int
} 