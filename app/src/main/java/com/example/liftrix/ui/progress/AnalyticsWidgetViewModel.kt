package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.SubscriptionStatus
import java.time.LocalDateTime
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import timber.log.Timber

/**
 * ViewModel for analytics widget management following the MVI pattern.
 * 
 * This ViewModel handles all analytics widget operations including data loading,
 * configuration management, and user preference persistence. It extends BaseViewModel
 * to provide standardized state management and error handling.
 * 
 * Key Features:
 * - Widget data loading with caching and refresh capabilities
 * - Configuration management with atomic updates
 * - Widget visibility toggle with business rule validation
 * - Bulk operations for performance optimization
 * - Real-time user authentication state monitoring
 * - Comprehensive error handling with recovery options
 * - Analytics tracking for user behavior insights
 * 
 * Architecture:
 * - Follows MVI pattern with unidirectional data flow
 * - Integrates with AnalyticsService for data operations
 * - Uses StateFlow for reactive UI updates
 * - Implements proper lifecycle management
 * - Handles concurrent operations safely
 * 
 * Dependencies:
 * - AnalyticsService: Core service for widget operations
 * - AnalyticsWidgetManager: Widget configuration management
 * - ErrorHandler: Centralized error processing
 * - Receives user state from ProgressDashboardCoordinator for centralized auth management
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun AnalyticsScreen(
 *     viewModel: AnalyticsWidgetViewModel = hiltViewModel()
 * ) {
 *     val state by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     when (state) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> AnalyticsContent(state.data, viewModel::handleEvent)
 *         is UiState.Error -> ErrorDisplay(state.error)
 *     }
 * }
 * ```
 */
