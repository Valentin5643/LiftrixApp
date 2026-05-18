package com.example.liftrix.widget

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.WeightUnitManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Singleton
class LiftrixHomeWidgetDataSource @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutDao: WorkoutDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val weightUnitManager: WeightUnitManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun loadSnapshots(): LiftrixHomeWidgetSnapshots {
        val now = System.currentTimeMillis()
        return try {
            val userId = authRepository.getCurrentUserId()?.value
            if (userId.isNullOrBlank()) {
                return LiftrixHomeWidgetSnapshots.empty(now)
            }

            val today = LocalDate.now()
            val recentStart = today.minusDays(29)
            val completedWorkouts = workoutDao.getCompletedWorkoutsForStats(userId = userId, limit = 1000)
            val completedDates = completedWorkouts.map { it.date }.toSet()
            val dailyVolumes = workoutDao.getDailyVolumesByDateRange(
                userId = userId,
                startDate = recentStart.toString(),
                endDate = today.toString()
            )
            val jsonMetrics = calculateJsonMetrics(
                completedWorkouts = completedWorkouts,
                startDate = recentStart,
                endDate = today
            )
            val volumeByDate = jsonMetrics.dailyVolumeKg.toMutableMap()
            dailyVolumes.forEach { volume ->
                val date = runCatching { LocalDate.parse(volume.date) }.getOrNull() ?: return@forEach
                val existingVolume = volumeByDate[date] ?: 0.0
                volumeByDate[date] = maxOf(existingVolume, volume.total_volume)
            }
            val totalVolumeKg = volumeByDate.values.sum()
            val currentStreakWeeks = calculateCurrentWeekStreak(completedDates, today)
            val hasData = completedDates.isNotEmpty()
            val totalVolumeLabel = formatVolume(totalVolumeKg)
            val recentDays = (4L downTo 0L).map { offset ->
                val date = today.minusDays(offset)
                ConsistencyDay(
                    dateLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).take(1),
                    isActive = completedDates.contains(date),
                    volumeLabel = volumeByDate[date]?.takeIf { it > 0.0 }?.let(::formatVolume),
                    isToday = date == today
                )
            }
            val normalizedFeaturedExercise = exerciseSetDao.getExerciseRankings(
                userId = userId,
                startDate = recentStart.toString(),
                endDate = today.toString(),
                limit = 1
            ).firstOrNull()?.let { ranking ->
                FeaturedExercise(
                    name = ranking.exercise_name,
                    weightLabel = formatWeight(ranking.max_estimated_1rm),
                    repsLabel = "${ranking.total_sets} sets"
                )
            }
            val featuredExercise = normalizedFeaturedExercise ?: jsonMetrics.featuredExercise

            LiftrixHomeWidgetSnapshots(
                streak = StreakWidgetSnapshot(
                    currentStreakWeeks = currentStreakWeeks,
                    hasData = hasData,
                    lastUpdatedMillis = now
                ),
                consistency = ConsistencyWidgetSnapshot(
                    currentStreakWeeks = currentStreakWeeks,
                    lastSevenDays = recentDays,
                    totalVolumeLabel = totalVolumeLabel,
                    totalVolumeTrend = null,
                    lastUpdatedMillis = now
                ),
                dashboard = DashboardWidgetSnapshot(
                    currentStreakWeeks = currentStreakWeeks,
                    featuredExercise = featuredExercise,
                    recentVolumeDays = recentDays,
                    totalVolumeLabel = totalVolumeLabel,
                    recentPrState = if (featuredExercise != null) "Top" else null,
                    hasActiveSession = workoutDao.getActiveWorkoutForUser(userId) != null,
                    lastUpdatedMillis = now
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load native home widget snapshots")
            LiftrixHomeWidgetSnapshots.empty(now)
        }
    }

