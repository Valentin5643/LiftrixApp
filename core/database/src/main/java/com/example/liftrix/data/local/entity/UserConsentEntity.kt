package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity representing user consent for GDPR/CCPA compliance.
 *
 * Tracks user consent for various data processing activities:
 * - Privacy Policy acceptance
 * - Health data processing
 * - AI chat feature usage
 * - Analytics/Crashlytics participation
 * - Marketing communications
 *
 * IMPORTANT: All consent must be explicit. No pre-checked boxes or implicit consent.
 * Existing users are NOT given implicit consent and must explicitly consent on next login.
 */
@Entity(
    tableName = "user_consents",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserConsentEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "privacy_policy_version")
    val privacyPolicyVersion: String? = null,

    @ColumnInfo(name = "privacy_policy_accepted_at")
    val privacyPolicyAcceptedAt: Long? = null,

    @ColumnInfo(name = "health_data_consent")
    val healthDataConsent: Boolean = false,

    @ColumnInfo(name = "health_data_consent_at")
    val healthDataConsentAt: Long? = null,

    @ColumnInfo(name = "ai_chat_consent")
    val aiChatConsent: Boolean = false,

    @ColumnInfo(name = "ai_chat_consent_at")
    val aiChatConsentAt: Long? = null,

    @ColumnInfo(name = "analytics_consent")
    val analyticsConsent: Boolean = false,

    @ColumnInfo(name = "analytics_consent_at")
    val analyticsConsentAt: Long? = null,

    @ColumnInfo(name = "marketing_consent")
    val marketingConsent: Boolean = false,

    @ColumnInfo(name = "marketing_consent_at")
    val marketingConsentAt: Long? = null,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
