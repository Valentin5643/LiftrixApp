package com.example.liftrix.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.sharing.TemplateShareEvent
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutShareInboxViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val templateQueryUseCase: TemplateQueryUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutShareInboxUiState())
    val uiState: StateFlow<WorkoutShareInboxUiState> = _uiState.asStateFlow()

    fun load(senderId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val receiverId = authRepository.currentUser.first()?.uid
            if (receiverId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "User not authenticated")
                return@launch
            }

            templateQueryUseCase.getPendingSharesFromBuddy(senderId, receiverId).fold(
                onSuccess = { shares ->
                    _uiState.value = _uiState.value.copy(isLoading = false, shares = shares)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load shared workouts"
                    )
                }
            )
        }
    }
}

data class WorkoutShareInboxUiState(
    val isLoading: Boolean = false,
    val shares: List<TemplateShareEvent> = emptyList(),
    val error: String? = null
)

