package com.example.liftrix.data.serialization

import com.example.liftrix.domain.model.*
import com.google.gson.*
import timber.log.Timber
import java.lang.reflect.Type
import java.time.Duration
import java.time.Instant

/**
 * Custom Gson deserializer for ExerciseSet that enforces domain validation
 * and handles corrupted data gracefully by providing safe defaults
 */
class ExerciseSetDeserializer : JsonDeserializer<ExerciseSet> {
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ExerciseSet {
        val jsonObject = json.asJsonObject
        
        // Extract basic fields
        val setNumber = jsonObject.get("setNumber")?.asInt ?: 1
        val notes = jsonObject.get("notes")?.takeIf { !it.isJsonNull }?.asString
        
        // Extract metrics with null safety
        val reps = jsonObject.get("reps")?.takeIf { !it.isJsonNull }?.let { repsJson ->
            try {
                when {
                    repsJson.isJsonObject -> {
                        val repsObj = repsJson.asJsonObject
                        val count = repsObj.get("count")?.asInt ?: 0
                        if (count > 0) Reps.of(count) else null
                    }
                    repsJson.isJsonPrimitive -> {
                        val count = repsJson.asInt
                        if (count > 0) Reps.of(count) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize reps: ${e.message}")
                null
            }
        }
        
        val weight = jsonObject.get("weight")?.takeIf { !it.isJsonNull }?.let { weightJson ->
            try {
                when {
                    weightJson.isJsonObject -> {
                        val weightObj = weightJson.asJsonObject
                        val kg = weightObj.get("kilograms")?.asDouble ?: 0.0
                        if (kg > 0) Weight.fromKilograms(kg) else null
                    }
                    weightJson.isJsonPrimitive -> {
                        val kg = weightJson.asDouble
                        if (kg > 0) Weight.fromKilograms(kg) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize weight: ${e.message}")
                null
            }
        }
        
        val time = jsonObject.get("time")?.takeIf { !it.isJsonNull }?.let { timeJson ->
            try {
                when {
                    timeJson.isJsonObject -> {
                        val timeObj = timeJson.asJsonObject
                        val seconds = timeObj.get("seconds")?.asLong ?: 0L
                        if (seconds > 0) Duration.ofSeconds(seconds) else null
                    }
                    timeJson.isJsonPrimitive -> {
                        val seconds = timeJson.asLong
                        if (seconds > 0) Duration.ofSeconds(seconds) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize time: ${e.message}")
                null
            }
        }
        
        val distance = jsonObject.get("distance")?.takeIf { !it.isJsonNull }?.let { distanceJson ->
            try {
                when {
                    distanceJson.isJsonObject -> {
                        val distanceObj = distanceJson.asJsonObject
                        val meters = distanceObj.get("meters")?.asFloat ?: 0f
                        if (meters > 0) Distance.fromMeters(meters) else null
                    }
                    distanceJson.isJsonPrimitive -> {
                        val meters = distanceJson.asFloat
                        if (meters > 0) Distance.fromMeters(meters) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize distance: ${e.message}")
                null
            }
        }
        
        val rpe = jsonObject.get("rpe")?.takeIf { !it.isJsonNull }?.let { rpeJson ->
            try {
                when {
                    rpeJson.isJsonObject -> {
                        val rpeObj = rpeJson.asJsonObject
                        val value = rpeObj.get("value")?.asInt ?: 0
                        if (value > 0) RPE.fromInt(value) else null
                    }
                    rpeJson.isJsonPrimitive -> {
                        val value = rpeJson.asInt
                        if (value > 0) RPE.fromInt(value) else null
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize RPE: ${e.message}")
                null
            }
        }
        
        val completedAt = jsonObject.get("completedAt")?.takeIf { !it.isJsonNull }?.let { completedJson ->
            try {
                when {
                    completedJson.isJsonPrimitive -> {
                        val timestamp = completedJson.asLong
                        Instant.ofEpochMilli(timestamp)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize completedAt: ${e.message}")
                null
            }
        }
        
        // Extract or generate ID
        val id = jsonObject.get("id")?.takeIf { !it.isJsonNull }?.let { idJson ->
            try {
                when {
                    idJson.isJsonObject -> {
                        val idObj = idJson.asJsonObject
                        idObj.get("value")?.asString ?: ExerciseSetId.generate().value
                    }
                    idJson.isJsonPrimitive -> {
                        idJson.asString
                    }
                    else -> ExerciseSetId.generate().value
                }
            } catch (e: Exception) {
                Timber.w("Failed to deserialize ID: ${e.message}")
                ExerciseSetId.generate().value
            }
        } ?: ExerciseSetId.generate().value
        
        // CRITICAL: Ensure at least one metric is present to satisfy domain validation
        // If all metrics are null, provide a safe default (1 rep)
        val safeReps = if (reps == null && weight == null && time == null && distance == null) {
            Timber.w("🔥 DESERIALIZER-DEBUG: Found set with no metrics, providing safe default (1 rep) for setNumber $setNumber")
            Reps.of(1)
        } else {
            reps
        }
        
        return try {
            ExerciseSet(
                id = ExerciseSetId(id),
                setNumber = setNumber,
                reps = safeReps,
                weight = weight,
                time = time,
                distance = distance,
                rpe = rpe,
                completedAt = completedAt,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "🔥 DESERIALIZER-DEBUG: Failed to create ExerciseSet even with safe defaults, creating minimal valid set")
            // Last resort: create a minimal valid set
            ExerciseSet(
                id = ExerciseSetId(id),
                setNumber = setNumber,
                reps = Reps.of(1), // Guaranteed to be valid
                completedAt = completedAt,
                notes = notes
            )
        }
    }
}