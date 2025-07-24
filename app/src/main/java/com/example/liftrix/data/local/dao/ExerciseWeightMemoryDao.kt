package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.ExerciseWeightMemoryEntity

@Dao
interface ExerciseWeightMemoryDao {
    
    @Query("SELECT * FROM exercise_weight_memory WHERE user_id = :userId AND exercise_library_id = :exerciseId")
    suspend fun getLastWeight(userId: String, exerciseId: String): ExerciseWeightMemoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateWeight(weightMemory: ExerciseWeightMemoryEntity): Long
    
    @Query("UPDATE exercise_weight_memory SET usage_count = usage_count + 1, last_used_at = :timestamp WHERE user_id = :userId AND exercise_library_id = :exerciseId")
    suspend fun incrementUsageCount(userId: String, exerciseId: String, timestamp: Long): Int
    
    @Query("SELECT * FROM exercise_weight_memory WHERE user_id = :userId ORDER BY last_used_at DESC")
    suspend fun getWeightMemoryForUser(userId: String): List<ExerciseWeightMemoryEntity>
    
    @Query("SELECT * FROM exercise_weight_memory WHERE user_id = :userId ORDER BY usage_count DESC LIMIT :limit")
    suspend fun getMostUsedExercises(userId: String, limit: Int): List<ExerciseWeightMemoryEntity>
    
    @Query("DELETE FROM exercise_weight_memory WHERE user_id = :userId AND exercise_library_id = :exerciseId")
    suspend fun deleteWeightMemory(userId: String, exerciseId: String): Int
    
    @Query("DELETE FROM exercise_weight_memory WHERE user_id = :userId")
    suspend fun deleteAllWeightMemoryForUser(userId: String): Int
}