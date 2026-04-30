package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.ai.WorkoutAiContextSnapshot
import com.example.liftrix.domain.model.ai.WorkoutAiHistorySummary
import com.example.liftrix.domain.model.ai.WorkoutGenerationCatalogExercise
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.domain.usecase.settings.SettingsQueryUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BuildWorkoutAiContextUseCase @Inject constructor(
    private val profileQueryUseCase: ProfileQueryUseCase,
    private val settingsQueryUseCase: SettingsQueryUseCase,
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val exerciseQueryUseCase: ExerciseQueryUseCase
) {

    suspend operator fun invoke(userId: String): LiftrixResult<WorkoutAiContextSnapshot> = withContext(Dispatchers.IO) {
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "BUILD_WORKOUT_AI_CONTEXT_FAILED",
                    errorMessage = throwable.message ?: "Failed to build workout AI context",
                    analyticsContext = mapOf("operation" to "BUILD_WORKOUT_AI_CONTEXT", "user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            coroutineScope {
                val profileDeferred = async { profileQueryUseCase.getById(userId).getOrNull() }
                val settingsDeferred = async { settingsQueryUseCase(userId).first().getOrNull() }
                val historyCountDeferred = async { workoutQueryUseCase.getHistoryCount(userId).getOrElse { 0 } }
                val catalogDeferred = async { exerciseQueryUseCase().getOrElse { emptyList() } }

                val profile = profileDeferred.await()
                val settings = settingsDeferred.await()
                val historyCount = historyCountDeferred.await()
                val equipment = profile?.availableEquipment
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: setOf(Equipment.BODYWEIGHT_ONLY)
                val catalog = catalogDeferred.await()
                    .filter { it.equipment in equipment }
                    .sortedWith(compareBy({ it.difficultyLevel }, { it.name }))
                    .take(MAX_CONTEXT_CATALOG)
                    .map(WorkoutGenerationCatalogExercise::fromExercise)

                WorkoutAiContextSnapshot(
                    userId = userId,
                    availableEquipment = equipment,
                    otherEquipmentSummary = profile?.otherEquipment?.take(MAX_OTHER_EQUIPMENT_CHARS),
                    fitnessGoals = profile?.fitnessGoals.orEmpty(),
                    goalPriority = profile?.goalsPriority.orEmpty(),
                    ageBand = ageBand(profile?.age),
                    profileCompletionPercent = profile?.profileCompletionPercentage ?: 0,
                    weightUnit = (settings?.weightUnit ?: WeightUnit.getSystemDefault()).symbol,
                    experienceLevel = experienceLevel(profile, historyCount),
                    recentHistory = WorkoutAiHistorySummary(
                        totalWorkouts = profile?.totalWorkouts ?: historyCount,
                        currentStreak = profile?.currentStreak ?: 0,
                        completedHistoryCount = historyCount,
                        recentExerciseCount = catalog.size
                    ),
                    exerciseCatalog = catalog
                )
            }
        }
    }

    private fun ageBand(age: Int?): String = when (age) {
        null -> "adult"
        in 13..17 -> "teen"
        in 18..64 -> "adult"
        else -> "older_adult"
    }

    private fun experienceLevel(profile: UserProfile?, historyCount: Int): WorkoutProgramLevel = when {
        (profile?.totalWorkouts ?: historyCount) >= 100 -> WorkoutProgramLevel.ADVANCED
        (profile?.totalWorkouts ?: historyCount) >= 20 -> WorkoutProgramLevel.INTERMEDIATE
        else -> WorkoutProgramLevel.BEGINNER
    }

    companion object {
        private const val MAX_CONTEXT_CATALOG = 40
        private const val MAX_OTHER_EQUIPMENT_CHARS = 120
    }
}
