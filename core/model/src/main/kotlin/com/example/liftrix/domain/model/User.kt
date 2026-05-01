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
        // Only enforce email validation for authentication contexts, not social/display contexts
        // Empty email is allowed for social User objects where email data is not available
    }
    
    val isEmailVerified: Boolean = !isAnonymous && email.isNotBlank()
    
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
    
    companion object {
        /**
         * Creates a User for authentication contexts where email is required
         */
        fun forAuthentication(
            uid: String,
            email: String,
            displayName: String? = null,
            photoUrl: String? = null,
            isAnonymous: Boolean = false,
            subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt: LocalDateTime? = null,
            premiumFeaturesEnabled: Boolean = false,
            onboardingCompleted: Boolean = false,
            profileVersion: Long = 1L,
            createdAt: LocalDateTime = LocalDateTime.now(),
            lastSignInAt: LocalDateTime = LocalDateTime.now(),
            updatedAt: LocalDateTime = LocalDateTime.now()
        ): User {
            require(isAnonymous || email.isNotBlank()) { 
                "Email cannot be blank for non-anonymous users in authentication context" 
            }
            
            return User(
                uid = uid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                isAnonymous = isAnonymous,
                subscriptionTier = subscriptionTier,
                subscriptionStatus = subscriptionStatus,
                subscriptionExpiresAt = subscriptionExpiresAt,
                premiumFeaturesEnabled = premiumFeaturesEnabled,
                onboardingCompleted = onboardingCompleted,
                profileVersion = profileVersion,
                createdAt = createdAt,
                lastSignInAt = lastSignInAt,
                updatedAt = updatedAt
            )
        }
        
        /**
         * Creates a User for social/display contexts where email may not be available
         */
        fun forSocialDisplay(
            uid: String,
            displayName: String? = "User",
            photoUrl: String? = null,
            email: String = "",
            subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE,
            premiumFeaturesEnabled: Boolean = false
        ): User {
            val now = LocalDateTime.now()
            return User(
                uid = uid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                isAnonymous = false,
                subscriptionTier = subscriptionTier,
                subscriptionStatus = subscriptionStatus,
                subscriptionExpiresAt = null,
                premiumFeaturesEnabled = premiumFeaturesEnabled,
                onboardingCompleted = true,
                profileVersion = 1L,
                createdAt = now,
                lastSignInAt = now,
                updatedAt = now
            )
        }
    }
}

enum class SubscriptionTier(val displayName: String) {
    FREE("Free"),
    PREMIUM("Premium"),
    PRO("Pro")
}

 