package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.SubscriptionTier as DataSubscriptionTier
import com.example.liftrix.domain.model.Subscription
import com.example.liftrix.domain.model.SubscriptionProvider
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between domain subscription models and data layer entities.
 * Handles conversion between different tier and status representations.
 */
@Singleton
class SubscriptionMapper @Inject constructor() {

    /**
     * Convert data layer SubscriptionEntity to domain model Subscription
     */
    fun toDomain(entity: SubscriptionEntity): Subscription {
        return Subscription(
            userId = entity.userId,
            tier = entity.tier.toDomainTier(),
            status = entity.status.toDomainStatus(),
            provider = SubscriptionProvider.fromIdentifier(entity.provider),
            productId = entity.productId,
            subscriptionId = entity.subscriptionId,
            startedAt = entity.startedAt,
            expiresAt = entity.expiresAt,
            cancelledAt = entity.cancelledAt,
            trialEndsAt = entity.trialEndsAt,
            autoRenew = entity.autoRenew,
            priceCents = entity.priceCents,
            currency = entity.currency
        )
    }

    /**
     * Convert domain model Subscription to data layer SubscriptionEntity
     */
    fun toEntity(subscription: Subscription, isSynced: Boolean = false, syncVersion: Long = 1L): SubscriptionEntity {
        return SubscriptionEntity(
            userId = subscription.userId,
            tier = subscription.tier.toDataTier(),
            status = subscription.status.toDataStatus(),
            provider = subscription.provider.identifier,
            productId = subscription.productId,
            subscriptionId = subscription.subscriptionId,
            startedAt = subscription.startedAt,
            expiresAt = subscription.expiresAt,
            cancelledAt = subscription.cancelledAt,
            trialEndsAt = subscription.trialEndsAt,
            autoRenew = subscription.autoRenew,
            priceCents = subscription.priceCents,
            currency = subscription.currency,
            createdAt = subscription.startedAt, // Use startedAt as createdAt for new entities
            updatedAt = subscription.expiresAt ?: subscription.startedAt, // Use expiresAt or startedAt as updatedAt
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }

    /**
     * Update existing entity with new domain data while preserving sync metadata
     */
    fun updateEntity(
        existingEntity: SubscriptionEntity,
        domainSubscription: Subscription,
        isSynced: Boolean = false
    ): SubscriptionEntity {
        return existingEntity.copy(
            tier = domainSubscription.tier.toDataTier(),
            status = domainSubscription.status.toDataStatus(),
            provider = domainSubscription.provider.identifier,
            productId = domainSubscription.productId,
            subscriptionId = domainSubscription.subscriptionId,
            startedAt = domainSubscription.startedAt,
            expiresAt = domainSubscription.expiresAt,
            cancelledAt = domainSubscription.cancelledAt,
            trialEndsAt = domainSubscription.trialEndsAt,
            autoRenew = domainSubscription.autoRenew,
            priceCents = domainSubscription.priceCents,
            currency = domainSubscription.currency,
            updatedAt = java.time.Instant.now(),
            isSynced = isSynced,
            syncVersion = existingEntity.syncVersion + 1
        )
    }

    /**
     * Convert data layer SubscriptionTier to domain SubscriptionTier
     */
    private fun DataSubscriptionTier.toDomainTier(): SubscriptionTier {
        return when (this) {
            DataSubscriptionTier.FREE -> SubscriptionTier.FREE
            DataSubscriptionTier.PREMIUM -> SubscriptionTier.PREMIUM
            DataSubscriptionTier.PRO -> SubscriptionTier.PRO
        }
    }

    /**
     * Convert domain SubscriptionTier to data layer SubscriptionTier
     */
    private fun SubscriptionTier.toDataTier(): DataSubscriptionTier {
        return when (this) {
            SubscriptionTier.FREE -> DataSubscriptionTier.FREE
            SubscriptionTier.PREMIUM -> DataSubscriptionTier.PREMIUM
            SubscriptionTier.PRO -> DataSubscriptionTier.PRO
        }
    }

    /**
     * Convert string status to domain SubscriptionStatus
     */
    private fun String.toDomainStatus(): SubscriptionStatus {
        return when (this.lowercase()) {
            "active" -> SubscriptionStatus.ACTIVE
            "cancelled" -> SubscriptionStatus.CANCELLED
            "expired" -> SubscriptionStatus.EXPIRED
            "trial" -> SubscriptionStatus.TRIAL
            "paused" -> SubscriptionStatus.PAUSED
            "pending" -> SubscriptionStatus.PENDING
            else -> SubscriptionStatus.ACTIVE // Default to active for unknown statuses
        }
    }

    /**
     * Convert domain SubscriptionStatus to string status
     */
    private fun SubscriptionStatus.toDataStatus(): String {
        return when (this) {
            SubscriptionStatus.ACTIVE -> "active"
            SubscriptionStatus.CANCELLED -> "cancelled"
            SubscriptionStatus.EXPIRED -> "expired"
            SubscriptionStatus.TRIAL -> "trial"
            SubscriptionStatus.PAUSED -> "paused"
            SubscriptionStatus.PENDING -> "pending"
        }
    }
}