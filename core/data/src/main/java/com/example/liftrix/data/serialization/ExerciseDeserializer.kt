package com.example.liftrix.data.serialization

import com.example.liftrix.domain.model.*
import com.google.gson.*
import timber.log.Timber
import java.lang.reflect.Type
import java.time.Instant

/**
 * Custom Gson deserializer for Exercise that properly handles ExerciseSets
 * and ensures all domain validation is respected
 */
class ExerciseDeserializer : JsonDeserializer<Exercise> {
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Exercise {
        val jsonObject = json.asJsonObject
        
        // Extract basic fields with safe defaults
        val id = jsonObject.get("id")?.takeIf { !it.isJsonNull }?.let { idJson ->
            try {
                when {
                    idJson.isJsonObject -> {
                        val idObj = idJson.asJsonObject
                        ExerciseId(idObj.get("value")?.asString ?: ExerciseId.generate().value)
                    }
                    idJson.isJsonPrimitive -> {
                        ExerciseId(idJson.asString)
                    }
                    else -> ExerciseId.generate()
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize Exercise ID: ${e.message}")
                ExerciseId.generate()
            }
        } ?: ExerciseId.generate()
        
        val workoutId = jsonObject.get("workoutId")?.takeIf { !it.isJsonNull }?.let { workoutIdJson ->
            try {
                when {
                    workoutIdJson.isJsonObject -> {
                        val workoutIdObj = workoutIdJson.asJsonObject
                        WorkoutId(workoutIdObj.get("value")?.asString ?: "")
                    }
                    workoutIdJson.isJsonPrimitive -> {
                        WorkoutId(workoutIdJson.asString)
                    }
                    else -> WorkoutId("")
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize Workout ID: ${e.message}")
                WorkoutId("")
            }
        } ?: WorkoutId("")
        
        // Extract library exercise
        val libraryExercise = jsonObject.get("libraryExercise")?.takeIf { !it.isJsonNull }?.let { libExJson ->
            try {
                context.deserialize<ExerciseLibrary>(libExJson, ExerciseLibrary::class.java)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize library exercise, creating placeholder")
                ExerciseLibrary(
                    id = "placeholder",
                    name = "Unknown Exercise",
                    primaryMuscleGroup = ExerciseCategory.CHEST,
                    equipment = Equipment.BODYWEIGHT_ONLY,
                    secondaryMuscleGroups = emptyList(),
                    movementPattern = "Unknown",
                    difficultyLevel = 5,
                    instructions = "No instructions available",
                    isCompound = false,
                    searchableTerms = emptyList()
                )
            }
        } ?: ExerciseLibrary(
            id = "placeholder",
            name = "Unknown Exercise",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "Unknown",
            difficultyLevel = 5,
            instructions = "No instructions available",
            isCompound = false,
            searchableTerms = emptyList()
        )
        
        val orderIndex = jsonObject.get("orderIndex")?.asInt ?: 0
        val notes = jsonObject.get("notes")?.takeIf { !it.isJsonNull }?.asString
        
        // Extract target values safely
        val targetSets = jsonObject.get("targetSets")?.asInt
        val targetReps = jsonObject.get("targetReps")?.takeIf { !it.isJsonNull }?.let { targetRepsJson ->
            try {
                when {
                    targetRepsJson.isJsonObject -> {
                        val repsObj = targetRepsJson.asJsonObject
                        val count = repsObj.get("count")?.asInt ?: 0
                        if (count > 0) count else null
                    }
                    targetRepsJson.isJsonPrimitive -> {
                        val count = targetRepsJson.asInt
                        if (count > 0) count else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize target reps: ${e.message}")
                null
            }
        }
        
        val targetWeight = jsonObject.get("targetWeight")?.takeIf { !it.isJsonNull }?.let { targetWeightJson ->
            try {
                when {
                    targetWeightJson.isJsonObject -> {
                        val weightObj = targetWeightJson.asJsonObject
                        val kg = weightObj.get("kilograms")?.asDouble ?: 0.0
                        if (kg > 0) Weight.fromKilograms(kg) else null
                    }
                    targetWeightJson.isJsonPrimitive -> {
                        val kg = targetWeightJson.asDouble
                        if (kg > 0) Weight.fromKilograms(kg) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize target weight: ${e.message}")
                null
            }
        }
        
        val targetTime = jsonObject.get("targetTime")?.takeIf { !it.isJsonNull }?.let { targetTimeJson ->
            try {
                when {
                    targetTimeJson.isJsonObject -> {
                        val timeObj = targetTimeJson.asJsonObject
                        val seconds = timeObj.get("seconds")?.asLong ?: 0L
                        if (seconds > 0) java.time.Duration.ofSeconds(seconds) else null
                    }
                    targetTimeJson.isJsonPrimitive -> {
                        val seconds = targetTimeJson.asLong
                        if (seconds > 0) java.time.Duration.ofSeconds(seconds) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize target time: ${e.message}")
                null
            }
        }
        
        val targetDistance = jsonObject.get("targetDistance")?.takeIf { !it.isJsonNull }?.let { targetDistanceJson ->
            try {
                when {
                    targetDistanceJson.isJsonObject -> {
                        val distanceObj = targetDistanceJson.asJsonObject
                        val meters = distanceObj.get("meters")?.asFloat ?: 0f
                        if (meters > 0) Distance.fromMeters(meters) else null
                    }
                    targetDistanceJson.isJsonPrimitive -> {
                        val meters = targetDistanceJson.asFloat
                        if (meters > 0) Distance.fromMeters(meters) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize target distance: ${e.message}")
                null
            }
        }
        
        val createdAt = jsonObject.get("createdAt")?.takeIf { !it.isJsonNull }?.let { createdAtJson ->
            try {
                when {
                    createdAtJson.isJsonPrimitive -> {
                        val timestamp = createdAtJson.asLong
                        Instant.ofEpochMilli(timestamp)
                    }
                    else -> Instant.now()
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize createdAt: ${e.message}")
                Instant.now()
            }
        } ?: Instant.now()
        
        // Deserialize sets using our custom ExerciseSet deserializer
        val sets = jsonObject.get("sets")?.takeIf { !it.isJsonNull }?.let { setsJson ->
            try {
                when {
                    setsJson.isJsonArray -> {
                        val setsArray = setsJson.asJsonArray
                        setsArray.mapNotNull { setElement ->
                            try {
                                context.deserialize<ExerciseSet>(setElement, ExerciseSet::class.java)
                            } catch (e: Exception) {
                                Timber.w("Failed to deserialize exercise set, skipping: ${e.message}")
                                null
                            }
                        }
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize sets array: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
        
        return try {
            Exercise(
                id = id,
                workoutId = workoutId,
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeight = targetWeight,
                targetTime = targetTime,
                targetDistance = targetDistance,
                sets = sets,
                notes = notes,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Timber.e(e, "🔥 DESERIALIZER-DEBUG: Failed to create Exercise, creating minimal valid exercise")
            // Create a minimal valid exercise
            Exercise(
                id = id,
                workoutId = workoutId,
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                sets = emptyList(), // Start with empty sets to avoid validation issues
                createdAt = createdAt
            )
        }
    }
}