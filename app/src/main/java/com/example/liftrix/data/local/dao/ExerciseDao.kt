package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.annotations.UserScoped
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    
    suspend fun getExercisesByWorkout(workoutId: String, userId: String): List<ExerciseEntity> {
        return getExercisesForWorkout(workoutId, userId)
    }

    @Query("""
        SELECT e.* FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        WHERE e.workout_id = :workoutId
        AND w.user_id = :userId
        ORDER BY e.order_index ASC
    """)
    @UserScoped
    suspend fun getExercisesForWorkout(workoutId: String, userId: String): List<ExerciseEntity>
    
    suspend fun getExercisesByWorkoutId(workoutId: String, userId: String): List<ExerciseEntity> {
        return getExercisesForWorkout(workoutId, userId)
    }
    
    @Query("""
        SELECT e.* FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        WHERE e.workout_id = :workoutId
        AND w.user_id = :userId
        ORDER BY e.order_index ASC
    """)
    @UserScoped
    fun getExercisesByWorkoutFlow(workoutId: String, userId: String): Flow<List<ExerciseEntity>>
    
    @Query("""
        SELECT e.* FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        WHERE e.id = :exerciseId
        AND w.user_id = :userId
    """)
    @UserScoped
    suspend fun getExerciseById(exerciseId: Long, userId: String): ExerciseEntity?
    
    @Query("""
        SELECT e.* FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND e.exercise_library_id = :exerciseLibraryId 
        ORDER BY e.created_at DESC 
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExerciseHistory(userId: String, exerciseLibraryId: String, limit: Int): List<ExerciseEntity>
    
    @Query("""
        SELECT e.* FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        WHERE e.exercise_library_id = :exerciseLibraryId
        AND w.user_id = :userId
        ORDER BY e.created_at DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExercisesByLibraryId(
        exerciseLibraryId: String,
        userId: String,
        limit: Int
    ): List<ExerciseEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>): List<Long>
    
    @Update
    suspend fun updateExercise(exercise: ExerciseEntity): Int
    
    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity): Int
    
    @Query("""
        DELETE FROM exercises
        WHERE id = :exerciseId
        AND workout_id IN (SELECT id FROM workouts WHERE user_id = :userId)
    """)
    @UserScoped
    suspend fun deleteExerciseById(exerciseId: Long, userId: String): Int
    
    @Query("""
        DELETE FROM exercises
        WHERE workout_id = :workoutId
        AND workout_id IN (SELECT id FROM workouts WHERE user_id = :userId)
    """)
    @UserScoped
    suspend fun deleteExercisesForWorkout(workoutId: String, userId: String): Int
    
    @Query("""
        SELECT COUNT(*) FROM exercises
        WHERE workout_id = :workoutId
        AND workout_id IN (SELECT id FROM workouts WHERE user_id = :userId)
    """)
    @UserScoped
    suspend fun getExerciseCountForWorkout(workoutId: String, userId: String): Int
    
    @Query("""
        SELECT MAX(order_index) FROM exercises
        WHERE workout_id = :workoutId
        AND workout_id IN (SELECT id FROM workouts WHERE user_id = :userId)
    """)
    @UserScoped
    suspend fun getMaxOrderIndex(workoutId: String, userId: String): Int?
    
    @Query("""
        UPDATE exercises
        SET order_index = :newIndex
        WHERE id = :exerciseId
        AND workout_id IN (SELECT id FROM workouts WHERE user_id = :userId)
    """)
    @UserScoped
    suspend fun updateExerciseOrder(exerciseId: Long, newIndex: Int, userId: String): Int
    
    @Transaction
    suspend fun reorderExercises(workoutId: String, userId: String, exerciseIds: List<Long>) {
        exerciseIds.forEachIndexed { index, exerciseId ->
            updateExerciseOrder(exerciseId, index, userId)
        }
    }
    
    @Query("""
        SELECT DISTINCT e.exercise_library_id FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        ORDER BY e.created_at DESC
    """)
    @UserScoped
    suspend fun getRecentlyUsedExerciseIds(userId: String): List<String>
    
    @Query("""
        SELECT e.* FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND e.exercise_library_id = :exerciseLibraryId 
        AND w.date BETWEEN :startDate AND :endDate
        ORDER BY e.created_at DESC
    """)
    @UserScoped
    suspend fun getExerciseHistoryInDateRange(userId: String, exerciseLibraryId: String, startDate: String, endDate: String): List<ExerciseEntity>
    
    @Query("""
        SELECT e.* FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        ORDER BY e.created_at DESC
    """)
    @UserScoped
    suspend fun getAllExercisesForUser(userId: String): List<ExerciseEntity>

    @Query("""
        SELECT
            e.workout_id,
            COUNT(e.id) as exercise_count
        FROM exercises e
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND w.status = 'COMPLETED'
        AND w.date BETWEEN :startDate AND :endDate
        GROUP BY e.workout_id
    """)
    @UserScoped
    suspend fun getCompletedExerciseCountsByWorkout(
        userId: String,
        startDate: String,
        endDate: String
    ): List<WorkoutExerciseCountResult>
    
    @Query("""
        SELECT COUNT(*) FROM exercises e 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId AND e.workout_id = :workoutId
    """)
    @UserScoped
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
    @UserScoped
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
    @UserScoped
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
    @UserScoped
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

/**
 * Data class for normalized exercise counts by workout.
 */
data class WorkoutExerciseCountResult(
    val workout_id: String,
    val exercise_count: Int
)
