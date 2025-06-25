package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class UserDto(
    @PropertyName("uid")
    val uid: String = "",
    
    @PropertyName("email")
    val email: String = "",
    
    @PropertyName("display_name")
    val displayName: String? = null,
    
    @PropertyName("photo_url")
    val photoUrl: String? = null,
    
    @PropertyName("is_anonymous")
    val isAnonymous: Boolean = false,
    
    @PropertyName("subscription_tier")
    val subscriptionTier: String = "free", // free, premium, pro
    
    @PropertyName("subscription_status")
    val subscriptionStatus: String = "active", // active, cancelled, expired, trial
    
    @PropertyName("subscription_expires_at")
    val subscriptionExpiresAt: Timestamp? = null,
    
    @PropertyName("premium_features_enabled")
    val premiumFeaturesEnabled: Boolean = false,
    
    @PropertyName("onboarding_completed")
    val onboardingCompleted: Boolean = false,
    
    @PropertyName("profile_version")
    val profileVersion: Long = 1L,
    
    @PropertyName("created_at")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("last_sign_in_at")
    val lastSignInAt: Timestamp = Timestamp.now(),
    
    @PropertyName("updated_at")
    val updatedAt: Timestamp = Timestamp.now()
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        uid = "",
        email = "",
        displayName = null,
        photoUrl = null,
        isAnonymous = false,
        subscriptionTier = "free",
        subscriptionStatus = "active",
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = false,
        profileVersion = 1L,
        createdAt = Timestamp.now(),
        lastSignInAt = Timestamp.now(),
        updatedAt = Timestamp.now()
    )
} 