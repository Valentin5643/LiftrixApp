package com.example.liftrix.ui.chat

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
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
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val chatRepository: ChatRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val checkUsageLimitsUseCase: CheckUsageLimitsUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<ChatbotUiState, ChatbotEvent>(errorHandler) {

    override val _uiState = MutableStateFlow(ChatbotUiState())

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
        viewModelScope.launch {
            try {
                // Get authenticated user ID
                val userIdResult = getCurrentUserIdUseCase()
                if (userIdResult != null) {
                    userId = userIdResult
                    viewModelScope.launch {
                        setupConversation(userIdResult)
                    }
                    checkUsageLimits()
                } else {
                    handleError(LiftrixError.AuthenticationError(
                        errorMessage = "Failed to get current user ID"
                    ))
                }
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
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        conversationState = UiState.Success(messages)
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

    override fun handleEvent(event: ChatbotEvent) {
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

    /**
     * Sends a user message with optimistic UI update and triggers AI response.
     */
    private fun sendMessage(content: String) {
        if (content.isBlank() || userId == null) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            userId = userId!!,
            conversationId = currentConversationId,
            type = MessageType.USER,
            language = _uiState.value.currentLanguage.code,
            content = content.trim(),
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                // Optimistic UI update
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + userMessage,
                    currentInput = "",
                    isTyping = true,
                    error = null
                )

                // Save user message to repository
                chatRepository.saveMessage(
                    userId = userId!!,
                    message = content.trim(),
                    type = MessageType.USER,
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code
                )
                
                // Use real AI integration
                sendChatMessageUseCase(
                    userId = userId!!,
                    message = content.trim(),
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code
                ).fold(
                    onSuccess = { (userMessage, aiMessage) ->
                        Timber.d("AI response received successfully")
                        _uiState.value = _uiState.value.copy(
                            isTyping = false
                        )
                        // Update usage limits after AI response
                        checkUsageLimits()
                    },
                    onFailure = { error ->
                        Timber.e("Failed to get AI response: $error")
                        _uiState.value = _uiState.value.copy(
                            isTyping = false,
                            error = error as? LiftrixError
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
        // TODO: Save language preference to repository
    }

    private fun toggleAutoDetect() {
        _uiState.value = _uiState.value.copy(
            autoDetectLanguage = !_uiState.value.autoDetectLanguage
        )
        // TODO: Save auto-detect preference to repository
    }

    private fun retryLastMessage() {
        val lastUserMessage = _uiState.value.messages
            .lastOrNull { it.type == MessageType.USER }
        
        if (lastUserMessage != null) {
            sendMessage(lastUserMessage.content)
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
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Checks current usage limits and updates UI state accordingly.
     */
    private fun checkUsageLimits() {
        userId?.let { id ->
            viewModelScope.launch {
                checkUsageLimitsUseCase.getUserUsageLimits(id).fold(
                    onSuccess = { limits ->
                        _uiState.value = _uiState.value.copy(
                            usageLimits = limits,
                            showUsageWarning = limits.isNearDailyLimit || limits.isNearMonthlyLimit
                        )
                    },
                    onFailure = { error ->
                        Timber.w("Failed to check usage limits: $error")
                        // Don't show error for usage limit checks, just log
                    }
                )
            }
        }
    }

    override fun setLoadingState() {
        _uiState.value = _uiState.value.copy(
            conversationState = UiState.Loading
        )
    }

    override fun updateErrorState(error: LiftrixError) {
        _uiState.value = _uiState.value.copy(
            error = error,
            isTyping = false
        )
    }
}

/**
 * UI state for the chatbot screen.
 */
data class ChatbotUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversationState: UiState<List<ChatMessage>> = UiState.Loading,
    val currentInput: String = "",
    val isTyping: Boolean = false,
    val usageLimits: UsageLimits? = null,
    val showUsageWarning: Boolean = false,
    val currentLanguage: Language = Language.ENGLISH,
    val autoDetectLanguage: Boolean = true,
    val error: LiftrixError? = null
)

/**
 * Events for the chatbot screen.
 */
sealed class ChatbotEvent : ViewModelEvent {
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