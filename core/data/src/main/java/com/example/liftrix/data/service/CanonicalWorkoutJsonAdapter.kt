package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.RPE
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.Equipment
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalWorkoutJsonAdapter @Inject constructor(
    private val exerciseLibraryDao: ExerciseLibraryDao,
    private val gson: Gson
) {

    suspend fun serializeFromNormalized(
        exercises: List<ExerciseEntity>,
        setsByExercise: Map<Long, List<ExerciseSetEntity>>
    ): String {
        val canonicalExercises = exercises.sortedBy { it.orderIndex }.map { exercise ->
            val sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.setNumber }
            val library = exerciseLibraryDao.getExerciseById(exercise.exerciseLibraryId)
            val name = library?.name ?: exercise.exerciseLibraryId
            val muscleGroup = library?.primaryMuscleGroup?.name ?: "UNKNOWN"

            val canonicalSets = sets.map { set ->
                CanonicalSet(
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weight = set.weightKg?.toDouble(),
                    rpe = set.rpe,
                    completed = set.completedAt != null
                )
            }

            val totalVolume = canonicalSets.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
            val completedSets = canonicalSets.count { it.completed }

            CanonicalExercise(
                id = exercise.id.toString(),
                exerciseId = exercise.exerciseLibraryId,
                name = name,
                muscleGroup = muscleGroup,
                orderIndex = exercise.orderIndex,
                sets = canonicalSets,
                totalVolume = totalVolume,
                totalSets = canonicalSets.size,
                completedSets = completedSets
            )
        }

        val payload = CanonicalWorkoutPayload(
            exercises = canonicalExercises,
            version = CANONICAL_VERSION,
            format = CANONICAL_FORMAT
        )

        return gson.toJson(payload)
    }

    fun serializeFromDomain(exercises: List<Exercise>): String {
        val canonicalExercises = exercises.sortedBy { it.orderIndex }.map { exercise ->
            val canonicalSets = exercise.sets.sortedBy { it.setNumber }.map { set ->
                CanonicalSet(
                    setNumber = set.setNumber,
                    reps = set.reps?.count,
                    weight = set.weight?.kilograms,
                    rpe = set.rpe?.value,
                    completed = set.completedAt != null
                )
            }

            val totalVolume = canonicalSets.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
            val completedSets = canonicalSets.count { it.completed }

            CanonicalExercise(
                id = exercise.id.value,
                exerciseId = exercise.libraryExercise.id,
                name = exercise.libraryExercise.name,
                muscleGroup = exercise.libraryExercise.primaryMuscleGroup.name,
                orderIndex = exercise.orderIndex,
                sets = canonicalSets,
                totalVolume = totalVolume,
                totalSets = canonicalSets.size,
                completedSets = completedSets
            )
        }

        val payload = CanonicalWorkoutPayload(
            exercises = canonicalExercises,
            version = CANONICAL_VERSION,
            format = CANONICAL_FORMAT
        )

        return gson.toJson(payload)
    }

    fun deserializeToDomain(json: String, workoutId: WorkoutId): List<Exercise> {
        val exercises = parseCanonicalExercises(json)
        return exercises.mapIndexedNotNull { index, canonicalExercise ->
            try {
                val libraryExercise = createFallbackLibrary(canonicalExercise)
                val sets = canonicalExercise.sets.mapIndexedNotNull { setIndex, set ->
                    val repsCount = set.reps?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
                    val reps = Reps(repsCount)
                    val weight = set.weight?.let { Weight.fromKilograms(it) }
                    val rpe = set.rpe?.takeIf { it in RPE.MIN_RPE..RPE.MAX_RPE }?.let { RPE(it) }
                    val completedAt = if (set.completed == true) Instant.EPOCH else null
                    val setNumber = set.setNumber?.takeIf { it > 0 } ?: (setIndex + 1)

                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = setNumber,
                        reps = reps,
                        weight = weight,
                        rpe = rpe,
                        completedAt = completedAt
                    )
                }

                Exercise.createSafe(
                    id = ExerciseId(canonicalExercise.id ?: ExerciseId.generate().value),
                    workoutId = workoutId,
                    libraryExercise = libraryExercise,
                    orderIndex = canonicalExercise.orderIndex ?: index,
                    sets = sets,
                    notes = null,
                    createdAt = Instant.now()
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun isCanonicalJson(json: String): Boolean {
        return try {
            val element = JsonParser.parseString(json)
            element.isJsonObject &&
                element.asJsonObject.get("format")?.asString == CANONICAL_FORMAT
        } catch (_: Exception) {
            false
        }
    }

    private data class CanonicalWorkoutPayload(
        val exercises: List<CanonicalExercise>,
        val version: Int,
        val format: String
    )

    private data class CanonicalExercise(
        val id: String,
        val exerciseId: String,
        val name: String,
        val muscleGroup: String,
        val orderIndex: Int,
        val sets: List<CanonicalSet>,
        val totalVolume: Double,
        val totalSets: Int,
        val completedSets: Int
    )

    private data class CanonicalSet(
        val setNumber: Int,
        val reps: Int?,
        val weight: Double?,
        val rpe: Int?,
        val completed: Boolean
    )

    private data class CanonicalWorkoutPayloadInput(
        val exercises: List<CanonicalExerciseInput> = emptyList()
    )

    private data class CanonicalExerciseInput(
        val id: String? = null,
        val exerciseId: String? = null,
        val name: String? = null,
        val muscleGroup: String? = null,
        val orderIndex: Int? = null,
        val sets: List<CanonicalSetInput> = emptyList()
    )

    private data class CanonicalSetInput(
        val setNumber: Int? = null,
        val reps: Int? = null,
        val weight: Double? = null,
        val rpe: Int? = null,
        val completed: Boolean? = null
    )

    private fun parseCanonicalExercises(json: String): List<CanonicalExerciseInput> {
        return try {
            val element = JsonParser.parseString(json)
            when {
                element.isJsonArray -> {
                    val listType = com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        CanonicalExerciseInput::class.java
                    ).type
                    gson.fromJson<List<CanonicalExerciseInput>>(element, listType) ?: emptyList()
                }
                element.isJsonObject && element.asJsonObject.has("exercises") -> {
                    val payload = gson.fromJson(element, CanonicalWorkoutPayloadInput::class.java)
                    payload.exercises
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun createFallbackLibrary(exercise: CanonicalExerciseInput): ExerciseLibrary {
        val name = exercise.name?.takeIf { it.isNotBlank() }
            ?: exercise.exerciseId?.takeIf { it.isNotBlank() }
            ?: "Exercise"
        val category = parseCategory(exercise.muscleGroup)
        val id = exercise.exerciseId?.takeIf { it.isNotBlank() } ?: name

        return ExerciseLibrary(
            id = id,
            name = name,
            primaryMuscleGroup = category,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "unknown",
            difficultyLevel = 1,
            instructions = null,
            isCompound = category.isCompound,
            searchableTerms = listOf(name)
        )
    }

    private fun parseCategory(raw: String?): ExerciseCategory {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return ExerciseCategory.OTHER
        }
        return ExerciseCategory.values().firstOrNull { category ->
            category.name.equals(normalized, ignoreCase = true) ||
                category.displayName.equals(normalized, ignoreCase = true)
        } ?: ExerciseCategory.OTHER
    }

    companion object {
        private const val CANONICAL_VERSION = 2
        private const val CANONICAL_FORMAT = "canonical"
    }
}
