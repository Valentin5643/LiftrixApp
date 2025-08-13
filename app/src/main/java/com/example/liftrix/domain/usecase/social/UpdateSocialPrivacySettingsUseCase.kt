package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for updating social privacy settings with validation and sync.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class UpdateSocialPrivacySettingsUseCase @Inject constructor(
    private val repository: SocialPrivacySettingsRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    suspend operator fun invoke(
        privacySettings: SocialPrivacySettings
    ): LiftrixResult<SocialPrivacySettings> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_PRIVACY_SETTINGS",
                errorMessage = "Failed to update privacy settings",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val userId = getCurrentUserIdUseCase() 
            ?: throw IllegalStateException("User not authenticated")

        // Ensure the privacy settings belong to the current user
        val settingsForUser = privacySettings.copy(userId = userId)
        
        repository.updatePrivacySettings(settingsForUser).getOrThrow()
    }
}