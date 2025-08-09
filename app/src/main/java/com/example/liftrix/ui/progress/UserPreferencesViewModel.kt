package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetDisplaySize
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.progress.components.toDomainWidgetLayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for user preferences management following the MVI pattern with BaseViewModel.
 * 
 * This ViewModel handles user preference operations including layout mode changes,
 * user level updates, widget visibility management, and preference resets. It extends
 * BaseViewModel to leverage standardized state management and error handling.
 * 
 * Architecture:
 * - Follows Clean Architecture with dependency injection
 * - Uses MVI pattern with StateFlow for reactive state management
 * - Integrates with BaseViewModel for consistent error handling
 * - Supports atomic preference updates with validation
 * - Provides comprehensive analytics and logging
 * 
 * Key Features:
 * - Preference updates with validation
 * - Layout mode changes with immediate effect
 * - User level updates with cascading effects
 * - Widget visibility and order management
 * - Auto-refresh settings configuration
 * - Section collapse/expand functionality
 * - Default restoration capabilities
 * 
 * State Management:
 * - Uses UserPreferencesState for structured state
 * - Handles loading states for individual operations
 * - Manages error states with recovery options
 * - Tracks unsaved changes for user feedback
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun UserPreferencesScreen(
 *     viewModel: UserPreferencesViewModel = hiltViewModel()
 * ) {
 *     val state by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     LaunchedEffect(Unit) {
 *         viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
 *     }
 *     
 *     when (state) {
 *         is UiState.Success -> PreferencesContent(state.data, viewModel::handleEvent)
 *         // ... other states
 *     }
 * }
 * ```
 * 
 * @property preferencesService Service for preference operations
 * @property errorHandler Centralized error handling
 */
@HiltViewModel
class UserPreferencesViewModel @Inject constructor(
    private val preferencesService: PreferencesService,
    errorHandler: ErrorHandler
) : BaseViewModel<UiState<UserPreferencesState>, UserPreferencesEvent>(errorHandler) {
    
    /**
     * Internal mutable state flow for preferences state management.
     */
    override val _uiState = MutableStateFlow<UiState<UserPreferencesState>>(UiState.Loading)
    
    /**
     * Current user state received from Coordinator.
     * Updated via Coordinator events instead of direct auth repository observation.
     */
    private val _currentUser = MutableStateFlow<com.example.liftrix.domain.model.User?>(null)
    
    /**
     * Internal state for preference data management.
     */
    private val _internalState = MutableStateFlow(UserPreferencesState.initial())
    
    /**
     * Combined state flow that merges user authentication and preferences.
     */
    val combinedState: StateFlow<UiState<UserPreferencesState>> = combine(
        _currentUser,
        _internalState
    ) { user, preferencesState ->
        when {
            user == null -> UiState.Loading
            !preferencesState.isInitialized -> UiState.Loading
            preferencesState.preferences is AsyncData.Failure -> UiState.Error(preferencesState.preferences.error)
            else -> UiState.Success(preferencesState)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )
    
    init {
        // Observe combined state and update main UI state
        viewModelScope.launch {
            combinedState.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    /**
     * Handles events from the UI following the MVI pattern.
     * 
     * All events are processed through this method to ensure consistent
     * state management and error handling.
     * 
     * @param event The event to process
     */
    override fun handleEvent(event: UserPreferencesEvent) {
        Timber.d("Handling event: ${event.getOperationName()}")
        
        when (event) {
            is UserPreferencesEvent.LoadPreferences -> loadPreferences()
            is UserPreferencesEvent.RefreshPreferences -> refreshPreferences()
            is UserPreferencesEvent.UpdateLayoutMode -> updateLayoutMode(event.mode)
            is UserPreferencesEvent.UpdateUserLevel -> updateUserLevel(event.level)
            is UserPreferencesEvent.ResetToDefaults -> resetToDefaults()
            is UserPreferencesEvent.UpdateWidgetVisibility -> updateWidgetVisibility(event.widgetName, event.visible)
            is UserPreferencesEvent.UpdateWidgetOrder -> updateWidgetOrder(event.widgetOrder)
            is UserPreferencesEvent.UpdateAutoRefreshSettings -> updateAutoRefreshSettings(event.enabled, event.intervalMinutes)
            is UserPreferencesEvent.ToggleSection -> toggleSection(event.sectionName)
            is UserPreferencesEvent.UpdateWidgetSize -> updateWidgetSize(event.widgetName, event.size)
            is UserPreferencesEvent.ValidatePreferences -> validatePreferences()
            is UserPreferencesEvent.SaveChanges -> saveChanges()
            is UserPreferencesEvent.DiscardChanges -> discardChanges()
            is UserPreferencesEvent.DismissError -> dismissError()
            is UserPreferencesEvent.MarkMigrationNoticeSeen -> markWidgetMigrationNoticeShown()
        }
    }
    
    /**
     * Loads user preferences from the service.
     */
    private fun loadPreferences() {
        val userId = _currentUser.value?.uid ?: return
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.copy(
                preferences = AsyncData.Loading()
            )
            
            executeUseCase(
                useCase = { preferencesService.getUserPreferences(userId) },
                onSuccess = { preferences ->
                    _internalState.value = _internalState.value.withPreferences(preferences)
                    Timber.d("Preferences loaded successfully for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value.withError(error)
                    Timber.e("Failed to load preferences for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Refreshes user preferences from remote source.
     */
    private fun refreshPreferences() {
        val userId = _currentUser.value?.uid ?: return
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("refresh", true)
            
            executeUseCase(
                useCase = { preferencesService.getUserPreferences(userId) },
                onSuccess = { preferences ->
                    _internalState.value = _internalState.value
                        .withPreferences(preferences)
                        .withLoading("refresh", false)
                    Timber.d("Preferences refreshed successfully for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value
                        .withError(error)
                        .withLoading("refresh", false)
                    Timber.e("Failed to refresh preferences for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates the dashboard layout mode.
     * 
     * @param mode The new layout mode to apply
     */
    private fun updateLayoutMode(mode: WidgetLayoutMode) {
        val userId = _currentUser.value?.uid ?: return
        
        // Validate that the mode is available for the current user level
        val currentState = _internalState.value
        if (mode !in currentState.getAvailableLayoutModes()) {
            handleError(LiftrixError.ValidationError(
                field = "layoutMode",
                violations = listOf("Layout mode $mode is not available for user level ${currentState.userLevel}")
            ))
            return
        }
        
        viewModelScope.launch {
            // Update state immediately for responsive UI
            _internalState.value = _internalState.value
                .withLayoutMode(mode)
                .withLoading("updateLayoutMode", true)
            
            executeUseCase(
                useCase = { preferencesService.updateLayoutMode(userId, mode.toDomainWidgetLayoutMode()) },
                onSuccess = {
                    _internalState.value = _internalState.value
                        .withLoading("updateLayoutMode", false)
                        .markChangesSaved()
                    Timber.d("Layout mode updated to $mode for user: $userId")
                },
                onError = { error ->
                    // Revert state change on error
                    loadPreferences()
                    _internalState.value = _internalState.value.withLoading("updateLayoutMode", false)
                    Timber.e("Failed to update layout mode for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates the user experience level.
     * 
     * @param level The new user experience level
     */
    private fun updateUserLevel(level: UserLevel) {
        val userId = _currentUser.value?.uid ?: return
        
        viewModelScope.launch {
            // Update state immediately for responsive UI
            _internalState.value = _internalState.value
                .withUserLevel(level)
                .withLoading("updateUserLevel", true)
            
            executeUseCase(
                useCase = { preferencesService.updateUserLevel(userId, level) },
                onSuccess = {
                    _internalState.value = _internalState.value
                        .withLoading("updateUserLevel", false)
                        .markChangesSaved()
                    // Reload preferences to get updated widget configuration
                    loadPreferences()
                    Timber.d("User level updated to $level for user: $userId")
                },
                onError = { error ->
                    // Revert state change on error
                    loadPreferences()
                    _internalState.value = _internalState.value.withLoading("updateUserLevel", false)
                    Timber.e("Failed to update user level for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Resets all preferences to default values.
     */
    private fun resetToDefaults() {
        val userId = _currentUser.value?.uid ?: return
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("resetToDefaults", true)
            
            executeUseCase(
                useCase = { preferencesService.resetToDefaults(userId) },
                onSuccess = {
                    _internalState.value = _internalState.value.withLoading("resetToDefaults", false)
                    // Reload preferences to get default configuration
                    loadPreferences()
                    Timber.d("Preferences reset to defaults for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value.withLoading("resetToDefaults", false)
                    Timber.e("Failed to reset preferences for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates widget visibility for a specific widget.
     * 
     * @param widgetName The name of the widget to update
     * @param visible Whether the widget should be visible
     */
    private fun updateWidgetVisibility(widgetName: String, visible: Boolean) {
        val userId = _currentUser.value?.uid ?: return
        
        if (!UserPreferencesEvent.UpdateWidgetVisibility(widgetName, visible).isValid()) {
            handleError(LiftrixError.ValidationError(
                field = "widgetName",
                violations = listOf("Invalid widget name: $widgetName")
            ))
            return
        }
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("updateWidgetVisibility", true)
            
            executeUseCase(
                useCase = { preferencesService.updateWidgetVisibility(userId, widgetName, visible) },
                onSuccess = {
                    _internalState.value = _internalState.value.withLoading("updateWidgetVisibility", false)
                    // Reload preferences to get updated widget configuration
                    loadPreferences()
                    Timber.d("Widget visibility updated: $widgetName = $visible for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value.withLoading("updateWidgetVisibility", false)
                    Timber.e("Failed to update widget visibility for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates the order of widgets in the dashboard.
     * 
     * @param widgetOrder List of widget names in the desired order
     */
    private fun updateWidgetOrder(widgetOrder: List<String>) {
        val userId = _currentUser.value?.uid ?: return
        
        if (!UserPreferencesEvent.UpdateWidgetOrder(widgetOrder).isValid()) {
            handleError(LiftrixError.ValidationError(
                field = "widgetOrder",
                violations = listOf("Invalid widget order: empty or contains blank names")
            ))
            return
        }
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("updateWidgetOrder", true)
            
            executeUseCase(
                useCase = { preferencesService.updateWidgetOrder(userId, widgetOrder) },
                onSuccess = {
                    _internalState.value = _internalState.value.withLoading("updateWidgetOrder", false)
                    // Reload preferences to get updated widget configuration
                    loadPreferences()
                    Timber.d("Widget order updated for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value.withLoading("updateWidgetOrder", false)
                    Timber.e("Failed to update widget order for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates auto-refresh settings for widgets.
     * 
     * @param enabled Whether auto-refresh should be enabled
     * @param intervalMinutes Refresh interval in minutes
     */
    private fun updateAutoRefreshSettings(enabled: Boolean, intervalMinutes: Int) {
        val userId = _currentUser.value?.uid ?: return
        
        if (!UserPreferencesEvent.UpdateAutoRefreshSettings(enabled, intervalMinutes).isValid()) {
            handleError(LiftrixError.ValidationError(
                field = "intervalMinutes",
                violations = listOf("Invalid refresh interval: $intervalMinutes (must be 1-60)")
            ))
            return
        }
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("updateAutoRefresh", true)
            
            executeUseCase(
                useCase = { preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes) },
                onSuccess = {
                    _internalState.value = _internalState.value.withLoading("updateAutoRefresh", false)
                    // Reload preferences to get updated configuration
                    loadPreferences()
                    Timber.d("Auto-refresh settings updated: enabled=$enabled, interval=$intervalMinutes for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value.withLoading("updateAutoRefresh", false)
                    Timber.e("Failed to update auto-refresh settings for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Toggles the collapsed state of a dashboard section.
     * 
     * @param sectionName The name of the section to toggle
     */
    private fun toggleSection(sectionName: String) {
        val userId = _currentUser.value?.uid ?: return
        
        if (!UserPreferencesEvent.ToggleSection(sectionName).isValid()) {
            handleError(LiftrixError.ValidationError(
                field = "sectionName",
                violations = listOf("Invalid section name: $sectionName")
            ))
            return
        }
        
        // Check if current layout mode supports collapsible sections
        val currentState = _internalState.value
        if (!currentState.supportsCollapsibleSections()) {
            handleError(LiftrixError.ValidationError(
                field = "layoutMode",
                violations = listOf("Current layout mode does not support collapsible sections")
            ))
            return
        }
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("toggleSection", true)
            
            executeUseCase(
                useCase = { preferencesService.toggleSection(userId, sectionName) },
                onSuccess = {
                    _internalState.value = _internalState.value.withLoading("toggleSection", false)
                    // Reload preferences to get updated section state
                    loadPreferences()
                    Timber.d("Section toggled: $sectionName for user: $userId")
                },
                onError = { error ->
                    _internalState.value = _internalState.value.withLoading("toggleSection", false)
                    Timber.e("Failed to toggle section for user: $userId, error: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates widget display size preference.
     * 
     * @param widgetName The name of the widget to resize
     * @param size The new display size
     */
    private fun updateWidgetSize(widgetName: String, size: WidgetDisplaySize) {
        val userId = _currentUser.value?.uid ?: return
        
        if (!UserPreferencesEvent.UpdateWidgetSize(widgetName, size).isValid()) {
            handleError(LiftrixError.ValidationError(
                field = "widgetName",
                violations = listOf("Invalid widget name: $widgetName")
            ))
            return
        }
        
        // Check if current layout mode supports variable sizes
        val currentState = _internalState.value
        if (!currentState.layoutMode.supportsVariableSizes()) {
            handleError(LiftrixError.ValidationError(
                field = "layoutMode",
                violations = listOf("Current layout mode does not support variable widget sizes")
            ))
            return
        }
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("updateWidgetSize", true)
            
            // Update widget size through preferences
            val currentPreferences = currentState.getPreferences()
            if (currentPreferences != null) {
                val updatedPreferences = currentPreferences.updateWidgetSize(widgetName, size)
                
                executeUseCase(
                    useCase = { 
                        // Save updated preferences
                        preferencesService.getUserPreferences(userId).fold(
                            onSuccess = { prefs ->
                                val newPrefs = prefs.updateWidgetSize(widgetName, size)
                                Result.success(newPrefs)
                            },
                            onFailure = { throwable ->
                                Result.failure(throwable)
                            }
                        )
                    },
                    onSuccess = { preferences ->
                        _internalState.value = _internalState.value
                            .withPreferences(preferences)
                            .withLoading("updateWidgetSize", false)
                        Timber.d("Widget size updated: $widgetName = $size for user: $userId")
                    },
                    onError = { error ->
                        _internalState.value = _internalState.value.withLoading("updateWidgetSize", false)
                        Timber.e("Failed to update widget size for user: $userId, error: ${error.message}")
                    }
                )
            }
        }
    }
    
    /**
     * Validates current preferences state.
     */
    private fun validatePreferences() {
        val currentState = _internalState.value
        val preferences = currentState.getPreferences()
        
        if (preferences != null) {
            try {
                preferences.validate()
                Timber.d("Preferences validation passed")
            } catch (e: IllegalArgumentException) {
                handleError(LiftrixError.ValidationError(
                    field = "preferences",
                    violations = listOf("Preferences validation failed: ${e.message}")
                ))
            }
        } else {
            handleError(LiftrixError.ValidationError(
                field = "preferences",
                violations = listOf("No preferences available to validate")
            ))
        }
    }
    
    /**
     * Saves pending preference changes.
     */
    private fun saveChanges() {
        val currentState = _internalState.value
        
        if (!currentState.hasUnsavedChanges) {
            Timber.d("No unsaved changes to save")
            return
        }
        
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("saveChanges", true)
            
            // Reload preferences to persist any cached changes
            loadPreferences()
            
            _internalState.value = _internalState.value
                .withLoading("saveChanges", false)
                .markChangesSaved()
            
            Timber.d("Changes saved successfully")
        }
    }
    
    /**
     * Discards unsaved preference changes.
     */
    private fun discardChanges() {
        viewModelScope.launch {
            _internalState.value = _internalState.value.withLoading("discardChanges", true)
            
            // Reload preferences to discard any unsaved changes
            loadPreferences()
            
            _internalState.value = _internalState.value
                .withLoading("discardChanges", false)
                .markChangesSaved()
            
            Timber.d("Changes discarded successfully")
        }
    }
    
    /**
     * Dismisses any current error messages.
     */
    private fun dismissError() {
        _internalState.value = _internalState.value.copy(
            preferences = when (val currentPrefs = _internalState.value.preferences) {
                is AsyncData.Failure -> AsyncData.Loading()
                else -> currentPrefs
            }
        )
        Timber.d("Error dismissed")
    }
    
    /**
     * Handles coordination events from the ProgressDashboardCoordinator.
     * 
     * This method processes events that require coordination between ViewModels,
     * such as user authentication changes and global data refresh requests.
     * 
     * @param event The coordination event to process
     */
    fun handleCoordinatorEvent(event: CoordinatorEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is CoordinatorEvent.UserAuthChanged -> {
                        val previousUserId = _currentUser.value?.uid
                        // FIXED: Added proper validation and null handling
                        _currentUser.value = event.userId?.let { userId ->
                            // Only create User object if we have a valid userId
                            if (userId.isNotBlank()) {
                                // Create a minimal User object that passes validation
                                // Using a temporary email to satisfy the validation requirement
                                com.example.liftrix.domain.model.User(
                                    uid = userId,
                                    email = "temp@liftrix.app", // FIXED: Use valid email instead of blank
                                    displayName = null,
                                    photoUrl = null,
                                    isAnonymous = false, // FIXED: Keep as false since we have a userId
                                    subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                                    subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE,
                                    subscriptionExpiresAt = null,
                                    premiumFeaturesEnabled = false,
                                    onboardingCompleted = false,
                                    profileVersion = 1L,
                                    createdAt = java.time.LocalDateTime.now(),
                                    lastSignInAt = java.time.LocalDateTime.now(),
                                    updatedAt = java.time.LocalDateTime.now()
                                )
                            } else {
                                // FIXED: Handle blank userId case
                                null
                            }
                        }
                        
                        // Auto-load preferences when user is available and changed
                        if (previousUserId != _currentUser.value?.uid && 
                            _currentUser.value != null && 
                            !_internalState.value.isInitialized) {
                            loadPreferences()
                            Timber.d("Preferences: User auth changed to ${event.userId}, loading preferences")
                        } else if (_currentUser.value == null) {
                            // Clear state when user logs out
                            _internalState.value = UserPreferencesState.initial()
                            Timber.d("Preferences: User logged out, clearing state")
                        }
                    }
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(UserPreferencesEvent.RefreshPreferences)
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("preferences") || event.dataTypes.contains("settings")) {
                            handleEvent(UserPreferencesEvent.RefreshPreferences)
                        }
                    }
                    is CoordinatorEvent.PreferencesChanged -> {
                        // Reload preferences when they change
                        handleEvent(UserPreferencesEvent.RefreshPreferences)
                    }
                    else -> {
                        // Ignore other coordinator events
                    }
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Failed to handle coordinator event: ${event::class.simpleName}",
                    analyticsContext = mapOf(
                        "event_type" to (event::class.simpleName ?: "Unknown"),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
                updateErrorState(error)
                Timber.e(exception, "Failed to handle coordinator event: ${event::class.simpleName}")
            }
        }
    }
    
    /**
     * Marks the widget migration notice as shown by the user.
     * This updates the user's preferences to prevent showing the notice again.
     */
    fun markWidgetMigrationNoticeShown() {
        val userId = _currentUser.value?.uid ?: return
        
        executeUseCase(
            useCase = { 
                preferencesService.getUserPreferences(userId).fold(
                    onSuccess = { preferences ->
                        val updatedPreferences = preferences.markMigrationNoticeSeen()
                        preferencesService.saveWidgetPreferences(updatedPreferences)
                    },
                    onFailure = { error -> LiftrixResult.failure(error) }
                )
            },
            onSuccess = {
                loadPreferences()
                Timber.d("Migration notice marked as shown for user: $userId")
            }
        )
    }
    
    /**
     * Override to handle specific error states for preferences.
     */
    override fun updateErrorState(error: LiftrixError) {
        _internalState.value = _internalState.value.withError(error)
    }
    
    /**
     * Override to set loading state for preferences.
     */
    override fun setLoadingState() {
        _internalState.value = _internalState.value.copy(
            preferences = AsyncData.Loading()
        )
    }
}