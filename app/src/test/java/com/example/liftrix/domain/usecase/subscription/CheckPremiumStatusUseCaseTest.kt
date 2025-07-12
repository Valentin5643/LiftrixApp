package com.example.liftrix.domain.usecase.subscription

import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class CheckPremiumStatusUseCaseTest {

    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    private lateinit var checkPremiumStatusUseCase: CheckPremiumStatusUseCase

    private val testUserId = "test_user_123"
    private val testInstant = Instant.now()

    @Before
    fun setup() {
        subscriptionRepository = mockk()
        getCurrentUserIdUseCase = mockk()
        checkPremiumStatusUseCase = CheckPremiumStatusUseCase(
            subscriptionRepository,
            getCurrentUserIdUseCase
        )
    }

    @Test
    fun `invoke returns true when user has active premium subscription`() = runTest {
        // Given
        val activePremiumSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600), // 1 hour from now
            autoRenew = true
        )

        coEvery { getCurrentUserIdUseCase() } returns testUserId
        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(activePremiumSubscription)

        // When
        val result = checkPremiumStatusUseCase().first()

        // Then
        assertTrue(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `invoke returns false when user has no subscription`() = runTest {
        // Given
        coEvery { getCurrentUserIdUseCase() } returns testUserId
        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(null)

        // When
        val result = checkPremiumStatusUseCase().first()

        // Then
        assertFalse(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `invoke returns false when user has free subscription`() = runTest {
        // Given
        val freeSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.FREE,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant,
            autoRenew = false
        )

        coEvery { getCurrentUserIdUseCase() } returns testUserId
        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(freeSubscription)

        // When
        val result = checkPremiumStatusUseCase().first()

        // Then
        assertFalse(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `invoke returns false when user has expired premium subscription`() = runTest {
        // Given
        val expiredPremiumSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.EXPIRED,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant.minusSeconds(7200), // 2 hours ago
            expiresAt = testInstant.minusSeconds(3600), // 1 hour ago
            autoRenew = false
        )

        coEvery { getCurrentUserIdUseCase() } returns testUserId
        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(expiredPremiumSubscription)

        // When
        val result = checkPremiumStatusUseCase().first()

        // Then
        assertFalse(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `invoke returns false when no authenticated user`() = runTest {
        // Given
        coEvery { getCurrentUserIdUseCase() } returns null

        // When
        val result = checkPremiumStatusUseCase().first()

        // Then
        assertFalse(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify(exactly = 0) { subscriptionRepository.getSubscriptionStatus(any()) }
    }

    @Test
    fun `invoke returns true when user has active trial subscription`() = runTest {
        // Given
        val trialSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.TRIAL,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant,
            trialEndsAt = testInstant.plusSeconds(3600), // 1 hour from now
            autoRenew = true
        )

        coEvery { getCurrentUserIdUseCase() } returns testUserId
        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(trialSubscription)

        // When
        val result = checkPremiumStatusUseCase().first()

        // Then
        assertTrue(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `checkForUser returns true when specific user has active premium subscription`() = runTest {
        // Given
        val activePremiumSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PRO,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            autoRenew = true
        )

        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(activePremiumSubscription)

        // When
        val result = checkPremiumStatusUseCase.checkForUser(testUserId).first()

        // Then
        assertTrue(result)
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `checkForUser returns false when user ID is blank`() = runTest {
        // Given
        val blankUserId = ""

        // When
        val result = checkPremiumStatusUseCase.checkForUser(blankUserId).first()

        // Then
        assertFalse(result)
        coVerify(exactly = 0) { subscriptionRepository.getSubscriptionStatus(any()) }
    }

    @Test
    fun `getSubscriptionDetails returns subscription when user is authenticated`() = runTest {
        // Given
        val activeSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            autoRenew = true
        )

        coEvery { getCurrentUserIdUseCase() } returns testUserId
        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(activeSubscription)

        // When
        val result = checkPremiumStatusUseCase.getSubscriptionDetails().first()

        // Then
        assertNotNull(result)
        assertEquals(testUserId, result?.userId)
        assertEquals(SubscriptionTier.PREMIUM, result?.tier)
        assertEquals(SubscriptionStatus.ACTIVE, result?.status)
        coVerify { getCurrentUserIdUseCase() }
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `getSubscriptionDetails returns null when no authenticated user`() = runTest {
        // Given
        coEvery { getCurrentUserIdUseCase() } returns null

        // When
        val result = checkPremiumStatusUseCase.getSubscriptionDetails().first()

        // Then
        assertNull(result)
        coVerify { getCurrentUserIdUseCase() }
        coVerify(exactly = 0) { subscriptionRepository.getSubscriptionStatus(any()) }
    }

    @Test
    fun `getSubscriptionDetailsForUser returns subscription for specific user`() = runTest {
        // Given
        val activeSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PRO,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            autoRenew = true
        )

        coEvery { subscriptionRepository.getSubscriptionStatus(testUserId) } returns flowOf(activeSubscription)

        // When
        val result = checkPremiumStatusUseCase.getSubscriptionDetailsForUser(testUserId).first()

        // Then
        assertNotNull(result)
        assertEquals(testUserId, result?.userId)
        assertEquals(SubscriptionTier.PRO, result?.tier)
        assertEquals(SubscriptionStatus.ACTIVE, result?.status)
        coVerify { subscriptionRepository.getSubscriptionStatus(testUserId) }
    }

    @Test
    fun `getSubscriptionDetailsForUser returns null when user ID is blank`() = runTest {
        // Given
        val blankUserId = ""

        // When
        val result = checkPremiumStatusUseCase.getSubscriptionDetailsForUser(blankUserId).first()

        // Then
        assertNull(result)
        coVerify(exactly = 0) { subscriptionRepository.getSubscriptionStatus(any()) }
    }
}