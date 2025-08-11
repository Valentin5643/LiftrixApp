package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    
    @Query("SELECT * FROM exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    suspend fun getExercisesByWorkout(workoutId: String): List<ExerciseEntity>
    
    @Query("SELECT * FROM exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    suspend fun getExercisesByWorkoutId(workoutId: String): List<ExerciseEntity>
    
    @Query("SELECT * FROM exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    fun getExercisesByWorkoutFlow(workoutId: String): Flow<List<ExerciseEntity>>
    
    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    suspend fun getExerciseById(exerciseId: Long): ExerciseEntity?
    
    @Query("""
        SELECT e.* FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND e.exercise_library_id = :exerciseLibraryId 
        ORDER BY e.created_at DESC 
        LIMIT :limit
    """)
    suspend fun getExerciseHistory(userId: String, exerciseLibraryId: String, limit: Int): List<ExerciseEntity>
    
    @Query("SELECT * FROM exercises WHERE exercise_library_id = :exerciseLibraryId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getExercisesByLibraryId(exerciseLibraryId: String, limit: Int): List<ExerciseEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>): List<Long>
    
    @Update
    suspend fun updateExercise(exercise: ExerciseEntity): Int
    
    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity): Int
    
    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    suspend fun deleteExerciseById(exerciseId: Long): Int
    
    @Query("DELETE FROM exercises WHERE workout_id = :workoutId")
    suspend fun deleteExercisesForWorkout(workoutId: String): Int
    
    @Query("SELECT COUNT(*) FROM exercises WHERE workout_id = :workoutId")
    suspend fun getExerciseCountForWorkout(workoutId: String): Int
    
    @Query("SELECT MAX(order_index) FROM exercises WHERE workout_id = :workoutId")
    suspend fun getMaxOrderIndex(workoutId: String): Int?
    
    @Query("UPDATE exercises SET order_index = :newIndex WHERE id = :exerciseId")
    suspend fun updateExerciseOrder(exerciseId: Long, newIndex: Int): Int
    
    @Transaction
    suspend fun reorderExercises(workoutId: String, exerciseIds: List<Long>) {
        exerciseIds.forEachIndexed { index, exerciseId ->
            updateExerciseOrder(exerciseId, index)
        }
    }
    
    @Query("""
        SELECT DISTINCT e.exercise_library_id FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        ORDER BY e.created_at DESC
    """)
    suspend fun getRecentlyUsedExerciseIds(userId: String): List<String>
    
    @Query("""
        SELECT e.* FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND e.exercise_library_id = :exerciseLibraryId 
        AND w.date BETWEEN :startDate AND :endDate
        ORDER BY e.created_at DESC
    """)
    suspend fun getExerciseHistoryInDateRange(userId: String, exerciseLibraryId: String, startDate: String, endDate: String): List<ExerciseEntity>
    
    @Query("""
        SELECT e.* FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        ORDER BY e.created_at DESC
    """)
    suspend fun getAllExercisesForUser(userId: String): List<ExerciseEntity>
    
    @Query("""
        SELECT COUNT(*) FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId AND e.workout_id = :workoutId
    """)
    suspend fun validateWorkoutExerciseRelationship(userId: String, workoutId: String): Int
    
    // Enhanced analytics queries for dashboard real data integration
    
    /**
     * Gets distinct exercise library IDs used by user within date range
     * Used for filtering and building exercise selection lists in analytics
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of exercise library IDs ordered by most recently used
     */
    @Query("""
        SELECT e.exercise_library_id 
        FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND w.status = 'COMPLETED'
        AND DATE(w.date) BETWEEN :startDate AND :endDate
        GROUP BY e.exercise_library_id
        ORDER BY MAX(e.created_at) DESC
    """)
    suspend fun getUsedExerciseIds(userId: String, startDate: String, endDate: String): List<String>
    
    /**
     * Gets exercise frequency counts for ranking and usage analysis
     * Shows how often each exercise is performed within the date range
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of ExerciseFrequencyResult ordered by frequency DESC
     */
    @Query("""
        SELECT 
            e.exercise_library_id,
            el.name as exercise_name,
            el.primary_muscle_group,
            COUNT(e.id) as exercise_frequency,
            COUNT(DISTINCT w.date) as workout_days
        FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        JOIN exercise_library el ON e.exercise_library_id = el.id
        WHERE w.user_id = :userId
        AND w.status = 'COMPLETED'
        AND DATE(w.date) BETWEEN :startDate AND :endDate
        GROUP BY e.exercise_library_id, el.name, el.primary_muscle_group
        ORDER BY exercise_frequency DESC, workout_days DESC
    """)
    suspend fun getExerciseFrequency(
        userId: String,
        startDate: String,
        endDate: String
    ): List<ExerciseFrequencyResult>
    
    /**
     * Gets muscle group distribution from completed workouts
     * Used for muscle group balance analysis in dashboard
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of MuscleGroupDistributionResult ordered by count DESC
     */
    @Query("""
        SELECT 
            el.primary_muscle_group,
            COUNT(e.id) as exercise_count,
            COUNT(DISTINCT e.exercise_library_id) as unique_exercises,
            COUNT(DISTINCT w.date) as workout_days
        FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        JOIN exercise_library el ON e.exercise_library_id = el.id
        WHERE w.user_id = :userId
        AND w.status = 'COMPLETED'
        AND DATE(w.date) BETWEEN :startDate AND :endDate
        GROUP BY el.primary_muscle_group
        ORDER BY exercise_count DESC
    """)
    suspend fun getMuscleGroupDistribution(
        userId: String,
        startDate: String,
        endDate: String
    ): List<MuscleGroupDistributionResult>
}

/**
 * Data class for exercise frequency analysis results
 */
data class ExerciseFrequencyResult(
    val exercise_library_id: String,
    val exercise_name: String,
    val primary_muscle_group: String,
    val exercise_frequency: Int,
    val workout_days: Int
)

/**
 * Data class for muscle group distribution analysis results  
 */
data class MuscleGroupDistributionResult(
    val primary_muscle_group: String,
    val exercise_count: Int,
    val unique_exercises: Int,
    val workout_days: Int
)