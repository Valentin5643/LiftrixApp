package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for clearing user's chat history with safety confirmation.
 * Provides secure deletion with confirmation and audit logging.
 */
class ClearChatHistoryUseCase @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    companion object {
        private const val CONFIRMATION_TEXT = "DELETE ALL CHATS"
        private const val CONFIRMATION_TEXT_RO = "ȘTERGE TOATE CONVERSAȚIILE"
    }
    
    /**
     * Clears all chat history for the current user with confirmation.
     * 
     * @param confirmationText User-provided confirmation text (must match exactly)
     * @param language User's language preference for confirmation validation
     * @return LiftrixResult<Int> with the number of deleted messages
     */
    suspend operator fun invoke(
        confirmationText: String,
        language: String = "en"
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            Timber.e(throwable, "Failed to clear chat history")
            LiftrixError.BusinessLogicError(
                code = "CLEAR_HISTORY_FAILED",
                errorMessage = "Failed to clear chat history. Please try again.",
                analyticsContext = mapOf(
                    "operation" to "CLEAR_CHAT_HISTORY",
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
        }
    ) {
        // Get current user ID
        val userId = getCurrentUserIdUseCase() 
            ?: throw IllegalStateException("User not authenticated")
        
        // Validate confirmation text
        validateConfirmation(confirmationText, language)
        
        // Get count before deletion for statistics
        val messageCountBefore = chatHistoryDao.getTotalMessageCount(userId)
        val tokenUsageBefore = chatHistoryDao.getTotalTokenUsage(userId) ?: 0
        
        Timber.d("Clearing chat history for user: $userId. Messages: $messageCountBefore, Tokens: $tokenUsageBefore")
        
        // Clear all history for the user
        val deletedCount = chatHistoryDao.clearAllHistory(userId)
        
        // Log the operation for audit
        Timber.i("Chat history cleared for user: $userId. Deleted $deletedCount messages.")
        
        // Verify deletion was successful
        val remainingCount = chatHistoryDao.getTotalMessageCount(userId)
        if (remainingCount > 0) {
            Timber.w("History clear incomplete: $remainingCount messages remain for user $userId")
        }
        
        deletedCount
    }
    
    /**
     * Validates the confirmation text to prevent accidental deletion.
     * Supports both English and Romanian confirmation.
     */
    private fun validateConfirmation(confirmationText: String, language: String) {
        val expectedText = when (language) {
            "ro" -> CONFIRMATION_TEXT_RO
            else -> CONFIRMATION_TEXT
        }
        
        if (confirmationText.trim() != expectedText) {
            throw IllegalArgumentException(
                when (language) {
                    "ro" -> "Textul de confirmare trebuie să fie exact: '$expectedText'"
                    else -> "Confirmation text must be exactly: '$expectedText'"
                }
            )
        }
    }
    
    /**
     * Gets the required confirmation text for a given language.
     * Used by UI to display the confirmation requirement.
     */
    fun getRequiredConfirmationText(language: String = "en"): String {
        return when (language) {
            "ro" -> CONFIRMATION_TEXT_RO
            else -> CONFIRMATION_TEXT
        }
    }
    
    /**
     * Gets statistics about the user's chat history before clearing.
     * Useful for showing users what they're about to delete.
     */
    suspend fun getHistoryStats(userId: String): LiftrixResult<HistoryStats> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "STATS_FETCH_FAILED",
                errorMessage = "Failed to get history statistics",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val messageCount = chatHistoryDao.getTotalMessageCount(userId)
        val tokenUsage = chatHistoryDao.getTotalTokenUsage(userId) ?: 0
        val conversationCount = chatHistoryDao.getConversationIds(userId).size
        
        HistoryStats(
            totalMessages = messageCount,
            totalTokens = tokenUsage,
            conversationCount = conversationCount
        )
    }
}

/**
 * Statistics about user's chat history.
 */
data class HistoryStats(
    val totalMessages: Int,
    val totalTokens: Int,
    val conversationCount: Int
)