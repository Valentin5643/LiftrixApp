package com.example.liftrix.data.local.seed

import android.content.Context
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
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
                
                // Debug database state
                if (exerciseCount == 0) {
                } else {
                }
                
                if (exerciseCount == 0) {
                    populateExerciseLibrary(database)
                    
                    // Verify population succeeded
                    val newCount = database.exerciseLibraryDao().getExerciseCount()
                    
                    if (newCount == 0) {
                        Timber.e("ExerciseLibrarySeedData: Population failed - no exercises were inserted")
                    } else {
                        Timber.i("ExerciseLibrarySeedData: Successfully populated $newCount exercises")
                    }
                } else {
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking exercise library count or populating")
                // Try to populate anyway if count check failed
                try {
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
                val exercises = loadExercisesFromJson()
                
                // Debug sample exercises
                if (exercises.isNotEmpty()) {
                    exercises.take(3).forEach { exercise ->
                    }
                }
                
                val entities = exercises.map { convertToEntity(it) }
                
                // Debug sample entities
                if (entities.isNotEmpty()) {
                    entities.take(3).forEach { entity ->
                    }
                }
                
                val insertResults = database.exerciseLibraryDao().insertExercises(entities)
                
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
            
            parseExerciseLibraryJson(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "🔥 JSON-DEBUG: Error loading exercise library JSON")
            throw e
        }
    }
    
    /**
     * Parses the seed asset without Gson reflection/TypeToken metadata.
     *
     * Release builds minify this app, and anonymous Gson TypeToken plus private DTO
     * reflection is fragile when R8 strips generic signatures or obfuscates field names.
     */
    private fun parseExerciseLibraryJson(jsonString: String): List<ExerciseJsonData> {
        val root = JsonParser.parseString(jsonString)
        if (!root.isJsonArray) {
            throw JsonParseException("exercise_library.json root must be an array")
        }

        return root.asJsonArray.mapIndexed { index, element ->
            val jsonObject = element.asJsonObjectOrThrow(index)
            ExerciseJsonData(
                id = jsonObject.requiredString("id", index),
                name = jsonObject.requiredString("name", index),
                primaryMuscleGroup = jsonObject.requiredString("primaryMuscleGroup", index),
                equipment = jsonObject.requiredString("equipment", index),
                secondaryMuscleGroups = jsonObject.requiredStringList("secondaryMuscleGroups", index),
                movementPattern = jsonObject.requiredString("movementPattern", index),
                difficultyLevel = jsonObject.requiredInt("difficultyLevel", index),
                instructions = jsonObject.optionalString("instructions"),
                isCompound = jsonObject.requiredBoolean("isCompound", index),
                searchableTerms = jsonObject.requiredStringList("searchableTerms", index)
            )
        }
    }

    private fun JsonElement.asJsonObjectOrThrow(index: Int): JsonObject {
        if (!isJsonObject) {
            throw JsonParseException("Exercise at index $index must be an object")
        }
        return asJsonObject
    }

    private fun JsonObject.requiredString(field: String, index: Int): String {
        val element = get(field)
        if (element == null || element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw JsonParseException("Exercise at index $index has invalid '$field'; expected string")
        }
        return element.asString
    }

    private fun JsonObject.optionalString(field: String): String? {
        val element = get(field)
        return if (element == null || element.isJsonNull) null else element.asString
    }

    private fun JsonObject.requiredInt(field: String, index: Int): Int {
        val element = get(field)
        if (element == null || element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw JsonParseException("Exercise at index $index has invalid '$field'; expected number")
        }
        return element.asInt
    }

    private fun JsonObject.requiredBoolean(field: String, index: Int): Boolean {
        val element = get(field)
        if (element == null || element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            throw JsonParseException("Exercise at index $index has invalid '$field'; expected boolean")
        }
        return element.asBoolean
    }

    private fun JsonObject.requiredStringList(field: String, index: Int): List<String> {
        val element = get(field)
        if (element == null || element.isJsonNull || !element.isJsonArray) {
            throw JsonParseException("Exercise at index $index has invalid '$field'; expected string array")
        }
        return element.asJsonArray.toStringList(field, index)
    }

    private fun JsonArray.toStringList(field: String, exerciseIndex: Int): List<String> {
        return mapIndexed { valueIndex, element ->
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                throw JsonParseException("Exercise at index $exerciseIndex has invalid '$field[$valueIndex]'; expected string")
            }
            element.asString
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
