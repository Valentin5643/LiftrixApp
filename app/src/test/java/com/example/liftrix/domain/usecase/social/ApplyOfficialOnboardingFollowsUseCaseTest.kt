package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplyOfficialOnboardingFollowsUseCaseTest {
    private lateinit var seedWorkoutPostsUseCase: SeedWorkoutPostsUseCase
    private lateinit var followRepository: FollowRepository
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var useCase: ApplyOfficialOnboardingFollowsUseCase

    @Before
    fun setUp() {
        seedWorkoutPostsUseCase = mockk()
        followRepository = mockk()
        syncScheduler = mockk(relaxed = true)
        useCase = ApplyOfficialOnboardingFollowsUseCase(
            seedWorkoutPostsUseCase = seedWorkoutPostsUseCase,
            followRepository = followRepository,
            syncScheduler = syncScheduler
        )
    }

    @Test
    fun `invoke seeds before applying selected official follows`() = runTest {
        val snapshot = snapshot(
            equipment = setOf(Equipment.BODYWEIGHT_ONLY.name),
            goals = emptySet()
        )
        coEvery { seedWorkoutPostsUseCase(USER_ID) } returns Result.success(25)
        coEvery { followRepository.getFollowStatus(USER_ID, any()) } returns Result.success(null)
        coEvery { followRepository.sendFollowRequest(USER_ID, any(), "ONBOARDING_INTERESTS", null) } returns Result.success(FollowStatus.FOLLOWING)

        val result = useCase(USER_ID, snapshot)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                OfficialLiftrixAccountCatalog.COACH_ID,
                OfficialLiftrixAccountCatalog.CHALLENGE_ID,
                OfficialLiftrixAccountCatalog.BEGINNER_ID,
                OfficialLiftrixAccountCatalog.CALISTHENICS_ID
            ),
            result.getOrThrow()
        )
        coVerifyOrder {
            seedWorkoutPostsUseCase(USER_ID)
            followRepository.getFollowStatus(USER_ID, OfficialLiftrixAccountCatalog.COACH_ID)
            followRepository.sendFollowRequest(USER_ID, OfficialLiftrixAccountCatalog.COACH_ID, "ONBOARDING_INTERESTS", null)
        }
        verify { syncScheduler.enqueueFollowRelationshipSync(USER_ID, forceSync = true) }
    }

    @Test
    fun `invoke skips already followed and pending official accounts`() = runTest {
        val snapshot = snapshot(
            equipment = setOf(Equipment.BARBELL.name),
            goals = setOf(FitnessGoal.INCREASE_STRENGTH.name)
        )
        coEvery { seedWorkoutPostsUseCase(USER_ID) } returns Result.success(0)
        coEvery { followRepository.getFollowStatus(USER_ID, OfficialLiftrixAccountCatalog.COACH_ID) } returns Result.success(FollowStatus.FOLLOWING)
        coEvery { followRepository.getFollowStatus(USER_ID, OfficialLiftrixAccountCatalog.CHALLENGE_ID) } returns Result.success(FollowStatus.PENDING_SENT)
        coEvery { followRepository.getFollowStatus(USER_ID, OfficialLiftrixAccountCatalog.BEGINNER_ID) } returns Result.success(null)
        coEvery { followRepository.getFollowStatus(USER_ID, OfficialLiftrixAccountCatalog.POWERLIFTING_ID) } returns Result.success(null)
        coEvery { followRepository.sendFollowRequest(USER_ID, any(), "ONBOARDING_INTERESTS", null) } returns Result.success(FollowStatus.FOLLOWING)

        val result = useCase(USER_ID, snapshot)

        assertEquals(
            listOf(
                OfficialLiftrixAccountCatalog.BEGINNER_ID,
                OfficialLiftrixAccountCatalog.POWERLIFTING_ID
            ),
            result.getOrThrow()
        )
        coVerify(exactly = 0) { followRepository.sendFollowRequest(USER_ID, OfficialLiftrixAccountCatalog.COACH_ID, any(), any()) }
        coVerify(exactly = 0) { followRepository.sendFollowRequest(USER_ID, OfficialLiftrixAccountCatalog.CHALLENGE_ID, any(), any()) }
        verify { syncScheduler.enqueueFollowRelationshipSync(USER_ID, forceSync = true) }
    }

    @Test
    fun `invoke does not enqueue sync when no new follow is created`() = runTest {
        val snapshot = snapshot(equipment = emptySet(), goals = emptySet())
        coEvery { seedWorkoutPostsUseCase(USER_ID) } returns Result.success(0)
        coEvery { followRepository.getFollowStatus(USER_ID, any()) } returns Result.success(FollowStatus.FOLLOWING)

        val result = useCase(USER_ID, snapshot)

        assertEquals(emptyList<String>(), result.getOrThrow())
        verify(exactly = 0) { syncScheduler.enqueueFollowRelationshipSync(any(), any(), any()) }
    }

    @Test
    fun `invoke returns failure when follow creation fails`() = runTest {
        val expected = IllegalStateException("follow failed")
        val snapshot = snapshot(equipment = emptySet(), goals = emptySet())
        coEvery { seedWorkoutPostsUseCase(USER_ID) } returns Result.success(0)
        coEvery { followRepository.getFollowStatus(USER_ID, any()) } returns Result.success(null)
        coEvery { followRepository.sendFollowRequest(USER_ID, any(), "ONBOARDING_INTERESTS", null) } returns Result.failure(expected)

        val result = useCase(USER_ID, snapshot)

        assertTrue(result.isFailure)
    }

    private fun snapshot(
        equipment: Set<String>,
        goals: Set<String>
    ): OnboardingDataSnapshot = OnboardingDataSnapshot(
        userId = USER_ID,
        selectedEquipment = equipment,
        selectedGoals = goals
    )

    private companion object {
        private const val USER_ID = "user-1"
    }
}
