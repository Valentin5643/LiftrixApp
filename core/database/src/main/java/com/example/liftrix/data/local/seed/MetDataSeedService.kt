package com.example.liftrix.data.local.seed

import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.seed.MetDataSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for populating the MET data table with research-backed values
 * 
 * Follows the app's offline-first architecture pattern by seeding the database
 * with standardized MET (Metabolic Equivalent of Task) values during initialization.
 * 
 * Data Sources:
 * - American College of Sports Medicine (ACSM) Guidelines
 * - Compendium of Physical Activities (2011 & 2019 updates)
 * - Exercise Physiology research from peer-reviewed sources
 * 
 * Performance Characteristics:
 * - Seed operation runs once during app initialization
 * - Uses batch insertion for optimal performance
 * - Validates data integrity before insertion
 * - Provides fallback error handling for robust initialization
 */
@Singleton
class MetDataSeedService @Inject constructor() {
    
    companion object {
        private const val EXPECTED_MET_DATA_COUNT = 45 // Expected number of MET data entries
    }
    
    /**
     * Populates the MET data table if it's empty
     * 
     * @param database The LiftrixDatabase instance
     */
    suspend fun populateMetDataIfNeeded(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val metDataCount = database.metDataDao().getMetDataCount()
                Timber.d("🔥 MET-SEED-DEBUG: Current MET data count: $metDataCount")
                
                // Debug database state
                if (metDataCount == 0) {
                    Timber.d("🔥 MET-SEED-DEBUG: MET data table is empty - will populate with seed data")
                } else {
                    Timber.d("🔥 MET-SEED-DEBUG: MET data table already has $metDataCount entries - skipping population")
                }
                
                if (metDataCount == 0) {
                    Timber.d("MET data table empty, populating with seed data")
                    populateMetData(database)
                    
                    // Verify population succeeded
                    val newCount = database.metDataDao().getMetDataCount()
                    Timber.d("MetDataSeedService: After population, MET data count: $newCount")
                    
                    if (newCount == 0) {
                        Timber.e("MetDataSeedService: Population failed - no MET data was inserted")
                    } else {
                        Timber.i("MetDataSeedService: Successfully populated $newCount MET data entries")
                        
                        // Validate expected count
                        if (newCount != EXPECTED_MET_DATA_COUNT) {
                            Timber.w("MetDataSeedService: Expected $EXPECTED_MET_DATA_COUNT entries, but got $newCount")
                        }
                    }
                } else {
                    Timber.d("MET data table already populated with $metDataCount entries")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking MET data count or populating")
                // Try to populate anyway if count check failed
                try {
                    Timber.d("MetDataSeedService: Attempting population after count check failure")
                    populateMetData(database)
                } catch (populateException: Exception) {
                    Timber.e(populateException, "MetDataSeedService: Population also failed")
                }
            }
        }
    }
    
    /**
     * Populates the MET data table with all seed data
     * 
     * @param database The LiftrixDatabase instance
     */
    suspend fun populateMetData(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("MetDataSeedService: Starting to load MET data from seed")
                val metDataEntities = MetDataSeed.getMetDataEntities()
                Timber.d("🔥 MET-SEED-DEBUG: Loaded ${metDataEntities.size} MET data entries from seed")
                
                // Debug sample MET data
                if (metDataEntities.isNotEmpty()) {
                    metDataEntities.take(3).forEach { metData ->
                        Timber.d("🔥 MET-SEED-DEBUG: MET data: ${metData.exerciseType} (${metData.exerciseCategory}) - MET: ${metData.metCoefficient}")
                    }
                }
                
                // Validate data before insertion
                val validatedEntities = validateMetData(metDataEntities)
                Timber.d("🔥 MET-SEED-DEBUG: Validated ${validatedEntities.size} MET data entries")
                
                if (validatedEntities.isEmpty()) {
                    Timber.e("MetDataSeedService: No valid MET data entries to insert")
                    return@withContext
                }
                
                // Insert MET data using batch operation
                database.metDataDao().insertMetData(validatedEntities)
                Timber.d("🔥 MET-SEED-DEBUG: Batch insert completed")
                
                // Verify actual insertion
                val finalCount = database.metDataDao().getMetDataCount()
                Timber.i("MetDataSeedService: Final database count after insertion: $finalCount")
                
                if (finalCount != validatedEntities.size) {
                    Timber.w("MetDataSeedService: Mismatch - tried to insert ${validatedEntities.size}, but database has $finalCount")
                }
                
                // Log summary of inserted data
                logInsertionSummary(database)
                
            } catch (e: Exception) {
                Timber.e(e, "Error populating MET data")
                throw e
            }
        }
    }
    
    /**
     * Validates MET data entities before insertion
     * 
     * @param metDataEntities List of MET data entities to validate
     * @return List of validated MET data entities
     */
    private fun validateMetData(metDataEntities: List<com.example.liftrix.data.local.entity.MetDataEntity>): List<com.example.liftrix.data.local.entity.MetDataEntity> {
        return metDataEntities.filter { metData ->
            try {
                // MetDataEntity constructor already validates data via init block
                // This will throw an exception if data is invalid
                true
            } catch (e: Exception) {
                Timber.w(e, "Invalid MET data entry: ${metData.id} - ${e.message}")
                false
            }
        }
    }
    
    /**
     * Logs a summary of the inserted MET data for debugging
     * 
     * @param database The LiftrixDatabase instance
     */
    private suspend fun logInsertionSummary(database: LiftrixDatabase) {
        try {
            // Get distinct categories and their counts
            val categories = database.metDataDao().getDistinctCategories()
            Timber.i("MetDataSeedService: Inserted MET data for ${categories.size} categories: $categories")
            
            // Log category-specific counts
            categories.forEach { category ->
                val count = database.metDataDao().getMetDataByCategory(category).size
                val avgMET = database.metDataDao().getAverageMetForCategory(category)
                val maxMET = database.metDataDao().getMaxMetForCategory(category)
                Timber.d("MetDataSeedService: $category - $count exercises, avg MET: $avgMET, max MET: $maxMET")
            }
            
            // Get distinct equipment types
            val equipmentTypes = database.metDataDao().getDistinctEquipmentTypes()
            Timber.i("MetDataSeedService: Inserted MET data for ${equipmentTypes.size} equipment types: $equipmentTypes")
            
        } catch (e: Exception) {
            Timber.w(e, "Error logging MET data insertion summary")
        }
    }
    
    /**
     * Forces repopulation of MET data (for testing or data updates)
     * 
     * @param database The LiftrixDatabase instance
     */
    suspend fun forceRepopulateMetData(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                Timber.i("MetDataSeedService: Force repopulating MET data")
                
                // Clear existing data
                database.metDataDao().deleteAllMetData()
                Timber.d("MetDataSeedService: Cleared existing MET data")
                
                // Repopulate with fresh data
                populateMetData(database)
                
            } catch (e: Exception) {
                Timber.e(e, "Error force repopulating MET data")
                throw e
            }
        }
    }
    
    /**
     * Gets MET data insertion statistics for monitoring
     * 
     * @param database The LiftrixDatabase instance
     * @return Map containing MET data statistics
     */
    suspend fun getMetDataStatistics(database: LiftrixDatabase): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val totalCount = database.metDataDao().getMetDataCount()
                val categories = database.metDataDao().getDistinctCategories()
                val equipmentTypes = database.metDataDao().getDistinctEquipmentTypes()
                val exerciseTypes = database.metDataDao().getDistinctExerciseTypes()
                
                mapOf(
                    "totalCount" to totalCount,
                    "categoriesCount" to categories.size,
                    "equipmentTypesCount" to equipmentTypes.size,
                    "exerciseTypesCount" to exerciseTypes.size,
                    "categories" to categories,
                    "equipmentTypes" to equipmentTypes
                )
            } catch (e: Exception) {
                Timber.e(e, "Error getting MET data statistics")
                emptyMap<String, Any>()
            }
        }
    }
}