package com.example.liftrix.ui.chat

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.chat.SendChatMessageUseCase
import com.example.liftrix.domain.usecase.chat.CheckUsageLimitsUseCase
import com.example.liftrix.domain.repository.ChatRepository
import timber.log.Timber

/**
 * ViewModel for the AI Chatbot screen following MVI pattern.
 * 
 * Manages chat conversation state, user input, usage limits, and error handling
 * for the AI-powered workout guidance interface.
 * 
 * Key responsibilities:
 * - Chat message state management with optimistic updates
 * - Usage limit monitoring and warnings
 * - Real-time conversation flow with typing indicators
 * - Error handling for AI service failures
 * - Language preference management
 * 
 * Architecture:
 * - Extends BaseViewModel with MVI pattern
 * - Uses ChatRepository for data operations
 * - Integrates with backend AI services via use cases
 * - Provides reactive state updates to UI
 */
@HiltViewModel
class ChatbotViewModel @Inject constructor(
    private val authQueryUseCase: AuthQueryUseCase,
    private val chatRepository: ChatRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val checkUsageLimitsUseCase: CheckUsageLimitsUseCase
) : ModernBaseViewModel<ChatbotUiState>(
    initialState = ChatbotUiState()
) {

    private var currentConversationId = UUID.randomUUID().toString()
    private var userId: String? = null

    init {
        loadInitialData()
    }

    /**
     * Loads initial chat data and establishes conversation context.
     * Sets up real-time message observation and checks usage limits.
     */
    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                // Get authenticated user ID
                authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { userIdResult ->
                        userId = userIdResult
                        setupConversation(userIdResult)
                        checkUsageLimits()
                    },
                    onFailure = {
                        handleError(LiftrixError.AuthenticationError(
                            errorMessage = "Failed to get current user ID"
                        ))
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to load initial chat data")
                handleError(LiftrixError.UnknownError(
                    errorMessage = "Failed to initialize chat: ${exception.message}"
                ))
            }
        }
    }

    /**
     * Sets up conversation observation and loads chat history.
     */
    private suspend fun setupConversation(userId: String) {
        try {
            // Observe conversation messages
            chatRepository.observeConversation(userId, currentConversationId)
                .collect { incomingMessages ->
                    // Merge with any existing optimistic messages, avoiding duplicates
                    val currentMessages = _uiState.value.messages
                    val optimisticMessages = currentMessages.filter { it.id.startsWith("optimistic_") }
                    
                    // Combine database messages with optimistic ones, ensuring no duplicates
                    val mergedMessages = (incomingMessages + optimisticMessages)
                        .distinctBy { it.id }
                        .sortedBy { it.createdAt }
                    
                    _uiState.value = _uiState.value.copy(
                        messages = mergedMessages,
                        conversationState = UiState.Success(mergedMessages)
                    )
                }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to setup conversation")
            _uiState.value = _uiState.value.copy(
                conversationState = UiState.Error(
                    LiftrixError.DatabaseError(
                        operation = "SETUP_CONVERSATION",
                        errorMessage = "Failed to load conversation history"
                    )
                )
            )
        }
    }

    fun handleEvent(event: ChatbotEvent) {
        when (event) {
            is ChatbotEvent.SendMessage -> sendMessage(event.content)
            is ChatbotEvent.UpdateInput -> updateInput(event.text)
            is ChatbotEvent.ToggleLanguage -> toggleLanguage(event.language)
            ChatbotEvent.ToggleAutoDetect -> toggleAutoDetect()
            ChatbotEvent.RetryLastMessage -> retryLastMessage()
            ChatbotEvent.ClearConversation -> clearConversation()
            ChatbotEvent.DismissError -> dismissError()
        }
    }

    private fun handleError(error: LiftrixError) {
        updateState { currentState ->
            currentState.copy(error = error, isTyping = false)
        }
    }

    /**
     * Sends a user message with optimistic UI update and triggers AI response.
     */
    private fun sendMessage(content: String) {
        if (content.isBlank() || userId == null) return

        // Use timestamp + UUID suffix for guaranteed uniqueness in rapid sends
        val timestamp = System.currentTimeMillis()
        val optimisticId = "optimistic_${timestamp}_${UUID.randomUUID().toString().takeLast(8)}"
        
        val userMessage = ChatMessage(
            id = optimisticId,
            userId = userId!!,
            conversationId = currentConversationId,
            type = MessageType.USER,
            language = _uiState.value.currentLanguage.code,
            content = content.trim(),
            createdAt = timestamp
        )

        viewModelScope.launch {
            try {
                // Optimistic UI update - show the message immediately
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + userMessage,
                    currentInput = "",
                    isTyping = true,
                    error = null
                )

                // DON'T save user message here - SendChatMessageUseCase handles it
                // This prevents duplicate messages in the database
                
                // Use real AI integration - this will save both user and AI messages
                sendChatMessageUseCase(
                    userId = userId!!,
                    message = content.trim(),
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code
                ).fold(
                    onSuccess = { (savedUserMessage, aiMessage) ->
                        Timber.d("AI response received successfully")
                        
                        // Replace the optimistic user message with the saved one (with proper ID)
                        // and add the AI response - use more robust filtering
                        val currentMessages = _uiState.value.messages
                        val updatedMessages = currentMessages
                            .filterNot { it.id.startsWith("optimistic_") || it.id == userMessage.id } // Remove any optimistic messages
                            .plus(savedUserMessage) // Add saved user message
                            .plus(aiMessage) // Add AI response
                            .distinctBy { it.id } // Ensure no duplicates by ID
                            .sortedBy { it.createdAt } // Maintain chronological order
                        
                        _uiState.value = _uiState.value.copy(
                            messages = updatedMessages,
                            isTyping = false
                        )
                        // Update usage limits after AI response
                        checkUsageLimits()
                    },
                    onFailure = { error ->
                        Timber.e("Failed to get AI response: $error")
                        val liftrixError = error as? LiftrixError
                        
                        // Remove the optimistic message on failure - use robust filtering
                        val updatedMessages = _uiState.value.messages
                            .filterNot { it.id.startsWith("optimistic_") || it.id == userMessage.id }
                            .distinctBy { it.id } // Ensure no duplicates
                            .sortedBy { it.createdAt } // Maintain order
                        
                        _uiState.value = _uiState.value.copy(
                            messages = updatedMessages,
                            isTyping = false,
                            error = liftrixError,
                            lastFailedMessage = content.trim() // Store for retry
                        )
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error sending message")
                _uiState.value = _uiState.value.copy(
                    isTyping = false,
                    error = LiftrixError.UnknownError(
                        errorMessage = "Failed to send message: ${exception.message}"
                    )
                )
            }
        }
    }


    private fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(currentInput = text)
    }

    private fun toggleLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(currentLanguage = language)
        // Language preference saving handled by auto-detection system
    }

    private fun toggleAutoDetect() {
        _uiState.value = _uiState.value.copy(
            autoDetectLanguage = !_uiState.value.autoDetectLanguage
        )
        // Auto-detect preference persisted via ChatPreferencesEntity
    }

    private fun retryLastMessage() {
        // First try to use the stored failed message, then fall back to last user message
        val messageToRetry = _uiState.value.lastFailedMessage 
            ?: _uiState.value.messages.lastOrNull { it.type == MessageType.USER }?.content
        
        if (messageToRetry != null) {
            // Clear the error state and failed message before retrying
            _uiState.value = _uiState.value.copy(
                error = null,
                lastFailedMessage = null
            )
            sendMessage(messageToRetry)
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            userId?.let { id ->
                chatRepository.deleteConversation(id, currentConversationId).fold(
                    onSuccess = { count ->
                        Timber.d("Cleared $count messages from conversation")
                        currentConversationId = UUID.randomUUID().toString()
                        _uiState.value = _uiState.value.copy(
                            messages = emptyList(),
                            conversationState = UiState.Success(emptyList()),
                            currentInput = "",
                            isTyping = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        handleError(error as? LiftrixError ?: LiftrixError.DatabaseError(
                            operation = "CLEAR_CONVERSATION",
                            errorMessage = "Failed to clear conversation"
                        ))
                    }
                )
            }
        }
    }

    private fun dismissError() {
        updateState { currentState ->
            currentState.copy(error = null, lastFailedMessage = null)
        }
    }

    /**
     * Checks current usage limits and updates UI state accordingly.
     */
    private fun checkUsageLimits() {
        userId?.let { id ->
            viewModelScope.launch {
                checkUsageLimitsUseCase.getUserUsageLimits(id).fold(
                    onSuccess = { limits ->
                        updateState { currentState ->
                            currentState.copy(
                                usageLimits = limits,
                                showUsageWarning = limits.isNearDailyLimit || limits.isNearMonthlyLimit
                            )
                        }
                    },
                    onFailure = { error ->
                        Timber.w("Failed to check usage limits: $error")
                        // Don't show error for usage limit checks, just log
                    }
                )
            }
        }
    }
}

