package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for exercise library operations
 */
@Dao
interface ExerciseLibraryDao {
    
    /**
     * Insert multiple exercises into the library
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseLibraryEntity>): List<Long>
    
    /**
     * Get all exercises from the library
     */
    @Query("SELECT * FROM exercise_library ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Search exercises by name (fuzzy matching)
     */
    @Query("""
        SELECT * FROM exercise_library 
        WHERE name LIKE '%' || :query || '%' 
        OR EXISTS (
            SELECT 1 FROM json_each(searchable_terms) 
            WHERE json_each.value LIKE '%' || :query || '%'
        )
        ORDER BY 
            CASE WHEN name LIKE :query || '%' THEN 1 ELSE 2 END,
            name ASC
    """)
    fun searchExercises(query: String): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Get exercises by primary muscle group
     */
    @Query("SELECT * FROM exercise_library WHERE primary_muscle_group = :muscleGroup ORDER BY name ASC")
    fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Get exercises by equipment type
     */
    @Query("SELECT * FROM exercise_library WHERE equipment = :equipment ORDER BY name ASC")
    fun getExercisesByEquipment(equipment: Equipment): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Get exercises by movement pattern
     */
    @Query("SELECT * FROM exercise_library WHERE movement_pattern = :pattern ORDER BY name ASC")
    fun getExercisesByMovementPattern(pattern: String): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Get exercise variations for a specific movement pattern
     */
    @Query("""
        SELECT * FROM exercise_library 
        WHERE movement_pattern = :movementPattern 
        AND equipment IN (:availableEquipment)
        ORDER BY difficulty_level ASC, name ASC
    """)
    fun getVariationsByMovement(
        movementPattern: String, 
        availableEquipment: List<Equipment>
    ): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Get exercises filtered by multiple criteria
     */
    @Query("""
        SELECT * FROM exercise_library 
        WHERE (:muscleGroup IS NULL OR primary_muscle_group = :muscleGroup)
        AND (:equipment IS NULL OR equipment = :equipment)
        AND (:isCompound IS NULL OR is_compound = :isCompound)
        AND (:maxDifficulty IS NULL OR difficulty_level <= :maxDifficulty)
        ORDER BY name ASC
    """)
    fun getFilteredExercises(
        muscleGroup: ExerciseCategory?,
        equipment: Equipment?,
        isCompound: Boolean?,
        maxDifficulty: Int?
    ): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Get compound exercises for a muscle group
     */
    @Query("""
        SELECT * FROM exercise_library 
        WHERE (primary_muscle_group = :muscleGroup OR 
               EXISTS (SELECT 1 FROM json_each(secondary_muscle_groups) 
                      WHERE json_each.value = :muscleGroup))
        AND is_compound = 1
        ORDER BY difficulty_level ASC
    """)
    fun getCompoundExercisesForMuscle(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibraryEntity>>
    
    /**
     * Check if exercise library is populated
     */
    @Query("SELECT COUNT(*) FROM exercise_library")
    suspend fun getExerciseCount(): Int
} 