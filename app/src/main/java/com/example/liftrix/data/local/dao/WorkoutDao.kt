package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY date DESC, created_at DESC")
    fun getAllWorkoutsForUser(userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun getWorkoutByIdForUser(id: String, userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE date = :date AND user_id = :userId ORDER BY created_at DESC")
    fun getWorkoutsByDateForUser(date: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE is_synced = 0 AND user_id = :userId ORDER BY updated_at ASC")
    suspend fun getUnsyncedWorkoutsForUser(userId: String): List<WorkoutEntity>
    
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0 AND user_id = :userId")
    suspend fun getUnsyncedCountForUser(userId: String): Int
    
    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' AND user_id = :userId ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveWorkoutForUser(userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE template_id = :templateId AND user_id = :userId ORDER BY created_at DESC")
    fun getWorkoutsFromTemplateForUser(templateId: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>)
    
    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)
    
    @Query("UPDATE workouts SET is_synced = :isSynced, sync_version = :version WHERE id = :id AND user_id = :userId")
    suspend fun updateSyncStatusForUser(id: String, userId: String, isSynced: Boolean, version: Long = System.currentTimeMillis())
    
    @Query("UPDATE workouts SET is_synced = 1, sync_version = :version WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markWorkoutsAsSyncedForUser(ids: List<String>, userId: String, version: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)
    
    @Query("DELETE FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun deleteWorkoutByIdForUser(id: String, userId: String)
    
    @Query("DELETE FROM workouts WHERE user_id = :userId")
    suspend fun deleteAllWorkoutsForUser(userId: String)
    
    // Legacy methods without user filtering - deprecated for migration
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts ORDER BY date DESC, created_at DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: String): WorkoutEntity?
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE date = :date ORDER BY created_at DESC")
    fun getWorkoutsByDate(date: String): Flow<List<WorkoutEntity>>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE is_synced = 0 ORDER BY updated_at ASC")
    suspend fun getUnsyncedWorkouts(): List<WorkoutEntity>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("UPDATE workouts SET is_synced = 1, sync_version = :version WHERE id IN (:ids)")
    suspend fun markWorkoutsAsSynced(ids: List<String>, version: Long = System.currentTimeMillis())
    
    @Deprecated("Use user-scoped methods instead")
    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: String)
    
    @Deprecated("Use user-scoped methods instead")
    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()
    
    @Deprecated("Use user-scoped updateSyncStatusForUser instead")
    @Query("UPDATE workouts SET is_synced = :isSynced, sync_version = :version WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean, version: Long = System.currentTimeMillis())
} 