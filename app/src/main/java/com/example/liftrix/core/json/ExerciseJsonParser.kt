package com.example.liftrix.core.json

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import timber.log.Timber

/**
 * Shared utility for parsing exercise JSON data that handles multiple formats.
 * 
 * This prevents crashes from "Expected BEGIN_ARRAY but was BEGIN_OBJECT" and similar errors
 * by detecting the JSON structure first, then applying the appropriate parsing strategy.
 * 
 * Supported formats:
 * - Array: [ {...}, {...} ]
 * - Object with numeric keys: { "0": {...}, "1": {...} }  
 * - Single exercise object: { "name": "Bench Press", "sets": [...] }
 */
object ExerciseJsonParser {
    
    /**
     * Parse exercises JSON that could be either array or object format
     */
    fun <T> parseExercises(json: String, clazz: Class<T>): List<T> {
        if (json.isBlank()) return emptyList()
        
        return try {
            val element = JsonParser.parseString(json)
            val gson = Gson()
            
            when {
                element.isJsonArray -> {
                    // ✅ Safe array parsing - [ {...}, {...} ]
                    val listType = TypeToken.getParameterized(List::class.java, clazz).type
                    gson.fromJson<List<T>>(element, listType) ?: emptyList()
                }
                
                element.isJsonObject -> {
                    val jsonObject = element.asJsonObject
                    
                    // Check if this looks like a map with numeric keys: {"0": {...}, "1": {...}}
                    val keys = jsonObject.keySet()
                    val allKeysAreNumeric = keys.isNotEmpty() && keys.all { key ->
                        key.toIntOrNull() != null
                    }
                    
                    if (allKeysAreNumeric) {
                        // ✅ Map with numeric keys - extract values as list
                        val exercises = mutableListOf<T>()
                        keys.sortedBy { it.toInt() }.forEach { key ->
                            try {
                                val exerciseElement = jsonObject.get(key)
                                val exercise = gson.fromJson(exerciseElement, clazz)
                                if (exercise != null) exercises.add(exercise)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to parse exercise at key: $key")
                            }
                        }
                        exercises
                    } else {
                        // ✅ Single exercise object - wrap into a list
                        try {
                            val singleExercise = gson.fromJson(element, clazz)
                            if (singleExercise != null) listOf(singleExercise) else emptyList()
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse single exercise object")
                            emptyList()
                        }
                    }
                }
                
                else -> {
                    Timber.w("ExerciseJsonParser: Unexpected JSON format - ${element.javaClass.simpleName}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "ExerciseJsonParser: Failed to parse exercises JSON")
            emptyList()
        }
    }
    
    /**
     * Parse exercises JSON with wrapped format (e.g., {"exercises": [...]})
     */
    fun <T> parseWrappedExercises(json: String, clazz: Class<T>, wrapperKey: String = "exercises"): List<T> {
        if (json.isBlank()) return emptyList()
        
        return try {
            val element = JsonParser.parseString(json)
            val gson = Gson()
            
            when {
                element.isJsonObject -> {
                    val jsonObject = element.asJsonObject
                    if (jsonObject.has(wrapperKey)) {
                        val exercisesElement = jsonObject.get(wrapperKey)
                        parseExercisesElement(exercisesElement, gson, clazz)
                    } else {
                        // Fallback: treat the entire object as a map of exercises
                        val mapType = TypeToken.getParameterized(Map::class.java, String::class.java, clazz).type
                        val exerciseMap = gson.fromJson<Map<String, T>>(element, mapType) ?: emptyMap()
                        exerciseMap.values.toList()
                    }
                }
                
                element.isJsonArray -> {
                    // Direct array format
                    parseExercisesElement(element, gson, clazz)
                }
                
                else -> {
                    Timber.w("ExerciseJsonParser: Unexpected wrapped JSON format")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "ExerciseJsonParser: Failed to parse wrapped exercises JSON")
            emptyList()
        }
    }
    
    /**
     * Helper to parse exercises element that could be array or object
     */
    fun <T> parseExercisesElement(exercisesElement: com.google.gson.JsonElement, gson: Gson, clazz: Class<T>): List<T> {
        return when {
            exercisesElement.isJsonArray -> {
                try {
                    val listType = TypeToken.getParameterized(List::class.java, clazz).type
                    gson.fromJson<List<T>>(exercisesElement, listType) ?: emptyList()
                } catch (e: Exception) {
                    Timber.w(e, "ExerciseJsonParser: Failed to parse exercises as array")
                    emptyList()
                }
            }
            
            exercisesElement.isJsonObject -> {
                val jsonObject = exercisesElement.asJsonObject
                val keys = jsonObject.keySet()
                val allKeysAreNumeric = keys.isNotEmpty() && keys.all { key ->
                    key.toIntOrNull() != null
                }
                
                if (allKeysAreNumeric) {
                    // Map with numeric keys - extract values as list  
                    val exercises = mutableListOf<T>()
                    keys.sortedBy { it.toInt() }.forEach { key ->
                        try {
                            val exerciseElement = jsonObject.get(key)
                            val exercise = gson.fromJson(exerciseElement, clazz)
                            if (exercise != null) exercises.add(exercise)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse exercise at key: $key")
                        }
                    }
                    exercises
                } else {
                    // Single exercise object - wrap into a list
                    try {
                        val singleExercise = gson.fromJson(exercisesElement, clazz)
                        if (singleExercise != null) listOf(singleExercise) else emptyList()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse single exercise object")
                        emptyList()
                    }
                }
            }
            
            else -> {
                Timber.w("ExerciseJsonParser: Exercises element is neither array nor object")
                emptyList()
            }
        }
    }
}