    private fun calculateCurrentWeekStreak(completedDates: Set<LocalDate>, today: LocalDate): Int {
        if (completedDates.isEmpty()) return 0
        val completedWeekStarts = completedDates.map(::startOfWeek).toSet()
        var cursor = startOfWeek(today)
        if (cursor !in completedWeekStarts) {
            cursor = cursor.minusWeeks(1)
        }
        var streak = 0
        while (cursor in completedWeekStarts) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    private fun startOfWeek(date: LocalDate): LocalDate {
        return date.with(DayOfWeek.MONDAY)
    }

    private fun calculateJsonMetrics(
        completedWorkouts: List<WorkoutEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): JsonWorkoutMetrics {
        val dailyVolumeKg = mutableMapOf<LocalDate, Double>()
        val exerciseAccumulators = mutableMapOf<String, ExerciseAccumulator>()

        completedWorkouts
            .asSequence()
            .filter { it.date in startDate..endDate }
            .forEach { workout ->
                var workoutVolumeKg = 0.0
                parseExerciseObjects(workout.exercisesJson).forEach { exercise ->
                    val exerciseName = exercise.stringValue("name")
                        ?: exercise.stringValue("libraryExerciseName")
                        ?: exercise.stringValue("exerciseId")
                        ?: "Exercise"
                    val accumulator = exerciseAccumulators.getOrPut(exerciseName) {
                        ExerciseAccumulator(name = exerciseName)
                    }

                    exercise.arrayValue("sets").forEach { set ->
                        val completed = set.booleanValue("completed")
                            ?: set.hasNonNullValue("completedAtEpochMilli")
                            ?: set.hasNonNullValue("completedAt")
                        if (completed == false) return@forEach

                        val reps = set.intValue("actualReps")
                            ?: set.intValue("reps")
                            ?: set.intValue("repsCount")
                            ?: set.intValue("targetReps")
                            ?: return@forEach
                        val weightKg = set.doubleValue("actualWeight")
                            ?: set.doubleValue("weight")
                            ?: set.doubleValue("weightKg")
                            ?: set.doubleValue("targetWeight")
                            ?: return@forEach

                        if (reps <= 0 || weightKg <= 0.0) return@forEach

                        val volumeKg = weightKg * reps
                        workoutVolumeKg += volumeKg
                        accumulator.totalSets += 1
                        accumulator.maxEstimatedOneRepMaxKg = maxOf(
                            accumulator.maxEstimatedOneRepMaxKg,
                            weightKg * (1.0 + reps.toDouble() / 30.0)
                        )
                    }
                }

                if (workoutVolumeKg > 0.0) {
                    dailyVolumeKg[workout.date] = (dailyVolumeKg[workout.date] ?: 0.0) + workoutVolumeKg
                }
            }

        val featuredExercise = exerciseAccumulators.values
            .filter { it.totalSets > 0 && it.maxEstimatedOneRepMaxKg > 0.0 }
            .maxByOrNull { it.maxEstimatedOneRepMaxKg * 0.3 + it.totalSets * 2.0 }
            ?.let { exercise ->
                FeaturedExercise(
                    name = exercise.name,
                    weightLabel = formatWeight(exercise.maxEstimatedOneRepMaxKg),
                    repsLabel = "${exercise.totalSets} sets"
                )
            }

        return JsonWorkoutMetrics(
            dailyVolumeKg = dailyVolumeKg,
            featuredExercise = featuredExercise
        )
    }

    private fun parseExerciseObjects(exercisesJson: String): List<JsonObject> {
        if (exercisesJson.isBlank() || exercisesJson == "[]") return emptyList()
        val element = runCatching { json.parseToJsonElement(exercisesJson) }.getOrNull() ?: return emptyList()
        val exercisesElement = when (element) {
            is JsonArray -> element
            is JsonObject -> element["exercises"] as? JsonArray
            else -> null
        } ?: return emptyList()

        return exercisesElement.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.arrayValue(key: String): List<JsonObject> {
        val array = this[key]?.runCatchingJsonArray() ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }

    private fun JsonElement.runCatchingJsonArray(): JsonArray? {
        return runCatching { jsonArray }.getOrNull()
    }

    private fun JsonObject.stringValue(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.doubleValue(key: String): Double? {
        return this[key]?.jsonPrimitive?.doubleOrNull
    }

    private fun JsonObject.intValue(key: String): Int? {
        return this[key]?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        return this[key]?.jsonPrimitive?.booleanOrNull
    }

    private fun JsonObject.hasNonNullValue(key: String): Boolean? {
        return if (containsKey(key)) {
            this[key]?.jsonPrimitive?.contentOrNull != null
        } else {
            null
        }
    }

    private fun formatVolume(volumeKg: Double): String {
        if (volumeKg <= 0.0) return "0 ${weightUnitManager.getCurrentUnitSymbol()}"
        val displayValue = weightUnitManager.convertForDisplay(volumeKg, WeightUnit.KILOGRAMS)
        val suffix = weightUnitManager.getCurrentUnitSymbol()
        val abbreviated = when {
            displayValue >= 100_000 -> "${(displayValue / 1000).toInt()}k"
            displayValue >= 10_000 -> "%.1fk".format(displayValue / 1000).trimTrailingZero()
            displayValue >= 100 -> displayValue.toInt().toString()
            else -> "%.1f".format(displayValue).trimTrailingZero()
        }
        return "$abbreviated $suffix"
    }

    private fun formatWeight(weightKg: Double): String {
        if (weightKg <= 0.0) return "0 ${weightUnitManager.getCurrentUnitSymbol()}"
        val displayValue = weightUnitManager.convertForDisplay(weightKg, WeightUnit.KILOGRAMS)
        val suffix = weightUnitManager.getCurrentUnitSymbol()
        val rounded = when {
            displayValue >= 100 -> displayValue.toInt().toString()
            displayValue >= 10 -> "%.1f".format(displayValue).trimTrailingZero()
            else -> "%.1f".format(displayValue).trimTrailingZero()
        }
        return "$rounded $suffix"
    }

    private fun String.trimTrailingZero(): String {
        return replace(".0", "")
    }

    private data class JsonWorkoutMetrics(
        val dailyVolumeKg: Map<LocalDate, Double>,
        val featuredExercise: FeaturedExercise?
    )

    private data class ExerciseAccumulator(
        val name: String,
        var totalSets: Int = 0,
        var maxEstimatedOneRepMaxKg: Double = 0.0
    )
}
