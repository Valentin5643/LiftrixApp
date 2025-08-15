package com.example.liftrix.ui.social.gymbuddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.GymBuddy
import com.example.liftrix.domain.model.social.QRCodeData
import com.example.liftrix.domain.model.social.QRUserProfile
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.service.QRCodeService
import com.example.liftrix.domain.usecase.social.GetSocialProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for gym buddy screen state management and QR code operations.
 * 
 * Manages gym buddy UI state including buddy list, QR code generation/scanning,
 * loading states, and error handling. Integrates with GymBuddyRepository for
 * buddy management and QRCodeService for QR operations.
 */
@HiltViewModel
class GymBuddyViewModel @Inject constructor(
    private val gymBuddyRepository: GymBuddyRepository,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val qrCodeService: QRCodeService,
    private val getSocialProfileUseCase: GetSocialProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GymBuddyUiState())
    val uiState: StateFlow<GymBuddyUiState> = _uiState.asStateFlow()

    private var selectedBuddy: GymBuddy? = null

    init {
        observeUserAndLoadBuddies()
        trackScreenViewed()
    }

    /**
     * Handles UI events from the gym buddy screen
     */
    fun handleEvent(event: GymBuddyEvent) {
        when (event) {
            is GymBuddyEvent.LoadGymBuddies -> {
                loadGymBuddies()
            }
            is GymBuddyEvent.RefreshData -> {
                refreshData()
            }
            is GymBuddyEvent.GenerateQrCode -> {
                generateQrCode()
            }
            is GymBuddyEvent.RegenerateQrCode -> {
                regenerateQrCode()
            }
            is GymBuddyEvent.ClearQrCode -> {
                clearQrCode()
            }
            is GymBuddyEvent.ShowBuddyOptions -> {
                showBuddyOptions(event.buddy)
            }
            is GymBuddyEvent.RemoveBuddy -> {
                removeBuddy(event.buddy)
            }
            is GymBuddyEvent.UpdateBuddyNickname -> {
                updateBuddyNickname(event.buddy, event.nickname)
            }
            is GymBuddyEvent.ToggleNotifications -> {
                toggleNotifications(event.buddy)
            }
            is GymBuddyEvent.ErrorDismissed -> {
                dismissError()
            }
        }
    }

    /**
     * Loads gym buddies for the current user
     */
    fun loadGymBuddies() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(error = "User not authenticated") }
                    return@launch
                }

                updateState { copy(isLoading = true, error = null) }

                // Observe gym buddies for reactive updates
                gymBuddyRepository.observeGymBuddies(currentUser.uid)
                    .catch { throwable ->
                        Timber.e(throwable, "Error observing gym buddies")
                        updateState { 
                            copy(
                                isLoading = false,
                                error = "Failed to load gym buddies: ${throwable.message}"
                            ) 
                        }
                    }
                    .collect { buddies ->
                        // Check if user can add more buddies
                        val canAddMoreResult = gymBuddyRepository.canAddMoreBuddies(currentUser.uid)
                        val canAddMore = canAddMoreResult.fold(
                            onSuccess = { it },
                            onFailure = { throwable ->
                                Timber.w("Error checking buddy limit: $throwable")
                                buddies.size < 5 // Fallback to simple count check
                            }
                        )

                        updateState {
                            copy(
                                isLoading = false,
                                gymBuddies = buddies,
                                canAddMoreBuddies = canAddMore,
                                error = null
                            )
                        }
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadGymBuddies")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to load gym buddies: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Refreshes gym buddy data
     */
    fun refreshData() {
        loadGymBuddies()
        trackRefreshPerformed()
    }

    /**
     * Generates a QR code for buddy pairing
     */
    private fun generateQrCode() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(error = "User not authenticated") }
                    return@launch
                }

                updateState { copy(isGeneratingQr = true, error = null) }

                // Get user's social profile for QR data
                val socialProfileResult = getSocialProfileUseCase(currentUser.uid)
                if (socialProfileResult.isFailure) {
                    updateState { 
                        copy(
                            isGeneratingQr = false,
                            error = "Failed to load user profile: ${socialProfileResult.exceptionOrNull()?.message}"
                        ) 
                    }
                    return@launch
                }

                val socialProfile = socialProfileResult.getOrNull()
                val userProfile = QRUserProfile(
                    displayName = socialProfile?.displayName ?: currentUser.displayName ?: "User",
                    username = socialProfile?.username ?: "user_${currentUser.uid.take(8)}"
                )

                // Create gym buddy pairing token with expiration
                val pairingToken = "liftrix://gym-buddy/${currentUser.uid}?token=${System.currentTimeMillis()}"
                val expiresAt = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes

                // Generate QR code bitmap
                val qrResult = qrCodeService.generateQRCode(pairingToken, size = 300, margin = 1)
                
                qrResult.fold(
                    onSuccess = { bitmap ->
                        val qrCodeData = QRCodeData(
                            token = pairingToken,
                            expiresAt = expiresAt,
                            bitmap = bitmap
                        )

                        updateState {
                            copy(
                                isGeneratingQr = false,
                                qrCode = qrCodeData,
                                userProfile = userProfile,
                                error = null
                            )
                        }

                        trackQrCodeGenerated()
                    },
                    onFailure = { throwable ->
                        Timber.e("QR code generation failed: $throwable")
                        updateState { 
                            copy(
                                isGeneratingQr = false,
                                error = "Failed to generate QR code: ${throwable.message}"
                            ) 
                        }
                    }
                )

            } catch (exception: Exception) {
                Timber.e(exception, "Error generating QR code")
                updateState { 
                    copy(
                        isGeneratingQr = false,
                        error = "Failed to generate QR code: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Regenerates an expired QR code
     */
    private fun regenerateQrCode() {
        clearQrCode()
        generateQrCode()
        trackQrCodeRegenerated()
    }

    /**
     * Clears the current QR code
     */
    private fun clearQrCode() {
        updateState {
            copy(
                qrCode = null,
                userProfile = null,
                isGeneratingQr = false
            )
        }
    }

    /**
     * Shows options for a specific buddy
     */
    private fun showBuddyOptions(buddy: GymBuddy) {
        selectedBuddy = buddy
        // TODO: Show bottom sheet or dialog with buddy options
        trackBuddyOptionsViewed(buddy)
    }

    /**
     * Removes a gym buddy
     */
    private fun removeBuddy(buddy: GymBuddy) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(error = "User not authenticated") }
                    return@launch
                }

                updateState { copy(isLoading = true, error = null) }

                val result = gymBuddyRepository.removeMutualConnection(
                    userId1 = currentUser.uid,
                    userId2 = buddy.buddyId
                )

                result.fold(
                    onSuccess = {
                        trackBuddyRemoved(buddy)
                        // Buddies list will update automatically via observer
                    },
                    onFailure = { throwable ->
                        Timber.e("Failed to remove buddy: $throwable")
                        updateState { 
                            copy(
                                isLoading = false,
                                error = "Failed to remove buddy: ${throwable.message ?: "Unknown error"}"
                            ) 
                        }
                    }
                )

            } catch (exception: Exception) {
                Timber.e(exception, "Error removing buddy")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to remove buddy: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Updates a buddy's nickname
     */
    private fun updateBuddyNickname(buddy: GymBuddy, nickname: String?) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(error = "User not authenticated") }
                    return@launch
                }

                val result = gymBuddyRepository.updateBuddyNickname(
                    userId = currentUser.uid,
                    buddyId = buddy.buddyId,
                    nickname = nickname
                )

                result.fold(
                    onSuccess = {
                        trackBuddyNicknameUpdated(buddy)
                        // Buddies list will update automatically via observer
                    },
                    onFailure = { throwable ->
                        Timber.e("Failed to update buddy nickname: $throwable")
                        updateState { 
                            copy(error = "Failed to update nickname: ${throwable.message ?: "Unknown error"}") 
                        }
                    }
                )

            } catch (exception: Exception) {
                Timber.e(exception, "Error updating buddy nickname")
                updateState { 
                    copy(error = "Failed to update nickname: ${exception.message}") 
                }
            }
        }
    }

    /**
     * Toggles PR notifications for a buddy
     */
    private fun toggleNotifications(buddy: GymBuddy) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(error = "User not authenticated") }
                    return@launch
                }

                // Toggle cooldown: 0 hours = disabled, 24 hours = enabled
                val newCooldownHours = if (buddy.notificationCooldownHours > 0) 0 else 24

                val result = gymBuddyRepository.updateNotificationCooldown(
                    userId = currentUser.uid,
                    buddyId = buddy.buddyId,
                    cooldownHours = newCooldownHours
                )

                result.fold(
                    onSuccess = {
                        trackNotificationsToggled(buddy, newCooldownHours > 0)
                        // Buddies list will update automatically via observer
                    },
                    onFailure = { throwable ->
                        Timber.e("Failed to toggle notifications: $throwable")
                        updateState { 
                            copy(error = "Failed to update notifications: ${throwable.message ?: "Unknown error"}") 
                        }
                    }
                )

            } catch (exception: Exception) {
                Timber.e(exception, "Error toggling notifications")
                updateState { 
                    copy(error = "Failed to update notifications: ${exception.message}") 
                }
            }
        }
    }

    /**
     * Dismisses the current error
     */
    private fun dismissError() {
        updateState { copy(error = null) }
    }

    /**
     * Observes authentication state and loads data when user is available
     */
    private fun observeUserAndLoadBuddies() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { throwable ->
                    Timber.e(throwable, "Error observing auth state")
                    updateState { copy(error = "Authentication error") }
                }
                .collect { user ->
                    if (user != null) {
                        loadGymBuddies()
                    } else {
                        updateState { 
                            copy(
                                gymBuddies = emptyList(),
                                canAddMoreBuddies = true,
                                error = null
                            ) 
                        }
                    }
                }
        }
    }

    /**
     * Updates the UI state using the provided transform function
     */
    private fun updateState(transform: GymBuddyUiState.() -> GymBuddyUiState) {
        _uiState.value = _uiState.value.transform()
    }

    // Analytics tracking methods

    /**
     * Tracks gym buddy screen viewed event
     */
    private fun trackScreenViewed() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_screen_viewed",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track screen viewed")
            }
        }
    }

    /**
     * Tracks QR code generation event
     */
    private fun trackQrCodeGenerated() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_qr_generated",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "buddy_count" to _uiState.value.gymBuddies.size
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track QR code generation")
            }
        }
    }

    /**
     * Tracks QR code regeneration event
     */
    private fun trackQrCodeRegenerated() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_qr_regenerated",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track QR code regeneration")
            }
        }
    }

    /**
     * Tracks refresh performed event
     */
    private fun trackRefreshPerformed() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_refreshed",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "buddy_count" to _uiState.value.gymBuddies.size
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track refresh")
            }
        }
    }

    /**
     * Tracks buddy options viewed event
     */
    private fun trackBuddyOptionsViewed(buddy: GymBuddy) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_options_viewed",
                        additionalData = mapOf(
                            "buddy_id" to buddy.buddyId,
                            "paired_via_qr" to buddy.pairedViaQr,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track buddy options viewed")
            }
        }
    }

    /**
     * Tracks buddy removal event
     */
    private fun trackBuddyRemoved(buddy: GymBuddy) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_removed",
                        additionalData = mapOf(
                            "buddy_id" to buddy.buddyId,
                            "paired_via_qr" to buddy.pairedViaQr,
                            "connection_duration_days" to ((System.currentTimeMillis() - buddy.createdAt) / (24 * 60 * 60 * 1000)),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track buddy removal")
            }
        }
    }

    /**
     * Tracks buddy nickname update event
     */
    private fun trackBuddyNicknameUpdated(buddy: GymBuddy) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_nickname_updated",
                        additionalData = mapOf(
                            "buddy_id" to buddy.buddyId,
                            "had_previous_nickname" to !buddy.buddyNickname.isNullOrEmpty(),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track nickname update")
            }
        }
    }

    /**
     * Tracks notifications toggle event
     */
    private fun trackNotificationsToggled(buddy: GymBuddy, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
                        eventType = "gym_buddy_notifications_toggled",
                        additionalData = mapOf(
                            "buddy_id" to buddy.buddyId,
                            "notifications_enabled" to enabled,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track notifications toggle")
            }
        }
    }
}

/**
 * UI state for the gym buddy screen
 */
data class GymBuddyUiState(
    val isLoading: Boolean = false,
    val gymBuddies: List<GymBuddy> = emptyList(),
    val canAddMoreBuddies: Boolean = true,
    val qrCode: QRCodeData? = null,
    val userProfile: QRUserProfile? = null,
    val isGeneratingQr: Boolean = false,
    val error: String? = null
)

/**
 * Events that can be triggered from the gym buddy screen UI
 */
sealed class GymBuddyEvent {
    object LoadGymBuddies : GymBuddyEvent()
    object RefreshData : GymBuddyEvent()
    object GenerateQrCode : GymBuddyEvent()
    object RegenerateQrCode : GymBuddyEvent()
    object ClearQrCode : GymBuddyEvent()
    data class ShowBuddyOptions(val buddy: GymBuddy) : GymBuddyEvent()
    data class RemoveBuddy(val buddy: GymBuddy) : GymBuddyEvent()
    data class UpdateBuddyNickname(val buddy: GymBuddy, val nickname: String?) : GymBuddyEvent()
    data class ToggleNotifications(val buddy: GymBuddy) : GymBuddyEvent()
    object ErrorDismissed : GymBuddyEvent()
}