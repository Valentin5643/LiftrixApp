package com.example.liftrix.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.social.GymBuddy
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateBuddyShareViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val gymBuddyRepository: GymBuddyRepository,
    private val templateCommandUseCase: TemplateCommandUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(TemplateBuddyShareUiState())
    val uiState: StateFlow<TemplateBuddyShareUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val userId = authRepository.currentUser.first()?.uid
            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "User not authenticated")
                return@launch
            }

            gymBuddyRepository.getGymBuddies(userId).fold(
                onSuccess = { buddies ->
                    _uiState.value = _uiState.value.copy(isLoading = false, buddies = buddies)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load gym buddies"
                    )
                }
            )
        }
    }

    fun shareDirect(templateId: String, buddyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSharing = true, error = null, directShareComplete = false)
            templateCommandUseCase.shareTemplateToBuddy(templateId, buddyId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        directShareComplete = true,
                        successMessage = "Workout shared with gym buddy."
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        error = error.message ?: "Failed to share workout"
                    )
                }
            )
        }
    }

    fun createQrShare(templateId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSharing = true, error = null, qrShareReady = false)
            templateCommandUseCase.createQrTemplateShare(templateId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        qrShareReady = true,
                        successMessage = "QR share is ready."
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        error = error.message ?: "Failed to create QR share"
                    )
                }
            )
        }
    }
}

data class TemplateBuddyShareUiState(
    val isLoading: Boolean = false,
    val isSharing: Boolean = false,
    val buddies: List<GymBuddy> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val directShareComplete: Boolean = false,
    val qrShareReady: Boolean = false
)
