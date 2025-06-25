package com.example.liftrix.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.DailyWorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for daily workouts with user-scoped operations
 */
@Dao
interface DailyWorkoutDao {
    
    /**
     * Insert a new daily workout
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDailyWorkout(workout: DailyWorkoutEntity): Long
    
    /**
     * Get all daily workouts for a specific user
     */
    @Query("SELECT * FROM daily_workouts WHERE user_id = :userId ORDER BY date DESC, created_at DESC")
    fun getAllDailyWorkoutsForUser(userId: String): Flow<List<DailyWorkoutEntity>>
    
    /**
     * Get a specific daily workout by ID and user ID
     */
    @Query("SELECT * FROM daily_workouts WHERE id = :workoutId AND user_id = :userId")
    suspend fun getDailyWorkoutById(workoutId: String, userId: String): DailyWorkoutEntity?
    
    /**
     * Get daily workouts for a specific date and user
     */
    @Query("SELECT * FROM daily_workouts WHERE user_id = :userId AND date = :date ORDER BY created_at DESC")
    fun getDailyWorkoutsForDate(userId: String, date: LocalDate): Flow<List<DailyWorkoutEntity>>
    
    /**
     * Get daily workouts for a date range
     */
    @Query("""
        SELECT * FROM daily_workouts 
        WHERE user_id = :userId 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date DESC, created_at DESC
    """)
    fun getDailyWorkoutsForDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DailyWorkoutEntity>>
    
    /**
     * Get daily workouts by status for a specific user
     */
    @Query("SELECT * FROM daily_workouts WHERE user_id = :userId AND status = :status ORDER BY date DESC")
    fun getDailyWorkoutsByStatus(userId: String, status: WorkoutStatus): Flow<List<DailyWorkoutEntity>>
    
    /**
     * Get daily workouts created from a specific template
     */
    @Query("SELECT * FROM daily_workouts WHERE user_id = :userId AND template_id = :templateId ORDER BY date DESC")
    fun getDailyWorkoutsFromTemplate(userId: String, templateId: String): Flow<List<DailyWorkoutEntity>>
    
    /**
     * Get recent daily workouts for a specific user
     */
    @Query("""
        SELECT * FROM daily_workouts 
        WHERE user_id = :userId 
        ORDER BY date DESC, created_at DESC 
        LIMIT :limit
    """)
    fun getRecentDailyWorkouts(userId: String, limit: Int = 10): Flow<List<DailyWorkoutEntity>>
    
    /**
     * Update an existing daily workout
     */
    @Update
    suspend fun updateDailyWorkout(workout: DailyWorkoutEntity): Int
    
    /**
     * Delete a daily workout by ID (user-scoped)
     */
    @Query("DELETE FROM daily_workouts WHERE id = :workoutId AND user_id = :userId")
    suspend fun deleteDailyWorkout(workoutId: String, userId: String): Int
    
    /**
     * Delete a daily workout entity
     */
    @Delete
    suspend fun deleteDailyWorkout(workout: DailyWorkoutEntity): Int
    
    /**
     * Convert a daily workout to a template
     */
    @Query("""
        INSERT INTO workout_templates (id, user_id, name, template_exercises_json, created_at, updated_at)
        SELECT 
            :templateId,
            user_id,
            :templateName,
            exercises_json,
            :createdAt,
            :createdAt
        FROM daily_workouts 
        WHERE id = :workoutId AND user_id = :userId
    """)
    suspend fun convertToTemplate(
        workoutId: String, 
        userId: String, 
        templateId: String, 
        templateName: String, 
        createdAt: String
    ): Long
    
    /**
     * Get workout statistics for a user
     */
    @Query("""
        SELECT 
            COUNT(*) as total_workouts,
            SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_workouts,
            AVG(duration_minutes) as avg_duration,
            SUM(total_volume_kg) as total_volume
        FROM daily_workouts 
        WHERE user_id = :userId
    """)
    suspend fun getWorkoutStats(userId: String): WorkoutStats?
    
    /**
     * Get unsynced daily workouts for a user
     */
    @Query("SELECT * FROM daily_workouts WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedDailyWorkouts(userId: String): List<DailyWorkoutEntity>
    
    /**
     * Mark daily workouts as synced
     */
    @Query("UPDATE daily_workouts SET is_synced = 1 WHERE id IN (:workoutIds)")
    suspend fun markDailyWorkoutsAsSynced(workoutIds: List<String>): Int
    
    /**
     * Get count of daily workouts for a user
     */
    @Query("SELECT COUNT(*) FROM daily_workouts WHERE user_id = :userId")
    suspend fun getDailyWorkoutCount(userId: String): Int
    
    /**
     * Get workout streak for a user (consecutive days with completed workouts)
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT date FROM daily_workouts 
            WHERE user_id = :userId 
            AND status = 'COMPLETED'
            AND date >= :startDate
            GROUP BY date
            ORDER BY date DESC
        )
    """)
    suspend fun getWorkoutStreak(userId: String, startDate: LocalDate): Int
    
    /**
     * Check if user has workout on specific date
     */
    @Query("SELECT EXISTS(SELECT 1 FROM daily_workouts WHERE user_id = :userId AND date = :date)")
    suspend fun hasWorkoutOnDate(userId: String, date: LocalDate): Boolean
}

/**
 * Data class for workout statistics
 */
data class WorkoutStats(
    @ColumnInfo(name = "total_workouts")
    val totalWorkouts: Int,
    @ColumnInfo(name = "completed_workouts")
    val completedWorkouts: Int,
    @ColumnInfo(name = "avg_duration")
    val avgDuration: Double?,
    @ColumnInfo(name = "total_volume")
    val totalVolume: Double?
) 