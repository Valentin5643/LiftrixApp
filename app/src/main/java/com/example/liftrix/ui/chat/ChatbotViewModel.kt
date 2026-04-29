package com.example.liftrix.ui.chat

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.service.Language as DomainLanguage
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.ai.ChatIntent
import com.example.liftrix.domain.usecase.ai.GenerateWorkoutProgramUseCase
import com.example.liftrix.domain.usecase.ai.ModifyWorkoutProgramRequest
import com.example.liftrix.domain.usecase.ai.ModifyWorkoutProgramUseCase
import com.example.liftrix.domain.usecase.ai.WorkoutGenerationIntentClassifier
import com.example.liftrix.domain.model.ai.WorkoutModificationSaveMode
import com.example.liftrix.domain.usecase.chat.SendChatMessageUseCase
import com.example.liftrix.domain.usecase.chat.CheckUsageLimitsUseCase
import com.example.liftrix.domain.repository.ChatRepository
import com.google.firebase.functions.FirebaseFunctions
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
    private val checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase,
    private val chatRepository: ChatRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val checkUsageLimitsUseCase: CheckUsageLimitsUseCase,
    private val workoutGenerationIntentClassifier: WorkoutGenerationIntentClassifier,
    private val generateWorkoutProgramUseCase: GenerateWorkoutProgramUseCase,
    private val modifyWorkoutProgramUseCase: ModifyWorkoutProgramUseCase,
    private val firebaseFunctions: FirebaseFunctions
) : ModernBaseViewModel<ChatbotUiState>(
    initialState = ChatbotUiState()
) {

    private var currentConversationId = UUID.randomUUID().toString()
    private var userId: String? = null
    private var isAdminAuthorized = false

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
                        userId = userIdResult?.value
                        if (userId != null) {
                            checkAdminPermissionsUseCase(userId!!).fold(
                                onSuccess = { isAdmin ->
                                    isAdminAuthorized = isAdmin
                                    _uiState.value = _uiState.value.copy(isAdminAuthorized = isAdmin)
                                    if (isAdmin) {
                                        checkUsageLimits()
                                        setupConversation(userId!!)
                                    } else {
                                        handleError(adminAccessDeniedError())
                                    }
                                },
                                onFailure = {
                                    isAdminAuthorized = false
                                    _uiState.value = _uiState.value.copy(isAdminAuthorized = false)
                                    handleError(adminAccessDeniedError())
                                }
                            )
                        } else {
                            handleError(LiftrixError.AuthenticationError(
                                errorMessage = "Failed to get current user ID"
                            ))
                        }
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
            is ChatbotEvent.ReportAIMessage -> reportAIMessage(event.messageId, event.messageContent, event.reason, event.notes)
            ChatbotEvent.SaveGeneratedProgram -> saveGeneratedProgram()
            ChatbotEvent.OverwriteGeneratedProgram -> saveModification(WorkoutModificationSaveMode.OVERWRITE)
            ChatbotEvent.DismissGeneratedProgram -> dismissGeneratedProgram()
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
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return

        when (workoutGenerationIntentClassifier.classify(trimmedContent)) {
            ChatIntent.GenerateWorkout -> generateWorkoutProgram(trimmedContent)
            ChatIntent.ModifyWorkout -> modifyWorkoutProgram(trimmedContent, updateFromProgress = false)
            ChatIntent.UpdatePlanFromProgress -> modifyWorkoutProgram(trimmedContent, updateFromProgress = true)
            ChatIntent.NeedsClarification -> showWorkoutGenerationClarification(trimmedContent)
            ChatIntent.GeneralChat -> sendChatMessage(trimmedContent)
        }
    }

    private fun sendChatMessage(content: String) {
        if (content.isBlank() || userId == null) return
        if (!isAdminAuthorized) {
            handleError(adminAccessDeniedError())
            return
        }

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

    private fun adminAccessDeniedError(): LiftrixError.BusinessLogicError =
        LiftrixError.BusinessLogicError(
            code = "ADMIN_ACCESS_DENIED",
            errorMessage = "Admin permissions are required to use AI chat"
        )

    private fun generateWorkoutProgram(content: String) {
        if (content.isBlank() || userId == null) return
        if (!isAdminAuthorized) {
            handleError(adminAccessDeniedError())
            return
        }

        val timestamp = System.currentTimeMillis()
        val userMessage = localMessage(
            prefix = "workout_request",
            type = MessageType.USER,
            content = content,
            createdAt = timestamp
        )

        viewModelScope.launch {
            try {
                Timber.i("ChatbotViewModel: workout generation UI flow started promptChars=${content.length}")
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + userMessage,
                    currentInput = "",
                    isTyping = true,
                    isGeneratingProgram = true,
                    generatedProgramSaved = false,
                    pendingGeneratedProgram = null,
                    error = null
                )

                val savedUserMessage = chatRepository.saveMessage(
                    userId = userId!!,
                    message = content,
                    type = MessageType.USER,
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code
                ).getOrElse { error ->
                    Timber.e(error, "ChatbotViewModel: failed to save workout request message")
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                        isTyping = false,
                        isGeneratingProgram = false,
                        error = error.toLiftrixError("Failed to save workout request"),
                        lastFailedMessage = content
                    )
                    return@launch
                }

                Timber.i("ChatbotViewModel: calling GenerateWorkoutProgramUseCase")
                generateWorkoutProgramUseCase(
                    userId = userId!!,
                    prompt = content,
                    language = _uiState.value.currentLanguage.toDomainLanguage()
                ).fold(
                    onSuccess = { result ->
                        Timber.i("ChatbotViewModel: workout generation succeeded days=${result.program.days.size}")
                        val previewMessage = localMessage(
                            prefix = "workout_preview",
                            type = MessageType.AI_RESPONSE,
                            content = buildGeneratedProgramMessage(result),
                            createdAt = System.currentTimeMillis()
                        )
                        val savedPreviewMessage = chatRepository.saveMessage(
                            userId = userId!!,
                            message = previewMessage.content,
                            type = MessageType.AI_RESPONSE,
                            conversationId = currentConversationId,
                            language = _uiState.value.currentLanguage.code,
                            tokenCount = result.tokensUsed,
                            processingTimeMs = result.processingTimeMs
                        ).getOrElse { error ->
                            Timber.e(error, "ChatbotViewModel: failed to save workout preview message")
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                                isTyping = false,
                                isGeneratingProgram = false,
                                error = error.toLiftrixError("Generated workout, but failed to save the preview"),
                                lastFailedMessage = content
                            )
                            return@fold
                        }
                        _uiState.value = _uiState.value.copy(
                            messages = (_uiState.value.messages
                                .filterNot { it.id == userMessage.id }
                                + savedUserMessage
                                + savedPreviewMessage)
                                .distinctBy { it.id }
                                .sortedBy { it.createdAt },
                            pendingGeneratedProgram = result,
                            isTyping = false,
                            isGeneratingProgram = false
                        )
                        Timber.i("ChatbotViewModel: workout generation preview state emitted")
                        checkUsageLimits()
                    },
                    onFailure = { error ->
                        Timber.e(error, "ChatbotViewModel: workout generation failed")
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                            isTyping = false,
                            isGeneratingProgram = false,
                            error = error.toLiftrixError("Failed to generate workout program"),
                            lastFailedMessage = content
                        )
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "ChatbotViewModel: uncaught workout generation exception")
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                    isTyping = false,
                    isGeneratingProgram = false,
                    error = exception.toLiftrixError("Failed to generate workout program"),
                    lastFailedMessage = content
                )
            }
        }
    }

    private fun Throwable.toLiftrixError(fallbackMessage: String): LiftrixError =
        this as? LiftrixError ?: LiftrixError.UnknownError(
            errorMessage = "$fallbackMessage: ${message ?: javaClass.simpleName}"
        )

    private fun saveGeneratedProgram() {
        val pending = _uiState.value.pendingGeneratedProgram ?: return
        val id = userId ?: return
        if (_uiState.value.isSavingGeneratedProgram || _uiState.value.generatedProgramSaved) return
        if (pending.sourceReference != null) {
            saveModification(WorkoutModificationSaveMode.COPY)
            return
        }

        viewModelScope.launch {
            try {
                Timber.i("ChatbotViewModel: saving generated program days=${pending.program.days.size}")
                _uiState.value = _uiState.value.copy(isSavingGeneratedProgram = true, error = null)
                generateWorkoutProgramUseCase.saveGeneratedProgram(
                    userId = id,
                    program = pending.program
                ).fold(
                    onSuccess = { result ->
                        Timber.i("ChatbotViewModel: generated program save succeeded templates=${result.savedTemplates.size}")
                        val savedMessage = localMessage(
                            prefix = "workout_saved",
                            type = MessageType.AI_RESPONSE,
                            content = "Program saved as ${result.savedTemplates.size} workout templates.",
                            createdAt = System.currentTimeMillis()
                        )
                        _uiState.value = _uiState.value.copy(
                            pendingGeneratedProgram = result,
                            generatedProgramSaved = true,
                            isSavingGeneratedProgram = false,
                            messages = (_uiState.value.messages + savedMessage)
                                .distinctBy { it.id }
                                .sortedBy { it.createdAt }
                        )
                    },
                    onFailure = { error ->
                        Timber.e(error, "ChatbotViewModel: generated program save failed")
                        _uiState.value = _uiState.value.copy(
                            isSavingGeneratedProgram = false,
                            error = error.toLiftrixError("Failed to save generated workout")
                        )
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "ChatbotViewModel: uncaught generated program save exception")
                _uiState.value = _uiState.value.copy(
                    isSavingGeneratedProgram = false,
                    error = exception.toLiftrixError("Failed to save generated workout")
                )
            }
        }
    }

    private fun modifyWorkoutProgram(content: String, updateFromProgress: Boolean) {
        val id = userId ?: return
        if (content.isBlank()) return
        if (!isAdminAuthorized) {
            handleError(adminAccessDeniedError())
            return
        }

        val timestamp = System.currentTimeMillis()
        val userMessage = localMessage(
            prefix = "workout_modify_request",
            type = MessageType.USER,
            content = content,
            createdAt = timestamp
        )

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + userMessage,
                    currentInput = "",
                    isTyping = true,
                    isGeneratingProgram = true,
                    generatedProgramSaved = false,
                    error = null
                )
                val savedUserMessage = chatRepository.saveMessage(
                    userId = id,
                    message = content,
                    type = MessageType.USER,
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code
                ).getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                        isTyping = false,
                        isGeneratingProgram = false,
                        error = error.toLiftrixError("Failed to save workout edit request"),
                        lastFailedMessage = content
                    )
                    return@launch
                }

                modifyWorkoutProgramUseCase.preview(
                    ModifyWorkoutProgramRequest(
                        userId = id,
                        message = content,
                        language = _uiState.value.currentLanguage.toDomainLanguage(),
                        pendingGeneratedProgram = _uiState.value.pendingGeneratedProgram,
                        updateFromProgress = updateFromProgress
                    )
                ).fold(
                    onSuccess = { result ->
                        val previewMessage = localMessage(
                            prefix = "workout_modification_preview",
                            type = MessageType.AI_RESPONSE,
                            content = buildGeneratedProgramMessage(result),
                            createdAt = System.currentTimeMillis()
                        )
                        val savedPreviewMessage = chatRepository.saveMessage(
                            userId = id,
                            message = previewMessage.content,
                            type = MessageType.AI_RESPONSE,
                            conversationId = currentConversationId,
                            language = _uiState.value.currentLanguage.code,
                            tokenCount = result.tokensUsed,
                            processingTimeMs = result.processingTimeMs
                        ).getOrElse { error ->
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                                isTyping = false,
                                isGeneratingProgram = false,
                                error = error.toLiftrixError("Modified workout, but failed to save the preview"),
                                lastFailedMessage = content
                            )
                            return@fold
                        }
                        _uiState.value = _uiState.value.copy(
                            messages = (_uiState.value.messages
                                .filterNot { it.id == userMessage.id }
                                + savedUserMessage
                                + savedPreviewMessage)
                                .distinctBy { it.id }
                                .sortedBy { it.createdAt },
                            pendingGeneratedProgram = result,
                            isTyping = false,
                            isGeneratingProgram = false
                        )
                        checkUsageLimits()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                            isTyping = false,
                            isGeneratingProgram = false,
                            error = error.toLiftrixError("Failed to modify workout"),
                            lastFailedMessage = content
                        )
                    }
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id == userMessage.id },
                    isTyping = false,
                    isGeneratingProgram = false,
                    error = exception.toLiftrixError("Failed to modify workout"),
                    lastFailedMessage = content
                )
            }
        }
    }

    private fun saveModification(saveMode: WorkoutModificationSaveMode) {
        val pending = _uiState.value.pendingGeneratedProgram ?: return
        val id = userId ?: return
        if (_uiState.value.isSavingGeneratedProgram || _uiState.value.generatedProgramSaved) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSavingGeneratedProgram = true, error = null)
                modifyWorkoutProgramUseCase.saveConfirmedModification(
                    userId = id,
                    result = pending,
                    saveMode = saveMode
                ).fold(
                    onSuccess = { result ->
                        val savedMessage = localMessage(
                            prefix = "workout_modification_saved",
                            type = MessageType.AI_RESPONSE,
                            content = if (saveMode == WorkoutModificationSaveMode.OVERWRITE) {
                                "Workout template updated."
                            } else {
                                "Modified workout saved as a new template."
                            },
                            createdAt = System.currentTimeMillis()
                        )
                        _uiState.value = _uiState.value.copy(
                            pendingGeneratedProgram = result,
                            generatedProgramSaved = true,
                            isSavingGeneratedProgram = false,
                            messages = (_uiState.value.messages + savedMessage)
                                .distinctBy { it.id }
                                .sortedBy { it.createdAt }
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isSavingGeneratedProgram = false,
                            error = error.toLiftrixError("Failed to save modified workout")
                        )
                    }
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingGeneratedProgram = false,
                    error = exception.toLiftrixError("Failed to save modified workout")
                )
            }
        }
    }

    private fun showWorkoutGenerationClarification(content: String) {
        if (content.isBlank() || userId == null) return
        val timestamp = System.currentTimeMillis()
        val clarification = if (_uiState.value.currentLanguage == Language.ROMANIAN) {
            "Do you want me to generate a complete workout program, or answer a general workout question?"
        } else {
            "Do you want me to generate a complete workout program, or answer a general workout question?"
        }
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + listOf(
                localMessage("clarify_user", MessageType.USER, content, timestamp),
                localMessage("clarify_ai", MessageType.AI_RESPONSE, clarification, timestamp + 1)
            ),
            currentInput = "",
            error = null
        )
    }

    private fun dismissGeneratedProgram() {
        _uiState.value = _uiState.value.copy(
            pendingGeneratedProgram = null,
            generatedProgramSaved = false,
            isSavingGeneratedProgram = false
        )
    }

    private fun localMessage(
        prefix: String,
        type: MessageType,
        content: String,
        createdAt: Long
    ): ChatMessage = ChatMessage(
        id = "${prefix}_${createdAt}_${UUID.randomUUID().toString().takeLast(8)}",
        userId = userId.orEmpty(),
        conversationId = currentConversationId,
        type = type,
        language = _uiState.value.currentLanguage.code,
        content = content,
        createdAt = createdAt
    )

    private fun buildGeneratedProgramMessage(result: WorkoutGenerationResult): String {
        val program = result.program
        val cacheHint = if (result.cacheHit) " Cached result." else ""
        return if (result.sourceReference != null) {
            "Modified ${program.workoutName} from ${result.sourceReference.sourceName} with ${result.changeSummaries.size} changes.$cacheHint Review the preview below before saving."
        } else {
            "Generated ${program.workoutName} with ${program.days.size} days.$cacheHint Review the preview below, then save it as workout templates."
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
     * Reports an AI-generated message for safety review.
     * Calls the aiReport Firebase Cloud Function with message content and reason.
     */
    private fun reportAIMessage(
        messageId: String,
        messageContent: String,
        reason: com.example.liftrix.ui.chat.components.AIReportReason,
        notes: String?
    ) {
        userId?.let { uid ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val reportData = hashMapOf(
                        "messageId" to messageId,
                        "messageContent" to messageContent,
                        "reason" to reason.name,
                        "reasonDescription" to reason.description,
                        "notes" to (notes ?: ""),
                        "userId" to uid,
                        "timestamp" to System.currentTimeMillis()
                    )

                    firebaseFunctions
                        .getHttpsCallable("aiReport")
                        .call(reportData)
                        .continueWith { task ->
                            if (task.isSuccessful) {
                                Timber.i("AI message reported successfully: $messageId")
                                // Optionally show success toast to user
                            } else {
                                Timber.e(task.exception, "Failed to report AI message: $messageId")
                                // Optionally show error to user
                            }
                        }
                } catch (exception: Exception) {
                    Timber.e(exception, "Error reporting AI message: $messageId")
                }
            }
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
    val isAdminAuthorized: Boolean = false,
    val error: LiftrixError? = null,
    val pendingGeneratedProgram: WorkoutGenerationResult? = null,
    val isGeneratingProgram: Boolean = false,
    val isSavingGeneratedProgram: Boolean = false,
    val generatedProgramSaved: Boolean = false,
    val lastFailedMessage: String? = null // Store last failed message for retry
)

/**
 * Events for the chatbot screen.
 */
sealed class ChatbotEvent {
    data class SendMessage(val content: String) : ChatbotEvent()
    data class UpdateInput(val text: String) : ChatbotEvent()
    data class ToggleLanguage(val language: Language) : ChatbotEvent()
    data class ReportAIMessage(
        val messageId: String,
        val messageContent: String,
        val reason: com.example.liftrix.ui.chat.components.AIReportReason,
        val notes: String?
    ) : ChatbotEvent()
    object SaveGeneratedProgram : ChatbotEvent()
    object OverwriteGeneratedProgram : ChatbotEvent()
    object DismissGeneratedProgram : ChatbotEvent()
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

private fun Language.toDomainLanguage(): DomainLanguage = when (this) {
    Language.ENGLISH -> DomainLanguage.ENGLISH
    Language.ROMANIAN -> DomainLanguage.ROMANIAN
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
