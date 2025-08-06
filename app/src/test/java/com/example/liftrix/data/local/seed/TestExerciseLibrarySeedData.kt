package com.example.liftrix.data.local.seed

import android.content.Context
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing exercise data from JSON (same as production)
 */
private data class ExerciseJsonData(
    val id: String,
    val name: String,
    val primaryMuscleGroup: String,
    val equipment: String,
    val secondaryMuscleGroups: List<String>,
    val movementPattern: String,
    val difficultyLevel: Int,
    val instructions: String?,
    val isCompound: Boolean,
    val searchableTerms: List<String>
)

/**
 * Test-specific version of ExerciseLibrarySeedData that loads from test resources
 * instead of assets to work around Robolectric asset loading issues.
 */
@Singleton
class TestExerciseLibrarySeedData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    
    /**
     * Populates the exercise library if it's empty (test version)
     */
    suspend fun populateExerciseLibraryIfNeeded(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val exerciseCount = database.exerciseLibraryDao().getExerciseCount()
                Timber.d("🔥 TEST-SEED-DEBUG: Current exercise count: $exerciseCount")
                
                if (exerciseCount == 0) {
                    Timber.d("🔥 TEST-SEED-DEBUG: Database is empty - will populate with test seed data")
                    populateExerciseLibrary(database)
                    
                    // Verify population succeeded
                    val newCount = database.exerciseLibraryDao().getExerciseCount()
                    Timber.d("TestExerciseLibrarySeedData: After population, exercise count: $newCount")
                    
                    if (newCount == 0) {
                        Timber.e("TestExerciseLibrarySeedData: Population failed - no exercises were inserted")
                    } else {
                        Timber.i("TestExerciseLibrarySeedData: Successfully populated $newCount exercises")
                    }
                } else {
                    Timber.d("Test exercise library already populated with $exerciseCount exercises")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking test exercise library count or populating")
                throw e
            }
        }
    }
    
    /**
     * Populates the exercise library with all seed data (test version)
     */
    suspend fun populateExerciseLibrary(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("TestExerciseLibrarySeedData: Starting to load exercises from test resources")
                val exercises = loadExercisesFromTestResources()
                Timber.d("🔥 TEST-SEED-DEBUG: Loaded ${exercises.size} exercises from test resources")
                
                val entities = exercises.map { convertToEntity(it) }
                Timber.d("🔥 TEST-SEED-DEBUG: Converted to ${entities.size} entities")
                
                val insertResults = database.exerciseLibraryDao().insertExercises(entities)
                Timber.d("🔥 TEST-SEED-DEBUG: Insert results: ${insertResults.size} IDs returned")
                
                // Verify actual insertion
                val finalCount = database.exerciseLibraryDao().getExerciseCount()
                Timber.i("TestExerciseLibrarySeedData: Final database count after insertion: $finalCount")
                
            } catch (e: Exception) {
                Timber.e(e, "Error populating test exercise library")
                throw e
            }
        }
    }
    
    /**
     * Loads exercise data from test resources instead of assets
     */
    private fun loadExercisesFromTestResources(): List<ExerciseJsonData> {
        return try {
            // Load from classpath resources instead of Android assets
            val resourceStream = this::class.java.classLoader?.getResourceAsStream("exercise_library.json")
                ?: throw IllegalStateException("Could not find exercise_library.json in test resources")
            
            val jsonString = resourceStream.bufferedReader().use { it.readText() }
            Timber.d("🔥 TEST-JSON-DEBUG: Loaded JSON string length: ${jsonString.length}")
            
            val listType = object : TypeToken<List<ExerciseJsonData>>() {}.type
            val exercises = gson.fromJson<List<ExerciseJsonData>>(jsonString, listType)
            Timber.d("🔥 TEST-JSON-DEBUG: Parsed ${exercises.size} exercises from JSON")
            
            exercises
        } catch (e: Exception) {
            Timber.e(e, "🔥 TEST-JSON-DEBUG: Error loading exercise library from test resources")
            throw e
        }
    }
    
    /**
     * Converts JSON data to Room entity (same as production)
     */
    private fun convertToEntity(jsonData: ExerciseJsonData): ExerciseLibraryEntity {
        return ExerciseLibraryEntity(
            id = jsonData.id,
            name = jsonData.name,
            primaryMuscleGroup = ExerciseCategory.valueOf(jsonData.primaryMuscleGroup),
            equipment = Equipment.valueOf(jsonData.equipment),
            secondaryMuscleGroups = jsonData.secondaryMuscleGroups.map { ExerciseCategory.valueOf(it) },
            movementPattern = jsonData.movementPattern,
            difficultyLevel = jsonData.difficultyLevel,
            instructions = jsonData.instructions,
            isCompound = jsonData.isCompound,
            searchableTerms = jsonData.searchableTerms
        )
    }
    
    /**
     * Gets exercises for chest muscle group (test version)
     */
    suspend fun getChestExercises(): List<ExerciseLibraryEntity> {
        return loadExercisesFromTestResources()
            .filter { it.primaryMuscleGroup == "CHEST" }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercise variations by movement pattern (test version)
     */
    suspend fun getVariationsByMovement(movement: String): List<ExerciseLibraryEntity> {
        return loadExercisesFromTestResources()
            .filter { it.movementPattern == movement }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercises by equipment type (test version)
     */
    suspend fun getExercisesByEquipment(equipment: Equipment): List<ExerciseLibraryEntity> {
        return loadExercisesFromTestResources()
            .filter { Equipment.valueOf(it.equipment) == equipment }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercises by muscle group (test version)
     */
    suspend fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): List<ExerciseLibraryEntity> {
        return loadExercisesFromTestResources()
            .filter { ExerciseCategory.valueOf(it.primaryMuscleGroup) == muscleGroup }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets all compound exercises (test version)
     */
    suspend fun getCompoundExercises(): List<ExerciseLibraryEntity> {
        return loadExercisesFromTestResources()
            .filter { it.isCompound }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercises by difficulty range (test version)
     */
    suspend fun getExercisesByDifficulty(minLevel: Int, maxLevel: Int): List<ExerciseLibraryEntity> {
        return loadExercisesFromTestResources()
            .filter { it.difficultyLevel in minLevel..maxLevel }
            .map { convertToEntity(it) }
    }
}