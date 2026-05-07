package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.domain.usecase.settings.SettingsQueryUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class BuildWorkoutAiContextUseCaseTest {

    private val profileQueryUseCase = mockk<ProfileQueryUseCase>()
    private val settingsQueryUseCase = mockk<SettingsQueryUseCase>()
    private val workoutQueryUseCase = mockk<WorkoutQueryUseCase>()
    private val exerciseQueryUseCase = mockk<ExerciseQueryUseCase>()
    private val useCase = BuildWorkoutAiContextUseCase(
        profileQueryUseCase,
        settingsQueryUseCase,
        workoutQueryUseCase,
        exerciseQueryUseCase
    )

    @Test
    fun `builds minimized user-scoped context without private profile fields`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(profile())
        coEvery { settingsQueryUseCase("user-1") } returns flowOf(Result.success(UserSettings("user-1", weightUnit = WeightUnit.KILOGRAMS)))
        coEvery { workoutQueryUseCase.getHistoryCount("user-1") } returns Result.success(24)
        coEvery { exerciseQueryUseCase() } returns Result.success(listOf(catalog("db_row", Equipment.DUMBBELLS), catalog("barbell_squat", Equipment.BARBELL)))

        val context = useCase("user-1").getOrThrow()

        assertEquals("user-1", context.userId)
        assertEquals(setOf(Equipment.DUMBBELLS), context.availableEquipment)
        assertEquals(listOf(FitnessGoal.BUILD_MUSCLE), context.fitnessGoals)
        assertEquals("kg", context.weightUnit)
        assertEquals(1, context.exerciseCatalog.size)
        assertFalse(context.toString().contains("Private Display"))
        assertFalse(context.toString().contains("private bio"))
    }

    @Test
    fun `falls back to bodyweight context when profile is absent`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { settingsQueryUseCase("user-1") } returns flowOf(Result.success(UserSettings.createDefault("user-1")))
        coEvery { workoutQueryUseCase.getHistoryCount("user-1") } returns Result.success(0)
        coEvery { exerciseQueryUseCase() } returns Result.success(listOf(catalog("push_up", Equipment.BODYWEIGHT_ONLY)))

        val context = useCase("user-1").getOrThrow()

        assertEquals(setOf(Equipment.BODYWEIGHT_ONLY), context.availableEquipment)
        assertEquals("adult", context.ageBand)
        assertTrue(context.exerciseCatalog.all { it.equipment == Equipment.BODYWEIGHT_ONLY })
    }

    private fun profile(): UserProfile {
        val now = LocalDateTime.now()
        return UserProfile(
            userId = "user-1",
            displayName = "Private Display",
            bio = "private bio",
            age = 31,
            weight = null,
            availableEquipment = listOf(Equipment.DUMBBELLS),
            otherEquipment = "adjustable dumbbells and mat",
            fitnessGoals = listOf(FitnessGoal.BUILD_MUSCLE),
            goalsPriority = mapOf(FitnessGoal.BUILD_MUSCLE to 1),
            lastActiveAt = null,
            totalWorkouts = 24,
            currentStreak = 3,
            longestStreak = 8,
            memberSince = now,
            profileCompletionPercentage = 80,
            completedAt = now,
            updatedAt = now
        )
    }

    private fun catalog(id: String, equipment: Equipment) = ExerciseLibrary(
        id = id,
        name = id.replace('_', ' '),
        primaryMuscleGroup = ExerciseCategory.BACK,
        equipment = equipment,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "Pull",
        difficultyLevel = 2,
        instructions = null,
        isCompound = true,
        searchableTerms = emptyList()
    )
}
