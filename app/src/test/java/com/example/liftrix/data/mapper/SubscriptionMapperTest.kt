package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.SubscriptionTier as DataSubscriptionTier
import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SubscriptionMapperTest {

    private lateinit var subscriptionMapper: SubscriptionMapper

    private val testUserId = "test_user_123"
    private val testInstant = Instant.now()
    private val testProductId = "premium_monthly"
    private val testSubscriptionId = "gpa_subscription_123"
    private val testPriceCents = 999L

    @Before
    fun setup() {
        subscriptionMapper = SubscriptionMapper()
    }

    @Test
    fun `toDomain converts SubscriptionEntity to Subscription correctly`() {
        // Given
        val entity = SubscriptionEntity(
            userId = testUserId,
            tier = DataSubscriptionTier.PREMIUM,
            status = "active",
            provider = "google_play",
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            cancelledAt = null,
            trialEndsAt = null,
            autoRenew = true,
            priceCents = testPriceCents,
            currency = "USD",
            createdAt = testInstant,
            updatedAt = testInstant,
            isSynced = true,
            syncVersion = 1L
        )

        // When
        val domain = subscriptionMapper.toDomain(entity)

        // Then
        assertEquals(testUserId, domain.userId)
        assertEquals(SubscriptionTier.PREMIUM, domain.tier)
        assertEquals(SubscriptionStatus.ACTIVE, domain.status)
        assertEquals(SubscriptionProvider.GOOGLE_PLAY, domain.provider)
        assertEquals(testProductId, domain.productId)
        assertEquals(testSubscriptionId, domain.subscriptionId)
        assertEquals(testInstant, domain.startedAt)
        assertEquals(testInstant.plusSeconds(3600), domain.expiresAt)
        assertNull(domain.cancelledAt)
        assertNull(domain.trialEndsAt)
        assertTrue(domain.autoRenew)
        assertEquals(testPriceCents, domain.priceCents)
        assertEquals("USD", domain.currency)
    }

    @Test
    fun `toDomain handles different subscription tiers correctly`() {
        // Test FREE tier
        val freeEntity = createTestEntity(tier = DataSubscriptionTier.FREE)
        val freeDomain = subscriptionMapper.toDomain(freeEntity)
        assertEquals(SubscriptionTier.FREE, freeDomain.tier)

        // Test PREMIUM tier
        val premiumEntity = createTestEntity(tier = DataSubscriptionTier.PREMIUM)
        val premiumDomain = subscriptionMapper.toDomain(premiumEntity)
        assertEquals(SubscriptionTier.PREMIUM, premiumDomain.tier)

        // Test PRO tier
        val proEntity = createTestEntity(tier = DataSubscriptionTier.PRO)
        val proDomain = subscriptionMapper.toDomain(proEntity)
        assertEquals(SubscriptionTier.PRO, proDomain.tier)
    }

    @Test
    fun `toDomain handles different subscription statuses correctly`() {
        // Test ACTIVE
        val activeEntity = createTestEntity(status = "active")
        val activeDomain = subscriptionMapper.toDomain(activeEntity)
        assertEquals(SubscriptionStatus.ACTIVE, activeDomain.status)

        // Test CANCELLED
        val cancelledEntity = createTestEntity(status = "cancelled")
        val cancelledDomain = subscriptionMapper.toDomain(cancelledEntity)
        assertEquals(SubscriptionStatus.CANCELLED, cancelledDomain.status)

        // Test EXPIRED
        val expiredEntity = createTestEntity(status = "expired")
        val expiredDomain = subscriptionMapper.toDomain(expiredEntity)
        assertEquals(SubscriptionStatus.EXPIRED, expiredDomain.status)

        // Test TRIAL
        val trialEntity = createTestEntity(status = "trial")
        val trialDomain = subscriptionMapper.toDomain(trialEntity)
        assertEquals(SubscriptionStatus.TRIAL, trialDomain.status)

        // Test PAUSED
        val pausedEntity = createTestEntity(status = "paused")
        val pausedDomain = subscriptionMapper.toDomain(pausedEntity)
        assertEquals(SubscriptionStatus.PAUSED, pausedDomain.status)
    }

    @Test
    fun `toDomain handles different subscription providers correctly`() {
        // Test Google Play
        val googlePlayEntity = createTestEntity(provider = "google_play")
        val googlePlayDomain = subscriptionMapper.toDomain(googlePlayEntity)
        assertEquals(SubscriptionProvider.GOOGLE_PLAY, googlePlayDomain.provider)

        // Test Stripe
        val stripeEntity = createTestEntity(provider = "stripe")
        val stripeDomain = subscriptionMapper.toDomain(stripeEntity)
        assertEquals(SubscriptionProvider.STRIPE, stripeDomain.provider)

        // Test Revenue Cat
        val revenueCatEntity = createTestEntity(provider = "revenueCat")
        val revenueCatDomain = subscriptionMapper.toDomain(revenueCatEntity)
        assertEquals(SubscriptionProvider.REVENUE_CAT, revenueCatDomain.provider)

        // Test Manual
        val manualEntity = createTestEntity(provider = "manual")
        val manualDomain = subscriptionMapper.toDomain(manualEntity)
        assertEquals(SubscriptionProvider.MANUAL, manualDomain.provider)

        // Test unknown provider defaults to MANUAL
        val unknownEntity = createTestEntity(provider = "unknown_provider")
        val unknownDomain = subscriptionMapper.toDomain(unknownEntity)
        assertEquals(SubscriptionProvider.MANUAL, unknownDomain.provider)
    }

    @Test
    fun `toEntity converts Subscription to SubscriptionEntity correctly`() {
        // Given
        val domain = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            cancelledAt = null,
            trialEndsAt = null,
            autoRenew = true,
            priceCents = testPriceCents,
            currency = "USD"
        )

        // When
        val entity = subscriptionMapper.toEntity(domain, isSynced = true, syncVersion = 2L)

        // Then
        assertEquals(testUserId, entity.userId)
        assertEquals(DataSubscriptionTier.PREMIUM, entity.tier)
        assertEquals("active", entity.status)
        assertEquals("google_play", entity.provider)
        assertEquals(testProductId, entity.productId)
        assertEquals(testSubscriptionId, entity.subscriptionId)
        assertEquals(testInstant, entity.startedAt)
        assertEquals(testInstant.plusSeconds(3600), entity.expiresAt)
        assertNull(entity.cancelledAt)
        assertNull(entity.trialEndsAt)
        assertTrue(entity.autoRenew)
        assertEquals(testPriceCents, entity.priceCents)
        assertEquals("USD", entity.currency)
        assertTrue(entity.isSynced)
        assertEquals(2L, entity.syncVersion)
    }

    @Test
    fun `toEntity handles different domain tiers correctly`() {
        val baseDomain = createTestDomain()

        // Test FREE tier
        val freeDomain = baseDomain.copy(tier = SubscriptionTier.FREE)
        val freeEntity = subscriptionMapper.toEntity(freeDomain)
        assertEquals(DataSubscriptionTier.FREE, freeEntity.tier)

        // Test PREMIUM tier
        val premiumDomain = baseDomain.copy(tier = SubscriptionTier.PREMIUM)
        val premiumEntity = subscriptionMapper.toEntity(premiumDomain)
        assertEquals(DataSubscriptionTier.PREMIUM, premiumEntity.tier)

        // Test PRO tier
        val proDomain = baseDomain.copy(tier = SubscriptionTier.PRO)
        val proEntity = subscriptionMapper.toEntity(proDomain)
        assertEquals(DataSubscriptionTier.PRO, proEntity.tier)
    }

    @Test
    fun `toEntity handles different domain statuses correctly`() {
        val baseDomain = createTestDomain()

        // Test ACTIVE
        val activeDomain = baseDomain.copy(status = SubscriptionStatus.ACTIVE)
        val activeEntity = subscriptionMapper.toEntity(activeDomain)
        assertEquals("active", activeEntity.status)

        // Test CANCELLED
        val cancelledDomain = baseDomain.copy(status = SubscriptionStatus.CANCELLED)
        val cancelledEntity = subscriptionMapper.toEntity(cancelledDomain)
        assertEquals("cancelled", cancelledEntity.status)

        // Test EXPIRED
        val expiredDomain = baseDomain.copy(status = SubscriptionStatus.EXPIRED)
        val expiredEntity = subscriptionMapper.toEntity(expiredDomain)
        assertEquals("expired", expiredEntity.status)

        // Test TRIAL
        val trialDomain = baseDomain.copy(status = SubscriptionStatus.TRIAL)
        val trialEntity = subscriptionMapper.toEntity(trialDomain)
        assertEquals("trial", trialEntity.status)

        // Test PAUSED
        val pausedDomain = baseDomain.copy(status = SubscriptionStatus.PAUSED)
        val pausedEntity = subscriptionMapper.toEntity(pausedDomain)
        assertEquals("paused", pausedEntity.status)
    }

    @Test
    fun `updateEntity preserves existing entity metadata while updating domain data`() {
        // Given
        val existingEntity = createTestEntity(
            isSynced = true,
            syncVersion = 5L,
            createdAt = testInstant.minusSeconds(3600)
        )

        val updatedDomain = Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PRO,
            status = SubscriptionStatus.CANCELLED,
            provider = SubscriptionProvider.STRIPE,
            productId = "new_product_id",
            subscriptionId = "new_subscription_id",
            startedAt = testInstant.minusSeconds(1800),
            expiresAt = testInstant.plusSeconds(1800),
            cancelledAt = testInstant.minusSeconds(900),
            trialEndsAt = null,
            autoRenew = false,
            priceCents = 1999L,
            currency = "EUR"
        )

        // When
        val updatedEntity = subscriptionMapper.updateEntity(existingEntity, updatedDomain, isSynced = false)

        // Then
        // Domain data should be updated
        assertEquals(SubscriptionTier.PRO.name, updatedEntity.tier.name)
        assertEquals("cancelled", updatedEntity.status)
        assertEquals("stripe", updatedEntity.provider)
        assertEquals("new_product_id", updatedEntity.productId)
        assertEquals("new_subscription_id", updatedEntity.subscriptionId)
        assertEquals(testInstant.minusSeconds(1800), updatedEntity.startedAt)
        assertEquals(testInstant.plusSeconds(1800), updatedEntity.expiresAt)
        assertEquals(testInstant.minusSeconds(900), updatedEntity.cancelledAt)
        assertNull(updatedEntity.trialEndsAt)
        assertFalse(updatedEntity.autoRenew)
        assertEquals(1999L, updatedEntity.priceCents)
        assertEquals("EUR", updatedEntity.currency)

        // Metadata should be preserved/updated correctly
        assertEquals(testInstant.minusSeconds(3600), updatedEntity.createdAt) // Original createdAt preserved
        assertFalse(updatedEntity.isSynced) // Updated as specified
        assertEquals(6L, updatedEntity.syncVersion) // Incremented from original
        assertTrue(updatedEntity.updatedAt.isAfter(testInstant)) // Updated timestamp
    }

    private fun createTestEntity(
        userId: String = testUserId,
        tier: DataSubscriptionTier = DataSubscriptionTier.PREMIUM,
        status: String = "active",
        provider: String = "google_play",
        isSynced: Boolean = false,
        syncVersion: Long = 1L,
        createdAt: Instant = testInstant
    ): SubscriptionEntity {
        return SubscriptionEntity(
            userId = userId,
            tier = tier,
            status = status,
            provider = provider,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            cancelledAt = null,
            trialEndsAt = null,
            autoRenew = true,
            priceCents = testPriceCents,
            currency = "USD",
            createdAt = createdAt,
            updatedAt = testInstant,
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }

    private fun createTestDomain(): Subscription {
        return Subscription(
            userId = testUserId,
            tier = SubscriptionTier.PREMIUM,
            status = SubscriptionStatus.ACTIVE,
            provider = SubscriptionProvider.GOOGLE_PLAY,
            productId = testProductId,
            subscriptionId = testSubscriptionId,
            startedAt = testInstant,
            expiresAt = testInstant.plusSeconds(3600),
            cancelledAt = null,
            trialEndsAt = null,
            autoRenew = true,
            priceCents = testPriceCents,
            currency = "USD"
        )
    }
}