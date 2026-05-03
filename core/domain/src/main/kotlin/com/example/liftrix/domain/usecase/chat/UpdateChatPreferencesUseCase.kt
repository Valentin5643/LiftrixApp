package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Use case for updating user's AI chat preferences.
 * Handles optimistic updates and sync coordination.
 */
class UpdateChatPreferencesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    
    /**
     * Updates the user's chat preferences with immediate persistence.
     * 
     * @param preferences The updated chat preferences
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend operator fun invoke(preferences: ChatPreferences): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            Timber.e(throwable, "Failed to update chat preferences for user: ${preferences.userId}")
            LiftrixError.BusinessLogicError(
                code = "PREFERENCES_UPDATE_FAILED",
                errorMessage = "Failed to update AI chat preferences. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to preferences.userId,
                    "operation" to "UPDATE_CHAT_PREFERENCES"
                )
            )
        }
    ) {
        // Validate preferences before update
        validatePreferences(preferences)
        
        // Update preferences through repository
        chatRepository.updatePreferences(preferences).fold(
            onSuccess = { 
                Timber.d("Successfully updated chat preferences for user: ${preferences.userId}")
            },
            onFailure = { error ->
                Timber.e("Repository failed to update preferences: $error")
                throw Exception("Repository update failed: ${error.message}")
            }
        )
    }
    
    /**
     * Validates chat preferences before updating.
     * Throws exception if validation fails.
     */
    private fun validatePreferences(preferences: ChatPreferences) {
        // Validate user ID
        if (preferences.userId.isBlank()) {
            throw IllegalArgumentException("User ID cannot be blank")
        }
        
        // Validate language preference
        if (preferences.preferredLanguage !in listOf("en", "ro")) {
            throw IllegalArgumentException("Invalid language preference: ${preferences.preferredLanguage}")
        }
        
        // Validate AI response style
        if (preferences.aiResponseStyle !in listOf("concise", "balanced", "detailed")) {
            throw IllegalArgumentException("Invalid AI response style: ${preferences.aiResponseStyle}")
        }
        
        // Validate usage limits
        if (preferences.maxMessagesPerDay < 1 || preferences.maxMessagesPerDay > 1000) {
            throw IllegalArgumentException("Daily message limit must be between 1 and 1000")
        }
        
        if (preferences.maxTokensPerMonth < 100 || preferences.maxTokensPerMonth > 100000) {
            throw IllegalArgumentException("Monthly token limit must be between 100 and 100,000")
        }
        
        if (preferences.autoClearDays < 1 || preferences.autoClearDays > 365) {
            throw IllegalArgumentException("Auto-clear days must be between 1 and 365")
        }
        
        // Validate usage notification threshold
        if (preferences.usageNotificationsThreshold < 50 || preferences.usageNotificationsThreshold > 95) {
            throw IllegalArgumentException("Usage notification threshold must be between 50% and 95%")
        }
        
        // Validate user context prompt length
        preferences.userContextPrompt?.let { prompt ->
            if (prompt.length > 500) {
                throw IllegalArgumentException("User context prompt cannot exceed 500 characters")
            }
        }
    }
}
