package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.SubscriptionTier
import com.example.liftrix.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SubscriptionDao
 * Tests CRUD operations and subscription-specific queries
 */
@RunWith(RobolectricTestRunner::class)
class SubscriptionDaoTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var userProfileDao: UserProfileDao

    private val testUserId = "test_user_123"
    private val testUserProfile = UserProfileEntity(
        id = testUserId,
        userId = testUserId,
        displayName = "Test User",
        age = 25,
        weightKg = 70.0,
        heightCm = 175.0,
        fitnessLevel = "intermediate",
        goals = "strength",
        availableEquipment = "gym",
        workoutFrequency = 3,
        preferredWorkoutDuration = 60,
        completedAt = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()

        subscriptionDao = database.subscriptionDao()
        userProfileDao = database.userProfileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertSubscription and getSubscription should work correctly`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val subscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = "active",
            startedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(30 * 24 * 60 * 60) // 30 days
        )

        // When
        subscriptionDao.insertSubscription(subscription)
        val retrieved = subscriptionDao.getSubscription(testUserId)

        // Then
        assertNotNull(retrieved)
        assertEquals(testUserId, retrieved.userId)
        assertEquals(SubscriptionTier.PREMIUM, retrieved.tier)
        assertEquals("active", retrieved.status)
        assertTrue(retrieved.isActive)
    }

    @Test
    fun `getSubscriptionFlow should emit updates`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val subscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.FREE
        )

        // When
        val flow = subscriptionDao.getSubscriptionFlow(testUserId)
        
        // Initially null
        assertNull(flow.first())
        
        // Insert subscription
        subscriptionDao.insertSubscription(subscription)
        val retrieved = flow.first()

        // Then
        assertNotNull(retrieved)
        assertEquals(SubscriptionTier.FREE, retrieved.tier)
    }

    @Test
    fun `updateSubscription should modify existing subscription`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val subscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.FREE,
            status = "active"
        )
        subscriptionDao.insertSubscription(subscription)

        // When
        val updatedSubscription = subscription.copy(
            tier = SubscriptionTier.PREMIUM,
            status = "trial",
            updatedAt = Instant.now()
        )
        subscriptionDao.updateSubscription(updatedSubscription)
        val retrieved = subscriptionDao.getSubscription(testUserId)

        // Then
        assertNotNull(retrieved)
        assertEquals(SubscriptionTier.PREMIUM, retrieved.tier)
        assertEquals("trial", retrieved.status)
    }

    @Test
    fun `deleteSubscription should remove subscription`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val subscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM
        )
        subscriptionDao.insertSubscription(subscription)

        // When
        subscriptionDao.deleteSubscription(testUserId)
        val retrieved = subscriptionDao.getSubscription(testUserId)

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `getActiveSubscriptions should return only active subscriptions`() = runTest {
        // Given
        val user1 = testUserProfile.copy(id = "user1", userId = "user1")
        val user2 = testUserProfile.copy(id = "user2", userId = "user2")
        userProfileDao.insertProfile(user1)
        userProfileDao.insertProfile(user2)

        val activeSubscription = SubscriptionEntity(
            userId = "user1",
            tier = SubscriptionTier.PREMIUM,
            status = "active"
        )
        val cancelledSubscription = SubscriptionEntity(
            userId = "user2",
            tier = SubscriptionTier.PREMIUM,
            status = "cancelled"
        )

        subscriptionDao.insertSubscription(activeSubscription)
        subscriptionDao.insertSubscription(cancelledSubscription)

        // When
        val activeSubscriptions = subscriptionDao.getActiveSubscriptions()

        // Then
        assertEquals(1, activeSubscriptions.size)
        assertEquals("user1", activeSubscriptions.first().userId)
        assertEquals("active", activeSubscriptions.first().status)
    }

    @Test
    fun `getUnsyncedSubscriptions should return unsynced subscriptions`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val unsyncedSubscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            isSynced = false
        )
        subscriptionDao.insertSubscription(unsyncedSubscription)

        // When
        val unsyncedSubscriptions = subscriptionDao.getUnsyncedSubscriptions()

        // Then
        assertEquals(1, unsyncedSubscriptions.size)
        assertEquals(testUserId, unsyncedSubscriptions.first().userId)
        assertEquals(false, unsyncedSubscriptions.first().isSynced)
    }

    @Test
    fun `markAsSynced should update sync status`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val subscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            isSynced = false,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(subscription)

        // When
        subscriptionDao.markAsSynced(testUserId, 2L)
        val retrieved = subscriptionDao.getSubscription(testUserId)

        // Then
        assertNotNull(retrieved)
        assertTrue(retrieved.isSynced)
        assertEquals(2L, retrieved.syncVersion)
    }

    @Test
    fun `hasActivePremiumSubscription should detect active premium subscriptions`() = runTest {
        // Given
        userProfileDao.insertProfile(testUserProfile)
        val currentTime = Instant.now().epochSecond
        val activeSubscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = "active",
            expiresAt = Instant.now().plusSeconds(24 * 60 * 60) // 1 day from now
        )
        subscriptionDao.insertSubscription(activeSubscription)

        // When
        val hasActivePremium = subscriptionDao.hasActivePremiumSubscription(testUserId, currentTime)

        // Then
        assertTrue(hasActivePremium)
    }

    @Test
    fun `subscription entity isActive property should work correctly`() {
        // Given
        val activeSubscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = "active",
            expiresAt = Instant.now().plusSeconds(24 * 60 * 60) // 1 day from now
        )

        val expiredSubscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = "active",
            expiresAt = Instant.now().minusSeconds(24 * 60 * 60) // 1 day ago
        )

        val freeSubscription = SubscriptionEntity(
            userId = testUserId,
            tier = SubscriptionTier.FREE,
            status = "active"
        )

        // Then
        assertTrue(activeSubscription.isActive)
        assertEquals(false, expiredSubscription.isActive)
        assertEquals(false, freeSubscription.isActive)
    }
}