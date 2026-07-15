package com.example.liftrix.ui.chat.settings

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.interactor.auth.AuthInteractor
import com.example.liftrix.domain.interactor.chat.ChatInteractor
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.chat.ExportFormat
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for AI Chat Settings screen using MVI pattern.
 * Manages chat preferences, history operations, and export functionality.
 */
@HiltViewModel
class AIChatSettingsViewModel @Inject constructor(
    private val chatInteractor: ChatInteractor,
    private val chatRepository: ChatRepository,
    private val authInteractor: AuthInteractor,
    private val checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase
) : ModernBaseViewModel<AIChatSettingsUiState>(
    initialState = AIChatSettingsUiState()
) {
    
    private var currentUserId: String? = null
    private var settingsObservationJob: Job? = null
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        settingsObservationJob?.cancel()
        settingsObservationJob = viewModelScope.launch {
            val userId = authInteractor.currentUser(waitForAuth = true).fold(
                onSuccess = { it.value },
                onFailure = { error ->
                    Timber.e(error, "Failed to load AI chat settings user")
                    _uiState.value = _uiState.value.copy(
                        preferencesState = UiState.Error(
                            error as? LiftrixError ?: LiftrixError.AuthenticationError(
                                errorMessage = "Unable to load AI settings because the user is not authenticated.",
                                errorCode = "AI_SETTINGS_AUTH_FAILED"
                            )
                        )
                    )
                    null
                }
            ) ?: return@launch

            if (!hasAdminAiAccess(userId)) {
                denyAiAccess()
                return@launch
            }

            currentUserId = userId

            launch {
                loadUsageStatistics(userId)
            }

            chatRepository.observePreferences(userId)
                .catch { error ->
                    Timber.e(error, "Failed to observe AI chat preferences")
                    _uiState.value = _uiState.value.copy(
                        preferencesState = UiState.Error(
                            LiftrixError.DatabaseError(
                                operation = "OBSERVE_CHAT_PREFERENCES",
                                errorMessage = "Failed to load AI chat settings"
                            )
                        )
                    )
                }
                .collect { preferences ->
                    val effectivePreferences = preferences ?: ChatPreferences(userId = userId)
                    _uiState.value = _uiState.value.copy(
                        preferences = effectivePreferences,
                        preferencesState = UiState.Success(effectivePreferences)
                    )
                }
        }
    }
    
    private suspend fun loadUsageStatistics(userId: String) {
        val messageCountResult = chatRepository.getTotalMessageCount(userId)
        val usageStatsResult = chatInteractor.usageStats(userId)
        val usageLimitsResult = chatInteractor.usageLimits(userId)
        
        messageCountResult.fold(
            onSuccess = { messageCount ->
                usageStatsResult.fold(
                    onSuccess = { stats ->
                        usageLimitsResult.fold(
                            onSuccess = { limits ->
                                _uiState.value = _uiState.value.copy(
                                    totalMessages = messageCount,
                                    totalTokens = stats.monthlyTokens,
                                    dailyAiOperations = stats.dailyMessages,
                                    dailyAiOperationLimit = stats.dailyMessageLimit,
                                    dailyAiOperationsRemaining = limits.dailyMessagesRemaining,
                                    monthlyTokenLimit = stats.monthlyTokenLimit,
                                    monthlyTokensRemaining = limits.monthlyTokensRemaining,
                                    hourlyTokens = stats.hourlyTokens,
                                    estimatedMonthlyCost = stats.estimatedMonthlyCost,
                                    usageWarning = limits.isNearDailyLimit || limits.isNearMonthlyLimit,
                                    statisticsLoaded = true
                                )
                            },
                            onFailure = { error ->
                                Timber.e(error, "Failed to load ledger-backed usage limits")
                            }
                        )
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to load ledger-backed AI usage")
                    }
                )
            },
            onFailure = { error ->
                Timber.e("Failed to load message count: $error")
            }
        )
    }

    private suspend fun hasAdminAiAccess(expectedUserId: String): Boolean {
        val isAdmin = checkAdminPermissionsUseCase(expectedUserId).getOrElse { false }
        val currentUserId = authInteractor.currentUser(waitForAuth = false)
            .getOrNull()
            ?.value
        return isAdmin && currentUserId == expectedUserId
    }

    private suspend fun requireAdminAiAccess(): Boolean {
        val expectedUserId = currentUserId
        if (expectedUserId != null && hasAdminAiAccess(expectedUserId)) return true
        denyAiAccess()
        return false
    }

    private fun denyAiAccess() {
        val accessError = LiftrixError.BusinessLogicError(
            code = "AI_ACCESS_DENIED",
            errorMessage = "AI access is limited to authorized competition administrators."
        )
        settingsObservationJob?.cancel()
        currentUserId = null
        _uiState.value = _uiState.value.copy(
            preferences = null,
            preferencesState = UiState.Error(accessError),
            clearHistoryInProgress = false,
            exportInProgress = false,
            showClearHistoryDialog = false,
            showExportDialog = false,
            error = accessError
        )
    }
    
    fun handleEvent(event: AIChatSettingsEvent) {
        when (event) {
            is AIChatSettingsEvent.UpdateLanguagePreference -> updateLanguagePreference(event.language)
            is AIChatSettingsEvent.UpdateAutoDetectLanguage -> updateAutoDetectLanguage(event.enabled)
            is AIChatSettingsEvent.UpdateResponseStyle -> updateResponseStyle(event.style)
            is AIChatSettingsEvent.UpdateUserContextPrompt -> updateUserContextPrompt(event.prompt)
            is AIChatSettingsEvent.UpdateWorkoutHistoryInclusion -> updateWorkoutHistoryInclusion(event.include)
            is AIChatSettingsEvent.UpdateExerciseFormTips -> updateExerciseFormTips(event.include)
            is AIChatSettingsEvent.UpdateConversationSaveEnabled -> updateConversationSaveEnabled(event.enabled)
            is AIChatSettingsEvent.ClearAllHistory -> clearAllHistory(event.confirmationText)
            is AIChatSettingsEvent.ExportHistory -> exportHistory(event.format)
            AIChatSettingsEvent.DismissError -> dismissError()
            AIChatSettingsEvent.DismissSuccessMessage -> dismissSuccessMessage()
            AIChatSettingsEvent.RetryLoad -> loadInitialData()
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
    
    private fun updateConversationSaveEnabled(enabled: Boolean) {
        updatePreference { it.copy(conversationSaveEnabled = enabled) }
    }
    
    private fun updatePreference(update: (ChatPreferences) -> ChatPreferences) {
        val currentPreferences = _uiState.value.preferences ?: return
        val updatedPreferences = update(currentPreferences.copy(updatedAt = System.currentTimeMillis()))
        
        viewModelScope.launch {
            if (!requireAdminAiAccess()) return@launch

            // Optimistic update
            _uiState.value = _uiState.value.copy(preferences = updatedPreferences)
            
            chatInteractor.updatePreferences(updatedPreferences).fold(
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
            if (!requireAdminAiAccess()) return@launch

            _uiState.value = currentState.copy(
                clearHistoryInProgress = true,
                showClearHistoryDialog = false
            )
            
            chatInteractor.clearAllHistory(
                confirmationText = confirmationText,
                language = currentState.preferences?.preferredLanguage ?: "en"
            ).fold(
                onSuccess = { deletedCount ->
                    Timber.i("Successfully cleared $deletedCount messages")
                    _uiState.value = _uiState.value.copy(
                        clearHistoryInProgress = false,
                        successMessage = "Chat history cleared. $deletedCount messages deleted.",
                        totalMessages = 0
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
            if (!requireAdminAiAccess()) return@launch

            _uiState.value = currentState.copy(
                exportInProgress = true,
                showExportDialog = true,
                exportedData = null
            )
            
            chatInteractor.exportHistory(format).fold(
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
        _uiState.value = _uiState.value.copy(
            showExportDialog = true,
            exportedData = null
        )
    }
    
    private fun hideExportDialog() {
        _uiState.value = _uiState.value.copy(
            showExportDialog = false,
            exportedData = null
        )
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
        return chatInteractor.requiredConfirmationText(language)
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
    val dailyAiOperations: Int = 0,
    val dailyAiOperationLimit: Int = 0,
    val dailyAiOperationsRemaining: Int = 0,
    val monthlyTokenLimit: Int = 0,
    val monthlyTokensRemaining: Int = 0,
    val hourlyTokens: Int = 0,
    val estimatedMonthlyCost: Double = 0.0,
    val usageWarning: Boolean = false,
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
sealed class AIChatSettingsEvent {
    // Language Settings
    data class UpdateLanguagePreference(val language: String) : AIChatSettingsEvent()
    data class UpdateAutoDetectLanguage(val enabled: Boolean) : AIChatSettingsEvent()
    
    // AI Behavior Settings
    data class UpdateResponseStyle(val style: String) : AIChatSettingsEvent()
    data class UpdateUserContextPrompt(val prompt: String?) : AIChatSettingsEvent()
    data class UpdateWorkoutHistoryInclusion(val include: Boolean) : AIChatSettingsEvent()
    data class UpdateExerciseFormTips(val include: Boolean) : AIChatSettingsEvent()
    
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
    object RetryLoad : AIChatSettingsEvent()
}
