package com.example.liftrix.data.fallback

import android.content.Context
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Service that provides placeholder exercises from JSON for immediate display
 * while database is being populated
 */
@Singleton
class ExercisePlaceholderService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    
    private var cachedExercises: List<ExerciseLibrary>? = null
    
    /**
     * Gets placeholder exercises from JSON file for immediate display
     */
    fun getPlaceholderExercises(): List<ExerciseLibrary> {
        if (cachedExercises != null) {
            return cachedExercises!!
        }
        
        return try {
            val jsonString = context.assets.open("exercise_library.json")
                .bufferedReader()
                .use { it.readText() }
            
            val exercises = parseExerciseLibraryJson(jsonString).map { convertToDomain(it) }
            cachedExercises = exercises
            
            exercises
        } catch (e: Exception) {
            Timber.e(e, "Failed to load placeholder exercises from JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parses the exercise asset without Gson reflection/TypeToken metadata so the
     * fallback path remains stable in minified release builds.
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
     * Converts JSON data to domain model
     */
    private fun convertToDomain(jsonData: ExerciseJsonData): ExerciseLibrary {
        return ExerciseLibrary(
            id = jsonData.id,
            name = jsonData.name,
            primaryMuscleGroup = try {
                ExerciseCategory.valueOf(jsonData.primaryMuscleGroup)
            } catch (e: Exception) {
                ExerciseCategory.CHEST // fallback
            },
            equipment = try {
                Equipment.valueOf(jsonData.equipment)
            } catch (e: Exception) {
                Equipment.BODYWEIGHT_ONLY // fallback
            },
            secondaryMuscleGroups = jsonData.secondaryMuscleGroups.mapNotNull { secondary ->
                try {
                    ExerciseCategory.valueOf(secondary)
                } catch (e: Exception) {
                    null
                }
            },
            movementPattern = jsonData.movementPattern,
            difficultyLevel = jsonData.difficultyLevel,
            instructions = jsonData.instructions ?: "",
            isCompound = jsonData.isCompound,
            searchableTerms = jsonData.searchableTerms
        )
    }
    
    /**
     * Clears cached exercises (useful for testing)
     */
    fun clearCache() {
        cachedExercises = null
    }
}