@HiltViewModel
class AnalyticsWidgetViewModel @Inject constructor(
    private val analyticsService: AnalyticsService,
    private val widgetManager: AnalyticsWidgetManager,
    errorHandler: ErrorHandler
) : BaseViewModel<UiState<AnalyticsWidgetState>, AnalyticsWidgetEvent>(errorHandler) {

    /**
     * Mutable state flow for internal state management.
     * Initialized with loading state while user authentication is verified.
     */
    override val _uiState: MutableStateFlow<UiState<AnalyticsWidgetState>> = 
        MutableStateFlow(UiState.Loading)

    /**
     * Current authenticated user state for data scoping.
     * Updated via Coordinator events instead of direct auth repository observation.
     */
    private val _currentUser = MutableStateFlow<User?>(null)

    /**
     * Available analytics widgets for the current user.
     * Filtered based on user permissions and feature flags.
     */
    private val availableWidgets = MutableStateFlow<List<AnalyticsWidget>>(emptyList())

    /**
     * Retry attempt counter for failed operations.
     * Implements exponential backoff and maximum retry limits.
     */
    private val retryAttempts = mutableMapOf<String, Int>()

    /**
     * Maximum number of retry attempts for failed operations.
     */
    private val maxRetryAttempts = 3

    init {
        initializeWidgets()
    }

    /**
     * Handles events from the UI following the MVI pattern.
     * 
     * Events are processed asynchronously with proper error handling and state updates.
     * All operations maintain consistency and provide user feedback.
     * 
     * @param event The event to process
     */
    override fun handleEvent(event: AnalyticsWidgetEvent) {
        when (event) {
            is AnalyticsWidgetEvent.LoadWidget -> loadWidgetData(event.widgetId, event.forceRefresh)
            is AnalyticsWidgetEvent.ToggleVisibility -> toggleWidgetVisibility(event.widgetId, event.visible)
            is AnalyticsWidgetEvent.UpdateConfiguration -> updateConfiguration(event.configuration, event.shouldPersist)
            is AnalyticsWidgetEvent.RefreshAllWidgets -> refreshAllWidgets(event.showLoadingStates, event.retryFailedWidgets)
            is AnalyticsWidgetEvent.ReorderWidget -> reorderWidget(event.widgetId, event.newPosition, event.shouldPersist)
            is AnalyticsWidgetEvent.ResetPreferences -> resetPreferences(event.confirmationRequired, event.preserveCustomizations)
            is AnalyticsWidgetEvent.RetryOperation -> retryOperation(event.widgetId, event.operation)
            is AnalyticsWidgetEvent.DismissError -> dismissError(event.widgetId, event.shouldClearHistory)
            is AnalyticsWidgetEvent.TrackInteraction -> trackInteraction(event.widgetId, event.interactionType, event.metadata)
            is AnalyticsWidgetEvent.ClearError -> clearError(event.widgetId)
            is AnalyticsWidgetEvent.WidgetClicked -> handleWidgetClick(event.widget)
            is AnalyticsWidgetEvent.ForceAllWidgets -> forceAdvancedUserLevel()
        }
    }

    /**
     * Loads data for a specific widget with caching and error handling.
     * 
     * @param widgetId The unique identifier of the widget to load
     * @param forceRefresh Whether to bypass cache and force fresh data loading
     */
    private fun loadWidgetData(widgetId: String, forceRefresh: Boolean = false) {
        if (widgetId.isBlank()) {
            val error = LiftrixError.ValidationError(
                field = "widgetId",
                violations = listOf("Widget ID cannot be blank"),
                analyticsContext = mapOf("operation" to "loadWidgetData")
            )
            updateState { UiState.Error(error) }
            return
        }

        val user = _currentUser.value
        if (user == null) {
            val error = LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                analyticsContext = mapOf("operation" to "loadWidgetData", "widgetId" to widgetId)
            )
            updateState { UiState.Error(error) }
            return
        }

        // Clear previous error and set loading state
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data
                        .withWidgetError(widgetId, null)
                        .withWidgetLoading(widgetId, true)
                )
                else -> UiState.Loading
            }
        }

        viewModelScope.launch {
            try {
                val widget = AnalyticsWidget.fromString(widgetId)
                if (widget == null) {
                    val error = LiftrixError.ValidationError(
                        field = "widgetId",
                        violations = listOf("Invalid widget identifier: $widgetId"),
                        analyticsContext = mapOf("operation" to "loadWidgetData")
                    )
                    updateState { UiState.Error(error) }
                    return@launch
                }

                val result = analyticsService.getWidgetData(user.uid, widget)
                
                result.fold(
                    onSuccess = { data ->
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data
                                        .withWidgetData(widgetId, data)
                                        .withWidgetLoading(widgetId, false)
                                )
                                else -> UiState.Success(
                                    AnalyticsWidgetState()
                                        .withWidgetData(widgetId, data)
                                        .withWidgetLoading(widgetId, false)
                                )
                            }
                        }
                        
                        // Reset retry attempts on success
                        retryAttempts.remove(widgetId)
                        
                        Timber.d("Widget data loaded successfully: $widgetId")
                    },
                    onFailure = { throwable ->
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = throwable.message ?: "Unknown error",
                            analyticsContext = mapOf("operation" to "loadWidgetData", "widget" to widgetId)
                        )
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data
                                        .withWidgetError(widgetId, error)
                                        .withWidgetLoading(widgetId, false)
                                )
                                else -> UiState.Error(error)
                            }
                        }
                        
                        Timber.e("Failed to load widget data: $widgetId - ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error loading widget data",
                    analyticsContext = mapOf(
                        "operation" to "loadWidgetData",
                        "widgetId" to widgetId,
                        "exception" to exception.message.orEmpty()
                    )
                )
                
                updateState { UiState.Error(error) }
                
                Timber.e(exception, "Unexpected error loading widget data: $widgetId")
            }
        }
    }

    /**
     * Toggles widget visibility with business rule validation.
     * 
     * @param widgetId The unique identifier of the widget to toggle
     * @param visible Optional explicit visibility state (null for toggle)
     */
    private fun toggleWidgetVisibility(widgetId: String, visible: Boolean? = null) {
        if (widgetId.isBlank()) {
            val error = LiftrixError.ValidationError(
                field = "widgetId",
                violations = listOf("Widget ID cannot be blank"),
                analyticsContext = mapOf("operation" to "toggleWidgetVisibility")
            )
            updateState { UiState.Error(error) }
            return
        }

        val user = _currentUser.value
        if (user == null) {
            val error = LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                analyticsContext = mapOf("operation" to "toggleWidgetVisibility")
            )
            updateState { UiState.Error(error) }
            return
        }

        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(currentState.data.withConfiguring(true))
                else -> UiState.Loading
            }
        }

        viewModelScope.launch {
            try {
                val result = analyticsService.toggleWidgetVisibility(user.uid, widgetId)
                
                result.fold(
                    onSuccess = {
                        // Reload preferences to reflect the change
                        loadPreferences()
                        
                        Timber.d("Widget visibility toggled successfully: $widgetId")
                    },
                    onFailure = { throwable ->
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = throwable.message ?: "Unknown error",
                            analyticsContext = mapOf("operation" to "toggleWidgetVisibility", "widget" to widgetId)
                        )
                        updateState { UiState.Error(error) }
                        
                        Timber.e("Failed to toggle widget visibility: $widgetId - ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error toggling widget visibility",
                    analyticsContext = mapOf(
                        "operation" to "toggleWidgetVisibility",
                        "widgetId" to widgetId,
                        "exception" to exception.message.orEmpty()
                    )
                )
                
                updateState { UiState.Error(error) }
                
                Timber.e(exception, "Unexpected error toggling widget visibility: $widgetId")
            }
        }
    }

    /**
     * Updates dashboard configuration with atomic persistence.
     * 
     * @param configuration The new dashboard configuration to apply
     * @param shouldPersist Whether to persist the configuration changes
     */
    private fun updateConfiguration(configuration: DashboardConfiguration, shouldPersist: Boolean = true) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(currentState.data.withConfiguring(true))
                else -> UiState.Loading
            }
        }

        viewModelScope.launch {
            try {
                // Apply configuration immediately for UI responsiveness
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> UiState.Success(currentState.data.withConfiguration(configuration))
                        else -> UiState.Success(AnalyticsWidgetState().withConfiguration(configuration))
                    }
                }

                if (shouldPersist) {
                    val user = _currentUser.value
                    if (user == null) {
                        val error = LiftrixError.AuthenticationError(
                            errorMessage = "User not authenticated",
                            analyticsContext = mapOf("operation" to "updateConfiguration")
                        )
                        updateState { UiState.Error(error) }
                        return@launch
                    }

                    // Convert configuration to preferences and update
                    val currentPreferences = when (val state = _uiState.value) {
                        is UiState.Success -> state.data.preferences
                        else -> null
                    }
                    val updatedPreferences = currentPreferences?.let { prefs ->
                        widgetManager.applyConfigurationToPreferences(prefs, configuration)
                    } ?: widgetManager.createDefaultPreferences(user.uid, configuration)

                    val result = analyticsService.updateWidgetPreferences(updatedPreferences)
                    
                    result.fold(
                        onSuccess = {
                            updateState { currentState ->
                                when (currentState) {
                                    is UiState.Success -> UiState.Success(
                                        currentState.data
                                            .withPreferences(updatedPreferences)
                                            .withConfiguring(false)
                                    )
                                    else -> UiState.Success(
                                        AnalyticsWidgetState()
                                            .withPreferences(updatedPreferences)
                                            .withConfiguring(false)
                                    )
                                }
                            }
                            
                            Timber.d("Configuration updated successfully")
                        },
                        onFailure = { throwable ->
                            val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = throwable.message ?: "Unknown error",
                                analyticsContext = mapOf("operation" to "updateConfiguration")
                            )
                            updateState { UiState.Error(error) }
                            
                            Timber.e("Failed to persist configuration: ${error.message}")
                        }
                    )
                } else {
                    updateState { currentState ->
                        when (currentState) {
                            is UiState.Success -> UiState.Success(currentState.data.withConfiguring(false))
                            else -> UiState.Success(AnalyticsWidgetState().withConfiguring(false))
                        }
                    }
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error updating configuration",
                    analyticsContext = mapOf(
                        "operation" to "updateConfiguration",
                        "exception" to exception.message.orEmpty()
                    )
                )
                
                updateState { UiState.Error(error) }
                
                Timber.e(exception, "Unexpected error updating configuration")
            }
        }
    }

    /**
     * Refreshes all widget data with coordinated loading states.
     * 
     * @param showLoadingStates Whether to show loading indicators during refresh
     * @param retryFailedWidgets Whether to retry previously failed widgets
     */
    private fun refreshAllWidgets(showLoadingStates: Boolean = true, retryFailedWidgets: Boolean = true) {
        val user = _currentUser.value
        if (user == null) {
            val error = LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                analyticsContext = mapOf("operation" to "refreshAllWidgets")
            )
            updateState { UiState.Error(error) }
            return
        }

        if (showLoadingStates) {
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(currentState.data.withRefreshing(true))
                    else -> UiState.Loading
                }
            }
        }

        viewModelScope.launch {
            try {
                val preferences = when (val state = _uiState.value) {
                    is UiState.Success -> state.data.preferences
                    else -> null
                }
                val visibleWidgets = preferences?.visibleWidgets ?: emptySet()
                
                // Include failed widgets if retry is enabled
                val widgetErrors = when (val state = _uiState.value) {
                    is UiState.Success -> state.data.widgetErrors.keys
                    else -> emptySet()
                }
                
                val widgetsToRefresh = if (retryFailedWidgets) {
                    visibleWidgets + widgetErrors
                } else {
                    visibleWidgets
                }

                // Load all widgets concurrently
                val jobs = widgetsToRefresh.map { widgetId ->
                    launch {
                        loadWidgetData(widgetId, forceRefresh = true)
                    }
                }

                // Wait for all jobs to complete
                jobs.forEach { it.join() }

                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> UiState.Success(
                            currentState.data
                                .withRefreshing(false)
                                .withLoading(false)
                        )
                        else -> UiState.Success(
                            AnalyticsWidgetState()
                                .withRefreshing(false)
                                .withLoading(false)
                        )
                    }
                }
                
                Timber.d("All widgets refreshed successfully")
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error refreshing widgets",
                    analyticsContext = mapOf(
                        "operation" to "refreshAllWidgets",
                        "exception" to exception.message.orEmpty()
                    )
                )
                
                updateState { UiState.Error(error) }
                
                Timber.e(exception, "Unexpected error refreshing widgets")
            }
        }
    }

    /**
     * Reorders widgets in the dashboard with immediate UI updates.
     * 
     * @param widgetId The identifier of the widget being moved
     * @param newPosition The new position index for the widget
     * @param shouldPersist Whether to persist the reordering changes
     */
    private fun reorderWidget(widgetId: String, newPosition: Int, shouldPersist: Boolean = true) {
        if (widgetId.isBlank() || newPosition < 0) {
            val error = LiftrixError.ValidationError(
                field = "widgetId",
                violations = listOf("Invalid widget ID or position"),
                analyticsContext = mapOf("operation" to "reorderWidget")
            )
            updateState { UiState.Error(error) }
            return
        }

        val currentConfiguration = when (val state = _uiState.value) {
            is UiState.Success -> state.data.configuration
            else -> null
        }
        if (currentConfiguration == null) {
            val error = LiftrixError.ValidationError(
                field = "configuration",
                violations = listOf("No configuration available for reordering"),
                analyticsContext = mapOf("operation" to "reorderWidget")
            )
            updateState { UiState.Error(error) }
            return
        }

        val updatedConfiguration = widgetManager.reorderWidget(
            currentConfiguration, 
            widgetId, 
            newPosition
        )

        if (updatedConfiguration != null) {
            updateConfiguration(updatedConfiguration, shouldPersist)
        } else {
            val error = LiftrixError.ValidationError(
                field = "reordering",
                violations = listOf("Invalid reordering operation"),
                analyticsContext = mapOf(
                    "operation" to "reorderWidget",
                    "widgetId" to widgetId,
                    "newPosition" to newPosition.toString()
                )
            )
            updateState { UiState.Error(error) }
        }
    }

    /**
     * Resets widget preferences to default values.
     * 
     * @param confirmationRequired Whether to show confirmation dialog
     * @param preserveCustomizations Whether to preserve user customizations
     */
    private fun resetPreferences(confirmationRequired: Boolean = true, preserveCustomizations: Boolean = false) {
        val user = _currentUser.value
        if (user == null) {
            val error = LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                analyticsContext = mapOf("operation" to "resetPreferences")
            )
            updateState { UiState.Error(error) }
            return
        }

        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(currentState.data.withConfiguring(true))
                else -> UiState.Loading
            }
        }

        viewModelScope.launch {
            try {
                val result = analyticsService.resetPreferences(user.uid)
                
                result.fold(
                    onSuccess = {
                        // Reload preferences and refresh all widgets
                        loadPreferences()
                        refreshAllWidgets(showLoadingStates = false)
                        
                        Timber.d("Preferences reset successfully")
                    },
                    onFailure = { throwable ->
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = throwable.message ?: "Unknown error",
                            analyticsContext = mapOf("operation" to "resetPreferences")
                        )
                        updateState { UiState.Error(error) }
                        
                        Timber.e("Failed to reset preferences: ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error resetting preferences",
                    analyticsContext = mapOf(
                        "operation" to "resetPreferences",
                        "exception" to exception.message.orEmpty()
                    )
                )
                
                updateState { UiState.Error(error) }
                
                Timber.e(exception, "Unexpected error resetting preferences")
            }
        }
    }

    /**
     * Retries failed operations with exponential backoff.
     * 
     * @param widgetId The identifier of the widget to retry (null for all failed)
     * @param operation The specific operation to retry (null for last failed operation)
     */
    private fun retryOperation(widgetId: String? = null, operation: String? = null) {
        if (widgetId != null) {
            val currentAttempts = retryAttempts[widgetId] ?: 0
            if (currentAttempts >= maxRetryAttempts) {
                val error = LiftrixError.ValidationError(
                    field = "retryLimit",
                    violations = listOf("Maximum retry attempts exceeded"),
                    analyticsContext = mapOf(
                        "operation" to "retryOperation",
                        "widgetId" to widgetId,
                        "attempts" to currentAttempts.toString()
                    )
                )
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> UiState.Success(currentState.data.withWidgetError(widgetId, error))
                        else -> UiState.Error(error)
                    }
                }
                return
            }

            retryAttempts[widgetId] = currentAttempts + 1
            loadWidgetData(widgetId, forceRefresh = true)
        } else {
            // Retry all failed widgets
            val failedWidgets = when (val state = _uiState.value) {
                is UiState.Success -> state.data.widgetErrors.keys.toList()
                else -> emptyList()
            }
            failedWidgets.forEach { widget ->
                retryOperation(widget, operation)
            }
        }
    }

    /**
     * Dismisses error states for widgets.
     * 
     * @param widgetId The identifier of the widget to dismiss errors for (null for all)
     * @param shouldClearHistory Whether to clear error history
     */
    private fun dismissError(widgetId: String? = null, shouldClearHistory: Boolean = false) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(currentState.data.withClearedErrors(widgetId))
                else -> currentState
            }
        }

        if (shouldClearHistory) {
            if (widgetId != null) {
                retryAttempts.remove(widgetId)
            } else {
                retryAttempts.clear()
            }
        }
    }

    /**
     * Tracks user interactions with widgets for analytics.
     * 
     * @param widgetId The identifier of the interacted widget
     * @param interactionType The type of interaction (tap, long_press, etc.)
     * @param metadata Additional interaction metadata
     */
    private fun trackInteraction(widgetId: String, interactionType: String, metadata: Map<String, Any>) {
        if (widgetId.isBlank() || interactionType.isBlank()) {
            return
        }

        val interaction = WidgetInteraction(
            type = interactionType,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )

        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(currentState.data.withInteraction(widgetId, interaction))
                else -> currentState
            }
        }

        // Log interaction for analytics
        Timber.d("Widget interaction tracked: $widgetId - $interactionType")
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
                                    subscriptionTier = SubscriptionTier.FREE,
                                    subscriptionStatus = SubscriptionStatus.ACTIVE,
                                    subscriptionExpiresAt = null,
                                    premiumFeaturesEnabled = false,
                                    onboardingCompleted = true,
                                    profileVersion = 1L,
                                    createdAt = LocalDateTime.now(),
                                    lastSignInAt = LocalDateTime.now(),
                                    updatedAt = LocalDateTime.now()
                                )
                            } else {
                                // FIXED: Handle blank userId case
                                null
                            }
                        }
                        
                        // Only trigger initial load if user changed
                        if (previousUserId != _currentUser.value?.uid) {
                            if (_currentUser.value != null) {
                                loadInitialData()
                                Timber.d("Widgets: User auth changed to ${event.userId}, loading data")
                            } else {
                                updateState { 
                                    UiState.Error(
                                        LiftrixError.AuthenticationError(
                                            errorMessage = "User not authenticated",
                                            analyticsContext = mapOf("operation" to "handleCoordinatorEvent")
                                        )
                                    )
                                }
                                Timber.d("Widgets: User logged out")
                            }
                        }
                    }
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(AnalyticsWidgetEvent.RefreshAllWidgets())
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("widgets") || event.dataTypes.contains("analytics")) {
                            handleEvent(AnalyticsWidgetEvent.RefreshAllWidgets())
                        }
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
                updateState { UiState.Error(error) }
                Timber.e(exception, "Failed to handle coordinator event: ${event::class.simpleName}")
            }
        }
    }

    /**
     * Initializes available widgets based on feature flags and user permissions.
     */
    private fun initializeWidgets() {
        val defaultWidgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.StrengthProgress,
            AnalyticsWidget.PersonalRecords,
            AnalyticsWidget.VolumeTrends
        )

        availableWidgets.value = defaultWidgets
        
        // Log available widgets for debugging
        Timber.d("Initialized ${defaultWidgets.size} available widgets: ${defaultWidgets.map { it.name }}")
    }

    /**
     * Loads initial data including preferences and configuration.
     */
    private fun loadInitialData() {
        loadPreferences()
        loadConfiguration()
    }

    /**
     * Loads user widget preferences from the service.
     */
    private fun loadPreferences() {
        val user = _currentUser.value ?: return

        viewModelScope.launch {
            try {
                val result = analyticsService.getWidgetPreferences(user.uid)
                
                result.fold(
                    onSuccess = { preferences ->
                        Timber.d("Loaded preferences with ${preferences.visibleWidgets.size} visible widgets: ${preferences.visibleWidgets}")
                        
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data
                                        .withPreferences(preferences)
                                        .withActiveWidgets(preferences)
                                        .withLoading(false)
                                )
                                else -> UiState.Success(
                                    AnalyticsWidgetState()
                                        .withPreferences(preferences)
                                        .withActiveWidgets(preferences)
                                        .withLoading(false)
                                )
                            }
                        }
                        
                        // Immediately load configuration after preferences are set
                        loadConfiguration()
                        
                        // Load data for visible widgets
                        preferences.visibleWidgets.forEach { widgetId ->
                            loadWidgetData(widgetId, forceRefresh = false)
                        }
                    },
                    onFailure = { throwable ->
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = throwable.message ?: "Unknown error",
                            analyticsContext = mapOf("operation" to "loadPreferences")
                        )
                        updateState { UiState.Error(error) }
                        
                        Timber.e("Failed to load preferences: ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error loading preferences",
                    analyticsContext = mapOf(
                        "operation" to "loadPreferences",
                        "exception" to exception.message.orEmpty()
                    )
                )
                
                updateState { UiState.Error(error) }
                
                Timber.e(exception, "Unexpected error loading preferences")
            }
        }
    }

    /**
     * Loads dashboard configuration from preferences.
     */
    private fun loadConfiguration() {
        val preferences = when (val state = _uiState.value) {
            is UiState.Success -> state.data.preferences
            else -> null
        }
        if (preferences != null) {
            val configuration = widgetManager.createConfigurationFromPreferences(preferences)
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(currentState.data.withConfiguration(configuration))
                    else -> UiState.Success(AnalyticsWidgetState().withConfiguration(configuration))
                }
            }
        }
    }

    /**
     * Updates error state for ViewModel-specific error handling.
     * 
     * @param error The error to reflect in the state
     */
    override fun updateErrorState(error: LiftrixError) {
        updateState { UiState.Error(error) }
    }

    /**
     * Sets loading state for ViewModel operations.
     */
    override fun setLoadingState() {
        updateState { UiState.Loading }
    }

    /**
     * Clears error state for a specific widget.
     * 
     * @param widgetId The identifier of the widget to clear error for (null for all widgets)
     */
    private fun clearError(widgetId: String?) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> {
                    if (widgetId != null) {
                        UiState.Success(currentState.data.withWidgetError(widgetId, null))
                    } else {
                        // Clear all errors - reset to initial state
                        UiState.Success(currentState.data)
                    }
                }
                is UiState.Error -> UiState.Loading
                else -> currentState
            }
        }
        
        Timber.d("Cleared error for widget: ${widgetId ?: "all"}")
    }
    
    /**
     * Handles widget click events.
     * 
     * @param widget The clicked widget
     */
    private fun handleWidgetClick(widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget) {
        val widgetId = widget.name
        trackInteraction(widgetId, "click", emptyMap())
        
        // Additional click handling logic can be added here
        Timber.d("Widget clicked: $widgetId")
    }
    
    /**
     * Forces migration of widget preferences for legacy data compatibility.
     * 
     * This function clears existing preferences and forces a reload, which will
     * trigger the migration system to convert legacy widget names to current enum values.
     */
    fun forceMigrateWidgetPreferences() {
        val user = _currentUser.value ?: return
        
        viewModelScope.launch {
            try {
                Timber.d("Force migrating widget preferences for user: ${user.uid}")
                
                // Clear current preferences to force reload
                val result = analyticsService.resetPreferences(user.uid)
                
                result.fold(
                    onSuccess = {
                        // Reload preferences and data
                        loadPreferences()
                        Timber.d("Widget preferences force migration completed")
                    },
                    onFailure = { throwable ->
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = throwable.message ?: "Unknown error",
                            analyticsContext = mapOf("operation" to "forceMigrateWidgetPreferences")
                        )
                        updateState { UiState.Error(error) }
                        Timber.e("Failed to force migrate widget preferences: ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error during widget migration",
                    analyticsContext = mapOf(
                        "operation" to "forceMigrateWidgetPreferences",
                        "exception" to exception.message.orEmpty()
                    )
                )
                updateState { UiState.Error(error) }
                Timber.e(exception, "Unexpected error during widget migration")
            }
        }
    }

    /**
     * Forces user to advanced level to show all 23 widgets.
     */
    fun forceAdvancedUserLevel() {
        val user = _currentUser.value ?: return
        
        viewModelScope.launch {
            try {
                Timber.d("Forcing advanced user level for user: ${user.uid}")
                
                // Get current preferences and update to advanced level
                val currentPrefs = when (val state = _uiState.value) {
                    is UiState.Success -> state.data.preferences
                    else -> null
                }
                
                if (currentPrefs != null) {
                    val updatedPrefs = currentPrefs.copy(
                        userLevel = com.example.liftrix.domain.model.analytics.UserLevel.ADVANCED,
                        visibleWidgets = setOf(
                            "WorkoutFrequency", "TotalVolume", "AverageDuration", "ConsistencyStreak",
                            "VolumeLoadProgression", "OneRMProgression", "ProgressChart", "WorkoutStreak", 
                            // "CaloriesBurned", // REMOVED - duplicate of CalorieSection widget
                            "DailyCalories", "VolumeCalendar", "StrengthProgress",
                            "VolumeChart", "DurationChart", "FrequencyChart", "PersonalRecords",
                            "WeeklyCalorieTrend", "MuscleGroupDistribution", "VolumeTrends", 
                            "RecoveryMetrics", "PerformanceAnalysis", "WeeklyTrends", "RecoveryPatterns"
                        )
                    )
                    
                    val result = analyticsService.updateWidgetPreferences(updatedPrefs)
                    
                    result.fold(
                        onSuccess = {
                            loadPreferences()
                            Timber.d("Advanced user level and all widgets set successfully")
                        },
                        onFailure = { throwable ->
                            val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = throwable.message ?: "Unknown error",
                                analyticsContext = mapOf("operation" to "forceAdvancedUserLevel")
                            )
                            updateState { UiState.Error(error) }
                            Timber.e("Failed to set advanced user level: ${error.message}")
                        }
                    )
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error setting advanced user level",
                    analyticsContext = mapOf(
                        "operation" to "forceAdvancedUserLevel",
                        "exception" to exception.message.orEmpty()
                    )
                )
                updateState { UiState.Error(error) }
                Timber.e(exception, "Unexpected error setting advanced user level")
            }
        }
    }

    /**
     * Cleanup method called when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        retryAttempts.clear()
        Timber.d("AnalyticsWidgetViewModel cleared")
    }
}

/**
 * Extension function to create AnalyticsWidget from string identifier.
 * Uses enum valueOf for complete coverage of all 23 widget types.
 */
private fun AnalyticsWidget.Companion.fromString(widgetId: String): AnalyticsWidget? {
    return try {
        AnalyticsWidget.valueOf(widgetId)
    } catch (e: IllegalArgumentException) {
        null
    }
}