package com.example.liftrix.data.local.seed

import android.content.Context
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing exercise data from JSON
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
 * Service for populating the exercise library with seed data
 */
@Singleton
class ExerciseLibrarySeedData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    
    /**
     * Populates the exercise library if it's empty
     */
    suspend fun populateExerciseLibraryIfNeeded(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val exerciseCount = database.exerciseLibraryDao().getExerciseCount()
                Timber.d("🔥 SEED-DEBUG: Current exercise count: $exerciseCount")
                
                // Debug database state
                if (exerciseCount == 0) {
                    Timber.d("🔥 SEED-DEBUG: Database is empty - will populate with seed data")
                } else {
                    Timber.d("🔥 SEED-DEBUG: Database already has $exerciseCount exercises - skipping population")
                }
                
                if (exerciseCount == 0) {
                    Timber.d("Exercise library empty, populating with seed data")
                    populateExerciseLibrary(database)
                    
                    // Verify population succeeded
                    val newCount = database.exerciseLibraryDao().getExerciseCount()
                    Timber.d("ExerciseLibrarySeedData: After population, exercise count: $newCount")
                    
                    if (newCount == 0) {
                        Timber.e("ExerciseLibrarySeedData: Population failed - no exercises were inserted")
                    } else {
                        Timber.i("ExerciseLibrarySeedData: Successfully populated $newCount exercises")
                    }
                } else {
                    Timber.d("Exercise library already populated with $exerciseCount exercises")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking exercise library count or populating")
                // Try to populate anyway if count check failed
                try {
                    Timber.d("ExerciseLibrarySeedData: Attempting population after count check failure")
                    populateExerciseLibrary(database)
                } catch (populateException: Exception) {
                    Timber.e(populateException, "ExerciseLibrarySeedData: Population also failed")
                }
            }
        }
    }
    
    /**
     * Populates the exercise library with all seed data
     */
    suspend fun populateExerciseLibrary(database: LiftrixDatabase) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("ExerciseLibrarySeedData: Starting to load exercises from JSON")
                val exercises = loadExercisesFromJson()
                Timber.d("🔥 SEED-DEBUG: Loaded ${exercises.size} exercises from JSON")
                
                // Debug sample exercises
                if (exercises.isNotEmpty()) {
                    exercises.take(3).forEach { exercise ->
                        Timber.d("🔥 SEED-DEBUG: JSON exercise: ${exercise.name} (${exercise.id})")
                    }
                }
                
                val entities = exercises.map { convertToEntity(it) }
                Timber.d("🔥 SEED-DEBUG: Converted to ${entities.size} entities")
                
                // Debug sample entities
                if (entities.isNotEmpty()) {
                    entities.take(3).forEach { entity ->
                        Timber.d("🔥 SEED-DEBUG: Entity: ${entity.name} (${entity.id})")
                    }
                }
                
                val insertResults = database.exerciseLibraryDao().insertExercises(entities)
                Timber.d("🔥 SEED-DEBUG: Insert results: ${insertResults.size} IDs returned")
                
                // Verify actual insertion
                val finalCount = database.exerciseLibraryDao().getExerciseCount()
                Timber.i("ExerciseLibrarySeedData: Final database count after insertion: $finalCount")
                
                if (finalCount != entities.size) {
                    Timber.w("ExerciseLibrarySeedData: Mismatch - tried to insert ${entities.size}, but database has $finalCount")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error populating exercise library")
                throw e
            }
        }
    }
    
    /**
     * Loads exercise data from JSON asset file
     */
    private fun loadExercisesFromJson(): List<ExerciseJsonData> {
        return try {
            val jsonString = context.assets.open("exercise_library.json").bufferedReader().use { it.readText() }
            Timber.d("🔥 JSON-DEBUG: Loaded JSON string length: ${jsonString.length}")
            
            val listType = object : TypeToken<List<ExerciseJsonData>>() {}.type
            val exercises = gson.fromJson<List<ExerciseJsonData>>(jsonString, listType)
            Timber.d("🔥 JSON-DEBUG: Parsed ${exercises.size} exercises from JSON")
            
            exercises
        } catch (e: Exception) {
            Timber.e(e, "🔥 JSON-DEBUG: Error loading exercise library JSON")
            throw e
        }
    }
    
    /**
     * Converts JSON data to Room entity
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
     * Gets exercises for chest muscle group
     */
    suspend fun getChestExercises(): List<ExerciseLibraryEntity> {
        return loadExercisesFromJson()
            .filter { it.primaryMuscleGroup == "CHEST" }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercise variations by movement pattern
     */
    suspend fun getVariationsByMovement(movement: String): List<ExerciseLibraryEntity> {
        return loadExercisesFromJson()
            .filter { it.movementPattern == movement }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercises by equipment type
     */
    suspend fun getExercisesByEquipment(equipment: Equipment): List<ExerciseLibraryEntity> {
        return loadExercisesFromJson()
            .filter { Equipment.valueOf(it.equipment) == equipment }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercises by muscle group
     */
    suspend fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): List<ExerciseLibraryEntity> {
        return loadExercisesFromJson()
            .filter { ExerciseCategory.valueOf(it.primaryMuscleGroup) == muscleGroup }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets all compound exercises
     */
    suspend fun getCompoundExercises(): List<ExerciseLibraryEntity> {
        return loadExercisesFromJson()
            .filter { it.isCompound }
            .map { convertToEntity(it) }
    }
    
    /**
     * Gets exercises by difficulty range
     */
    suspend fun getExercisesByDifficulty(minLevel: Int, maxLevel: Int): List<ExerciseLibraryEntity> {
        return loadExercisesFromJson()
            .filter { it.difficultyLevel in minLevel..maxLevel }
            .map { convertToEntity(it) }
    }
} 