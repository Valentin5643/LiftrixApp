package com.example.liftrix.ui.chat.settings

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.chat.ChatOperationsUseCase
import com.example.liftrix.domain.usecase.chat.ExportFormat
import com.example.liftrix.domain.usecase.chat.UpdateChatPreferencesUseCase
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for AI Chat Settings screen using MVI pattern.
 * Manages chat preferences, history operations, and export functionality.
 */
@HiltViewModel
class AIChatSettingsViewModel @Inject constructor(
    private val updateChatPreferencesUseCase: UpdateChatPreferencesUseCase,
    private val chatOperationsUseCase: ChatOperationsUseCase,
    private val chatRepository: ChatRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<AIChatSettingsUiState, AIChatSettingsEvent>(errorHandler) {
    
    override val _uiState = MutableStateFlow(AIChatSettingsUiState())
    
    private var currentUserId: String? = null
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            currentUserId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = { null }
            )
            currentUserId?.let { userId ->
                // Load current preferences
                chatRepository.observePreferences(userId).collect { preferences ->
                    _uiState.value = _uiState.value.copy(
                        preferences = preferences,
                        preferencesState = if (preferences != null) UiState.Success(preferences) else UiState.Loading
                    )
                }
                
                // Load usage statistics
                loadUsageStatistics(userId)
            }
        }
    }
    
    private suspend fun loadUsageStatistics(userId: String) {
        val messageCountResult = chatRepository.getTotalMessageCount(userId)
        val tokenUsageResult = chatRepository.getTotalTokenUsage(userId)
        
        messageCountResult.fold(
            onSuccess = { messageCount ->
                tokenUsageResult.fold(
                    onSuccess = { tokenUsage ->
                        _uiState.value = _uiState.value.copy(
                            totalMessages = messageCount,
                            totalTokens = tokenUsage,
                            statisticsLoaded = true
                        )
                    },
                    onFailure = { error ->
                        Timber.e("Failed to load token usage: $error")
                    }
                )
            },
            onFailure = { error ->
                Timber.e("Failed to load message count: $error")
            }
        )
    }
    
    override fun handleEvent(event: AIChatSettingsEvent) {
        when (event) {
            is AIChatSettingsEvent.UpdateLanguagePreference -> updateLanguagePreference(event.language)
            is AIChatSettingsEvent.UpdateAutoDetectLanguage -> updateAutoDetectLanguage(event.enabled)
            is AIChatSettingsEvent.UpdateResponseStyle -> updateResponseStyle(event.style)
            is AIChatSettingsEvent.UpdateUserContextPrompt -> updateUserContextPrompt(event.prompt)
            is AIChatSettingsEvent.UpdateWorkoutHistoryInclusion -> updateWorkoutHistoryInclusion(event.include)
            is AIChatSettingsEvent.UpdateExerciseFormTips -> updateExerciseFormTips(event.include)
            is AIChatSettingsEvent.UpdateUsageThreshold -> updateUsageThreshold(event.threshold)
            is AIChatSettingsEvent.UpdateMaxMessagesPerDay -> updateMaxMessagesPerDay(event.maxMessages)
            is AIChatSettingsEvent.UpdateMaxTokensPerMonth -> updateMaxTokensPerMonth(event.maxTokens)
            is AIChatSettingsEvent.UpdateConversationSaveEnabled -> updateConversationSaveEnabled(event.enabled)
            is AIChatSettingsEvent.ClearAllHistory -> clearAllHistory(event.confirmationText)
            is AIChatSettingsEvent.ExportHistory -> exportHistory(event.format)
            AIChatSettingsEvent.DismissError -> dismissError()
            AIChatSettingsEvent.DismissSuccessMessage -> dismissSuccessMessage()
            AIChatSettingsEvent.HideClearHistoryDialog -> hideClearHistoryDialog()
            AIChatSettingsEvent.ShowClearHistoryDialog -> showClearHistoryDialog()
            AIChatSettingsEvent.HideExportDialog -> hideExportDialog()
            AIChatSettingsEvent.ShowExportDialog -> showExportDialog()
        }
    }
    
    private fun updateLanguagePreference(language: String) {
        updatePreference { it.copy(preferredLanguage = language) }
    }
    
    private fun updateAutoDetectLanguage(enabled: Boolean) {
        updatePreference { it.copy(autoDetectLanguage = enabled) }
    }
    
    private fun updateResponseStyle(style: String) {
        updatePreference { it.copy(aiResponseStyle = style) }
    }
    
    private fun updateUserContextPrompt(prompt: String?) {
        updatePreference { it.copy(userContextPrompt = prompt) }
    }
    
    private fun updateWorkoutHistoryInclusion(include: Boolean) {
        updatePreference { it.copy(includeWorkoutHistory = include) }
    }
    
    private fun updateExerciseFormTips(include: Boolean) {
        updatePreference { it.copy(includeExerciseFormTips = include) }
    }
    
    private fun updateUsageThreshold(threshold: Int) {
        updatePreference { it.copy(usageNotificationsThreshold = threshold) }
    }
    
    private fun updateMaxMessagesPerDay(maxMessages: Int) {
        updatePreference { it.copy(maxMessagesPerDay = maxMessages) }
    }
    
    private fun updateMaxTokensPerMonth(maxTokens: Int) {
        updatePreference { it.copy(maxTokensPerMonth = maxTokens) }
    }
    
    private fun updateConversationSaveEnabled(enabled: Boolean) {
        updatePreference { it.copy(conversationSaveEnabled = enabled) }
    }
    
    private fun updatePreference(update: (ChatPreferences) -> ChatPreferences) {
        val currentPreferences = _uiState.value.preferences ?: return
        val updatedPreferences = update(currentPreferences.copy(updatedAt = System.currentTimeMillis()))
        
        viewModelScope.launch {
            // Optimistic update
            _uiState.value = _uiState.value.copy(preferences = updatedPreferences)
            
            updateChatPreferencesUseCase(updatedPreferences).fold(
                onSuccess = {
                    Timber.d("Successfully updated preferences")
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Settings saved successfully"
                    )
                },
                onFailure = { error ->
                    Timber.e("Failed to update preferences: $error")
                    // Revert optimistic update
                    _uiState.value = _uiState.value.copy(
                        preferences = currentPreferences,
                        error = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error occurred")
                    )
                }
            )
        }
    }
    
    private fun clearAllHistory(confirmationText: String) {
        val currentState = _uiState.value
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                clearHistoryInProgress = true,
                showClearHistoryDialog = false
            )
            
            chatOperationsUseCase.clearAllHistory(
                confirmationText = confirmationText,
                language = currentState.preferences?.preferredLanguage ?: "en"
            ).fold(
                onSuccess = { deletedCount ->
                    Timber.i("Successfully cleared $deletedCount messages")
                    _uiState.value = _uiState.value.copy(
                        clearHistoryInProgress = false,
                        successMessage = "Chat history cleared. $deletedCount messages deleted.",
                        totalMessages = 0,
                        totalTokens = 0
                    )
                },
                onFailure = { error ->
                    Timber.e("Failed to clear history: $error")
                    _uiState.value = _uiState.value.copy(
                        clearHistoryInProgress = false,
                        error = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error occurred")
                    )
                }
            )
        }
    }
    
    private fun exportHistory(format: ExportFormat) {
        val currentState = _uiState.value
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                exportInProgress = true,
                showExportDialog = false
            )
            
            chatOperationsUseCase.exportHistory(format).fold(
                onSuccess = { exportData ->
                    Timber.i("Successfully exported chat history")
                    _uiState.value = _uiState.value.copy(
                        exportInProgress = false,
                        exportedData = exportData,
                        successMessage = "Chat history exported successfully"
                    )
                },
                onFailure = { error ->
                    Timber.e("Failed to export history: $error")
                    _uiState.value = _uiState.value.copy(
                        exportInProgress = false,
                        error = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error occurred")
                    )
                }
            )
        }
    }
    
    private fun showClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = true)
    }
    
    private fun hideClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = false)
    }
    
    private fun showExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = true)
    }
    
    private fun hideExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = false)
    }
    
    private fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    private fun dismissSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    /**
     * Gets the required confirmation text for clearing history.
     */
    fun getRequiredConfirmationText(): String {
        val language = _uiState.value.preferences?.preferredLanguage ?: "en"
        return chatOperationsUseCase.getRequiredConfirmationText(language)
    }
}

