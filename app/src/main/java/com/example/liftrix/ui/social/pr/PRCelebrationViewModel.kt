package com.example.liftrix.ui.social.pr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.social.PRNotification
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.AnalyticsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for PR celebration dialog state management and reaction handling.
 * 
 * Manages celebration UI state including reactions, animations, and analytics
 * tracking for gym buddy PR celebrations.
 */
@HiltViewModel
class PRCelebrationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService
    // TODO: Inject PRNotificationRepository when available for reaction persistence
) : ViewModel() {

    private val _uiState = MutableStateFlow(PRCelebrationUiState())
    val uiState: StateFlow<PRCelebrationUiState> = _uiState.asStateFlow()

    private var currentNotification: PRNotification? = null
    private var currentUserId: String? = null

    init {
        observeAuthState()
    }

    /**
     * Handles UI events from the PR celebration dialog
     */
    fun handleEvent(event: PRCelebrationEvent) {
        when (event) {
            is PRCelebrationEvent.LoadNotification -> {
                loadNotification(event.notification)
            }
            is PRCelebrationEvent.AddReaction -> {
                addReaction(event.reaction)
            }
            is PRCelebrationEvent.ViewWorkout -> {
                trackWorkoutViewed()
            }
            is PRCelebrationEvent.DismissDialog -> {
                trackDialogDismissed()
            }
        }
    }

    /**
     * Loads the PR notification and initializes the celebration
     */
    private fun loadNotification(notification: PRNotification) {
        currentNotification = notification
        updateState { 
            copy(
                notification = notification,
                userReaction = notification.reactedWith,
                error = null
            ) 
        }
        
        trackCelebrationViewed(notification)
    }

    /**
     * Adds a reaction to the PR notification
     */
    private fun addReaction(reaction: String) {
        val notification = currentNotification ?: return
        val userId = currentUserId ?: return

        viewModelScope.launch {
            try {
                updateState { copy(isReacting = true, error = null) }

                // TODO: Save reaction to repository when available
                // For now, just update local state
                updateState { 
                    copy(
                        userReaction = reaction,
                        isReacting = false,
                        error = null
                    ) 
                }

                trackReactionAdded(notification, reaction)

            } catch (exception: Exception) {
                Timber.e(exception, "Error adding reaction")
                updateState { 
                    copy(
                        isReacting = false,
                        error = "Failed to add reaction: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Observes authentication state
     */
    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { throwable ->
                    Timber.e(throwable, "Error observing auth state in PR celebration")
                    updateState { copy(error = "Authentication error") }
                }
                .collect { user ->
                    currentUserId = user?.uid
                }
        }
    }

    /**
     * Updates the UI state using the provided transform function
     */
    private fun updateState(transform: PRCelebrationUiState.() -> PRCelebrationUiState) {
        _uiState.value = _uiState.value.transform()
    }

    // Analytics tracking methods

    /**
     * Tracks celebration dialog viewed event
     */
    private fun trackCelebrationViewed(notification: PRNotification) {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "pr_celebration_viewed",
                        additionalData = mapOf(
                            "from_user_id" to notification.fromUserId,
                            "exercise_name" to notification.exerciseName,
                            "pr_type" to notification.prType,
                            "pr_weight" to (notification.prWeight ?: 0f),
                            "pr_reps" to (notification.prReps ?: 0),
                            "improvement_percent" to (notification.improvementPercent ?: 0f),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track celebration viewed")
            }
        }
    }

    /**
     * Tracks reaction added event
     */
    private fun trackReactionAdded(notification: PRNotification, reaction: String) {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "pr_celebration_reacted",
                        additionalData = mapOf(
                            "from_user_id" to notification.fromUserId,
                            "reaction" to reaction,
                            "exercise_name" to notification.exerciseName,
                            "pr_type" to notification.prType,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track reaction added")
            }
        }
    }

    /**
     * Tracks workout viewed from celebration
     */
    private fun trackWorkoutViewed() {
        viewModelScope.launch {
            try {
                val notification = currentNotification
                if (notification != null && currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId!!,
                        eventType = "pr_celebration_workout_viewed",
                        additionalData = mapOf(
                            "from_user_id" to notification.fromUserId,
                            "workout_id" to notification.workoutId,
                            "exercise_name" to notification.exerciseName,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track workout viewed")
            }
        }
    }

    /**
     * Tracks dialog dismissed event
     */
    private fun trackDialogDismissed() {
        viewModelScope.launch {
            try {
                val notification = currentNotification
                if (notification != null && currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId!!,
                        eventType = "pr_celebration_dismissed",
                        additionalData = mapOf(
                            "from_user_id" to notification.fromUserId,
                            "had_reaction" to (uiState.value.userReaction != null),
                            "reaction" to (uiState.value.userReaction ?: "none"),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track dialog dismissed")
            }
        }
    }
}

/**
 * UI state for the PR celebration dialog
 */
data class PRCelebrationUiState(
    val notification: PRNotification? = null,
    val userReaction: String? = null,
    val isReacting: Boolean = false,
    val error: String? = null
)

/**
 * Events that can be triggered from the PR celebration dialog UI
 */
sealed class PRCelebrationEvent {
    data class LoadNotification(val notification: PRNotification) : PRCelebrationEvent()
    data class AddReaction(val reaction: String) : PRCelebrationEvent()
    object ViewWorkout : PRCelebrationEvent()
    object DismissDialog : PRCelebrationEvent()
}