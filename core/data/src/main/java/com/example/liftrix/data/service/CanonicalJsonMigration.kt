package com.example.liftrix.data.service

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.domain.service.AnalyticsService
import com.google.gson.JsonParser
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalJsonMigration @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val canonicalAdapter: CanonicalWorkoutJsonAdapter,
    private val analyticsService: AnalyticsService
) {

    data class MigrationResult(
        val migratedCount: Int,
        val skippedCount: Int,
        val failedCount: Int
    )

    suspend fun migrateUserWorkouts(userId: String): MigrationResult {
        if (!OfflineArchitectureFlags.ENABLE_CANONICAL_JSON_FORMAT) {
            return MigrationResult(0, 0, 0)
        }

        val workouts = workoutDao.getAllWorkoutsForUserSync(userId)
        var migrated = 0
        var skipped = 0
        var failed = 0

        workouts.forEach { workout ->
            try {
                if (workout.exercisesJson.isBlank()) {
                    skipped++
                    return@forEach
                }
                if (isCanonicalJson(workout.exercisesJson)) {
                    skipped++
                    return@forEach
                }

                val exercises = exerciseDao.getExercisesForWorkout(workout.id, userId)
                if (exercises.isEmpty()) {
                    skipped++
                    return@forEach
                }

                val setsByExercise = exercises.associate { exercise ->
                    exercise.id to exerciseSetDao.getSetsForExercise(exercise.id, userId)
                }

                val canonicalJson = canonicalAdapter.serializeFromNormalized(exercises, setsByExercise)
                workoutDao.updateExercisesJsonForWorkout(
                    workoutId = workout.id,
                    userId = userId,
                    exercisesJson = canonicalJson,
                    updatedAt = Instant.now(),
                    lastModified = System.currentTimeMillis()
                )
                migrated++
            } catch (e: Exception) {
                failed++
                Timber.w(e, "CanonicalJsonMigration: Failed to migrate workout ${workout.id}")
            }
        }

        analyticsService.logEvent(
            "canonical_json_migration",
            mapOf(
                "user_id" to userId,
                "migrated" to migrated,
                "skipped" to skipped,
                "failed" to failed
            )
        )

        return MigrationResult(migrated, skipped, failed)
    }

    private fun isCanonicalJson(exercisesJson: String): Boolean {
        return try {
            val element = JsonParser.parseString(exercisesJson)
            element.isJsonObject &&
                element.asJsonObject.get("format")?.asString == "canonical"
        } catch (_: Exception) {
            false
        }
    }
}
