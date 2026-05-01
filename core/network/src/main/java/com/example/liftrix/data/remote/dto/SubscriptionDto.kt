package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore DTO representing a subscription document
 * Used to manage premium access and trigger custom claims assignment
 */
data class SubscriptionDto(
    @DocumentId
    val id: String = "",
    
    @PropertyName("user_id")
    val userId: String = "",
    
    @PropertyName("tier")
    val tier: String = "free", // free, premium, pro
    
    @PropertyName("status")
    val status: String = "active", // active, cancelled, expired, trial, paused
    
    @PropertyName("provider")
    val provider: String = "", // google_play, stripe, revenueCat, manual
    
    @PropertyName("product_id")
    val productId: String? = null, // External product identifier
    
    @PropertyName("subscription_id")
    val subscriptionId: String? = null, // External subscription identifier
    
    @PropertyName("started_at")
    val startedAt: Timestamp = Timestamp.now(),
    
    @PropertyName("expires_at")
    val expiresAt: Timestamp? = null,
    
    @PropertyName("cancelled_at")
    val cancelledAt: Timestamp? = null,
    
    @PropertyName("trial_ends_at")
    val trialEndsAt: Timestamp? = null,
    
    @PropertyName("auto_renew")
    val autoRenew: Boolean = true,
    
    @PropertyName("price_cents")
    val priceCents: Long? = null,
    
    @PropertyName("currency")
    val currency: String = "USD",
    
    @PropertyName("features")
    val features: List<String> = emptyList(), // List of enabled premium features
    
    @PropertyName("metadata")
    val metadata: Map<String, String> = emptyMap(), // Additional provider-specific data
    
    @PropertyName("claims_updated")
    val claimsUpdated: Boolean = false, // Flag to track custom claims sync
    
    @PropertyName("claims_updated_at")
    val claimsUpdatedAt: Timestamp? = null,
    
    @PropertyName("created_at")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("updated_at")
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    
    @PropertyName("version")
    val version: Long = 1L
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = "",
        userId = "",
        tier = "free",
        status = "active",
        provider = "",
        productId = null,
        subscriptionId = null,
        startedAt = Timestamp.now(),
        expiresAt = null,
        cancelledAt = null,
        trialEndsAt = null,
        autoRenew = true,
        priceCents = null,
        currency = "USD",
        features = emptyList(),
        metadata = emptyMap(),
        claimsUpdated = false,
        claimsUpdatedAt = null,
        createdAt = Timestamp.now(),
        updatedAt = null,
        version = 1L
    )
    
    /**
     * Check if subscription provides premium access
     */
    val isPremiumActive: Boolean = tier != "free" && 
                                  status in listOf("active", "trial") &&
                                  (expiresAt == null || expiresAt.toDate().after(java.util.Date()))
    
    /**
     * Get features that should be enabled for this subscription
     */
    val enabledFeatures: Set<String> = when (tier) {
        "premium" -> setOf(
            "ai_summaries",
            "advanced_analytics",
            "premium_templates",
            "priority_support"
        )
        "pro" -> setOf(
            "ai_summaries",
            "advanced_analytics", 
            "premium_templates",
            "priority_support",
            "unlimited_workouts",
            "custom_templates",
            "export_data",
            "team_features"
        )
        else -> emptySet()
    } + features.toSet() // Include any additional custom features
} 