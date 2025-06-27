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
    suspend fun getExercisesByWorkout(workoutId: Long): List<ExerciseEntity>
    
    @Query("SELECT * FROM exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    fun getExercisesByWorkoutFlow(workoutId: Long): Flow<List<ExerciseEntity>>
    
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
    suspend fun deleteExercisesForWorkout(workoutId: Long): Int
    
    @Query("SELECT COUNT(*) FROM exercises WHERE workout_id = :workoutId")
    suspend fun getExerciseCountForWorkout(workoutId: Long): Int
    
    @Query("SELECT MAX(order_index) FROM exercises WHERE workout_id = :workoutId")
    suspend fun getMaxOrderIndex(workoutId: Long): Int?
    
    @Query("UPDATE exercises SET order_index = :newIndex WHERE id = :exerciseId")
    suspend fun updateExerciseOrder(exerciseId: Long, newIndex: Int): Int
    
    @Transaction
    suspend fun reorderExercises(workoutId: Long, exerciseIds: List<Long>) {
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
}