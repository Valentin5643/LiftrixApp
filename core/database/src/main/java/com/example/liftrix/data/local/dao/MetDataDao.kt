package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.MetDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for MET (Metabolic Equivalent of Task) data
 * 
 * Provides efficient access to exercise-specific MET values for calorie calculations.
 * Supports category-based and exercise-specific lookups with optimized queries.
 */
@Dao
interface MetDataDao {
    
    /**
     * Inserts MET data entities, replacing existing ones on conflict
     * Used for seeding and updating MET values
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetData(metDataList: List<MetDataEntity>)
    
    /**
     * Inserts a single MET data entity
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetData(metData: MetDataEntity)
    
    /**
     * Gets all MET data entries
     */
    @Query("SELECT * FROM met_data ORDER BY exercise_category, exercise_type")
    fun getAllMetData(): Flow<List<MetDataEntity>>
    
    /**
     * Gets MET data for a specific exercise type
     */
    @Query("SELECT * FROM met_data WHERE exercise_type = :exerciseType")
    suspend fun getMetDataByExerciseType(exerciseType: String): List<MetDataEntity>
    
    /**
     * Gets MET data for a specific exercise category
     */
    @Query("SELECT * FROM met_data WHERE exercise_category = :category ORDER BY met_coefficient DESC")
    suspend fun getMetDataByCategory(category: String): List<MetDataEntity>
    
    /**
     * Gets MET data for a specific exercise type and category combination
     */
    @Query("SELECT * FROM met_data WHERE exercise_type = :exerciseType AND exercise_category = :category")
    suspend fun getMetDataByTypeAndCategory(exerciseType: String, category: String): MetDataEntity?
    
    /**
     * Gets MET data for a specific exercise type, category, and equipment combination
     */
    @Query("SELECT * FROM met_data WHERE exercise_type = :exerciseType AND exercise_category = :category AND (equipment_type = :equipment OR equipment_type IS NULL)")
    suspend fun getMetDataByTypeAndCategoryAndEquipment(
        exerciseType: String,
        category: String,
        equipment: String?
    ): MetDataEntity?
    
    /**
     * Gets MET data for exercises matching a partial name
     */
    @Query("SELECT * FROM met_data WHERE exercise_type LIKE '%' || :partialName || '%' ORDER BY met_coefficient DESC")
    suspend fun getMetDataByPartialName(partialName: String): List<MetDataEntity>
    
    /**
     * Gets the highest MET value for a category (for vigorous exercises)
     */
    @Query("SELECT MAX(met_coefficient) FROM met_data WHERE exercise_category = :category")
    suspend fun getMaxMetForCategory(category: String): Float?
    
    /**
     * Gets the average MET value for a category
     */
    @Query("SELECT AVG(met_coefficient) FROM met_data WHERE exercise_category = :category")
    suspend fun getAverageMetForCategory(category: String): Float?
    
    /**
     * Gets MET data for equipment-specific exercises
     */
    @Query("SELECT * FROM met_data WHERE equipment_type = :equipment ORDER BY exercise_category, met_coefficient DESC")
    suspend fun getMetDataByEquipment(equipment: String): List<MetDataEntity>
    
    /**
     * Gets count of MET data entries
     */
    @Query("SELECT COUNT(*) FROM met_data")
    suspend fun getMetDataCount(): Int
    
    /**
     * Deletes all MET data (for testing or reset purposes)
     */
    @Query("DELETE FROM met_data")
    suspend fun deleteAllMetData()
    
    /**
     * Gets distinct exercise categories from MET data
     */
    @Query("SELECT DISTINCT exercise_category FROM met_data ORDER BY exercise_category")
    suspend fun getDistinctCategories(): List<String>
    
    /**
     * Gets distinct exercise types from MET data
     */
    @Query("SELECT DISTINCT exercise_type FROM met_data ORDER BY exercise_type")
    suspend fun getDistinctExerciseTypes(): List<String>
    
    /**
     * Gets distinct equipment types from MET data
     */
    @Query("SELECT DISTINCT equipment_type FROM met_data WHERE equipment_type IS NOT NULL ORDER BY equipment_type")
    suspend fun getDistinctEquipmentTypes(): List<String>
}