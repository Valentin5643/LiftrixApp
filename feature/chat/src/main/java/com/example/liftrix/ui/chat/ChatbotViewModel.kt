package com.example.liftrix.ui.chat

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID
import javax.inject.Inject

import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.service.Language as DomainLanguage
import com.example.liftrix.domain.service.AIUsageStats
import com.example.liftrix.domain.interactor.auth.AuthInteractor
import com.example.liftrix.domain.interactor.chat.ChatInteractor
import com.example.liftrix.domain.usecase.ai.ChatIntent
import com.example.liftrix.domain.usecase.ai.ModifyWorkoutProgramRequest
import com.example.liftrix.domain.usecase.ai.WorkoutGenerationIntentClassifier
import com.example.liftrix.domain.usecase.ai.WorkoutProgramGateway
import com.example.liftrix.domain.model.ai.WorkoutModificationSaveMode
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.service.AIMessageReportService
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
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
    private val authInteractor: AuthInteractor,
    private val chatRepository: ChatRepository,
    private val chatInteractor: ChatInteractor,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    savedStateHandle: SavedStateHandle,
    private val workoutGenerationIntentClassifier: WorkoutGenerationIntentClassifier,
    private val workoutProgramGateway: WorkoutProgramGateway,
    private val aiMessageReportService: AIMessageReportService
) : ModernBaseViewModel<ChatbotUiState>(
    initialState = ChatbotUiState()
) {

    private var currentConversationId = savedStateHandle.get<String>("conversationId")
        ?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
    private var userId: String? = null
    private var isUserMessageInFlight = false
    private var conversationJob: Job? = null
    private var requestedConversationId: String? = savedStateHandle.get<String>("conversationId")

    private companion object {
        const val MONTHLY_USAGE_TAG = "MonthlyUsageDebug"
    }

    init {
        viewModelScope.launch {
            networkConnectivityMonitor.isConnected.collectLatest { connected ->
                _uiState.value = _uiState.value.copy(isOnline = connected)
            }
        }
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
                authInteractor.currentUser(waitForAuth = false).fold(
                    onSuccess = { userIdResult ->
                        userId = userIdResult.value
                        if (userId != null) {
                            val authenticatedUserId = userId!!
                            _uiState.value = _uiState.value.copy(isAiAccessEnabled = true)
                            observeChatPreferences(authenticatedUserId)
                            observeConversations(authenticatedUserId)
                            checkUsageLimits()
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

    private fun observeChatPreferences(userId: String) {
        viewModelScope.launch {
            chatRepository.observePreferences(userId).collect { preferences ->
                val effectivePreferences = preferences ?: ChatPreferences(userId = userId)
                _uiState.value = _uiState.value.copy(
                    currentLanguage = effectivePreferences.preferredLanguage.toChatLanguage(),
                    autoDetectLanguage = effectivePreferences.autoDetectLanguage
                )
            }
        }
    }

    private fun denyAiAccess() {
        _uiState.value = _uiState.value.copy(
            isAiAccessEnabled = false,
            error = LiftrixError.BusinessLogicError(
                code = "AI_ACCESS_UNAVAILABLE",
                errorMessage = "AI access is currently unavailable."
            )
        )
    }

    private fun observeConversations(userId: String) {
        viewModelScope.launch {
            chatInteractor.observeConversations(userId).collectLatest { result ->
                result.fold(
                    onSuccess = { conversations ->
                        val requested = requestedConversationId
                        val selected = when {
                            requested != null && conversations.any { it.id == requested } -> requested
                            conversations.any { it.id == currentConversationId } -> currentConversationId
                            conversations.isNotEmpty() -> conversations.first().id
                            else -> currentConversationId
                        }
                        requestedConversationId = null
                        _uiState.value = _uiState.value.copy(
                            conversations = conversations,
                            conversationsState = UiState.Success(conversations),
                            activeConversationId = selected
                        )
                        if (conversationJob == null || selected != currentConversationId) {
                            openConversation(selected)
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            conversationsState = UiState.Error(
                                error as? LiftrixError ?: LiftrixError.DatabaseError(
                                    operation = "LOAD_CONVERSATIONS",
                                    errorMessage = error.message ?: "Failed to load conversations"
                                )
                            )
                        )
                    }
                )
            }
        }
    }

    /**
     * Sets up conversation observation and loads chat history.
     */
    private fun openConversation(conversationId: String) {
        if (isUserMessageInFlight) return
        currentConversationId = conversationId
        conversationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            activeConversationId = conversationId,
            messages = emptyList(),
            conversationState = UiState.Loading
        )
        conversationJob = viewModelScope.launch {
            try {
                chatInteractor.observeConversation(userId ?: return@launch, conversationId)
                    .collectLatest { result -> result.fold(onSuccess = { incomingMessages ->
                    // Merge with any existing local pending messages, avoiding duplicates
                    val currentMessages = _uiState.value.messages
                    val pendingMessages = currentMessages
                        .filter { it.isPendingLocalMessage() }
                        .filterNot { pendingMessage ->
                            incomingMessages.any { incomingMessage ->
                                incomingMessage.isPersistedVersionOf(pendingMessage)
                            }
                        }
                    
                    // Combine database messages with unpersisted pending messages, ensuring no duplicates
                    val mergedMessages = (incomingMessages + pendingMessages)
                        .distinctBy { it.id }
                        .sortedBy { it.createdAt }
                    
                    _uiState.value = _uiState.value.copy(
                        messages = mergedMessages,
                        conversationState = UiState.Success(mergedMessages)
                    )
                    }, onFailure = { throw it }) }
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
            ChatbotEvent.NewConversation -> newConversation()
            is ChatbotEvent.OpenConversation -> openConversation(event.conversationId)
            is ChatbotEvent.RenameConversation -> renameConversation(event.conversationId, event.title)
            is ChatbotEvent.DeleteConversation -> deleteConversation(event.conversationId)
            ChatbotEvent.CreateWorkoutPlan -> _uiState.value = _uiState.value.copy(
                workoutBuilderNavigation = WorkoutBuilderNavigation(
                    currentConversationId,
                    _uiState.value.currentInput.takeIf(String::isNotBlank)
                )
            )
            ChatbotEvent.WorkoutBuilderNavigationConsumed -> _uiState.value = _uiState.value.copy(workoutBuilderNavigation = null)
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
        if (trimmedContent.isBlank() || userId == null) return
        if (!_uiState.value.isAiAccessEnabled) {
            denyAiAccess()
            return
        }
        if (isUserMessageInFlight || _uiState.value.isTyping || _uiState.value.isGeneratingProgram) {
            Timber.w("[AI] ChatbotViewModel: ignored duplicate send while message is already in flight")
            return
        }

        isUserMessageInFlight = true
        try {
            val intent = workoutGenerationIntentClassifier.classify(trimmedContent)
            Timber.i("[AI] ChatbotViewModel: classified chat message intent=${intent.javaClass.simpleName} chars=${trimmedContent.length}")
            when (intent) {
                ChatIntent.GenerateWorkout -> {
                    _uiState.value = _uiState.value.copy(
                        currentInput = "",
                        workoutBuilderNavigation = WorkoutBuilderNavigation(currentConversationId, trimmedContent)
                    )
                    isUserMessageInFlight = false
                }
                ChatIntent.ModifyWorkout -> modifyWorkoutProgram(trimmedContent, updateFromProgress = false)
                ChatIntent.UpdatePlanFromProgress -> modifyWorkoutProgram(trimmedContent, updateFromProgress = true)
                ChatIntent.NeedsClarification -> {
                    showWorkoutGenerationClarification(trimmedContent)
                    isUserMessageInFlight = false
                }
                ChatIntent.GeneralChat -> sendChatMessage(trimmedContent)
            }
        } catch (exception: Exception) {
            isUserMessageInFlight = false
            throw exception
        }
    }

    private fun sendChatMessage(content: String, requestId: String = UUID.randomUUID().toString()) {
        if (content.isBlank() || userId == null) return

        // Use timestamp + UUID suffix for guaranteed uniqueness in rapid sends
        val timestamp = System.currentTimeMillis()
        val optimisticId = "chat-$requestId-user"
        
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
                chatInteractor.sendMessage(
                    userId = userId!!,
                    requestId = requestId,
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
                        Timber.e(error, "[AI] ChatbotViewModel: failed to get AI response")
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
                            lastFailedMessage = content.trim(),
                            failedSubmission = FailedSubmission(requestId, content.trim())
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
            } finally {
                isUserMessageInFlight = false
            }
        }
    }

    private fun generateWorkoutProgram(content: String) {
        if (content.isBlank() || userId == null) return

        val timestamp = System.currentTimeMillis()
        val userMessage = localMessage(
            prefix = "workout_request",
            type = MessageType.USER,
            content = content,
            createdAt = timestamp
        )

        viewModelScope.launch {
            try {
                Timber.i("[AI] ChatbotViewModel: workout generation UI flow started promptChars=${content.length}")
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + userMessage,
                    currentInput = "",
                    isTyping = true,
                    isGeneratingProgram = true,
                    generatedProgramSaved = false,
                    pendingGeneratedProgram = null,
                    error = null
                )

                val savedUserMessage = chatInteractor.recordMessage(
                    messageId = userMessage.id,
                    userId = userId!!,
                    content = content,
                    type = MessageType.USER,
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code,
                    titleSeed = content
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

                Timber.i("[AI] ChatbotViewModel: calling GenerateWorkoutProgramUseCase")
                workoutProgramGateway.generate(
                    userId = userId!!,
                    prompt = content,
                    language = _uiState.value.currentLanguage.toDomainLanguage()
                ).fold(
                    onSuccess = { result ->
                        Timber.i("[AI] ChatbotViewModel: workout generation succeeded days=${result.program.days.size}")
                        val previewMessage = localMessage(
                            prefix = "workout_preview",
                            type = MessageType.AI_RESPONSE,
                            content = buildGeneratedProgramMessage(result),
                            createdAt = System.currentTimeMillis()
                        )
                        val savedPreviewMessage = chatInteractor.recordMessage(
                            messageId = previewMessage.id,
                            userId = userId!!,
                            content = previewMessage.content,
                            type = MessageType.AI_RESPONSE,
                            conversationId = currentConversationId,
                            language = _uiState.value.currentLanguage.code
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
                        Timber.i("[AI] ChatbotViewModel: workout generation preview state emitted")
                        checkUsageLimits()
                    },
                    onFailure = { error ->
                        Timber.e(error, "[AI] ChatbotViewModel: workout generation failed")
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
            } finally {
                isUserMessageInFlight = false
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
                Timber.i("[AI] ChatbotViewModel: saving generated program days=${pending.program.days.size}")
                _uiState.value = _uiState.value.copy(isSavingGeneratedProgram = true, error = null)
                workoutProgramGateway.saveGeneratedProgram(
                    userId = id,
                    program = pending.program
                ).fold(
                    onSuccess = { result ->
                        Timber.i("[AI] ChatbotViewModel: generated program save succeeded templates=${result.savedTemplates.size}")
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
                        Timber.e(error, "[AI] ChatbotViewModel: generated program save failed")
                        _uiState.value = _uiState.value.copy(
                            isSavingGeneratedProgram = false,
                            error = error.toLiftrixError("Failed to save generated workout")
                        )
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "[AI] ChatbotViewModel: uncaught generated program save exception")
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
                val savedUserMessage = chatInteractor.recordMessage(
                    messageId = userMessage.id,
                    userId = id,
                    content = content,
                    type = MessageType.USER,
                    conversationId = currentConversationId,
                    language = _uiState.value.currentLanguage.code,
                    titleSeed = content
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

                workoutProgramGateway.previewModification(
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
                        val savedPreviewMessage = chatInteractor.recordMessage(
                            messageId = previewMessage.id,
                            userId = id,
                            content = previewMessage.content,
                            type = MessageType.AI_RESPONSE,
                            conversationId = currentConversationId,
                            language = _uiState.value.currentLanguage.code
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
            } finally {
                isUserMessageInFlight = false
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
                workoutProgramGateway.saveConfirmedModification(
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
            "Vrei s\u0103 generez un program complet de antrenament sau s\u0103 r\u0103spund la o \u00eentrebare general\u0103 despre antrenamente?"
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

    private fun ChatMessage.isPendingLocalMessage(): Boolean {
        return id.startsWith("optimistic_") ||
            id.startsWith("local_") ||
            id.startsWith("workout_request_") ||
            id.startsWith("workout_preview_") ||
            id.startsWith("workout_modify_request_") ||
            id.startsWith("workout_modification_preview_")
    }

    private fun ChatMessage.isPersistedVersionOf(pendingMessage: ChatMessage): Boolean {
        val createdAfterPending = createdAt >= pendingMessage.createdAt
        val closeToPendingMessage = createdAt - pendingMessage.createdAt <= 2 * 60 * 1000
        return createdAfterPending &&
            closeToPendingMessage &&
            userId == pendingMessage.userId &&
            conversationId == pendingMessage.conversationId &&
            type == pendingMessage.type &&
            content == pendingMessage.content
    }

    private fun buildGeneratedProgramMessage(result: WorkoutGenerationResult): String {
        val program = result.program
        val cacheHint = if (result.cacheHit) " Cached result." else ""
        val sourceReference = result.sourceReference
        return if (sourceReference != null) {
            "Modified ${program.workoutName} from ${sourceReference.sourceName} with ${result.changeSummaries.size} changes.$cacheHint Review the preview below before saving."
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
        val failed = _uiState.value.failedSubmission
        val messageToRetry = failed?.content ?: _uiState.value.lastFailedMessage
            ?: _uiState.value.messages.lastOrNull { it.type == MessageType.USER }?.content
        
        if (messageToRetry != null) {
            // Clear the error state and failed message before retrying
            _uiState.value = _uiState.value.copy(
                error = null,
                lastFailedMessage = null
            )
            if (failed != null) sendChatMessage(messageToRetry, failed.requestId) else sendMessage(messageToRetry)
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            userId?.let { id ->
                chatInteractor.deleteConversation(id, currentConversationId).fold(
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

    private fun newConversation() {
        if (!isUserMessageInFlight) openConversation(UUID.randomUUID().toString())
    }

    private fun renameConversation(conversationId: String, title: String) {
        val id = userId ?: return
        viewModelScope.launch {
            chatInteractor.renameConversation(id, conversationId, title).onFailure {
                handleError(it as? LiftrixError ?: LiftrixError.UnknownError(it.message ?: "Rename failed"))
            }
        }
    }

    private fun deleteConversation(conversationId: String) {
        val id = userId ?: return
        viewModelScope.launch {
            chatInteractor.deleteConversation(id, conversationId)
                .onSuccess {
                    openConversation(
                        _uiState.value.conversations.firstOrNull { it.id != conversationId }?.id
                            ?: UUID.randomUUID().toString()
                    )
                }
                .onFailure {
                    handleError(it as? LiftrixError ?: LiftrixError.UnknownError(it.message ?: "Delete failed"))
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
     */
    private fun reportAIMessage(
        messageId: String,
        messageContent: String,
        reason: com.example.liftrix.ui.chat.components.AIReportReason,
        notes: String?
    ) {
        userId?.let { uid ->
            viewModelScope.launch {
                try {
                    aiMessageReportService.reportMessage(
                        userId = uid,
                        messageId = messageId,
                        messageContent = messageContent,
                        reason = reason.name,
                        reasonDescription = reason.description,
                        notes = notes
                    ).fold(
                        onSuccess = {
                            Timber.i("AI message reported successfully: $messageId")
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to report AI message: $messageId")
                        }
                    )
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
                chatInteractor.usageLimits(id).fold(
                    onSuccess = { limits ->
                        Timber.tag(MONTHLY_USAGE_TAG).d(
                            "UI usage state update dailyRemaining=%d monthlyRemaining=%d isNearDaily=%s isNearMonthly=%s showWarning=%s source=CheckUsageLimitsUseCase",
                            limits.dailyMessagesRemaining,
                            limits.monthlyTokensRemaining,
                            limits.isNearDailyLimit,
                            limits.isNearMonthlyLimit,
                            limits.isNearDailyLimit || limits.isNearMonthlyLimit
                        )
                        if (limits.monthlyTokensRemaining <= 0) {
                            Timber.tag(MONTHLY_USAGE_TAG).w(
                                "UI monthly maximum state triggered monthlyRemaining=%d source=UsageLimits",
                                limits.monthlyTokensRemaining
                            )
                        }
                        updateState { currentState ->
                            currentState.copy(
                                usageLimits = limits,
                                showUsageWarning = limits.isNearDailyLimit || limits.isNearMonthlyLimit
                            )
                        }
                        chatInteractor.usageStats(id).fold(
                            onSuccess = { stats ->
                                updateState { currentState -> currentState.copy(usageStats = stats) }
                            },
                            onFailure = { error ->
                                Timber.w(error, "UI usage statistics update failed")
                            }
                        )
                    },
                    onFailure = { error ->
                        Timber.tag(MONTHLY_USAGE_TAG).w(error, "UI usage state update failed")
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
    val conversations: List<ChatConversation> = emptyList(),
    val conversationsState: UiState<List<ChatConversation>> = UiState.Loading,
    val activeConversationId: String? = null,
    val isOnline: Boolean = true,
    val conversationState: UiState<List<ChatMessage>> = UiState.Loading,
    val currentInput: String = "",
    val isTyping: Boolean = false,
    val usageLimits: UsageLimits? = null,
    val usageStats: AIUsageStats? = null,
    val showUsageWarning: Boolean = false,
    val currentLanguage: Language = Language.ENGLISH,
    val autoDetectLanguage: Boolean = true,
    val isAiAccessEnabled: Boolean = false,
    val error: LiftrixError? = null,
    val pendingGeneratedProgram: WorkoutGenerationResult? = null,
    val isGeneratingProgram: Boolean = false,
    val isSavingGeneratedProgram: Boolean = false,
    val generatedProgramSaved: Boolean = false,
    val lastFailedMessage: String? = null,
    val failedSubmission: FailedSubmission? = null,
    val workoutBuilderNavigation: WorkoutBuilderNavigation? = null
)

data class FailedSubmission(val requestId: String, val content: String)
data class WorkoutBuilderNavigation(val conversationId: String?, val seedPrompt: String?)

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
    object NewConversation : ChatbotEvent()
    data class OpenConversation(val conversationId: String) : ChatbotEvent()
    data class RenameConversation(val conversationId: String, val title: String) : ChatbotEvent()
    data class DeleteConversation(val conversationId: String) : ChatbotEvent()
    object CreateWorkoutPlan : ChatbotEvent()
    object WorkoutBuilderNavigationConsumed : ChatbotEvent()
}

/**
 * Language options for the chat interface.
 */
enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    ROMANIAN("ro", "Română")
}

private fun String.toChatLanguage(): Language = when (this) {
    Language.ROMANIAN.code -> Language.ROMANIAN
    else -> Language.ENGLISH
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
        Language.ROMANIAN -> when {
            dailyMessagesRemaining <= 0 -> "Limita zilnică de mesaje a fost atinsă"
            monthlyTokensRemaining <= 0 -> "Limita lunară pentru AI a fost atinsă"
            isNearDailyLimit -> "$dailyMessagesRemaining mesaje rămase azi"
            isNearMonthlyLimit -> "Te apropii de limita lunară pentru AI. Mesajele mai scurte pot ajuta."
            else -> ""
        }
        Language.ENGLISH -> when {
            dailyMessagesRemaining <= 0 -> "Daily message limit reached"
            monthlyTokensRemaining <= 0 -> "Monthly AI usage limit reached"
            isNearDailyLimit -> "$dailyMessagesRemaining messages left today"
            isNearMonthlyLimit -> "You're close to this month's AI usage limit. Shorter messages can help."
            else -> ""
        }
    }
}
