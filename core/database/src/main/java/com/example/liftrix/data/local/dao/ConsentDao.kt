package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.UserConsentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing user consent records.
 *
 * All queries are user-scoped to prevent data leakage.
 * Consent must be explicit - no implicit consent is allowed.
 */
@Dao
interface ConsentDao {

    /**
     * Insert or update user consent record.
     * Uses REPLACE strategy to update existing records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(consent: UserConsentEntity)

    /**
     * Get consent record for a specific user.
     * Returns null if no consent record exists (user has never consented).
     *
     * @param userId The user ID to query (MANDATORY for user scoping)
     */
    @Query("SELECT * FROM user_consents WHERE user_id = :userId")
    suspend fun getConsent(userId: String): UserConsentEntity?

    /**
     * Observe consent changes for a user in real-time.
     *
     * @param userId The user ID to observe (MANDATORY for user scoping)
     */
    @Query("SELECT * FROM user_consents WHERE user_id = :userId")
    fun observeConsent(userId: String): Flow<UserConsentEntity?>

    /**
     * Update consent record.
     */
    @Update
    suspend fun update(consent: UserConsentEntity)

    /**
     * Delete consent record for a user (used during account deletion).
     *
     * @param userId The user ID to delete (MANDATORY for user scoping)
     */
    @Query("DELETE FROM user_consents WHERE user_id = :userId")
    suspend fun deleteConsent(userId: String)

    /**
     * Check if user has given specific consent type.
     *
     * @param userId The user ID to check (MANDATORY for user scoping)
     * @param consentType The consent field to check (health_data_consent, ai_chat_consent, etc.)
     */
    @Query("""
        SELECT CASE
            WHEN :consentType = 'health_data' THEN health_data_consent
            WHEN :consentType = 'ai_chat' THEN ai_chat_consent
            WHEN :consentType = 'analytics' THEN analytics_consent
            WHEN :consentType = 'marketing' THEN marketing_consent
            ELSE 0
        END
        FROM user_consents
        WHERE user_id = :userId
    """)
    suspend fun hasConsent(userId: String, consentType: String): Boolean?

    /**
     * Withdraw specific consent type.
     * Sets the consent flag to false and updates timestamp.
     *
     * @param userId The user ID (MANDATORY for user scoping)
     * @param consentType The consent type to withdraw
     * @param timestamp The withdrawal timestamp
     */
    @Query("""
        UPDATE user_consents
        SET health_data_consent = CASE WHEN :consentType = 'health_data' THEN 0 ELSE health_data_consent END,
            health_data_consent_at = CASE WHEN :consentType = 'health_data' THEN :timestamp ELSE health_data_consent_at END,
            ai_chat_consent = CASE WHEN :consentType = 'ai_chat' THEN 0 ELSE ai_chat_consent END,
            ai_chat_consent_at = CASE WHEN :consentType = 'ai_chat' THEN :timestamp ELSE ai_chat_consent_at END,
            analytics_consent = CASE WHEN :consentType = 'analytics' THEN 0 ELSE analytics_consent END,
            analytics_consent_at = CASE WHEN :consentType = 'analytics' THEN :timestamp ELSE analytics_consent_at END,
            marketing_consent = CASE WHEN :consentType = 'marketing' THEN 0 ELSE marketing_consent END,
            marketing_consent_at = CASE WHEN :consentType = 'marketing' THEN :timestamp ELSE marketing_consent_at END,
            last_updated = :timestamp
        WHERE user_id = :userId
    """)
    suspend fun withdrawConsent(userId: String, consentType: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get all users who have not provided required consents.
     * Used to identify users who need to be prompted for consent on next login.
     */
    @Query("""
        SELECT * FROM user_consents
        WHERE health_data_consent = 0
           OR ai_chat_consent = 0
           OR privacy_policy_accepted_at IS NULL
    """)
    suspend fun getUsersWithoutRequiredConsents(): List<UserConsentEntity>
}
