package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSetDao {
    
    @Query("SELECT * FROM exercise_sets WHERE exercise_id = :exerciseId ORDER BY set_number ASC")
    suspend fun getSetsByExercise(exerciseId: Long): List<ExerciseSetEntity>
    
    @Query("SELECT * FROM exercise_sets WHERE exercise_id = :exerciseId ORDER BY set_number ASC")
    fun getSetsByExerciseFlow(exerciseId: Long): Flow<List<ExerciseSetEntity>>
    
    @Query("SELECT * FROM exercise_sets WHERE id = :setId")
    suspend fun getSetById(setId: Long): ExerciseSetEntity?
    
    @Query("""
        SELECT es.* FROM exercise_sets es 
        JOIN exercises e ON es.exercise_id = e.id 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND e.exercise_library_id = :exerciseLibraryId 
        AND es.completed_at IS NOT NULL
        ORDER BY es.completed_at DESC 
        LIMIT :limit
    """)
    suspend fun getExerciseHistory(userId: String, exerciseLibraryId: String, limit: Int): List<ExerciseSetEntity>
    
    @Query("SELECT * FROM exercise_sets WHERE completed_at IS NOT NULL ORDER BY completed_at DESC LIMIT :limit")
    suspend fun getRecentCompletedSets(limit: Int): List<ExerciseSetEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: ExerciseSetEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<ExerciseSetEntity>): List<Long>
    
    @Update
    suspend fun updateSet(set: ExerciseSetEntity): Int
    
    @Update
    suspend fun updateSets(sets: List<ExerciseSetEntity>): Int
    
    @Delete
    suspend fun deleteSet(set: ExerciseSetEntity): Int
    
    @Query("DELETE FROM exercise_sets WHERE id = :setId")
    suspend fun deleteSetById(setId: Long): Int
    
    @Query("DELETE FROM exercise_sets WHERE exercise_id = :exerciseId")
    suspend fun deleteSetsForExercise(exerciseId: Long): Int
    
    @Query("DELETE FROM exercise_sets WHERE exercise_id IN (SELECT id FROM exercises WHERE workout_id = :workoutId)")
    suspend fun deleteSetsForWorkout(workoutId: String): Int
    
    @Query("SELECT COUNT(*) FROM exercise_sets WHERE exercise_id = :exerciseId")
    suspend fun getSetCountForExercise(exerciseId: Long): Int
    
    @Query("SELECT COUNT(*) FROM exercise_sets WHERE exercise_id = :exerciseId AND completed_at IS NOT NULL")
    suspend fun getCompletedSetCountForExercise(exerciseId: Long): Int
    
    @Query("UPDATE exercise_sets SET completed_at = :completedAt WHERE id = :setId")
    suspend fun markSetCompleted(setId: Long, completedAt: Long): Int
    
    @Query("UPDATE exercise_sets SET completed_at = NULL WHERE id = :setId")
    suspend fun markSetIncomplete(setId: Long): Int
    
    @Transaction
    suspend fun replaceAllSetsForExercise(exerciseId: Long, newSets: List<ExerciseSetEntity>) {
        deleteSetsForExercise(exerciseId)
        insertSets(newSets.map { it.copy(exerciseId = exerciseId) })
    }
    
    @Query("SELECT MAX(set_number) FROM exercise_sets WHERE exercise_id = :exerciseId")
    suspend fun getMaxSetNumber(exerciseId: Long): Int?
    
    @Query("""
        SELECT AVG(weight_kg) FROM exercise_sets 
        WHERE exercise_id IN (
            SELECT id FROM exercises WHERE exercise_library_id = :exerciseLibraryId
        ) 
        AND weight_kg IS NOT NULL 
        AND completed_at IS NOT NULL
    """)
    suspend fun getAverageWeightForExercise(exerciseLibraryId: String): Float?
    
    @Query("""
        SELECT MAX(weight_kg) FROM exercise_sets 
        WHERE exercise_id IN (
            SELECT id FROM exercises WHERE exercise_library_id = :exerciseLibraryId
        ) 
        AND weight_kg IS NOT NULL 
        AND completed_at IS NOT NULL
    """)
    suspend fun getMaxWeightForExercise(exerciseLibraryId: String): Float?
}