package com.example.liftrix.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.billing.BillingRepository
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.SubscriptionDao
import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.SubscriptionTier as DataSubscriptionTier
import com.example.liftrix.data.mapper.SubscriptionMapper
import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for SubscriptionRepositoryImpl with real Room database
 * and mocked Google Play Billing integration.
 * 
 * These tests verify:
 * - Google Play Billing integration and purchase handling
 * - Local subscription data persistence and retrieval
 * - Subscription status validation and business logic
 * - Sync operations between Google Play and local storage
 * - Error handling for billing and database operations
 * 
 * Uses real Room database and mocked BillingRepository for controlled testing.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionRepositoryIntegrationTest {
    
    private lateinit var database: LiftrixDatabase
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var billingRepository: BillingRepository
    private lateinit var subscriptionMapper: SubscriptionMapper
    private lateinit var repository: SubscriptionRepositoryImpl
    
    private val testUserId = "test-user-subscription-123"
    private val testUserId2 = "test-user-subscription-456"
    private val testProductId = "premium_subscription"
    private val testSubscriptionId = "sub_123456789"
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create in-memory Room database
        database = Room.inMemoryDatabaseBuilder(
            context,
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()
        
        subscriptionDao = database.subscriptionDao()
        billingRepository = mockk()
        subscriptionMapper = SubscriptionMapper()
        
        repository = SubscriptionRepositoryImpl(
            subscriptionDao = subscriptionDao,
            subscriptionMapper = subscriptionMapper,
            billingRepository = billingRepository
        )
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun subscriptionStatusRetrievalWorksCorrectly() = runTest {
        // Given - Subscription entity in database
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = Instant.now().minusSeconds(3600),
            expiresAt = Instant.now().plusSeconds(2592000), // 30 days
            autoRenew = true,
            currency = "USD",
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // When - Get subscription status
        val result = repository.getSubscriptionStatus(testUserId).first()
        
        // Then - Should return correct domain model
        assertNotNull(result)
        assertEquals(testUserId, result.userId)
        assertEquals(SubscriptionTier.PREMIUM, result.tier)
        assertEquals(SubscriptionStatus.ACTIVE, result.status)
        assertEquals(SubscriptionProvider.GOOGLE_PLAY, result.provider)
        assertEquals(testProductId, result.productId)
        assertEquals(testSubscriptionId, result.subscriptionId)
        assertTrue(result.autoRenew)
        assertEquals("USD", result.currency)
    }
    
    @Test
    fun hasActivePremiumSubscriptionWorksCorrectly() = runTest {
        // Given - Active premium subscription
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now().minusSeconds(3600),
            expiresAt = Instant.now().plusSeconds(2592000), // 30 days
            autoRenew = true,
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // When - Check for active premium subscription
        val hasActive = repository.hasActivePremiumSubscription(testUserId).first()
        
        // Then - Should return true
        assertTrue(hasActive)
    }
    
    @Test
    fun hasActivePremiumSubscriptionReturnsFalseForFreeUser() = runTest {
        // Given - Free tier subscription
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.FREE,
            status = "active",
            provider = "none",
            startedAt = Instant.now(),
            autoRenew = false,
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // When - Check for active premium subscription
        val hasActive = repository.hasActivePremiumSubscription(testUserId).first()
        
        // Then - Should return false
        assertFalse(hasActive)
    }
    
    @Test
    fun subscriptionInsertionWorksCorrectly() = runTest {
        // Given - New subscription
        val subscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(2592000), // 30 days
            autoRenew = true,
            currency = "USD"
        )
        
        // When - Insert subscription
        val result = repository.insertSubscription(subscription)
        
        // Then - Should succeed
        assertTrue(result.isSuccess)
        
        // Verify in database
        val entity = subscriptionDao.getSubscription(testUserId)
        assertNotNull(entity)
        assertEquals(testUserId, entity.userId)
        assertEquals(DataSubscriptionTier.PREMIUM, entity.tier)
        assertEquals("active", entity.status)
        assertEquals("google_play", entity.provider)
        assertEquals(testProductId, entity.productId)
        assertEquals(testSubscriptionId, entity.subscriptionId)
        assertTrue(entity.autoRenew)
        assertEquals("USD", entity.currency)
    }
    
    @Test
    fun subscriptionUpdateWorksCorrectly() = runTest {
        // Given - Existing subscription
        val originalEntity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = Instant.now().minusSeconds(3600),
            expiresAt = Instant.now().plusSeconds(2592000),
            autoRenew = true,
            currency = "USD",
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(originalEntity)
        
        // Updated subscription
        val updatedSubscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PRO,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = originalEntity.startedAt,
            expiresAt = Instant.now().plusSeconds(5184000), // 60 days
            autoRenew = false,
            currency = "EUR"
        )
        
        // When - Update subscription
        val result = repository.updateSubscription(updatedSubscription)
        
        // Then - Should succeed
        assertTrue(result.isSuccess)
        
        // Verify update in database
        val updatedEntity = subscriptionDao.getSubscription(testUserId)
        assertNotNull(updatedEntity)
        assertEquals(DataSubscriptionTier.PRO, updatedEntity.tier)
        assertFalse(updatedEntity.autoRenew)
        assertEquals("EUR", updatedEntity.currency)
    }
    
    @Test
    fun googlePlaySyncHandlesPurchasesCorrectly() = runTest {
        // Given - Mock successful billing operations
        coEvery { billingRepository.initialize() } returns Result.success(Unit)
        coEvery { billingRepository.queryPurchases() } returns Result.success(emptyList())
        
        // When - Sync with Google Play
        val result = repository.syncWithGooglePlay(testUserId)
        
        // Then - Should complete successfully
        assertTrue(result.isSuccess)
        
        // Verify billing operations were called
        coVerify { billingRepository.initialize() }
        coVerify { billingRepository.queryPurchases() }
    }
    
    @Test
    fun googlePlaySyncHandlesBillingInitializationFailure() = runTest {
        // Given - Mock billing initialization failure
        val billingError = RuntimeException("Billing service unavailable")
        coEvery { billingRepository.initialize() } returns Result.failure(billingError)
        
        // When - Sync with Google Play
        val result = repository.syncWithGooglePlay(testUserId)
        
        // Then - Should fail with billing error
        assertTrue(result.isFailure)
        assertEquals(billingError, result.exceptionOrNull())
        
        // Verify billing operations
        coVerify { billingRepository.initialize() }
        coVerify(exactly = 0) { billingRepository.queryPurchases() }
    }
    
    @Test
    fun subscriptionCountWorksCorrectly() = runTest {
        // Given - No subscriptions initially
        assertEquals(0, repository.getSubscriptionCount(testUserId))
        
        // When - Add subscription
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // Then - Count should be 1
        assertEquals(1, repository.getSubscriptionCount(testUserId))
    }
    
    @Test
    fun subscriptionDeletionWorksCorrectly() = runTest {
        // Given - Existing subscription
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // Verify subscription exists
        assertEquals(1, repository.getSubscriptionCount(testUserId))
        
        // When - Delete subscription
        val result = repository.deleteSubscription(testUserId)
        
        // Then - Should succeed
        assertTrue(result.isSuccess)
        
        // Verify subscription removed
        assertEquals(0, repository.getSubscriptionCount(testUserId))
        assertNull(subscriptionDao.getSubscription(testUserId))
    }
    
    @Test
    fun activeSubscriptionsRetrievalWorksCorrectly() = runTest {
        // Given - Mix of active and inactive subscriptions
        val activeEntity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        
        val inactiveEntity = SubscriptionEntity(
            userId = testUserId2,
            tier = DataSubscriptionTier.FREE,
            status = "cancelled",
            provider = "none",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        
        subscriptionDao.insertSubscription(activeEntity)
        subscriptionDao.insertSubscription(inactiveEntity)
        
        // When - Get active subscriptions
        val activeSubscriptions = repository.getActiveSubscriptions()
        
        // Then - Should return only active subscription
        assertEquals(1, activeSubscriptions.size)
        assertEquals(testUserId, activeSubscriptions[0].userId)
        assertEquals(SubscriptionTier.PREMIUM, activeSubscriptions[0].tier)
        assertEquals(SubscriptionStatus.ACTIVE, activeSubscriptions[0].status)
    }
    
    @Test
    fun unsyncedSubscriptionsRetrievalWorksCorrectly() = runTest {
        // Given - Mix of synced and unsynced subscriptions
        val syncedEntity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        
        val unsyncedEntity = SubscriptionEntity(
            userId = testUserId2,
            tier = DataSubscriptionTier.PRO,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = false,
            syncVersion = 1L
        )
        
        subscriptionDao.insertSubscription(syncedEntity)
        subscriptionDao.insertSubscription(unsyncedEntity)
        
        // When - Get unsynced subscriptions
        val unsyncedSubscriptions = repository.getUnsyncedSubscriptions()
        
        // Then - Should return only unsynced subscription
        assertEquals(1, unsyncedSubscriptions.size)
        assertEquals(testUserId2, unsyncedSubscriptions[0].userId)
        assertEquals(SubscriptionTier.PRO, unsyncedSubscriptions[0].tier)
    }
    
    @Test
    fun markAsSyncedWorksCorrectly() = runTest {
        // Given - Unsynced subscription
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = false,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // When - Mark as synced
        val result = repository.markAsSynced(testUserId, 2L)
        
        // Then - Should succeed
        assertTrue(result.isSuccess)
        
        // Verify sync status updated
        val updatedEntity = subscriptionDao.getSubscription(testUserId)
        assertNotNull(updatedEntity)
        assertTrue(updatedEntity.isSynced)
        assertEquals(2L, updatedEntity.syncVersion)
    }
    
    @Test
    fun subscriptionCreationFromGooglePlayWorksCorrectly() = runTest {
        // When - Create subscription from Google Play
        val result = repository.createSubscriptionFromGooglePlay(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            productId = testProductId,
            subscriptionId = testSubscriptionId
        )
        
        // Then - Should succeed
        assertTrue(result.isSuccess)
        
        val createdSubscription = result.getOrNull()
        assertNotNull(createdSubscription)
        assertEquals(testUserId, createdSubscription.userId)
        assertEquals(SubscriptionTier.PREMIUM, createdSubscription.tier)
        assertEquals(SubscriptionStatus.ACTIVE, createdSubscription.status)
        assertEquals(SubscriptionProvider.GOOGLE_PLAY, createdSubscription.provider)
        assertEquals(testProductId, createdSubscription.productId)
        assertEquals(testSubscriptionId, createdSubscription.subscriptionId)
        assertTrue(createdSubscription.autoRenew)
        assertEquals("USD", createdSubscription.currency)
        
        // Verify in database
        val entity = subscriptionDao.getSubscription(testUserId)
        assertNotNull(entity)
        assertEquals(testUserId, entity.userId)
        assertEquals(DataSubscriptionTier.PREMIUM, entity.tier)
        assertEquals("active", entity.status)
        assertEquals("google_play", entity.provider)
    }
    
    @Test
    fun duplicateSubscriptionInsertionFails() = runTest {
        // Given - Existing subscription
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        subscriptionDao.insertSubscription(entity)
        
        // When - Try to insert duplicate
        val subscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PRO,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = Instant.now(),
            autoRenew = true,
            currency = "USD"
        )
        
        val result = repository.insertSubscription(subscription)
        
        // Then - Should fail
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        
        // Verify original subscription unchanged
        val originalEntity = subscriptionDao.getSubscription(testUserId)
        assertNotNull(originalEntity)
        assertEquals(DataSubscriptionTier.PREMIUM, originalEntity.tier)
    }
    
    @Test
    fun updateNonExistentSubscriptionFails() = runTest {
        // Given - No existing subscription
        assertEquals(0, repository.getSubscriptionCount(testUserId))
        
        // When - Try to update non-existent subscription
        val subscription = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = Instant.now(),
            autoRenew = true,
            currency = "USD"
        )
        
        val result = repository.updateSubscription(subscription)
        
        // Then - Should fail
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        
        // Verify no subscription was created
        assertEquals(0, repository.getSubscriptionCount(testUserId))
    }
    
    @Test
    fun multipleUsersSubscriptionIsolation() = runTest {
        // Given - Subscriptions for two different users
        val user1Entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        
        val user2Entity = SubscriptionEntity(
            userId = testUserId2,
            tier = DataSubscriptionTier.PRO,
            status = "trial",
            provider = "google_play",
            startedAt = Instant.now(),
            isSynced = true,
            syncVersion = 1L
        )
        
        subscriptionDao.insertSubscription(user1Entity)
        subscriptionDao.insertSubscription(user2Entity)
        
        // When - Get subscriptions for each user
        val user1Subscription = repository.getSubscriptionStatus(testUserId).first()
        val user2Subscription = repository.getSubscriptionStatus(testUserId2).first()
        
        // Then - Each user should have isolated subscriptions
        assertNotNull(user1Subscription)
        assertNotNull(user2Subscription)
        
        assertEquals(testUserId, user1Subscription.userId)
        assertEquals(SubscriptionTier.PREMIUM, user1Subscription.tier)
        assertEquals(SubscriptionStatus.ACTIVE, user1Subscription.status)
        
        assertEquals(testUserId2, user2Subscription.userId)
        assertEquals(SubscriptionTier.PRO, user2Subscription.tier)
        assertEquals(SubscriptionStatus.TRIAL, user2Subscription.status)
        
        // Verify count isolation
        assertEquals(1, repository.getSubscriptionCount(testUserId))
        assertEquals(1, repository.getSubscriptionCount(testUserId2))
    }
}