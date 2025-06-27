package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseUsageHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: ExerciseUsageHistoryEntity): Long
    
    @Query("""
        SELECT weight_used 
        FROM exercise_usage_history 
        WHERE user_id = :userId AND exercise_id = :exerciseId 
        ORDER BY used_at DESC 
        LIMIT 1
    """)
    suspend fun getLastUsedWeight(userId: String, exerciseId: String): Float?
    
    @Query("""
        SELECT * 
        FROM exercise_usage_history 
        WHERE user_id = :userId 
        ORDER BY used_at DESC 
        LIMIT :limit
    """)
    suspend fun getRecentExercises(userId: String, limit: Int): List<ExerciseUsageHistoryEntity>
    
    @Query("""
        SELECT DISTINCT exercise_id 
        FROM exercise_usage_history 
        WHERE user_id = :userId 
        ORDER BY used_at DESC 
        LIMIT :limit
    """)
    suspend fun getRecentExerciseIds(userId: String, limit: Int): List<String>
    
    @Query("""
        SELECT * 
        FROM exercise_usage_history 
        WHERE user_id = :userId AND exercise_id = :exerciseId 
        ORDER BY used_at DESC 
        LIMIT :limit
    """)
    suspend fun getExerciseHistory(userId: String, exerciseId: String, limit: Int): List<ExerciseUsageHistoryEntity>
    
    @Query("""
        SELECT AVG(weight_used) 
        FROM exercise_usage_history 
        WHERE user_id = :userId AND exercise_id = :exerciseId 
        AND used_at >= datetime('now', '-30 days')
    """)
    suspend fun getAverageWeightLast30Days(userId: String, exerciseId: String): Float?
    
    @Query("""
        SELECT COUNT(*) 
        FROM exercise_usage_history 
        WHERE user_id = :userId AND exercise_id = :exerciseId
    """)
    suspend fun getExerciseUsageCount(userId: String, exerciseId: String): Int
    
    @Query("""
        DELETE FROM exercise_usage_history 
        WHERE user_id = :userId
    """)
    suspend fun deleteAllForUser(userId: String): Int
} 