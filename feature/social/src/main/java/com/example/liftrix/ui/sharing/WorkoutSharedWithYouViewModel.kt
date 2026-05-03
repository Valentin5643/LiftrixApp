package com.example.liftrix.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.sharing.SharedTemplatePreview
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutSharedWithYouViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val templateQueryUseCase: TemplateQueryUseCase,
    private val templateCommandUseCase: TemplateCommandUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutSharedWithYouUiState())
    val uiState: StateFlow<WorkoutSharedWithYouUiState> = _uiState.asStateFlow()

    fun load(shareId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val receiverId = authRepository.currentUser.first()?.uid
            if (receiverId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "User not authenticated")
                return@launch
            }

            templateQueryUseCase.getSharedTemplatePreview(shareId, receiverId).fold(
                onSuccess = { preview ->
                    _uiState.value = _uiState.value.copy(isLoading = false, preview = preview)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Shared workout is no longer available"
                    )
                }
            )
        }
    }

    fun saveToMyWorkouts(shareId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            templateCommandUseCase.acceptSharedTemplate(shareId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveComplete = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = error.message ?: "Failed to save workout"
                    )
                }
            )
        }
    }
}

data class WorkoutSharedWithYouUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val preview: SharedTemplatePreview? = null,
    val error: String? = null,
    val saveComplete: Boolean = false
)