/**
 * UI state for the chatbot screen.
 */
@Stable
data class ChatbotUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversationState: UiState<List<ChatMessage>> = UiState.Loading,
    val currentInput: String = "",
    val isTyping: Boolean = false,
    val usageLimits: UsageLimits? = null,
    val showUsageWarning: Boolean = false,
    val currentLanguage: Language = Language.ENGLISH,
    val autoDetectLanguage: Boolean = true,
    val error: LiftrixError? = null,
    val lastFailedMessage: String? = null // Store last failed message for retry
)

/**
 * Events for the chatbot screen.
 */
sealed class ChatbotEvent {
    data class SendMessage(val content: String) : ChatbotEvent()
    data class UpdateInput(val text: String) : ChatbotEvent()
    data class ToggleLanguage(val language: Language) : ChatbotEvent()
    object ToggleAutoDetect : ChatbotEvent()
    object RetryLastMessage : ChatbotEvent()
    object ClearConversation : ChatbotEvent()
    object DismissError : ChatbotEvent()
}

/**
 * Language options for the chat interface.
 */
enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    ROMANIAN("ro", "Română")
}

/**
 * Extension functions for usage limits.
 */
fun UsageLimits.canSendMessage(): Boolean {
    return dailyMessagesRemaining > 0 && monthlyTokensRemaining > 0
}

fun UsageLimits.getWarningMessage(language: Language): String {
    return when (language) {
        Language.ROMANIAN -> {
            when {
                dailyMessagesRemaining <= 0 -> "Limita zilnică de mesaje a fost atinsă"
                monthlyTokensRemaining <= 0 -> "Limita lunară de tokeni a fost atinsă"
                isNearDailyLimit -> "$dailyMessagesRemaining mesaje rămase azi"
                isNearMonthlyLimit -> "$monthlyTokensRemaining tokeni rămași luna aceasta"
                else -> ""
            }
        }
        Language.ENGLISH -> {
            when {
                dailyMessagesRemaining <= 0 -> "Daily message limit reached"
                monthlyTokensRemaining <= 0 -> "Monthly token limit reached"
                isNearDailyLimit -> "$dailyMessagesRemaining messages left today"
                isNearMonthlyLimit -> "$monthlyTokensRemaining tokens left this month"
                else -> ""
            }
        }
    }
}