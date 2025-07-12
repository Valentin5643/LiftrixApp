package com.example.liftrix.domain.model

import java.time.LocalDateTime

data class User(
    val uid: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean,
    val subscriptionTier: SubscriptionTier,
    val subscriptionStatus: SubscriptionStatus,
    val subscriptionExpiresAt: LocalDateTime?,
    val premiumFeaturesEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val profileVersion: Long,
    val createdAt: LocalDateTime,
    val lastSignInAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    init {
        require(uid.isNotBlank()) { "User ID cannot be blank" }
        require(isAnonymous || email.isNotBlank()) { "Email cannot be blank for non-anonymous users" }
    }
    
    val isEmailVerified: Boolean = !isAnonymous
    
    val isPremium: Boolean = subscriptionTier != SubscriptionTier.FREE && 
                            subscriptionStatus == SubscriptionStatus.ACTIVE &&
                            premiumFeaturesEnabled
    
    val isSubscriptionExpired: Boolean = subscriptionExpiresAt?.let { expiryDate ->
        LocalDateTime.now().isAfter(expiryDate)
    } ?: false
    
    fun withUpdatedSubscription(
        tier: SubscriptionTier,
        status: SubscriptionStatus,
        expiresAt: LocalDateTime? = null,
        premiumEnabled: Boolean = tier != SubscriptionTier.FREE
    ): User = copy(
        subscriptionTier = tier,
        subscriptionStatus = status,
        subscriptionExpiresAt = expiresAt,
        premiumFeaturesEnabled = premiumEnabled,
        profileVersion = profileVersion + 1,
        updatedAt = LocalDateTime.now()
    )
    
    fun withCompletedOnboarding(): User = copy(
        onboardingCompleted = true,
        profileVersion = profileVersion + 1,
        updatedAt = LocalDateTime.now()
    )
}

enum class SubscriptionTier(val displayName: String) {
    FREE("Free"),
    PREMIUM("Premium"),
    PRO("Pro")
}

 