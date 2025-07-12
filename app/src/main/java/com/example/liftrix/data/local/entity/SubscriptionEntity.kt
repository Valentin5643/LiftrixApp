package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant

/**
 * Room entity representing a user's subscription status in the local database.
 * Tracks subscription tier, expiration, and Google Play Billing integration.
 */
@Entity(
    tableName = "user_subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(DateTimeConverters::class)
data class SubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "tier")
    val tier: SubscriptionTier = SubscriptionTier.FREE,

    @ColumnInfo(name = "status")
    val status: String = "active", // active, cancelled, expired, trial, paused

    @ColumnInfo(name = "provider")
    val provider: String = "google_play", // google_play, stripe, revenueCat, manual

    @ColumnInfo(name = "product_id")
    val productId: String? = null, // External product identifier

    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String? = null, // External subscription identifier

    @ColumnInfo(name = "started_at")
    val startedAt: Instant = Instant.now(),

    @ColumnInfo(name = "expires_at")
    val expiresAt: Instant? = null,

    @ColumnInfo(name = "cancelled_at")
    val cancelledAt: Instant? = null,

    @ColumnInfo(name = "trial_ends_at")
    val trialEndsAt: Instant? = null,

    @ColumnInfo(name = "auto_renew")
    val autoRenew: Boolean = true,

    @ColumnInfo(name = "price_cents")
    val priceCents: Long? = null,

    @ColumnInfo(name = "currency")
    val currency: String = "USD",

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L
) {
    /**
     * Check if subscription provides premium access
     */
    val isActive: Boolean
        get() = tier != SubscriptionTier.FREE && 
                status in listOf("active", "trial") &&
                (expiresAt == null || expiresAt.isAfter(Instant.now()))
}

/**
 * Enum representing subscription tiers
 */
enum class SubscriptionTier {
    FREE,
    PREMIUM,
    PRO
}