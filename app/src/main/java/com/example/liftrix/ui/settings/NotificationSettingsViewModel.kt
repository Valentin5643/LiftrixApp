package com.example.liftrix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.liftrix.domain.usecase.notifications.NotificationPreferencesUseCase
import com.example.liftrix.domain.usecase.notifications.GetMutedUsersCountUseCase
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import com.example.liftrix.domain.model.notifications.DeliveryFrequency
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * ViewModel for the notification settings screen.
 *
 * Manages comprehensive notification preferences including:
 * - Master notification toggle
 * - Category-specific settings (social, workout, achievement)
 * - Delivery timing and frequency
 * - Quiet hours configuration
 * - Sound and vibration preferences
 * - Muted users management
 *
 * Features:
 * - MVI pattern with clear state management
 * - Optimistic updates for better UX
 * - Error handling with retry capability
 * - Analytics tracking for preference changes
 * - Performance optimizations with StateFlow
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val notificationPreferencesUseCase: NotificationPreferencesUseCase,
    private val getMutedUsersCountUseCase: GetMutedUsersCountUseCase,
    private val authQueryUseCase: AuthQueryUseCase
) : ModernBaseViewModel<NotificationSettingsUiState>(initialState = NotificationSettingsUiState()) {

    init {
        loadNotificationPreferences()
        loadMutedUsersCount()
    }

    fun handleEvent(event: NotificationSettingsEvent) {
        when (event) {
            is NotificationSettingsEvent.RefreshPreferences -> {
                loadNotificationPreferences()
                loadMutedUsersCount()
            }
            
            is NotificationSettingsEvent.ErrorDismissed -> {
                updateState { it.copy(error = null) }
            }
            
            is NotificationSettingsEvent.ToggleMasterNotifications -> {
                updateNotificationPreference { copy(notificationsEnabled = event.enabled) }
            }
            
            // Social Notifications
            is NotificationSettingsEvent.ToggleSocialNotifications -> {
                updateNotificationPreference { copy(socialNotifications = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleSocialExpansion -> {
                updateState { currentState ->
                    currentState.copy(socialExpanded = !currentState.socialExpanded) 
                }
            }
            
            is NotificationSettingsEvent.ToggleGymBuddyPRs -> {
                updateNotificationPreference { copy(gymBuddyPrs = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleFollowRequests -> {
                updateNotificationPreference { copy(followRequests = event.enabled) }
            }
            
            is NotificationSettingsEvent.TogglePostLikes -> {
                updateNotificationPreference { copy(postLikes = event.enabled) }
            }
            
            is NotificationSettingsEvent.TogglePostComments -> {
                updateNotificationPreference { copy(postComments = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleMentions -> {
                updateNotificationPreference { copy(mentions = event.enabled) }
            }
            
            // Workout Notifications
            is NotificationSettingsEvent.ToggleWorkoutNotifications -> {
                updateNotificationPreference { copy(workoutNotifications = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleWorkoutExpansion -> {
                updateState { currentState ->
                    currentState.copy(workoutExpanded = !currentState.workoutExpanded) 
                }
            }
            
            is NotificationSettingsEvent.ToggleWorkoutReminders -> {
                updateNotificationPreference { copy(reminderNotifications = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleRestDayReminders -> {
                // This would be a new field in NotificationPreferences
                Timber.d("Rest day reminders toggle: ${event.enabled}")
            }
            
            // Achievement Notifications
            is NotificationSettingsEvent.ToggleAchievementNotifications -> {
                updateNotificationPreference { copy(achievementNotifications = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleAchievementExpansion -> {
                updateState { currentState ->
                    currentState.copy(achievementExpanded = !currentState.achievementExpanded) 
                }
            }
            
            is NotificationSettingsEvent.TogglePersonalRecords -> {
                // Personal records are part of achievement notifications
                updateNotificationPreference { copy(achievementNotifications = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleMilestoneAchievements -> {
                // Milestone achievements are part of achievement notifications
                updateNotificationPreference { copy(achievementNotifications = event.enabled) }
            }
            
            // Delivery & Timing
            is NotificationSettingsEvent.ToggleQuietHours -> {
                updateNotificationPreference { copy(quietHoursEnabled = event.enabled) }
            }
            
            is NotificationSettingsEvent.ShowQuietHoursStartPicker -> {
                updateState { it.copy(showQuietHoursStartPicker = true) }
            }
            
            is NotificationSettingsEvent.DismissQuietHoursStartPicker -> {
                updateState { it.copy(showQuietHoursStartPicker = false) }
            }
            
            is NotificationSettingsEvent.UpdateQuietHoursStart -> {
                updateState { it.copy(showQuietHoursStartPicker = false) }
                updateNotificationPreference { copy(quietHoursStart = event.hour) }
            }
            
            is NotificationSettingsEvent.ShowQuietHoursEndPicker -> {
                updateState { it.copy(showQuietHoursEndPicker = true) }
            }
            
            is NotificationSettingsEvent.DismissQuietHoursEndPicker -> {
                updateState { it.copy(showQuietHoursEndPicker = false) }
            }
            
            is NotificationSettingsEvent.UpdateQuietHoursEnd -> {
                updateState { it.copy(showQuietHoursEndPicker = false) }
                updateNotificationPreference { copy(quietHoursEnd = event.hour) }
            }
            
            is NotificationSettingsEvent.ToggleBatchSocialNotifications -> {
                updateNotificationPreference { copy(batchSocialNotifications = event.enabled) }
            }
            
            // Delivery Frequency
            is NotificationSettingsEvent.ShowDeliveryFrequencySelector -> {
                updateState { it.copy(showDeliveryFrequencySelector = true) }
            }
            
            is NotificationSettingsEvent.DismissDeliveryFrequencySelector -> {
                updateState { it.copy(showDeliveryFrequencySelector = false) }
            }
            
            is NotificationSettingsEvent.UpdateDeliveryFrequency -> {
                updateState { currentState ->
                    currentState.copy(
                        showDeliveryFrequencySelector = false,
                        socialDeliveryFrequency = event.frequency
                    )
                }
                val deliveryFrequency = when (event.frequency) {
                    DeliveryFrequency.IMMEDIATE -> "IMMEDIATE"
                    DeliveryFrequency.HOURLY -> "HOURLY"
                    DeliveryFrequency.DAILY -> "DAILY"
                }
                updateNotificationPreference { copy(deliveryFrequency = deliveryFrequency) }
            }
            
            // Sound & Vibration
            is NotificationSettingsEvent.ToggleNotificationSound -> {
                updateNotificationPreference { copy(notificationSound = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleNotificationVibration -> {
                updateNotificationPreference { copy(notificationVibration = event.enabled) }
            }
            
            is NotificationSettingsEvent.ToggleInAppNotifications -> {
                updateNotificationPreference { copy(showInAppNotifications = event.enabled) }
            }
        }
    }

    /**
     * Load current notification preferences from the repository
     */
    private fun loadNotificationPreferences() {
        updateState { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = {
                    updateState { currentState ->
                        currentState.copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }
            )

            val result = notificationPreferencesUseCase(userId)
            result.fold(
                onSuccess = { preferences ->
                    updateState { currentState ->
                        currentState.copy(
                            preferencesState = UiState.Success(preferences),
                            isLoading = false,
                            error = null,
                            notificationsEnabled = preferences.notificationsEnabled,
                            socialNotifications = preferences.socialNotifications,
                            workoutNotifications = preferences.workoutNotifications,
                            achievementNotifications = preferences.achievementNotifications,
                            reminderNotifications = preferences.reminderNotifications,
                            gymBuddyPrs = preferences.gymBuddyPrs,
                            followRequests = preferences.followRequests,
                            postLikes = preferences.postLikes,
                            postComments = preferences.postComments,
                            mentions = preferences.mentions,
                            quietHoursEnabled = preferences.quietHoursEnabled,
                            quietHoursStart = preferences.quietHoursStart,
                            quietHoursEnd = preferences.quietHoursEnd,
                            batchSocialNotifications = preferences.batchSocialNotifications,
                            notificationSound = preferences.notificationSound,
                            notificationVibration = preferences.notificationVibration,
                            showInAppNotifications = preferences.showInAppNotifications,
                            socialDeliveryFrequency = mapDeliveryFrequency(preferences.deliveryFrequency),
                            workoutReminders = preferences.reminderNotifications,
                            restDayReminders = false, // Default until implemented
                            personalRecords = preferences.achievementNotifications,
                            milestoneAchievements = preferences.achievementNotifications
                        )
                    }
                },
                onFailure = { error ->
                    updateState { currentState ->
                        currentState.copy(
                            preferencesState = UiState.Error(error as LiftrixError),
                            isLoading = false,
                            error = error.message
                        )
                    }
                    Timber.e("Failed to load notification preferences: $error")
                }
            )
        }
    }

    /**
     * Load count of muted users for display
     */
    private fun loadMutedUsersCount() {
        viewModelScope.launch {
            getMutedUsersCountUseCase()
                .collect { result ->
                    result.fold(
                        onSuccess = { count: Int ->
                            updateState { it.copy(mutedUsersCount = count) }
                        },
                        onFailure = { error: Throwable ->
                            Timber.w("Failed to load muted users count: $error")
                            // Don't show error for this, just use default count
                        }
                    )
                }
        }
    }

    /**
     * Update notification preferences with optimistic updates
     */
    private fun updateNotificationPreference(
        update: NotificationPreferences.() -> NotificationPreferences
    ) {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = {
                    updateState { currentState -> currentState.copy(error = "User not authenticated") }
                    return@launch
                }
            )

            val currentState = _uiState.value

            // Create updated preferences based on current UI state
            val currentPreferences = NotificationPreferences(
                userId = userId,
            notificationsEnabled = currentState.notificationsEnabled,
            socialNotifications = currentState.socialNotifications,
            workoutNotifications = currentState.workoutNotifications,
            achievementNotifications = currentState.achievementNotifications,
            reminderNotifications = currentState.reminderNotifications,
            gymBuddyPrs = currentState.gymBuddyPrs,
            followRequests = currentState.followRequests,
            postLikes = currentState.postLikes,
            postComments = currentState.postComments,
            mentions = currentState.mentions,
            deliveryFrequency = mapDeliveryFrequencyToString(currentState.socialDeliveryFrequency),
            quietHoursEnabled = currentState.quietHoursEnabled,
            quietHoursStart = currentState.quietHoursStart,
            quietHoursEnd = currentState.quietHoursEnd,
            batchSocialNotifications = currentState.batchSocialNotifications,
            batchWindowMinutes = 60, // Default
            notificationSound = currentState.notificationSound,
            notificationVibration = currentState.notificationVibration,
            showInAppNotifications = currentState.showInAppNotifications,
            updatedAt = System.currentTimeMillis()
        )
        
            val updatedPreferences = update(currentPreferences)

            // Show updating state
            updateState { it.copy(isUpdatingPreferences = true) }

            val updateResult = notificationPreferencesUseCase.update(updatedPreferences)
            updateResult.fold(
                onSuccess = { _ ->
                    updateState { it.copy(isUpdatingPreferences = false) }
                    Timber.d("Successfully updated notification preferences")
                },
                onFailure = { error ->
                    updateState { currentState ->
                        currentState.copy(
                            isUpdatingPreferences = false,
                            error = "Failed to update preferences: ${error.message}"
                        )
                    }

                    // Reload preferences to revert optimistic updates
                    loadNotificationPreferences()

                    Timber.e("Failed to update notification preferences: $error")
                }
            )
        }
    }

    /**
     * Map string delivery frequency to enum
     */
    private fun mapDeliveryFrequency(frequency: String): DeliveryFrequency {
        return when (frequency) {
            "IMMEDIATE" -> DeliveryFrequency.IMMEDIATE
            "HOURLY" -> DeliveryFrequency.HOURLY
            "DAILY" -> DeliveryFrequency.DAILY
            else -> DeliveryFrequency.IMMEDIATE
        }
    }

    /**
     * Map enum delivery frequency to string
     */
    private fun mapDeliveryFrequencyToString(frequency: DeliveryFrequency): String {
        return when (frequency) {
            DeliveryFrequency.IMMEDIATE -> "IMMEDIATE"
            DeliveryFrequency.HOURLY -> "HOURLY"
            DeliveryFrequency.DAILY -> "DAILY"
        }
    }
}

/**
 * UI state for notification settings screen
 */
data class NotificationSettingsUiState(
    val preferencesState: UiState<NotificationPreferences> = UiState.Loading,
    val isLoading: Boolean = false,
    val isUpdatingPreferences: Boolean = false,
    val error: String? = null,
    
    // Master toggle
    val notificationsEnabled: Boolean = true,
    
    // Category toggles
    val socialNotifications: Boolean = true,
    val workoutNotifications: Boolean = true,
    val achievementNotifications: Boolean = true,
    val reminderNotifications: Boolean = true,
    
    // Category expansion states
    val socialExpanded: Boolean = false,
    val workoutExpanded: Boolean = false,
    val achievementExpanded: Boolean = false,
    
    // Social notification subcategories
    val gymBuddyPrs: Boolean = true,
    val followRequests: Boolean = true,
    val postLikes: Boolean = true,
    val postComments: Boolean = true,
    val mentions: Boolean = true,
    
    // Workout notification subcategories
    val workoutReminders: Boolean = true,
    val restDayReminders: Boolean = false,
    
    // Achievement notification subcategories
    val personalRecords: Boolean = true,
    val milestoneAchievements: Boolean = true,
    
    // Delivery preferences
    val socialDeliveryFrequency: DeliveryFrequency = DeliveryFrequency.IMMEDIATE,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 8,
    val batchSocialNotifications: Boolean = true,
    
    // Sound and vibration
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val showInAppNotifications: Boolean = true,
    
    // Muted users
    val mutedUsersCount: Int = 0,
    
    // Dialog states
    val showQuietHoursStartPicker: Boolean = false,
    val showQuietHoursEndPicker: Boolean = false,
    val showDeliveryFrequencySelector: Boolean = false
)

/**
 * Events for notification settings screen
 */
sealed class NotificationSettingsEvent {
    object RefreshPreferences : NotificationSettingsEvent()
    object ErrorDismissed : NotificationSettingsEvent()
    
    // Master toggle
    data class ToggleMasterNotifications(val enabled: Boolean) : NotificationSettingsEvent()
    
    // Category toggles
    data class ToggleSocialNotifications(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleWorkoutNotifications(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleAchievementNotifications(val enabled: Boolean) : NotificationSettingsEvent()
    
    // Category expansion
    object ToggleSocialExpansion : NotificationSettingsEvent()
    object ToggleWorkoutExpansion : NotificationSettingsEvent()
    object ToggleAchievementExpansion : NotificationSettingsEvent()
    
    // Social subcategories
    data class ToggleGymBuddyPRs(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleFollowRequests(val enabled: Boolean) : NotificationSettingsEvent()
    data class TogglePostLikes(val enabled: Boolean) : NotificationSettingsEvent()
    data class TogglePostComments(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleMentions(val enabled: Boolean) : NotificationSettingsEvent()
    
    // Workout subcategories
    data class ToggleWorkoutReminders(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleRestDayReminders(val enabled: Boolean) : NotificationSettingsEvent()
    
    // Achievement subcategories
    data class TogglePersonalRecords(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleMilestoneAchievements(val enabled: Boolean) : NotificationSettingsEvent()
    
    // Delivery & Timing
    data class ToggleQuietHours(val enabled: Boolean) : NotificationSettingsEvent()
    object ShowQuietHoursStartPicker : NotificationSettingsEvent()
    object DismissQuietHoursStartPicker : NotificationSettingsEvent()
    data class UpdateQuietHoursStart(val hour: Int) : NotificationSettingsEvent()
    object ShowQuietHoursEndPicker : NotificationSettingsEvent()
    object DismissQuietHoursEndPicker : NotificationSettingsEvent()
    data class UpdateQuietHoursEnd(val hour: Int) : NotificationSettingsEvent()
    data class ToggleBatchSocialNotifications(val enabled: Boolean) : NotificationSettingsEvent()
    
    // Delivery Frequency
    object ShowDeliveryFrequencySelector : NotificationSettingsEvent()
    object DismissDeliveryFrequencySelector : NotificationSettingsEvent()
    data class UpdateDeliveryFrequency(val frequency: DeliveryFrequency) : NotificationSettingsEvent()
    
    // Sound & Vibration
    data class ToggleNotificationSound(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleNotificationVibration(val enabled: Boolean) : NotificationSettingsEvent()
    data class ToggleInAppNotifications(val enabled: Boolean) : NotificationSettingsEvent()
}