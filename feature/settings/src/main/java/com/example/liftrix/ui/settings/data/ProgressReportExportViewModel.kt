package com.example.liftrix.ui.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.export.GenerateProgressReportUseCase
import com.example.liftrix.domain.usecase.export.ProgressReportFileActionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgressReportExportViewModel @Inject constructor(
    private val authQueryUseCase: AuthQueryUseCase,
    private val generateProgressReportUseCase: GenerateProgressReportUseCase,
    private val fileActionsUseCase: ProgressReportFileActionsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProgressReportExportState())
    val uiState: StateFlow<ProgressReportExportState> = _uiState.asStateFlow()

    private val _fileActionEvents = MutableSharedFlow<ProgressReportFileActionEvent>()
    val fileActionEvents: SharedFlow<ProgressReportFileActionEvent> = _fileActionEvents.asSharedFlow()

    private var currentUserId: String? = null

    init {
        loadCurrentUser()
    }

    fun onEvent(event: ProgressReportExportEvent) {
        when (event) {
            is ProgressReportExportEvent.SelectRange -> updateState { copy(selectedRange = event.range, errorMessage = null) }
            is ProgressReportExportEvent.UpdateCustomStartDate -> updateState { copy(customStartDateText = event.value, errorMessage = null) }
            is ProgressReportExportEvent.UpdateCustomEndDate -> updateState { copy(customEndDateText = event.value, errorMessage = null) }
            is ProgressReportExportEvent.UpdateIncludeOptions -> updateState { copy(includeOptions = event.includeOptions) }
            is ProgressReportExportEvent.UpdatePrivacyOptions -> updateState { copy(privacyOptions = event.privacyOptions) }
            ProgressReportExportEvent.GeneratePdf -> generatePdf()
            ProgressReportExportEvent.OpenPdf -> openPdf()
            ProgressReportExportEvent.SharePdf -> sharePdf()
            ProgressReportExportEvent.SaveToDownloads -> saveToDownloads()
            ProgressReportExportEvent.DismissError -> updateState { copy(errorMessage = null) }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
            currentUserId = userId
            updateState {
                copy(
                    isLoadingUser = false,
                    errorMessage = if (userId == null) "User not authenticated." else null
                )
            }
        }
    }

    private fun generatePdf() {
        val userId = currentUserId
        if (userId == null) {
            updateState { copy(errorMessage = "User not authenticated.") }
            return
        }

        val request = buildRequest() ?: return
        updateState { copy(isGenerating = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            generateProgressReportUseCase(userId, request).fold(
                onSuccess = { result ->
                    updateState {
                        copy(
                            isGenerating = false,
                            result = result,
                            successMessage = "PDF generated successfully",
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    updateState {
                        copy(
                            isGenerating = false,
                            errorMessage = userMessage(error),
                            successMessage = null
                        )
                    }
                }
            )
        }
    }

    private fun openPdf() {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            fileActionsUseCase.open(result.filePath).fold(
                onSuccess = { metadata ->
                    _fileActionEvents.emit(
                        ProgressReportFileActionEvent.Open(
                            uriString = metadata.uriString,
                            mimeType = metadata.mimeType
                        )
                    )
                },
                onFailure = { error -> updateState { copy(errorMessage = userMessage(error)) } }
            )
        }
    }

    private fun sharePdf() {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            fileActionsUseCase.share(result.filePath).fold(
                onSuccess = { metadata ->
                    _fileActionEvents.emit(
                        ProgressReportFileActionEvent.Share(
                            uriString = metadata.uriString,
                            mimeType = metadata.mimeType,
                            fileName = metadata.fileName
                        )
                    )
                },
                onFailure = { error -> updateState { copy(errorMessage = userMessage(error)) } }
            )
        }
    }

    private fun saveToDownloads() {
        val result = _uiState.value.result ?: return
        updateState { copy(isSavingToDownloads = true, errorMessage = null) }
        viewModelScope.launch {
            fileActionsUseCase.saveToDownloads(result.filePath, result.fileName).fold(
                onSuccess = {
                    updateState {
                        copy(
                            isSavingToDownloads = false,
                            successMessage = "PDF generated successfully"
                        )
                    }
                },
                onFailure = { error ->
                    updateState {
                        copy(
                            isSavingToDownloads = false,
                            errorMessage = userMessage(error)
                        )
                    }
                }
            )
        }
    }

    private fun buildRequest(): ProgressReportRequest? {
        val state = _uiState.value
        val selectedRange = if (state.selectedRange is ProgressReportDateRange.Custom) {
            val start = runCatching { LocalDate.parse(state.customStartDateText) }.getOrNull()
            val end = runCatching { LocalDate.parse(state.customEndDateText) }.getOrNull()
            if (start == null || end == null) {
                updateState { copy(errorMessage = "Use yyyy-MM-dd for custom dates.") }
                return null
            }
            ProgressReportDateRange.Custom(start, end)
        } else {
            state.selectedRange
        }
        return ProgressReportRequest(
            dateRange = selectedRange,
            includeOptions = state.includeOptions,
            privacyOptions = state.privacyOptions
        )
    }

    private fun userMessage(error: Throwable): String {
        return when (error) {
            is LiftrixError.PermissionError -> error.errorMessage
            is LiftrixError.ValidationError -> error.violations.firstOrNull() ?: error.errorMessage
            else -> "Could not generate the report. Please try again."
        }
    }

    private fun updateState(update: ProgressReportExportState.() -> ProgressReportExportState) {
        _uiState.value = _uiState.value.update()
    }
}
