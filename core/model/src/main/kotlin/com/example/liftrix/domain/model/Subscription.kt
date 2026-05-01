package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a user's subscription status.
 * Contains business logic for subscription validation and premium access checks.
 */
data class Subscription(
    val userId: String,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val provider: SubscriptionProvider,
    val productId: String? = null,
    val subscriptionId: String? = null,
    val startedAt: Instant,
    val expiresAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val trialEndsAt: Instant? = null,
    val autoRenew: Boolean = true,
    val priceCents: Long? = null,
    val currency: String = "USD"
) {
    
    /**
     * Check if subscription provides premium access
     */
    val isActive: Boolean
        get() = tier != SubscriptionTier.FREE && 
                status in listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL) &&
                (expiresAt == null || expiresAt.isAfter(Instant.now()))
    
    /**
     * Check if subscription is currently in trial period
     */
    val isInTrial: Boolean
        get() = status == SubscriptionStatus.TRIAL && 
                trialEndsAt != null && 
                trialEndsAt.isAfter(Instant.now())
    
    /**
     * Check if subscription has expired
     */
    val isExpired: Boolean
        get() = expiresAt != null && expiresAt.isBefore(Instant.now())
    
    /**
     * Check if subscription is cancelled but still active
     */
    val isCancelledButActive: Boolean
        get() = cancelledAt != null && isActive
    
    /**
     * Get days until expiration, null if no expiration set
     */
    val daysUntilExpiration: Long?
        get() = expiresAt?.let { 
            val now = Instant.now()
            if (it.isAfter(now)) {
                java.time.Duration.between(now, it).toDays()
            } else {
                0L
            }
        }
    
    /**
     * Get days remaining in trial, null if not in trial
     */
    val daysRemainingInTrial: Long?
        get() = if (isInTrial) {
            trialEndsAt?.let { 
                val now = Instant.now()
                if (it.isAfter(now)) {
                    java.time.Duration.between(now, it).toDays()
                } else {
                    0L
                }
            }
        } else {
            null
        }
}

/**
 * Extension property to check if this tier provides premium features
 */
val SubscriptionTier.isPremiumTier: Boolean
    get() = this != SubscriptionTier.FREE

/**
 * Extension property to check if this status allows access to premium features
 */
val SubscriptionStatus.allowsPremiumAccess: Boolean
    get() = this == SubscriptionStatus.ACTIVE || this == SubscriptionStatus.TRIAL

/**
 * Enum representing subscription providers
 */
enum class SubscriptionProvider {
    GOOGLE_PLAY,
    STRIPE,
    REVENUE_CAT,
    MANUAL;
    
    /**
     * Get the provider identifier string
     */
    val identifier: String
        get() = when (this) {
            GOOGLE_PLAY -> "google_play"
            STRIPE -> "stripe"
            REVENUE_CAT -> "revenueCat"
            MANUAL -> "manual"
        }
    
    companion object {
        /**
         * Get provider from identifier string
         */
        fun fromIdentifier(identifier: String): SubscriptionProvider {
            return when (identifier) {
                "google_play" -> GOOGLE_PLAY
                "stripe" -> STRIPE
                "revenueCat" -> REVENUE_CAT
                "manual" -> MANUAL
                else -> MANUAL
            }
        }
    }
}