package com.example.liftrix.domain.service

import com.example.liftrix.data.local.dao.ConsentDao
import com.example.liftrix.data.local.entity.UserConsentEntity
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing user consent (GDPR/CCPA compliance).
 *
 * Provides:
 * - Explicit consent collection (no pre-checked boxes)
 * - Granular consent management (health data, AI chat, analytics, marketing)
 * - Consent withdrawal
 * - Privacy Policy version tracking
 *
 * IMPORTANT: All consents must be explicit. No implicit consent is allowed.
 */
@Singleton
class ConsentManagementService @Inject constructor(
    private val consentDao: ConsentDao
) {
    /**
     * Check if user has given required consents.
     * Required: Privacy Policy + Health Data consent
     * Optional: AI Chat, Analytics, Marketing
     */
    suspend fun hasRequiredConsents(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONSENT_CHECK_FAILED",
                errorMessage = "Failed to check user consents: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val consent = consentDao.getConsent(userId)

        if (consent == null) {
            return@liftrixCatching false // No consent record = not consented
        }

        // Required: Privacy Policy accepted + Health Data consent
        val hasPrivacyPolicy = consent.privacyPolicyAcceptedAt != null
        val hasHealthData = consent.healthDataConsent

        hasPrivacyPolicy && hasHealthData
    }

    /**
     * Check if user has specific consent type.
     *
     * @param userId User ID (MANDATORY)
     * @param consentType Type: "health_data", "ai_chat", "analytics", "marketing"
     */
    suspend fun hasConsent(userId: String, consentType: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONSENT_CHECK_FAILED",
                errorMessage = "Failed to check consent type '$consentType': ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId, "consent_type" to consentType)
            )
        }
    ) {
        val result = consentDao.hasConsent(userId, consentType)
        result ?: false // Null = no consent record
    }

    /**
     * Record user consent.
     * All consent must be explicit - this should only be called when user actively checks boxes.
     *
     * @param userId User ID (MANDATORY)
     * @param privacyPolicyVersion Current privacy policy version
     * @param healthDataConsent Health data processing consent
     * @param aiChatConsent AI chat feature usage consent
     * @param analyticsConsent Analytics/Crashlytics participation
     * @param marketingConsent Marketing communications
     */
    suspend fun recordConsent(
        userId: String,
        privacyPolicyVersion: String,
        healthDataConsent: Boolean,
        aiChatConsent: Boolean,
        analyticsConsent: Boolean,
        marketingConsent: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONSENT_RECORD_FAILED",
                errorMessage = "Failed to record user consent: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val currentTime = System.currentTimeMillis()

        val consentEntity = UserConsentEntity(
            userId = userId,
            privacyPolicyVersion = privacyPolicyVersion,
            privacyPolicyAcceptedAt = currentTime,
            healthDataConsent = healthDataConsent,
            healthDataConsentAt = if (healthDataConsent) currentTime else null,
            aiChatConsent = aiChatConsent,
            aiChatConsentAt = if (aiChatConsent) currentTime else null,
            analyticsConsent = analyticsConsent,
            analyticsConsentAt = if (analyticsConsent) currentTime else null,
            marketingConsent = marketingConsent,
            marketingConsentAt = if (marketingConsent) currentTime else null,
            lastUpdated = currentTime
        )

        consentDao.insert(consentEntity)
    }

    /**
     * Withdraw specific consent type.
     * User can withdraw consent at any time (GDPR requirement).
     *
     * @param userId User ID (MANDATORY)
     * @param consentType Type to withdraw: "health_data", "ai_chat", "analytics", "marketing"
     */
    suspend fun withdrawConsent(
        userId: String,
        consentType: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONSENT_WITHDRAW_FAILED",
                errorMessage = "Failed to withdraw consent '$consentType': ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId, "consent_type" to consentType)
            )
        }
    ) {
        consentDao.withdrawConsent(userId, consentType, System.currentTimeMillis())
    }

    /**
     * Get user consent record.
     */
    suspend fun getConsent(userId: String): LiftrixResult<UserConsentEntity?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONSENT_FETCH_FAILED",
                errorMessage = "Failed to fetch user consent: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        consentDao.getConsent(userId)
    }

    /**
     * Observe consent changes in real-time.
     */
    fun observeConsent(userId: String): Flow<UserConsentEntity?> {
        return consentDao.observeConsent(userId)
    }

    /**
     * Delete user consent (used during account deletion).
     */
    suspend fun deleteConsent(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONSENT_DELETE_FAILED",
                errorMessage = "Failed to delete user consent: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        consentDao.deleteConsent(userId)
    }

    /**
     * Check if Privacy Policy has been updated since user last consented.
     * Returns true if user needs to re-consent.
     *
     * @param userId User ID
     * @param currentPolicyVersion Current privacy policy version (e.g., "1.0.0", "2024-12-30")
     */
    suspend fun needsPrivacyPolicyUpdate(
        userId: String,
        currentPolicyVersion: String
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "POLICY_VERSION_CHECK_FAILED",
                errorMessage = "Failed to check privacy policy version: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val consent = consentDao.getConsent(userId)

        if (consent == null) {
            return@liftrixCatching true // No consent = needs to accept
        }

        // Compare versions
        consent.privacyPolicyVersion != currentPolicyVersion
    }
}