/**
 * UI State for AI Chat Settings screen.
 */
data class AIChatSettingsUiState(
    val preferences: ChatPreferences? = null,
    val preferencesState: UiState<ChatPreferences> = UiState.Loading,
    val totalMessages: Int = 0,
    val totalTokens: Int = 0,
    val statisticsLoaded: Boolean = false,
    val clearHistoryInProgress: Boolean = false,
    val exportInProgress: Boolean = false,
    val showClearHistoryDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportedData: String? = null,
    val successMessage: String? = null,
    val error: LiftrixError? = null
)

/**
 * Events for AI Chat Settings screen.
 */
sealed class AIChatSettingsEvent : ViewModelEvent {
    // Language Settings
    data class UpdateLanguagePreference(val language: String) : AIChatSettingsEvent()
    data class UpdateAutoDetectLanguage(val enabled: Boolean) : AIChatSettingsEvent()
    
    // AI Behavior Settings
    data class UpdateResponseStyle(val style: String) : AIChatSettingsEvent()
    data class UpdateUserContextPrompt(val prompt: String?) : AIChatSettingsEvent()
    data class UpdateWorkoutHistoryInclusion(val include: Boolean) : AIChatSettingsEvent()
    data class UpdateExerciseFormTips(val include: Boolean) : AIChatSettingsEvent()
    
    // Usage Settings
    data class UpdateUsageThreshold(val threshold: Int) : AIChatSettingsEvent()
    data class UpdateMaxMessagesPerDay(val maxMessages: Int) : AIChatSettingsEvent()
    data class UpdateMaxTokensPerMonth(val maxTokens: Int) : AIChatSettingsEvent()
    data class UpdateConversationSaveEnabled(val enabled: Boolean) : AIChatSettingsEvent()
    
    // History Management
    data class ClearAllHistory(val confirmationText: String) : AIChatSettingsEvent()
    data class ExportHistory(val format: ExportFormat) : AIChatSettingsEvent()
    
    // Dialog Management
    object ShowClearHistoryDialog : AIChatSettingsEvent()
    object HideClearHistoryDialog : AIChatSettingsEvent()
    object ShowExportDialog : AIChatSettingsEvent()
    object HideExportDialog : AIChatSettingsEvent()
    
    // State Management
    object DismissError : AIChatSettingsEvent()
    object DismissSuccessMessage : AIChatSettingsEvent()
}