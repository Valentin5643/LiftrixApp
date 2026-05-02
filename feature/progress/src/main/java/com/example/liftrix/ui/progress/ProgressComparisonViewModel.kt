package com.example.liftrix.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.ProgressComparison
import com.example.liftrix.domain.model.ProgressPhoto
import com.example.liftrix.domain.model.BodyPart
import com.example.liftrix.domain.model.PhotoType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for ProgressComparison screen
 * 
 * Manages progress comparison data loading and display for before/after
 * workout progress visualization.
 */
@HiltViewModel
class ProgressComparisonViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    data class ProgressComparisonUiState(
        val isLoading: Boolean = false,
        val comparison: ProgressComparison? = null,
        val shareMode: Boolean = false,
        val error: LiftrixError? = null
    )

    private val _uiState = MutableStateFlow(ProgressComparisonUiState())
    val uiState: StateFlow<ProgressComparisonUiState> = _uiState.asStateFlow()

    /**
     * Load progress comparison data
     */
    fun loadComparison(comparisonId: String, shareMode: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                shareMode = shareMode, 
                error = null
            )
            
            try {
                // For now, create a placeholder comparison since the repository method
                // doesn't exist yet. In production, this would call:
                // val result = userRepository.getProgressComparison(comparisonId)
                
                val currentTime = System.currentTimeMillis()
                val placeholderComparison = ProgressComparison(
                    id = comparisonId,
                    userId = "current_user",
                    name = "4-Week Progress",
                    bodyPart = BodyPart.FULL_BODY,
                    beforePhoto = ProgressPhoto(
                        id = "before_$comparisonId",
                        userId = "current_user",
                        mediaId = "placeholder_before",
                        bodyPart = BodyPart.FULL_BODY,
                        photoType = PhotoType.FRONT,
                        isPrivate = !shareMode,
                        takenAt = currentTime - (4 * 7 * 24 * 60 * 60 * 1000), // 4 weeks ago
                        createdAt = currentTime - (4 * 7 * 24 * 60 * 60 * 1000)
                    ),
                    afterPhoto = ProgressPhoto(
                        id = "after_$comparisonId",
                        userId = "current_user",
                        mediaId = "placeholder_after",
                        bodyPart = BodyPart.FULL_BODY,
                        photoType = PhotoType.FRONT,
                        isPrivate = !shareMode,
                        takenAt = currentTime,
                        createdAt = currentTime
                    ),
                    timeDifferenceWeeks = 4,
                    weightChangeKg = -5.2f,
                    bodyFatChange = -2.7f,
                    notes = "Great progress in 4 weeks!",
                    createdAt = currentTime
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    comparison = placeholderComparison
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading progress comparison")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = LiftrixError.BusinessLogicError(
                        code = "COMPARISON_LOAD_FAILED",
                        errorMessage = "Failed to load progress comparison",
                        analyticsContext = mapOf("comparisonId" to comparisonId)
                    )
                )
            }
        }
    }
}