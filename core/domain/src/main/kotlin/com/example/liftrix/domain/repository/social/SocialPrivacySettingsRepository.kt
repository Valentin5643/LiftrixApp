package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for social privacy settings data operations.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
interface SocialPrivacySettingsRepository {
    
    /**
     * Creates initial privacy settings for a user with default values.
     * Called automatically when a user enables social features for the first time.
     */
    suspend fun createPrivacySettings(userId: String): LiftrixResult<SocialPrivacySettings>
    
    /**
     * Updates existing privacy settings for a user.
     * All changes are immediately applied and synced to Firebase.
     */
    suspend fun updatePrivacySettings(settings: SocialPrivacySettings): LiftrixResult<SocialPrivacySettings>
    
    /**
     * Gets current privacy settings for a user.
     * Creates default settings if none exist.
     */
    suspend fun getPrivacySettings(userId: String): LiftrixResult<SocialPrivacySettings?>
    
    /**
     * Observes privacy settings changes for reactive UI updates.
     * Emits new settings whenever they change locally or from sync.
     */
    fun observePrivacySettings(userId: String): Flow<SocialPrivacySettings?>
    
    /**
     * Deletes all privacy settings for a user.
     * Called when user completely disables social features or deletes account.
     */
    suspend fun deletePrivacySettings(userId: String): LiftrixResult<Unit>
}