package com.example.liftrix.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.onboarding.WeightUnit
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.service.OnboardingDataStore
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.domain.usecase.profile.ProfileCommandUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelStateRestorationTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeProfileRepository()
    private val onboardingDataStore = FakeOnboardingDataStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoreStateFromHandle_restoresCollectionDraftFieldsAfterRecreation() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        val userId = "user-restore"

        val firstViewModel = createViewModel(savedStateHandle)
        firstViewModel.initializeOnboarding(userId)
        advanceUntilIdle()

        firstViewModel.handleEvent(OnboardingEvent.UpdateAge("34"))
        firstViewModel.handleEvent(OnboardingEvent.UpdateWeight("185"))
        firstViewModel.handleEvent(OnboardingEvent.UpdateWeightUnit(WeightUnit.POUNDS))
        firstViewModel.handleEvent(OnboardingEvent.ToggleEquipment(Equipment.DUMBBELLS))
        firstViewModel.handleEvent(OnboardingEvent.ToggleEquipment(Equipment.RESISTANCE_BANDS))
        firstViewModel.handleEvent(OnboardingEvent.UpdateOtherEquipment("Yoga mat"))
        firstViewModel.handleEvent(OnboardingEvent.ToggleGoal(FitnessGoal.BUILD_MUSCLE))
        firstViewModel.handleEvent(OnboardingEvent.ToggleGoal(FitnessGoal.IMPROVE_ENDURANCE))
        firstViewModel.handleEvent(OnboardingEvent.UpdateGoalPriority(FitnessGoal.BUILD_MUSCLE, 1))
        firstViewModel.handleEvent(OnboardingEvent.UpdateGoalPriority(FitnessGoal.IMPROVE_ENDURANCE, 2))
        firstViewModel.onNavigationStepChanged(OnboardingStep.GOALS)
        advanceUntilIdle()

        val recreatedViewModel = createViewModel(savedStateHandle)
        recreatedViewModel.initializeOnboarding(userId)
        advanceUntilIdle()

        val restoredState = recreatedViewModel.state.value as OnboardingState.StepActive
        val restoredProfileData = restoredState.profileData

        assertEquals(OnboardingStep.GOALS, restoredState.step)
        assertEquals("34", restoredProfileData.ageInput)
        assertEquals("185", restoredProfileData.weightInput)
        assertEquals(WeightUnit.POUNDS, restoredProfileData.weightUnit)
        assertEquals("Yoga mat", restoredProfileData.otherEquipmentInput)
        assertTrue(restoredProfileData.selectedEquipment.contains(Equipment.DUMBBELLS))
        assertTrue(restoredProfileData.selectedEquipment.contains(Equipment.RESISTANCE_BANDS))
        assertTrue(restoredProfileData.selectedGoals.contains(FitnessGoal.BUILD_MUSCLE))
        assertTrue(restoredProfileData.selectedGoals.contains(FitnessGoal.IMPROVE_ENDURANCE))
        assertEquals(1, restoredProfileData.goalsPriority[FitnessGoal.BUILD_MUSCLE])
        assertEquals(2, restoredProfileData.goalsPriority[FitnessGoal.IMPROVE_ENDURANCE])
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): OnboardingViewModel {
        return OnboardingViewModel(
            profileCommandUseCase = ProfileCommandUseCase(repository),
            validateProfileInputUseCase = ValidateProfileInputUseCase(),
            profileQueryUseCase = ProfileQueryUseCase(repository),
            savedStateHandle = savedStateHandle,
            profileRepository = repository,
            onboardingDataStore = onboardingDataStore
        )
    }

    private class FakeProfileRepository : ProfileRepository {
        override fun getProfile(userId: String): Flow<UserProfile?> = flowOf(null)

        override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)

        override suspend fun updatePartialProfile(userId: String, updates: Map<String, Any>): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteProfile(userId: String): Result<Unit> = Result.success(Unit)

        override suspend fun hasProfile(userId: String): Boolean = false

        override suspend fun hasCompletedProfile(userId: String): Boolean = false

        override suspend fun getUnsyncedCount(userId: String): Int = 0

        override suspend fun queueSync(userId: String): Result<Unit> = Result.success(Unit)

        override suspend fun syncNow(userId: String): Result<Unit> = Result.success(Unit)

        override suspend fun getUserProfile(userId: String): LiftrixResult<UserProfile?> =
            liftrixSuccess(null)

        override suspend fun saveUserProfile(profile: UserProfile): LiftrixResult<Unit> =
            liftrixSuccess(Unit)

        override suspend fun updateProfileCompletion(userId: String): LiftrixResult<Int> =
            liftrixSuccess(0)

        override suspend fun calculateStreakData(userId: String): LiftrixResult<StreakData> =
            liftrixSuccess(
                StreakData(
                    currentStreak = 0,
                    longestStreak = 0,
                    totalWorkouts = 0,
                    lastWorkoutDate = null
                )
            )

        override suspend fun updatePrivacySettings(userId: String, isPublic: Boolean): LiftrixResult<Unit> =
            liftrixSuccess(Unit)

        override suspend fun getPublicProfiles(limit: Int): LiftrixResult<List<UserProfile>> =
            liftrixSuccess(emptyList())

        override suspend fun getPublicProfile(userId: String): LiftrixResult<UserProfile?> =
            liftrixSuccess(null)
    }

    private class FakeOnboardingDataStore : OnboardingDataStore {
        override suspend fun storeOnboardingData(
            profileData: com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot
        ): LiftrixResult<Unit> = liftrixSuccess(Unit)

        override suspend fun hasPendingOnboardingData(): Boolean = false

        override suspend fun retrievePendingOnboardingData(
            newUserId: String
        ): LiftrixResult<com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot?> =
            liftrixSuccess(null)

        override suspend fun clearPendingOnboardingData(): LiftrixResult<Unit> = liftrixSuccess(Unit)

        override suspend fun getPendingDataSummary(): String = ""
    }
}
