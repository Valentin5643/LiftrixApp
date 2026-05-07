package com.example.liftrix.data.service

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.core.json.ExerciseJsonParser
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.domain.service.AnalyticsService
import com.google.gson.Gson
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ExerciseConsistencyValidator @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val canonicalAdapter: CanonicalWorkoutJsonAdapter,
    private val analyticsService: AnalyticsService,
    private val gson: Gson
) {

    sealed class ConsistencyResult {
        data object Valid : ConsistencyResult()
        data class JsonParseError(val reason: String) : ConsistencyResult()
        data class CountMismatch(
            val jsonCount: Int,
            val normalizedCount: Int,
            val canSelfHeal: Boolean
        ) : ConsistencyResult()
        data class IdMismatch(
            val missingInJson: Set<String>,
            val missingInNormalized: Set<String>,
            val canSelfHeal: Boolean
        ) : ConsistencyResult()
        data class StaleTotals(
            val computed: Totals,
            val stored: Totals,
            val canSelfHeal: Boolean
        ) : ConsistencyResult()
        data class FatalError(val reason: String) : ConsistencyResult()
    }

    data class Totals(
        val totalVolume: Double?,
        val totalSets: Int?,
        val completedSets: Int?
    )

    suspend fun validateWorkoutConsistency(
        workoutId: String,
        userId: String,
        exercisesJson: String?
    ): ConsistencyResult {
        if (!OfflineArchitectureFlags.ENABLE_JSON_CONSISTENCY_VALIDATION) {
            return ConsistencyResult.Valid
        }

        if (exercisesJson.isNullOrBlank()) {
            return ConsistencyResult.Valid
        }

        val jsonExercises = try {
            parseExercisesFromJson(exercisesJson)
        } catch (e: Exception) {
            logConsistencyEvent(
                "exercise_consistency_parse_error",
                mapOf("workout_id" to workoutId, "error" to (e.message ?: "unknown"))
            )
            return ConsistencyResult.JsonParseError(e.message ?: "Unknown parse error")
        }

        val normalizedExercises = exerciseDao.getExercisesForWorkout(workoutId, userId)

        if (jsonExercises.size != normalizedExercises.size) {
            logConsistencyEvent(
                "exercise_consistency_mismatch",
                mapOf(
                    "workout_id" to workoutId,
                    "json_count" to jsonExercises.size,
                    "normalized_count" to normalizedExercises.size,
                    "type" to "COUNT_MISMATCH"
                )
            )
            return ConsistencyResult.CountMismatch(
                jsonCount = jsonExercises.size,
                normalizedCount = normalizedExercises.size,
                canSelfHeal = true
            )
        }

        val jsonIds = jsonExercises.mapNotNull { it.exerciseId ?: it.exerciseLibraryId }.toSet()
        if (jsonIds.isNotEmpty()) {
            val normalizedIds = normalizedExercises.map { it.exerciseLibraryId }.toSet()
            if (jsonIds != normalizedIds) {
                val missingInJson = normalizedIds - jsonIds
                val missingInNormalized = jsonIds - normalizedIds
                logConsistencyEvent(
                    "exercise_consistency_mismatch",
                    mapOf(
                        "workout_id" to workoutId,
                        "missing_in_json" to missingInJson.size,
                        "missing_in_normalized" to missingInNormalized.size,
                        "type" to "ID_MISMATCH"
                    )
                )
                return ConsistencyResult.IdMismatch(
                    missingInJson = missingInJson,
                    missingInNormalized = missingInNormalized,
                    canSelfHeal = missingInNormalized.isNotEmpty()
                )
            }
        }

        val computedTotals = calculateTotals(jsonExercises)
        val storedTotals = extractStoredTotals(exercisesJson)

        if (hasStoredTotals(storedTotals) && totalsMismatch(computedTotals, storedTotals)) {
            logConsistencyEvent(
                "exercise_totals_stale",
                mapOf(
                    "workout_id" to workoutId,
                    "computed_volume" to (computedTotals.totalVolume ?: 0.0),
                    "stored_volume" to (storedTotals.totalVolume ?: 0.0)
                )
            )
            return ConsistencyResult.StaleTotals(
                computed = computedTotals,
                stored = storedTotals,
                canSelfHeal = true
            )
        }

        return ConsistencyResult.Valid
    }

    suspend fun selfHealFromNormalized(
        workoutId: String,
        userId: String
    ): String {
        val exercises = exerciseDao.getExercisesForWorkout(workoutId, userId)
        val setsByExercise = exercises.associate { exercise ->
            exercise.id to exerciseSetDao.getSetsForExercise(exercise.id, userId)
        }

        logConsistencyEvent(
            "exercise_consistency_self_heal_generated",
            mapOf("workout_id" to workoutId, "exercise_count" to exercises.size)
        )

        return canonicalAdapter.serializeFromNormalized(exercises, setsByExercise)
    }

    private fun parseExercisesFromJson(exercisesJson: String): List<JsonExercise> {
        val element = JsonParser.parseString(exercisesJson)
        val gsonExercises = when {
            element.isJsonObject && element.asJsonObject.has("exercises") ->
                ExerciseJsonParser.parseExercisesElement(
                    element.asJsonObject.get("exercises"),
                    gson,
                    JsonExercise::class.java
                )
            else -> ExerciseJsonParser.parseExercises(exercisesJson, JsonExercise::class.java)
        }

        return gsonExercises
    }

    private fun calculateTotals(exercises: List<JsonExercise>): Totals {
        val totalSets = exercises.sumOf { it.sets.size }
        val completedSets = exercises.sumOf { exercise ->
            exercise.sets.count { set ->
                set.completed == true || set.completedAtEpochMilli != null
            }
        }
        val totalVolume = exercises.sumOf { exercise ->
            exercise.sets.sumOf { set ->
                val weight = set.weightKg ?: set.weight ?: 0.0
                val reps = set.reps ?: set.repsCount ?: 0
                weight * reps
            }
        }

        return Totals(
            totalVolume = totalVolume,
            totalSets = totalSets,
            completedSets = completedSets
        )
    }

    private fun extractStoredTotals(exercisesJson: String): Totals {
        return try {
            val element = JsonParser.parseString(exercisesJson)
            if (!element.isJsonObject) {
                return Totals(null, null, null)
            }

            val obj = element.asJsonObject
            val metadata = obj.getAsJsonObject("metadata")
            val totalVolume = when {
                obj.has("totalVolume") -> obj.get("totalVolume").asDouble
                metadata?.has("totalVolumeKg") == true -> metadata.get("totalVolumeKg").asDouble
                else -> null
            }
            val totalSets = when {
                obj.has("totalSets") -> obj.get("totalSets").asInt
                metadata?.has("totalSets") == true -> metadata.get("totalSets").asInt
                else -> null
            }
            val completedSets = when {
                obj.has("completedSets") -> obj.get("completedSets").asInt
                else -> null
            }

            Totals(totalVolume, totalSets, completedSets)
        } catch (_: Exception) {
            Totals(null, null, null)
        }
    }

    private fun hasStoredTotals(totals: Totals): Boolean {
        return totals.totalVolume != null || totals.totalSets != null || totals.completedSets != null
    }

    private fun totalsMismatch(computed: Totals, stored: Totals): Boolean {
        val volumeMismatch = if (stored.totalVolume != null && computed.totalVolume != null) {
            abs(stored.totalVolume - computed.totalVolume) > VOLUME_TOLERANCE
        } else {
            false
        }
        val setsMismatch = if (stored.totalSets != null && computed.totalSets != null) {
            stored.totalSets != computed.totalSets
        } else {
            false
        }
        val completedMismatch = if (stored.completedSets != null && computed.completedSets != null) {
            stored.completedSets != computed.completedSets
        } else {
            false
        }

        return volumeMismatch || setsMismatch || completedMismatch
    }

    private suspend fun logConsistencyEvent(eventName: String, parameters: Map<String, Any>) {
        analyticsService.logEvent(eventName, parameters)
    }

    private data class JsonExercise(
        val id: String? = null,
        val exerciseId: String? = null,
        val exerciseLibraryId: String? = null,
        val name: String? = null,
        val muscleGroup: String? = null,
        val orderIndex: Int? = null,
        val sets: List<JsonExerciseSet> = emptyList()
    )

    private data class JsonExerciseSet(
        val setNumber: Int? = null,
        val reps: Int? = null,
        val repsCount: Int? = null,
        val weight: Double? = null,
        val weightKg: Double? = null,
        val rpe: Int? = null,
        val rpeValue: Int? = null,
        val completed: Boolean? = null,
        val completedAtEpochMilli: Long? = null
    )

    companion object {
        private const val VOLUME_TOLERANCE = 0.01
    }
}
