package com.example.liftrix.domain.service.widget

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMetric
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.math.ln
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuscleHeatmapCalculator @Inject constructor(
    private val exerciseSetDao: ExerciseSetDao
) : WidgetCalculator {

    override suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> = calculate(
        userId = userId,
        startDate = startDate,
        endDate = endDate,
        metric = MuscleHeatmapMetric.VOLUME
    )

    suspend fun calculate(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        gender: MuscleHeatmapGender = MuscleHeatmapGender.MALE,
        viewSide: MuscleHeatmapViewSide = MuscleHeatmapViewSide.FRONT,
        metric: MuscleHeatmapMetric = MuscleHeatmapMetric.VOLUME,
        colorMode: MuscleHeatmapColorMode = MuscleHeatmapColorMode.APP_GRADIENT
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val rawValues = loadRawValues(
            userId = userId,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            metric = metric
        )
        val normalizedValues = normalize(rawValues)
        val muscleValues = MuscleGroup.getPrimaryMuscleGroups().map { muscleGroup ->
            val rawValue = rawValues[muscleGroup] ?: 0f
            mapOf(
                "muscleGroup" to muscleGroup.name,
                "displayLabel" to muscleGroup.displayName,
                "rawValue" to rawValue,
                "normalizedIntensity" to (normalizedValues[muscleGroup] ?: 0f),
                "formattedValue" to metric.formatValue(rawValue)
            )
        }
        val topTrained = muscleValues
            .filter { (it["rawValue"] as Float) > 0f }
            .sortedByDescending { it["rawValue"] as Float }
            .take(5)
        val recoveryCandidates = if (topTrained.isEmpty()) {
            muscleValues
        } else {
            muscleValues
                .filter { (it["rawValue"] as Float) >= 0f }
                .sortedBy { it["rawValue"] as Float }
                .take(5)
        }

        mapOf(
            "gender" to gender.configValue,
            "viewSide" to viewSide.configValue,
            "metric" to metric.configValue,
            "colorMode" to colorMode.configValue,
            "totalValue" to rawValues.values.sum(),
            "muscleValues" to muscleValues,
            "topTrained" to topTrained,
            "recoveryCandidates" to recoveryCandidates,
            "isEmpty" to rawValues.values.none { it > 0f }
        )
    }

    private suspend fun loadRawValues(
        userId: String,
        startDate: String,
        endDate: String,
        metric: MuscleHeatmapMetric
    ): Map<MuscleGroup, Float> {
        val rows = exerciseSetDao.getHeatmapExerciseActivity(userId, startDate, endDate)
        val totals = mutableMapOf<MuscleGroup, Float>()

        rows.forEach { row ->
            val baseValue = when (metric) {
                MuscleHeatmapMetric.VOLUME -> row.total_volume.toFloat()
                MuscleHeatmapMetric.SETS -> row.total_sets.toFloat()
                MuscleHeatmapMetric.REPS -> row.total_reps.toFloat()
                MuscleHeatmapMetric.SESSIONS -> row.total_sessions.toFloat()
            }

            if (baseValue <= 0f) {
                return@forEach
            }

            val weightedMuscles = weightedMusclesForActivity(
                primary = row.primary_muscle_group.toExerciseCategory(),
                secondary = row.secondary_muscle_groups.toExerciseCategories(),
                movementPattern = row.movement_pattern.orEmpty(),
                exerciseName = row.exercise_name,
                isCompound = row.is_compound != 0
            )

            weightedMuscles.forEach { weightedMuscle ->
                totals[weightedMuscle.muscleGroup] =
                    (totals[weightedMuscle.muscleGroup] ?: 0f) + (baseValue * weightedMuscle.weight)
            }
        }

        return totals
    }

    private fun normalize(rawValues: Map<MuscleGroup, Float>): Map<MuscleGroup, Float> {
        val positives = rawValues.values.filter { it > 0f }.sorted()
        if (positives.isEmpty()) {
            return MuscleGroup.getPrimaryMuscleGroups().associateWith { 0f }
        }

        val reference = if (positives.size >= 4) {
            positives[((positives.size - 1) * 0.95f).toInt()]
        } else {
            positives.last()
        }.coerceAtLeast(1f)

        return MuscleGroup.getPrimaryMuscleGroups().associateWith { muscleGroup ->
            val value = rawValues[muscleGroup] ?: 0f
            if (value <= 0f) {
                0f
            } else {
                (ln(1f + value) / ln(1f + reference)).coerceIn(0f, 1f)
            }
        }
    }

    private data class WeightedMuscle(
        val muscleGroup: MuscleGroup,
        val weight: Float
    )

    private fun weightedMusclesForActivity(
        primary: ExerciseCategory?,
        secondary: List<ExerciseCategory>,
        movementPattern: String,
        exerciseName: String,
        isCompound: Boolean
    ): List<WeightedMuscle> {
        val weighted = linkedMapOf<MuscleGroup, Float>()

        primary?.toHeatmapMuscles(movementPattern, exerciseName, isCompound)?.forEach { muscle ->
            weighted[muscle.muscleGroup] = maxOf(weighted[muscle.muscleGroup] ?: 0f, muscle.weight)
        }

        secondary.flatMap { it.toHeatmapMuscles(movementPattern, exerciseName, isCompound) }
            .forEach { muscle ->
                val secondaryWeight = muscle.weight * 0.45f
                weighted[muscle.muscleGroup] = maxOf(weighted[muscle.muscleGroup] ?: 0f, secondaryWeight)
            }

        return weighted.map { (muscleGroup, weight) -> WeightedMuscle(muscleGroup, weight) }
    }

    private fun ExerciseCategory.toHeatmapMuscles(
        movementPattern: String,
        exerciseName: String,
        isCompound: Boolean
    ): List<WeightedMuscle> {
        val movement = movementPattern.lowercase()
        val name = exerciseName.lowercase()
        return when (this) {
            ExerciseCategory.LEGS -> when {
                movement.contains("squat") || movement.contains("lunge") -> listOf(
                    WeightedMuscle(MuscleGroup.QUADRICEPS, 1f),
                    WeightedMuscle(MuscleGroup.GLUTES, 0.9f),
                    WeightedMuscle(MuscleGroup.HAMSTRINGS, 0.65f),
                    WeightedMuscle(MuscleGroup.CALVES, 0.35f)
                )
                movement.contains("hinge") || movement.contains("deadlift") -> listOf(
                    WeightedMuscle(MuscleGroup.HAMSTRINGS, 1f),
                    WeightedMuscle(MuscleGroup.GLUTES, 0.9f),
                    WeightedMuscle(MuscleGroup.LOWER_BACK, 0.65f),
                    WeightedMuscle(MuscleGroup.UPPER_BACK, 0.45f)
                )
                else -> listOf(
                    WeightedMuscle(MuscleGroup.QUADRICEPS, 1f),
                    WeightedMuscle(MuscleGroup.HAMSTRINGS, 0.75f),
                    WeightedMuscle(MuscleGroup.GLUTES, 0.75f),
                    WeightedMuscle(MuscleGroup.CALVES, 0.45f)
                )
            }
            ExerciseCategory.QUADRICEPS -> listOf(WeightedMuscle(MuscleGroup.QUADRICEPS, 1f))
            ExerciseCategory.HAMSTRINGS -> listOf(WeightedMuscle(MuscleGroup.HAMSTRINGS, 1f))
            ExerciseCategory.GLUTES -> listOf(WeightedMuscle(MuscleGroup.GLUTES, 1f))
            ExerciseCategory.CALVES -> listOf(WeightedMuscle(MuscleGroup.CALVES, 1f))
            ExerciseCategory.BACK -> when {
                movement.contains("hinge") || movement.contains("deadlift") -> listOf(
                    WeightedMuscle(MuscleGroup.UPPER_BACK, 0.75f),
                    WeightedMuscle(MuscleGroup.LOWER_BACK, 0.8f),
                    WeightedMuscle(MuscleGroup.HAMSTRINGS, 0.55f),
                    WeightedMuscle(MuscleGroup.GLUTES, 0.55f)
                )
                name.contains("pulldown") ||
                    name.contains("pull down") ||
                    name.contains("pull-up") ||
                    name.contains("pull up") ||
                    name.contains("chin-up") ||
                    name.contains("chin up") -> listOf(
                        WeightedMuscle(MuscleGroup.LATS, 1f),
                        WeightedMuscle(MuscleGroup.UPPER_BACK, 0.35f),
                        WeightedMuscle(MuscleGroup.BICEPS, 0.45f),
                        WeightedMuscle(MuscleGroup.FOREARMS, 0.3f)
                    )
                name.contains("row") -> listOf(
                    WeightedMuscle(MuscleGroup.UPPER_BACK, 1f),
                    WeightedMuscle(MuscleGroup.LATS, 0.65f),
                    WeightedMuscle(MuscleGroup.BICEPS, 0.45f),
                    WeightedMuscle(MuscleGroup.FOREARMS, 0.3f)
                )
                else -> listOf(
                    WeightedMuscle(MuscleGroup.LATS, 0.75f),
                    WeightedMuscle(MuscleGroup.UPPER_BACK, 0.75f)
                )
            }
            ExerciseCategory.CHEST -> listOf(WeightedMuscle(MuscleGroup.CHEST, 1f))
            ExerciseCategory.SHOULDERS -> listOf(WeightedMuscle(MuscleGroup.SHOULDERS, 1f))
            ExerciseCategory.ARMS -> listOf(
                WeightedMuscle(MuscleGroup.BICEPS, 0.8f),
                WeightedMuscle(MuscleGroup.TRICEPS, 0.8f),
                WeightedMuscle(MuscleGroup.FOREARMS, 0.55f)
            )
            ExerciseCategory.BICEPS -> listOf(WeightedMuscle(MuscleGroup.BICEPS, 1f))
            ExerciseCategory.TRICEPS -> listOf(WeightedMuscle(MuscleGroup.TRICEPS, 1f))
            ExerciseCategory.CORE, ExerciseCategory.ABS -> listOf(WeightedMuscle(MuscleGroup.CORE, 1f))
            ExerciseCategory.FULL_BODY -> listOf(
                WeightedMuscle(MuscleGroup.QUADRICEPS, 0.8f),
                WeightedMuscle(MuscleGroup.GLUTES, 0.8f),
                WeightedMuscle(MuscleGroup.LATS, 0.55f),
                WeightedMuscle(MuscleGroup.UPPER_BACK, 0.55f),
                WeightedMuscle(MuscleGroup.CHEST, 0.65f),
                WeightedMuscle(MuscleGroup.SHOULDERS, 0.55f),
                WeightedMuscle(MuscleGroup.CORE, 0.8f)
            )
            ExerciseCategory.CARDIO,
            ExerciseCategory.FLEXIBILITY,
            ExerciseCategory.OTHER -> emptyList()
        }.let { muscles ->
            if (isCompound) muscles else muscles.take(1)
        }
    }

    private fun String.toExerciseCategory(): ExerciseCategory? {
        val normalized = trim()
            .replace('-', '_')
            .replace(' ', '_')
            .uppercase()

        return ExerciseCategory.entries.firstOrNull { it.name == normalized }
    }

    private fun String?.toExerciseCategories(): List<ExerciseCategory> {
        if (isNullOrBlank()) {
            return emptyList()
        }

        return trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .mapNotNull { token ->
                token.trim()
                    .trim('"')
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.toExerciseCategory()
            }
    }
}
