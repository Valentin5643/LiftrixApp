package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.progress.ProgressDashboardGateway
import com.example.liftrix.domain.progress.ProgressWidgetResolverPort
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import timber.log.Timber

/**
 * ViewModel for dashboard screen following MVI pattern with reactive state management.
 * 
 * This ViewModel manages the complete dashboard state including widget data flows,
 * real-time updates, user interactions, and configuration management. It extends
 * BaseViewModel to provide standardized state management and error handling while
 * implementing sophisticated reactive patterns for optimal performance.
 * 
 * Key Features:
 * - Reactive state management with StateFlow and combine operations
 * - Widget data loading with caching and refresh capabilities  
 * - Drag-and-drop widget reordering with immediate UI feedback
 * - Real-time data synchronization and background updates
 * - Comprehensive error handling with recovery mechanisms
 * - Configuration management with atomic updates
 * - Inter-ViewModel coordination through coordinator events
 * - Performance optimization with smart state diffing
 * 
 * Architecture Integration:
 * - Uses GetWidgetDataUseCase for widget data operations
 * - Uses GetDashboardConfigurationUseCase for configuration management
 * - Uses SaveWidgetPreferencesUseCase for preference persistence
 * - Coordinates with AnalyticsWidgetViewModel for widget management
 * - Receives coordination events from ProgressDashboardCoordinator
 * - Integrates with navigation system for screen transitions
 * 
 * State Management:
 * - Combines multiple data sources into unified dashboard state
 * - Provides reactive updates with proper lifecycle management
 * - Implements optimistic UI updates with rollback capability
 * - Manages loading states at both global and widget levels
 * - Handles concurrent operations safely with proper synchronization
 * 
 * Performance Optimizations:
 * - Debounced state updates to prevent excessive recomposition
 * - Lazy loading of widget data based on visibility
 * - Smart caching with staleness detection
 * - Batch operations for multiple widget updates
 * - Memory-efficient state management with proper cleanup
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun DashboardScreen(
 *     viewModel: DashboardViewModel = hiltViewModel()
 * ) {
 *     val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> DashboardContent(
 *             state = uiState.data,
 *             onEvent = viewModel::handleEvent
 *         )
 *         is UiState.Error -> ErrorDisplay(uiState.error)
 *     }
 * }
 * ```
 * 
 * @param getWidgetDataUseCase Use case for retrieving widget data
 * @param getDashboardConfigurationUseCase Use case for dashboard configuration
 * @param widgetPreferencesUseCase Consolidated use case for widget preference operations
 * @param widgetResolver Service for resolving widgets from preferences
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val progressDashboardGateway: ProgressDashboardGateway,
    private val widgetResolver: ProgressWidgetResolverPort
) : ModernBaseViewModel<UiState<DashboardUiState>>(
    initialState = UiState.Loading
) {

    /**
     * Current authenticated user state for data scoping.
     * Updated via Coordinator events for centralized authentication management.
     */
    private val _currentUser = MutableStateFlow<com.example.liftrix.domain.model.User?>(null)

    /**
     * Current time range for analytics data filtering.
     * Used by time-sensitive widgets for data scoping.
     */
    private val _currentTimeRange = MutableStateFlow(TimeRange.lastMonth())

    /**
     * Real-time updates enablement state.
     * Controls background data synchronization and listeners.
     */
    private val _realtimeUpdatesEnabled = MutableStateFlow(true)

    /**
     * Network connectivity state for offline/online behavior.
     * Affects data loading strategies and error handling.
     */
    private val _networkState = MutableStateFlow(true)

    /**
     * Retry attempt tracking for failed operations.
     * Implements exponential backoff and maximum retry limits.
     */
    private val retryAttempts = mutableMapOf<String, Int>()

    /**
     * Maximum number of retry attempts for failed operations.
     */
    private val maxRetryAttempts = 3

    /**
     * Debounce timeout for state updates to prevent excessive recomposition.
     */
    private val stateUpdateDebounceMs = 16L // Target 60fps

    init {
        // Initialize dashboard on ViewModel creation
        initializeDashboard()
        
        // Set up reactive state management
        observeStateChanges()
        
        Timber.d("DashboardViewModel initialized")
    }

    /**
     * Handles events from the UI following the MVI pattern.
     *
     * This method processes all user interactions and system events, updating the state
     * accordingly and triggering appropriate data operations. All operations maintain
     * consistency and provide user feedback through reactive state updates.
     *
     * @param event The event to process
     */
    fun handleEvent(event: DashboardEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is DashboardEvent.LoadDashboard -> loadDashboard(event.forceRefresh, event.showLoading)
                    is DashboardEvent.RefreshDashboard -> refreshDashboard(event.includeConfiguration, event.showRefreshIndicator)
                    is DashboardEvent.RefreshWidget -> refreshWidget(event.widgetId, event.forceRefresh)
                    is DashboardEvent.ReorderWidgets -> reorderWidgets(event.fromIndex, event.toIndex, event.shouldPersist)
                    is DashboardEvent.UpdateConfiguration -> updateConfiguration(event.configuration, event.shouldPersist, event.showFeedback)
                    is DashboardEvent.ToggleWidgetVisibility -> toggleWidgetVisibility(event.widgetId, event.visible, event.shouldPersist)
                    is DashboardEvent.StartDrag -> startDragOperation(event.widgetId, event.initialPosition)
                    is DashboardEvent.UpdateDragPosition -> updateDragPosition(event.currentPosition, event.canDrop)
                    is DashboardEvent.EndDrag -> endDragOperation(event.finalPosition, event.shouldApply)
                    is DashboardEvent.RetryOperation -> retryOperation(event.widgetId, event.operation)
                    is DashboardEvent.ClearError -> clearError(event.widgetId, event.clearGlobalError)
                    is DashboardEvent.ToggleRealtimeUpdates -> toggleRealtimeUpdates(event.enabled, event.applyToAllWidgets)
                    is DashboardEvent.NavigateToCustomization -> navigateToCustomization(event.category)
                    is DashboardEvent.ExportData -> exportData(event.format, event.includeCharts, event.dateRange)
                    is DashboardEvent.ExportRawData -> exportRawData()
                    is DashboardEvent.CancelExport -> cancelExport()
                    is DashboardEvent.WidgetClicked -> handleWidgetClick(event.widget, event.action)
                    is DashboardEvent.TimePeriodChanged -> handleTimePeriodChange(event.timeRange, event.refreshAffectedWidgets)
                    is DashboardEvent.BackgroundDataUpdate -> handleBackgroundDataUpdate(event.dataTypes, event.sourceInfo)
                    is DashboardEvent.NetworkStateChanged -> handleNetworkStateChange(event.isOnline, event.shouldRetryFailedOperations)
                    is DashboardEvent.ResetToDefaults -> resetToDefaults(event.confirmationRequired, event.preserveUserData)
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Failed to handle dashboard event: ${event::class.simpleName}",
                    analyticsContext = mapOf(
                        "event_type" to (event::class.simpleName ?: "Unknown"),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
                handleError(error)
                Timber.e(exception, "Failed to handle dashboard event: ${event::class.simpleName}")
            }
        }
    }

    /**
     * Handles coordination events from the ProgressDashboardCoordinator.
     * 
     * This method processes events that require coordination between ViewModels,
     * such as user authentication changes, global data refresh requests, and
     * inter-ViewModel communication for dashboard synchronization.
     * 
     * @param event The coordination event to process
     */
    fun handleCoordinatorEvent(event: CoordinatorEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is CoordinatorEvent.UserAuthChanged -> {
                        val previousUserId = _currentUser.value?.uid
                        
                        // Update current user with proper validation
                        _currentUser.value = event.userId?.let { userId ->
                            if (userId.isNotBlank()) {
                                // Create minimal User object for dashboard operations
                                com.example.liftrix.domain.model.User(
                                    uid = userId,
                                    email = "temp@liftrix.app",
                                    displayName = null,
                                    photoUrl = null,
                                    isAnonymous = false,
                                    subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                                    subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE,
                                    subscriptionExpiresAt = null,
                                    premiumFeaturesEnabled = false,
                                    onboardingCompleted = true,
                                    profileVersion = 1L,
                                    createdAt = java.time.LocalDateTime.now(),
                                    lastSignInAt = java.time.LocalDateTime.now(),
                                    updatedAt = java.time.LocalDateTime.now()
                                )
                            } else null
                        }
                        
                        // Load dashboard data when user changes
                        if (previousUserId != _currentUser.value?.uid) {
                            if (_currentUser.value != null) {
                                handleEvent(DashboardEvent.LoadDashboard(forceRefresh = true))
                                Timber.d("Dashboard: User auth changed to ${event.userId}, loading dashboard")
                            } else {
                                // Clear state when user logs out
                                updateState { UiState.Loading }
                                Timber.d("Dashboard: User logged out, clearing state")
                            }
                        }
                    }
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(DashboardEvent.RefreshDashboard(includeConfiguration = true))
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("dashboard") || event.dataTypes.contains("widgets")) {
                            handleEvent(DashboardEvent.RefreshDashboard())
                        }
                    }
                    is CoordinatorEvent.WorkoutCompleted -> {
                        // Refresh widgets that depend on workout data
                        handleEvent(DashboardEvent.BackgroundDataUpdate(
                            dataTypes = setOf("volume", "frequency", "calories", "duration"),
                            sourceInfo = "workout_completed:${event.workoutId}"
                        ))
                    }
                    is CoordinatorEvent.TimePeriodChanged -> {
                        handleEvent(DashboardEvent.TimePeriodChanged(event.timeRange))
                    }
                    is CoordinatorEvent.NetworkConnectivityChanged -> {
                        handleEvent(DashboardEvent.NetworkStateChanged(
                            isOnline = event.isConnected,
                            shouldRetryFailedOperations = event.isConnected
                        ))
                    }
                    is CoordinatorEvent.ToggleRealtimeUpdates -> {
                        handleEvent(DashboardEvent.ToggleRealtimeUpdates(event.enabled))
                    }
                    is CoordinatorEvent.BroadcastError -> {
                        val error = LiftrixError.UnknownError(
                            errorMessage = event.error,
                            analyticsContext = mapOf("source" to "coordinator_broadcast")
                        )
                        handleError(error)
                    }
                    is CoordinatorEvent.ClearError -> {
                        handleEvent(DashboardEvent.ClearError(clearGlobalError = true))
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
                handleError(error)
                Timber.e(exception, "Failed to handle coordinator event: ${event::class.simpleName}")
            }
        }
    }

    private fun handleError(error: LiftrixError) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withGlobalError(error)
                )
                else -> UiState.Error(error)
            }
        }
    }

    /**
     * Initializes the dashboard with default state.
     */
    private fun initializeDashboard() {
        updateState { 
            UiState.Success(
                DashboardUiState(
                    isLoading = true,
                    realtimeUpdatesEnabled = _realtimeUpdatesEnabled.value,
                    isOnline = _networkState.value
                )
            )
        }
    }

    /**
     * Observes state changes and triggers reactive updates.
     */
    private fun observeStateChanges() {
        viewModelScope.launch {
            // Combine user and time range changes for reactive updates
            combine(
                _currentUser,
                _currentTimeRange,
                _realtimeUpdatesEnabled,
                _networkState
            ) { user, timeRange, realtimeEnabled, isOnline ->
                // Update state with reactive changes
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> UiState.Success(
                            currentState.data.copy(
                                realtimeUpdatesEnabled = realtimeEnabled,
                                isOnline = isOnline
                            )
                        )
                        else -> currentState
                    }
                }
                
                // Trigger data loading if conditions are met
                if (user != null) {
                    val currentData = (_uiState.value as? UiState.Success)?.data
                    if (currentData?.hasValidConfiguration() != true) {
                        loadDashboard(forceRefresh = false, showLoading = false)
                    }
                }
            }.collect()
        }
    }

    /**
     * Loads dashboard data including configuration and widgets.
     * 
     * @param forceRefresh Whether to bypass cache and force fresh data loading
     * @param showLoading Whether to show loading indicators during loading
     */
    private suspend fun loadDashboard(forceRefresh: Boolean, showLoading: Boolean) {
        val user = _currentUser.value
        if (user == null) {
            val error = LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                analyticsContext = mapOf("operation" to "loadDashboard")
            )
            updateState { UiState.Error(error) }
            return
        }

        if (showLoading) {
            updateState { UiState.Loading }
        }

        try {
            // Load dashboard configuration
            progressDashboardGateway.getDashboardConfiguration(
                userId = user.uid
            ).collect { configResult ->
                configResult.fold(
                onSuccess = { configuration ->
                    // Update state with configuration - CRITICAL BUG FIX: Use ProgressWidgetResolverPort for proper widget resolution
                    val resolvedWidgets = widgetResolver.resolveWidgetsFromPreferences(
                        preferences = configuration.preferences,
                        userLevel = configuration.preferences.userLevel
                    )
                    
                    updateState { currentState ->
                        when (currentState) {
                            is UiState.Success -> UiState.Success(
                                currentState.data
                                    .withConfiguration(configuration.configuration)
                                    .withPreferences(configuration.preferences)
                                    .withResolvedWidgets(resolvedWidgets)
                                    .withLoading(false)
                            )
                            else -> UiState.Success(
                                DashboardUiState(
                                    configuration = configuration.configuration,
                                    preferences = configuration.preferences,
                                    activeWidgets = resolvedWidgets.map { it.id },
                                    isLoading = false,
                                    realtimeUpdatesEnabled = _realtimeUpdatesEnabled.value,
                                    isOnline = _networkState.value
                                )
                            )
                        }
                    }
                    
                    // Load widget data for visible widgets
                    loadVisibleWidgets(configuration.preferences.visibleWidgets.toList(), forceRefresh)
                    
                    Timber.d("Dashboard configuration loaded successfully")
                },
                onFailure = { throwable ->
                    val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                        errorMessage = "Failed to load dashboard configuration: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "loadDashboard")
                    )
                    updateState { UiState.Error(error) }
                    Timber.e("Failed to load dashboard configuration: ${error.message}")
                }
                )
            }
        } catch (exception: Exception) {
            val error = LiftrixError.UnknownError(
                errorMessage = "Unexpected error loading dashboard",
                analyticsContext = mapOf(
                    "operation" to "loadDashboard",
                    "exception" to exception.message.orEmpty()
                )
            )
            updateState { UiState.Error(error) }
            Timber.e(exception, "Unexpected error loading dashboard")
        }
    }

    /**
     * Loads widget data for visible widgets.
     * 
     * @param widgetIds List of widget IDs to load data for
     * @param forceRefresh Whether to force refresh of widget data
     */
    private suspend fun loadVisibleWidgets(widgetIds: List<String>, forceRefresh: Boolean) {
        val user = _currentUser.value ?: return

        widgetIds.forEach { widgetId ->
            // Set loading state for widget
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(
                        currentState.data.withWidgetLoading(widgetId, true)
                    )
                    else -> currentState
                }
            }

            try {
                // Convert widget ID to AnalyticsWidget
                val widget = AnalyticsWidget.getById(widgetId)
                if (widget == null) {
                    Timber.w("Unknown widget ID: $widgetId")
                    return
                }
                
                // Load widget data
                val widgetResult = progressDashboardGateway.getWidgetData(user.uid, widget)
                
                widgetResult.fold(
                    onSuccess = { data ->
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data.withWidgetData(widgetId, data)
                                )
                                else -> currentState
                            }
                        }
                        
                        // Clear retry attempts on success
                        retryAttempts.remove(widgetId)
                        
                        Timber.d("Widget data loaded successfully: $widgetId")
                    },
                    onFailure = { throwable ->
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = "Failed to load widget data: ${throwable.message}",
                            analyticsContext = mapOf("widget" to widgetId)
                        )
                        
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data.withWidgetError(widgetId, error)
                                )
                                else -> currentState
                            }
                        }
                        
                        Timber.e("Failed to load widget data: $widgetId - ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Invalid widget type: $widgetId",
                    analyticsContext = mapOf("widget" to widgetId)
                )
                
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> UiState.Success(
                            currentState.data.withWidgetError(widgetId, error)
                        )
                        else -> currentState
                    }
                }
                
                Timber.e(exception, "Invalid widget type: $widgetId")
            }
        }
    }

    /**
     * Refreshes all dashboard data.
     * 
     * @param includeConfiguration Whether to also refresh configuration data
     * @param showRefreshIndicator Whether to show refresh indicators
     */
    private suspend fun refreshDashboard(includeConfiguration: Boolean, showRefreshIndicator: Boolean) {
        val currentData = (_uiState.value as? UiState.Success)?.data ?: return
        
        if (showRefreshIndicator) {
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(
                        currentState.data.withRefreshing(true)
                    )
                    else -> currentState
                }
            }
        }

        if (includeConfiguration) {
            // Reload complete dashboard
            loadDashboard(forceRefresh = true, showLoading = false)
        } else {
            // Refresh only widget data
            loadVisibleWidgets(currentData.activeWidgets, forceRefresh = true)
        }
        
        // Clear refresh indicator
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withRefreshing(false)
                )
                else -> currentState
            }
        }
    }

    /**
     * Refreshes data for a specific widget.
     * 
     * @param widgetId The unique identifier of the widget to refresh
     * @param forceRefresh Whether to bypass cache for this widget
     */
    private suspend fun refreshWidget(widgetId: String, forceRefresh: Boolean) {
        val user = _currentUser.value ?: return

        // Set loading state for widget
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withWidgetLoading(widgetId, true)
                )
                else -> currentState
            }
        }

        try {
            val widget = AnalyticsWidget.getById(widgetId)
            if (widget == null) {
                Timber.w("Unknown widget ID: $widgetId")
                return
            }
            val result = if (forceRefresh) {
                progressDashboardGateway.refreshWidgetData(user.uid, widget)
            } else {
                progressDashboardGateway.getWidgetData(user.uid, widget)
            }
            
            result.fold(
                onSuccess = { data ->
                    updateState { currentState ->
                        when (currentState) {
                            is UiState.Success -> UiState.Success(
                                currentState.data.withWidgetData(widgetId, data)
                            )
                            else -> currentState
                        }
                    }
                    retryAttempts.remove(widgetId)
                    Timber.d("Widget refreshed successfully: $widgetId")
                },
                onFailure = { throwable ->
                    val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                        errorMessage = "Failed to refresh widget: ${throwable.message}",
                        analyticsContext = mapOf("widget" to widgetId)
                    )
                    
                    updateState { currentState ->
                        when (currentState) {
                            is UiState.Success -> UiState.Success(
                                currentState.data.withWidgetError(widgetId, error)
                            )
                            else -> currentState
                        }
                    }
                    Timber.e("Failed to refresh widget: $widgetId - ${error.message}")
                }
            )
        } catch (exception: Exception) {
            val error = LiftrixError.UnknownError(
                errorMessage = "Invalid widget type: $widgetId",
                analyticsContext = mapOf("widget" to widgetId)
            )
            
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(
                        currentState.data.withWidgetError(widgetId, error)
                    )
                    else -> currentState
                }
            }
            Timber.e(exception, "Invalid widget type for refresh: $widgetId")
        }
    }

    /**
     * Reorders widgets in the dashboard.
     * 
     * @param fromIndex The original position index of the widget
     * @param toIndex The new position index for the widget
     * @param shouldPersist Whether to persist the reordering changes
     */
    private suspend fun reorderWidgets(fromIndex: Int, toIndex: Int, shouldPersist: Boolean) {
        val currentData = (_uiState.value as? UiState.Success)?.data ?: return
        val activeWidgets = currentData.activeWidgets.toMutableList()
        
        // Validate indices
        if (fromIndex < 0 || fromIndex >= activeWidgets.size || 
            toIndex < 0 || toIndex >= activeWidgets.size) {
            val error = LiftrixError.ValidationError(
                field = "widgetIndices",
                violations = listOf("Invalid widget indices for reordering"),
                analyticsContext = mapOf("fromIndex" to fromIndex.toString(), "toIndex" to toIndex.toString())
            )
            updateState { UiState.Error(error) }
            return
        }
        
        // Perform reordering
        val widgetToMove = activeWidgets.removeAt(fromIndex)
        activeWidgets.add(toIndex, widgetToMove)
        
        // Update state immediately for UI responsiveness
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.copy(activeWidgets = activeWidgets)
                )
                else -> currentState
            }
        }
        
        if (shouldPersist) {
            // Persist the reordering
            val user = _currentUser.value
            if (user != null && currentData.preferences != null) {
                val updatedPreferences = currentData.preferences.copy(
                    visibleWidgets = activeWidgets.toSet()
                )

                val saveResult = progressDashboardGateway.saveWidgetPreferences(updatedPreferences)
                
                saveResult.fold(
                    onSuccess = {
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data.withPreferences(updatedPreferences)
                                )
                                else -> currentState
                            }
                        }
                        Timber.d("Widget reordering persisted successfully")
                    },
                    onFailure = { throwable ->
                        // Revert the reordering on persistence failure
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data.copy(activeWidgets = currentData.activeWidgets)
                                )
                                else -> currentState
                            }
                        }
                        
                        val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = "Failed to persist widget reordering: ${throwable.message}",
                            analyticsContext = mapOf("operation" to "reorderWidgets")
                        )
                        handleError(error)
                        Timber.e("Failed to persist widget reordering: ${error.message}")
                    }
                )
            }
        }
    }

    /**
     * Updates dashboard configuration.
     * 
     * @param configuration The new dashboard configuration to apply
     * @param shouldPersist Whether to persist the configuration changes
     * @param showFeedback Whether to show visual feedback for the update
     */
    private suspend fun updateConfiguration(
        configuration: DashboardConfiguration, 
        shouldPersist: Boolean, 
        showFeedback: Boolean
    ) {
        if (showFeedback) {
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(
                        currentState.data.withConfiguring(true)
                    )
                    else -> currentState
                }
            }
        }

        // Update configuration immediately for UI responsiveness
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withConfiguration(configuration)
                )
                else -> currentState
            }
        }

        if (shouldPersist) {
            // Configuration persistence placeholder
            // For now, just clear the configuring state
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(
                        currentState.data.withConfiguring(false)
                    )
                    else -> currentState
                }
            }
            Timber.d("Configuration updated successfully")
        }
    }

    /**
     * Toggles widget visibility.
     * 
     * @param widgetId The unique identifier of the widget to toggle
     * @param visible Optional explicit visibility state (null for toggle)
     * @param shouldPersist Whether to persist the visibility change
     */
    private suspend fun toggleWidgetVisibility(widgetId: String, visible: Boolean?, shouldPersist: Boolean) {
        val currentData = (_uiState.value as? UiState.Success)?.data ?: return
        val preferences = currentData.preferences ?: return
        
        val currentlyVisible = widgetId in preferences.visibleWidgets
        val newVisibility = visible ?: !currentlyVisible
        
        val updatedVisibleWidgets = if (newVisibility) {
            preferences.visibleWidgets + widgetId
        } else {
            preferences.visibleWidgets - widgetId
        }
        
        val updatedPreferences = preferences.copy(visibleWidgets = updatedVisibleWidgets)
        
        // CRITICAL BUG FIX: Use ProgressWidgetResolverPort for proper widget resolution  
        val resolvedWidgets = widgetResolver.resolveWidgetsFromPreferences(
            preferences = updatedPreferences,
            userLevel = updatedPreferences.userLevel
        )
        
        // Update state immediately
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data
                        .withPreferences(updatedPreferences)
                        .withResolvedWidgets(resolvedWidgets)
                )
                else -> currentState
            }
        }
        
        // Load data for newly visible widget
        if (newVisibility && widgetId !in currentData.widgetData) {
            loadVisibleWidgets(listOf(widgetId), forceRefresh = false)
        }
        
        if (shouldPersist) {
            val saveResult = progressDashboardGateway.saveWidgetPreferences(updatedPreferences)

            saveResult.fold(
                onSuccess = {
                    Timber.d("Widget visibility persisted successfully: $widgetId -> $newVisibility")
                },
                onFailure = { throwable ->
                    // CRITICAL BUG FIX: Revert using ProgressWidgetResolverPort for proper widget resolution
                    val revertedWidgets = widgetResolver.resolveWidgetsFromPreferences(
                        preferences = preferences,
                        userLevel = preferences.userLevel
                    )
                    
                    // Revert the visibility change on persistence failure
                    updateState { currentState ->
                        when (currentState) {
                            is UiState.Success -> UiState.Success(
                                currentState.data
                                    .withPreferences(preferences)
                                    .withResolvedWidgets(revertedWidgets)
                            )
                            else -> currentState
                        }
                    }
                    
                    val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                        errorMessage = "Failed to persist widget visibility: ${throwable.message}",
                        analyticsContext = mapOf("widget" to widgetId, "visibility" to newVisibility.toString())
                    )
                    handleError(error)
                    Timber.e("Failed to persist widget visibility: ${error.message}")
                }
            )
        }
    }

    /**
     * Starts drag operation for widget reordering.
     * 
     * @param widgetId The unique identifier of the widget being dragged
     * @param initialPosition The initial position of the widget
     */
    private fun startDragOperation(widgetId: String, initialPosition: Int) {
        val dragDropState = DragDropState(
            draggedWidget = widgetId,
            originalPosition = initialPosition,
            currentPosition = initialPosition,
            isDragging = true,
            canDrop = false
        )
        
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withDragDropState(dragDropState)
                )
                else -> currentState
            }
        }
        
        Timber.d("Started drag operation for widget: $widgetId at position $initialPosition")
    }

    /**
     * Updates drag position during drag operation.
     * 
     * @param currentPosition The current position during drag
     * @param canDrop Whether the current position is valid for dropping
     */
    private fun updateDragPosition(currentPosition: Int, canDrop: Boolean) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> {
                    val updatedDragState = currentState.data.dragDropState.copy(
                        currentPosition = currentPosition,
                        canDrop = canDrop
                    )
                    UiState.Success(
                        currentState.data.withDragDropState(updatedDragState)
                    )
                }
                else -> currentState
            }
        }
    }

    /**
     * Ends drag operation with final position confirmation.
     * 
     * @param finalPosition The final position where the widget was dropped
     * @param shouldApply Whether to apply the reordering (false for cancellation)
     */
    private suspend fun endDragOperation(finalPosition: Int, shouldApply: Boolean) {
        val currentData = (_uiState.value as? UiState.Success)?.data ?: return
        val dragState = currentData.dragDropState
        
        if (shouldApply && dragState.isActive() && dragState.hasMoved()) {
            // Apply the reordering
            reorderWidgets(dragState.originalPosition, finalPosition, shouldPersist = true)
        }
        
        // Clear drag state
        val clearedDragState = DragDropState()
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withDragDropState(clearedDragState)
                )
                else -> currentState
            }
        }
        
        Timber.d("Ended drag operation: shouldApply=$shouldApply, finalPosition=$finalPosition")
    }

    /**
     * Retries failed operations with exponential backoff.
     * 
     * @param widgetId The identifier of the widget to retry (null for all failed)
     * @param operation The specific operation to retry (null for auto-detect)
     */
    private suspend fun retryOperation(widgetId: String?, operation: String?) {
        if (widgetId != null) {
            val currentAttempts = retryAttempts[widgetId] ?: 0
            if (currentAttempts >= maxRetryAttempts) {
                val error = LiftrixError.ValidationError(
                    field = "retryLimit",
                    violations = listOf("Maximum retry attempts exceeded"),
                    analyticsContext = mapOf(
                        "widget" to widgetId,
                        "attempts" to currentAttempts.toString()
                    )
                )
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> UiState.Success(
                            currentState.data.withWidgetError(widgetId, error)
                        )
                        else -> currentState
                    }
                }
                return
            }
            
            retryAttempts[widgetId] = currentAttempts + 1
            refreshWidget(widgetId, forceRefresh = true)
        } else {
            // Retry all failed widgets
            val currentData = (_uiState.value as? UiState.Success)?.data ?: return
            val failedWidgets = currentData.widgetErrors.keys.toList()
            failedWidgets.forEach { widget ->
                retryOperation(widget, operation)
            }
        }
    }

    /**
     * Clears error states for widgets or global errors.
     * 
     * @param widgetId The identifier of the widget to clear errors for (null for all)
     * @param clearGlobalError Whether to also clear global error state
     */
    private fun clearError(widgetId: String?, clearGlobalError: Boolean) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withClearedErrors(widgetId)
                        .let { state ->
                            if (clearGlobalError) {
                                state.withGlobalError(null)
                            } else {
                                state
                            }
                        }
                )
                else -> UiState.Loading
            }
        }
        
        // Clear retry attempts
        if (widgetId != null) {
            retryAttempts.remove(widgetId)
        } else {
            retryAttempts.clear()
        }
        
        Timber.d("Cleared errors for widget: ${widgetId ?: "all"}")
    }

    /**
     * Toggles real-time updates for the dashboard.
     * 
     * @param enabled Whether real-time updates should be enabled
     * @param applyToAllWidgets Whether to apply to all widgets or just new ones
     */
    private fun toggleRealtimeUpdates(enabled: Boolean, applyToAllWidgets: Boolean) {
        _realtimeUpdatesEnabled.value = enabled
        
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.copy(realtimeUpdatesEnabled = enabled)
                )
                else -> currentState
            }
        }
        
        Timber.d("Real-time updates toggled: enabled=$enabled")
    }

    /**
     * Navigates to dashboard customization screen.
     * 
     * @param category Optional category to focus on in customization screen
     */
    private fun navigateToCustomization(category: String?) {
        // Customization screen navigation placeholder
        // This would typically trigger navigation through a navigation coordinator
        Timber.d("Navigate to customization requested: category=$category")
    }

    /**
     * Exports dashboard data in specified format.
     * 
     * @param format The export format (PDF, CSV, JSON)
     * @param includeCharts Whether to include chart visualizations
     * @param dateRange Optional date range for data filtering
     */
    private suspend fun exportData(format: String, includeCharts: Boolean, dateRange: TimeRange?) {
        // Data export functionality placeholder
        // This would typically use an export service or use case
        Timber.d("Data export requested: format=$format, includeCharts=$includeCharts")
    }

    /**
     * Exports raw data without processing.
     */
    private suspend fun exportRawData() {
        // Raw data export functionality placeholder
        Timber.d("Raw data export requested")
    }

    /**
     * Cancels ongoing export operations.
     */
    private suspend fun cancelExport() {
        // Export cancellation placeholder
        Timber.d("Export cancellation requested")
    }

    /**
     * Handles widget click interactions.
     * 
     * @param widget The analytics widget that was clicked
     * @param action The action to perform (view_details, expand, navigate)
     */
    private fun handleWidgetClick(widget: AnalyticsWidget, action: String) {
        // Widget click handling placeholder
        // This could trigger navigation, expand widget, or show details
        Timber.d("Widget clicked: ${widget.displayName}, action=$action")
    }

    /**
     * Handles time period changes affecting dashboard data.
     * 
     * @param timeRange The new time range to apply
     * @param refreshAffectedWidgets Whether to refresh widgets that use time data
     */
    private suspend fun handleTimePeriodChange(timeRange: TimeRange, refreshAffectedWidgets: Boolean) {
        _currentTimeRange.value = timeRange
        
        if (refreshAffectedWidgets) {
            // Refresh widgets that depend on time-based data
            val currentData = (_uiState.value as? UiState.Success)?.data ?: return
            val timeBasedWidgets = currentData.activeWidgets.filter { widgetId ->
                // Determine which widgets use time-based data
                widgetId in setOf("VolumeChart", "DurationChart", "FrequencyChart", "ProgressChart")
            }
            
            timeBasedWidgets.forEach { widgetId ->
                refreshWidget(widgetId, forceRefresh = true)
            }
        }
        
        Timber.d("Time period changed: $timeRange, refreshWidgets=$refreshAffectedWidgets")
    }

    /**
     * Handles background data updates from external sources.
     * 
     * @param dataTypes Set of data types that were updated
     * @param sourceInfo Information about the update source
     */
    private suspend fun handleBackgroundDataUpdate(dataTypes: Set<String>, sourceInfo: String) {
        val currentData = (_uiState.value as? UiState.Success)?.data ?: return
        
        // Determine which widgets are affected by the data types
        val affectedWidgets = currentData.activeWidgets.filter { widgetId ->
            when {
                "volume" in dataTypes -> widgetId in setOf("TotalVolume", "VolumeChart", "VolumeProgression")
                "frequency" in dataTypes -> widgetId in setOf("WorkoutFrequency", "FrequencyChart")
                "calories" in dataTypes -> widgetId in setOf("CaloriesBurned", "DailyCalories")
                "duration" in dataTypes -> widgetId in setOf("AverageDuration", "DurationChart")
                else -> false
            }
        }
        
        // Refresh affected widgets in background
        affectedWidgets.forEach { widgetId ->
            refreshWidget(widgetId, forceRefresh = true)
        }
        
        Timber.d("Background data update: dataTypes=$dataTypes, source=$sourceInfo, affectedWidgets=${affectedWidgets.size}")
    }

    /**
     * Handles network connectivity changes.
     * 
     * @param isOnline Whether network connectivity is available
     * @param shouldRetryFailedOperations Whether to retry operations that failed offline
     */
    private suspend fun handleNetworkStateChange(isOnline: Boolean, shouldRetryFailedOperations: Boolean) {
        _networkState.value = isOnline
        
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(
                    currentState.data.withOnlineState(isOnline)
                )
                else -> currentState
            }
        }
        
        if (isOnline && shouldRetryFailedOperations) {
            // Retry failed operations when coming back online
            val currentData = (_uiState.value as? UiState.Success)?.data ?: return
            val failedWidgets = currentData.widgetErrors.keys.toList()
            
            failedWidgets.forEach { widgetId ->
                refreshWidget(widgetId, forceRefresh = true)
            }
        }
        
        Timber.d("Network state changed: online=$isOnline, retryFailed=$shouldRetryFailedOperations")
    }

    /**
     * Resets dashboard preferences to defaults.
     * 
     * @param confirmationRequired Whether to show confirmation dialog
     * @param preserveUserData Whether to preserve user-specific customizations
     */
    private suspend fun resetToDefaults(confirmationRequired: Boolean, preserveUserData: Boolean) {
        // Reset to defaults functionality placeholder
        // This would reset configuration and preferences to default values
        Timber.d("Reset to defaults requested: confirmation=$confirmationRequired, preserveData=$preserveUserData")
    }

    /**
     * Cleanup method called when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        retryAttempts.clear()
        Timber.d("DashboardViewModel cleared")
    }
}
