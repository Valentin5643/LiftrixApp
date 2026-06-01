package com.example.liftrix.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.domain.share.PlatformShareAdapter
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ShareWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    private val prDetectionService: PRDetectionService,
    private val statsFormatter: WorkoutShareStatsFormatter,
    private val imageExporter: WorkoutShareImageExporter,
    private val platformShareAdapter: PlatformShareAdapter
) : ViewModel() {

    data class ShareWorkoutUiState(
        val isLoading: Boolean = false,
        val shareableContent: ShareableContent? = null,
        val storyStats: WorkoutShareStoryStats? = null,
        val templates: List<WorkoutShareTemplate> = WorkoutShareTemplateCatalog.templates,
        val selectedTemplate: WorkoutShareTemplate = WorkoutShareTemplateCatalog.defaultTemplate,
        val isGenerating: Boolean = false,
        val activeAction: WorkoutShareAction? = null,
        val errorMessage: String? = null,
        val error: LiftrixError? = null
    )

    private val _uiState = MutableStateFlow(ShareWorkoutUiState())
    val uiState: StateFlow<ShareWorkoutUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<WorkoutShareEffect>()
    val effects: SharedFlow<WorkoutShareEffect> = _effects.asSharedFlow()

    fun loadWorkout(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, errorMessage = null)

            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it.value },
                onFailure = {
                    showLoadError(
                        LiftrixError.BusinessLogicError(
                            code = "USER_NOT_AUTHENTICATED",
                            errorMessage = "User must be authenticated to share workouts",
                            analyticsContext = mapOf("operation" to "SHARE_WORKOUT")
                        )
                    )
                    return@launch
                }
            )

            workoutRepository.getWorkoutById(WorkoutId.fromString(workoutId), userId).fold(
                onSuccess = { workout ->
                    if (workout == null) {
                        showLoadError(
                            LiftrixError.BusinessLogicError(
                                code = "WORKOUT_NOT_FOUND",
                                errorMessage = "Workout not found",
                                analyticsContext = mapOf("workoutId" to workoutId)
                            )
                        )
                        return@launch
                    }

                    val personalRecords = prDetectionService.detectPersonalRecords(workout, userId).fold(
                        onSuccess = { it },
                        onFailure = { error ->
                            Timber.w(error, "Unable to detect PRs for workout share")
                            emptyList()
                        }
                    )
                    val storyStats = statsFormatter.format(workout, personalRecords)
                    val shareableContent = ShareableContent(
                        id = workoutId,
                        type = ShareableContentType.WORKOUT,
                        title = storyStats.workoutName,
                        subtitle = storyStats.displayDate,
                        stats = mapOf(
                            "Volume" to storyStats.totalVolume,
                            "Exercises" to storyStats.exerciseCount,
                            "PRs" to storyStats.prCount.toString()
                        ),
                        imageUrl = null,
                        userAvatar = null,
                        metadata = mapOf("date" to workout.date.toString())
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        shareableContent = shareableContent,
                        storyStats = storyStats,
                        error = null,
                        errorMessage = null
                    )
                },
                onFailure = { throwable ->
                    showLoadError(
                        throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "WORKOUT_LOAD_FAILED",
                            errorMessage = "Failed to load workout: ${throwable.message}",
                            analyticsContext = mapOf("workoutId" to workoutId)
                        )
                    )
                }
            )
        }
    }

    fun selectTemplate(templateId: String) {
        _uiState.value = _uiState.value.copy(
            selectedTemplate = WorkoutShareTemplateCatalog.resolve(templateId)
        )
    }

    fun performAction(action: WorkoutShareAction) {
        viewModelScope.launch {
            val state = _uiState.value
            val stats = state.storyStats
            val content = state.shareableContent
            if (stats == null || content == null) {
                _effects.emit(WorkoutShareEffect.ShowError("Workout story is not ready yet"))
                return@launch
            }

            _uiState.value = state.copy(isGenerating = true, activeAction = action, errorMessage = null)
            when (action) {
                WorkoutShareAction.Save -> saveStory(stats, state.selectedTemplate)
                WorkoutShareAction.InstagramStory -> shareStory(
                    action = action,
                    platform = SocialPlatform.INSTAGRAM,
                    stats = stats,
                    template = state.selectedTemplate,
                    content = content
                )
                WorkoutShareAction.WhatsApp -> shareStory(
                    action = action,
                    platform = SocialPlatform.WHATSAPP,
                    stats = stats,
                    template = state.selectedTemplate,
                    content = content
                )
                WorkoutShareAction.NativeShare -> shareNative(stats, state.selectedTemplate, content)
            }
            _uiState.value = _uiState.value.copy(isGenerating = false, activeAction = null)
        }
    }

    private suspend fun shareStory(
        action: WorkoutShareAction,
        platform: SocialPlatform,
        stats: WorkoutShareStoryStats,
        template: WorkoutShareTemplate,
        content: ShareableContent
    ) {
        val export = imageExporter.exportToCache(stats, template).getOrElse { error ->
            _effects.emit(WorkoutShareEffect.ShowError(error.message ?: "Unable to generate story image"))
            return
        }
        val caption = "Just finished ${stats.workoutName} with ${stats.totalVolume} volume."
        platformShareAdapter.shareImage(platform, export.filePath, content, caption).fold(
            onSuccess = { intent -> _effects.emit(WorkoutShareEffect.LaunchShare(intent)) },
            onFailure = {
                if (action == WorkoutShareAction.InstagramStory || action == WorkoutShareAction.WhatsApp) {
                    platformShareAdapter.createNativeImageShare(export.filePath, content, caption).fold(
                        onSuccess = { intent -> _effects.emit(WorkoutShareEffect.LaunchShare(intent)) },
                        onFailure = { error -> _effects.emit(WorkoutShareEffect.ShowError(error.message ?: "Share failed")) }
                    )
                } else {
                    _effects.emit(WorkoutShareEffect.ShowError(it.message ?: "Share failed"))
                }
            }
        )
    }

    private suspend fun shareNative(
        stats: WorkoutShareStoryStats,
        template: WorkoutShareTemplate,
        content: ShareableContent
    ) {
        val export = imageExporter.exportToCache(stats, template).getOrElse { error ->
            _effects.emit(WorkoutShareEffect.ShowError(error.message ?: "Unable to generate story image"))
            return
        }
        val caption = "Shared from Liftrix"
        platformShareAdapter.createNativeImageShare(export.filePath, content, caption).fold(
            onSuccess = { intent -> _effects.emit(WorkoutShareEffect.LaunchShare(intent)) },
            onFailure = { error -> _effects.emit(WorkoutShareEffect.ShowError(error.message ?: "Share failed")) }
        )
    }

    private suspend fun saveStory(
        stats: WorkoutShareStoryStats,
        template: WorkoutShareTemplate
    ) {
        imageExporter.saveToDevice(stats, template).fold(
            onSuccess = { uri -> _effects.emit(WorkoutShareEffect.SaveSucceeded(uri)) },
            onFailure = { error -> _effects.emit(WorkoutShareEffect.ShowError(error.message ?: "Save failed")) }
        )
    }

    private fun showLoadError(error: LiftrixError) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = error,
            errorMessage = when (error) {
                is LiftrixError.BusinessLogicError -> error.errorMessage
                else -> error.message ?: "Unable to load workout"
            }
        )
    }
}
