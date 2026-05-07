package com.example.liftrix.data.fallback

import android.content.Context
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
            
            val listType = object : TypeToken<List<ExerciseJsonData>>() {}.type
            val jsonData: List<ExerciseJsonData> = gson.fromJson(jsonString, listType)
            
            val exercises = jsonData.map { convertToDomain(it) }
            cachedExercises = exercises
            
            exercises
        } catch (e: Exception) {
            Timber.e(e, "Failed to load placeholder exercises from JSON")
            emptyList()
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