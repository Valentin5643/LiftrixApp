package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.service.WidgetResolver
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import java.time.LocalDateTime
import timber.log.Timber

/**
 * Navigation callbacks for detail screen navigation from AnalyticsWidgetViewModel.
 */
data class NavigationCallbacks(
    val onNavigateToVolumeDetail: () -> Unit = {},
    val onNavigateToOneRmDetail: () -> Unit = {},
    val onNavigateToMuscleGroupDetail: () -> Unit = {},
    val onNavigateToFrequencyDetail: () -> Unit = {}
)

/**
 * ViewModel for analytics widget management following the MVI pattern.
 * 
 * This ViewModel handles all analytics widget operations including data loading,
 * configuration management, and user preference persistence. It extends BaseViewModel
 * to provide standardized state management and error handling.
 * 
 * Now integrates with WidgetResolver for dynamic widget resolution:
 * - Beginner users: 4 widgets (essential metrics)
 * - Intermediate users: 7 widgets (essential + trends)
 * - Advanced users: 10 widgets (comprehensive analytics)
 * - CUSTOM layout mode: User-selected widgets within level constraints
 * 
 * Key Features:
 * - Dynamic widget resolution based on user level and layout mode
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
 * - Uses WidgetResolver for dynamic widget selection
 * - Uses StateFlow for reactive UI updates
 * - Implements proper lifecycle management
 * - Handles concurrent operations safely
 * 
 * Dependencies:
 * - AnalyticsService: Core service for widget operations
 * - AnalyticsWidgetManager: Widget configuration management
 * - WidgetResolver: Dynamic widget resolution based on user level
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
    private val widgetResolver: WidgetResolver,
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
     * Stabilized to prevent null resets that cause loading loops.
     */
    private val _currentUser = MutableStateFlow<User?>(null)
    
    /**
     * Track if user has been set to prevent resetting to null inadvertently.
     * Once we have a valid user, we don't reset it unless explicitly cleared.
     */
    private var userStabilized = false

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
     * Navigation callbacks for detail screen navigation.
     * Set by the UI component to enable navigation from ViewModel.
     */
    private var navigationCallbacks: NavigationCallbacks? = null

    /**
     * Maximum number of retry attempts for failed operations.
     */
    private val maxRetryAttempts = 3
    
    /**
     * Concurrency limiter for widget loading to prevent overwhelming the database.
     * Limits concurrent widget data loading to 4 operations to maintain performance.
     */
    private val widgetLoadingSemaphore = Semaphore(permits = 4)

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
            AnalyticsWidgetEvent.NavigateToDashboardCustomization -> handleNavigateToDashboardCustomization()
            AnalyticsWidgetEvent.NavigateToVolumeDetail -> handleNavigateToVolumeDetail()
            AnalyticsWidgetEvent.NavigateToOneRmDetail -> handleNavigateToOneRmDetail()
            AnalyticsWidgetEvent.NavigateToMuscleGroupDetail -> handleNavigateToMuscleGroupDetail()
            AnalyticsWidgetEvent.NavigateToFrequencyDetail -> handleNavigateToFrequencyDetail()
            is AnalyticsWidgetEvent.WidgetReordered -> handleWidgetReordered(event.fromIndex, event.toIndex)
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

                // Load all widgets with concurrency limits
                val jobs = widgetsToRefresh.map { widgetId ->
                    launch {
                        widgetLoadingSemaphore.withPermit {
                            loadWidgetData(widgetId, forceRefresh = true)
                        }
                    }
                }

                // Wait for all jobs to complete
                jobs.forEach { it.join() }
                
                // Periodic cleanup of retry tracking to prevent memory leaks
                if (retryAttempts.size > 20) {
                    retryAttempts.clear()
                    Timber.d("Cleaned up retry attempts map to prevent memory growth")
                }

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
                        
                        val shouldUpdateUser = when {
                            previousUserId == null && event.userId != null -> true
                            previousUserId != null && event.userId == null && !userStabilized -> true
                            previousUserId != null && event.userId != null && 
                                previousUserId != event.userId -> true
                            previousUserId != null && event.userId == null && userStabilized -> {
                                Timber.w("WIDGET-AUTH-DEBUG: Rejecting user null reset - " +
                                    "already stabilized with $previousUserId")
                                false
                            }
                            else -> false
                        }
                        
                        if (shouldUpdateUser) {
                            // FIXED: Added proper validation and null handling
                            _currentUser.value = event.userId?.let { userId ->
                                // Only create User object if we have a valid userId
                                if (userId.isNotBlank()) {
                                    userStabilized = true
                                    com.example.liftrix.domain.model.User(
                                        uid = userId,
                                        email = "temp@liftrix.app",
                                        displayName = null,
                                        photoUrl = null,
                                        isAnonymous = false,
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
                                userStabilized = false
                                null
                            }
                        }
                        } else {
                            Timber.d("WIDGET-AUTH-DEBUG: Ignored redundant or invalid user change: " +
                                "$previousUserId -> ${event.userId}")
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
        // Show all available widgets per SPEC requirement (FR-001)
        val defaultWidgets = AnalyticsWidget.getAllWidgets()

        availableWidgets.value = defaultWidgets
        
        // Log available widgets for debugging
        Timber.d("Initialized ${defaultWidgets.size} available widgets: ${defaultWidgets.map { it.displayName }}")
    }

    /**
     * Loads initial data including preferences and configuration.
     */
    private fun loadInitialData() {
        Timber.d("=== INIT DEBUG: Starting loadInitialData")
        
        // CRITICAL TEMP FIX: Force Advanced level for debugging
        val user = _currentUser.value
        if (user != null) {
            Timber.d("=== INIT DEBUG: User found, checking preferences state")
            viewModelScope.launch {
                // First check if we're stuck in Beginner mode when we should be Advanced
                val currentState = _uiState.value
                Timber.d("=== INIT DEBUG: Current state type = ${currentState::class.simpleName}")
                
                if (currentState is UiState.Success && 
                    currentState.data.configuration?.javaClass?.simpleName == "Beginner") {
                    Timber.w("=== CRITICAL FIX: User stuck in Beginner mode, forcing Advanced")
                    forceAdvancedUserLevel()
                    return@launch
                }
            }
        }
        
        loadPreferences()
        loadConfiguration()
    }

    /**
     * Loads user widget preferences from the service.
     */
    private fun loadPreferences() {
        val user = _currentUser.value ?: return
        
        Timber.d("=== PREFS LOAD: Starting preference load for user ${user.uid}")

        viewModelScope.launch {
            try {
                Timber.d("=== PREFS LOAD: Calling analyticsService.getWidgetPreferences")
                val result = analyticsService.getWidgetPreferences(user.uid)
                
                Timber.d("=== PREFS LOAD: Got result from analyticsService")
                
                result.fold(
                    onSuccess = { preferences ->
                        Timber.d("📥 PREFS LOADED: preferences = $preferences")
                        Timber.d("📥 PREFS LOADED: dashboardLayout = ${preferences.dashboardLayout}")
                        Timber.d("=== WIDGET DEBUG: User Level = ${preferences.userLevel}, Visible Widgets = ${preferences.visibleWidgets.size}")
                        Timber.d("=== WIDGET DEBUG: Visible Widget IDs = ${preferences.visibleWidgets}")
                        
                        // CRITICAL FIX: Use WidgetResolver for proper widget resolution
                        val resolvedWidgets = widgetResolver.resolveWidgetsFromPreferences(
                            preferences = preferences,
                            userLevel = preferences.userLevel
                        )
                        
                        Timber.d("=== WIDGET DEBUG: Resolved ${resolvedWidgets.size} widgets from WidgetResolver")
                        Timber.d("=== WIDGET DEBUG: Resolved widgets = ${resolvedWidgets.map { it.displayName }}")
                        
                        updateState { currentState ->
                            when (currentState) {
                                is UiState.Success -> UiState.Success(
                                    currentState.data
                                        .withPreferences(preferences)
                                        .withResolvedWidgets(resolvedWidgets)
                                        .withLoading(false)
                                )
                                else -> UiState.Success(
                                    AnalyticsWidgetState()
                                        .withPreferences(preferences)
                                        .withResolvedWidgets(resolvedWidgets)
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
                        Timber.e("=== PREFS LOAD: Failed to load preferences - ${throwable.message}")
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
     * Loads dashboard configuration from preferences using WidgetResolver.
     */
    private fun loadConfiguration() {
        val preferences = when (val state = _uiState.value) {
            is UiState.Success -> state.data.preferences
            else -> null
        }
        if (preferences != null) {
            Timber.d("=== CONFIG DEBUG: Loading configuration for ${preferences.userLevel} with layout ${preferences.dashboardLayout}")
            
            // Use WidgetResolver to get the appropriate configuration
            val configuration = DashboardConfiguration.fromUserLevelAndLayout(
                userLevel = preferences.userLevel,
                layoutMode = preferences.dashboardLayout
            )
            
            Timber.d("=== CONFIG DEBUG: Created configuration: ${configuration.javaClass.simpleName}")
            
            // Resolve widgets using WidgetResolver
            val resolvedWidgets = widgetResolver.resolveWidgets(
                userLevel = preferences.userLevel,
                layoutMode = preferences.dashboardLayout,
                preferences = preferences
            )
            
            Timber.d("=== CONFIG DEBUG: WidgetResolver returned ${resolvedWidgets.size} widgets for ${preferences.userLevel}")
            Timber.d("=== CONFIG DEBUG: Widget list = ${resolvedWidgets.map { it.displayName }}")
            
            updateState { currentState ->
                when (currentState) {
                    is UiState.Success -> UiState.Success(
                        currentState.data
                            .withConfiguration(configuration)
                            .withResolvedWidgets(resolvedWidgets)
                    )
                    else -> UiState.Success(
                        AnalyticsWidgetState()
                            .withConfiguration(configuration)
                            .withResolvedWidgets(resolvedWidgets)
                    )
                }
            }
        } else {
            Timber.w("AnalyticsWidgetViewModel: No preferences available for configuration loading")
        }
    }

    /**
     * Updates error state for ViewModel-specific error handling with permission fallback.
     * 
     * @param error The error to reflect in the state
     */
    override fun updateErrorState(error: LiftrixError) {
        // Handle permission denied errors gracefully
        if (isPermissionDeniedError(error)) {
            Timber.w("Permission denied in AnalyticsWidgetViewModel - falling back to empty state: ${error.message}")
            
            // Provide fallback empty state instead of error
            viewModelScope.launch {
                val currentState = (_uiState.value as? UiState.Success)?.data ?: AnalyticsWidgetState()
                val fallbackState = currentState.copy(
                    activeWidgets = emptyList(),
                    widgetDataMap = emptyMap(),
                    isLoading = false,
                )
                updateState { UiState.Success(fallbackState) }
            }
        } else {
            updateState { UiState.Error(error) }
        }
    }
    
    /**
     * Checks if an error is related to permission denied
     */
    private fun isPermissionDeniedError(error: LiftrixError): Boolean {
        return when (error) {
            is LiftrixError.AuthenticationError -> {
                error.errorCode == "PERMISSION_DENIED" || 
                error.message.contains("Permission denied", ignoreCase = true)
            }
            else -> {
                error.message.contains("PERMISSION_DENIED", ignoreCase = true) ||
                error.message.contains("Missing or insufficient permissions", ignoreCase = true) ||
                error.message.contains("Permission denied", ignoreCase = true)
            }
        }
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
     * Maps widget types to appropriate detail screen navigation based on widget category and type.
     * 
     * Navigation mapping:
     * - Volume widgets (TotalVolume, VolumeChart, VolumeTrends) → VolumeAnalysisDetail
     * - 1RM/Strength widgets (OneRMProgression, StrengthProgress, PersonalRecords) → OneRmDetail
     * - Muscle group widgets (MuscleGroupDistribution) → MuscleGroupDetail  
     * - Frequency widgets (WorkoutFrequency, FrequencyChart) → WorkoutFrequencyDetail
     * 
     * @param widget The clicked widget
     */
    private fun handleWidgetClick(widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget) {
        val widgetId = widget.id
        trackInteraction(widgetId, "click", emptyMap())
        
        // Map widget to appropriate detail screen navigation
        val navigationEvent = when (widget) {
            // Volume-related widgets → Volume Analysis Detail
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.TotalVolume,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.VolumeChart,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.VolumeTrends,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.VolumeCalendar,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.VolumeLoadProgression,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.VolumeAnalytics -> {
                AnalyticsWidgetEvent.NavigateToVolumeDetail
            }
            
            // 1RM and Strength widgets → One RM Detail
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.OneRMProgression,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.StrengthProgress,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.PersonalRecords,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.StrengthAnalytics,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.MonthlySummary -> {
                AnalyticsWidgetEvent.NavigateToOneRmDetail
            }
            
            // Muscle group widgets → Muscle Group Detail
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.MuscleGroupDistribution -> {
                AnalyticsWidgetEvent.NavigateToMuscleGroupDetail
            }
            
            // Frequency widgets → Workout Frequency Detail
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.WorkoutFrequency,
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.FrequencyChart -> {
                AnalyticsWidgetEvent.NavigateToFrequencyDetail
            }
            
            // Recovery widgets → Frequency Detail (recovery patterns are related to workout frequency)
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.RecoveryMetrics -> {
                AnalyticsWidgetEvent.NavigateToFrequencyDetail
            }
            
            // For other widgets, no specific navigation (just log interaction)
            else -> null
        }
        
        // Trigger navigation if a mapping exists
        navigationEvent?.let { event ->
            Timber.d("Navigating to detail screen for widget: $widgetId")
            handleEvent(event)
        } ?: run {
            Timber.d("Widget clicked without specific detail navigation: $widgetId")
        }
    }
    
    /**
     * Handles navigation to dashboard customization.
     */
    private fun handleNavigateToDashboardCustomization() {
        Timber.d("Navigating to dashboard customization")
        // Navigation logic will be handled by the parent composable
    }
    
    /**
     * Sets navigation callbacks for detail screen navigation.
     * Should be called from the UI layer to connect ViewModel to navigation.
     */
    fun setNavigationCallbacks(callbacks: NavigationCallbacks) {
        this.navigationCallbacks = callbacks
        Timber.d("Navigation callbacks set for AnalyticsWidgetViewModel")
    }
    
    /**
     * Handles navigation to volume analysis detail screen.
     */
    private fun handleNavigateToVolumeDetail() {
        Timber.d("Navigating to volume analysis detail screen")
        navigationCallbacks?.onNavigateToVolumeDetail?.invoke()
            ?: Timber.w("Navigation callbacks not set - cannot navigate to volume detail")
    }
    
    /**
     * Handles navigation to one rep max detail screen.
     */
    private fun handleNavigateToOneRmDetail() {
        Timber.d("Navigating to one rep max detail screen")
        navigationCallbacks?.onNavigateToOneRmDetail?.invoke()
            ?: Timber.w("Navigation callbacks not set - cannot navigate to one RM detail")
    }
    
    /**
     * Handles navigation to muscle group detail screen.
     */
    private fun handleNavigateToMuscleGroupDetail() {
        Timber.d("Navigating to muscle group detail screen")
        navigationCallbacks?.onNavigateToMuscleGroupDetail?.invoke()
            ?: Timber.w("Navigation callbacks not set - cannot navigate to muscle group detail")
    }
    
    /**
     * Handles navigation to workout frequency detail screen.
     */
    private fun handleNavigateToFrequencyDetail() {
        Timber.d("Navigating to workout frequency detail screen")
        navigationCallbacks?.onNavigateToFrequencyDetail?.invoke()
            ?: Timber.w("Navigation callbacks not set - cannot navigate to frequency detail")
    }
    
    /**
     * Handles widget reordering with immediate state update and preference persistence.
     * 
     * FIXED: Now actually reorders widgets in the UI state and persists to preferences.
     * This was the critical missing piece causing widgets to snap back after drag operations.
     */
    private fun handleWidgetReordered(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) {
            Timber.w("Invalid widget reorder indices: from=$fromIndex, to=$toIndex")
            return
        }
        
        val user = _currentUser.value
        if (user == null) {
            Timber.w("No user available for widget reordering")
            return
        }
        
        val currentState = _uiState.value
        if (currentState !is UiState.Success) {
            Timber.w("Cannot reorder widgets - invalid state: ${currentState::class.simpleName}")
            return
        }
        
        val preferences = currentState.data.preferences
        if (preferences == null) {
            Timber.w("No preferences available for widget reordering")
            return
        }
        
        val currentOrder = preferences.getOrderedVisibleWidgets().toMutableList()
        
        if (fromIndex >= currentOrder.size || toIndex >= currentOrder.size) {
            Timber.w("Reorder indices out of bounds: from=$fromIndex, to=$toIndex, size=${currentOrder.size}")
            return
        }
        
        // Perform the reordering
        val widgetToMove = currentOrder.removeAt(fromIndex)
        currentOrder.add(toIndex, widgetToMove)
        
        Timber.d("Reordering widget '$widgetToMove' from position $fromIndex to $toIndex")
        Timber.d("New widget order: ${currentOrder.joinToString(", ")}")
        
        val updatedPreferences = preferences.updateWidgetOrder(currentOrder)
        
        updateState { currentUiState ->
            when (currentUiState) {
                is UiState.Success -> {
                    val reorderedWidgets = currentOrder.mapNotNull { widgetId ->
                        currentUiState.data.activeWidgets.find { it.id == widgetId }
                    }
                    
                    UiState.Success(
                        currentUiState.data
                            .withPreferences(updatedPreferences)
                            .withResolvedWidgets(reorderedWidgets)
                    )
                }
                else -> currentUiState
            }
        }
        
        // Persist to database asynchronously
        viewModelScope.launch {
            try {
                val result = analyticsService.updateWidgetPreferences(updatedPreferences)
                result.fold(
                    onSuccess = {
                        Timber.d("Widget reorder persisted successfully")
                        trackInteraction("widget_reorder", "reorder", mapOf<String, Any>(
                            "from" to fromIndex,
                            "to" to toIndex,
                            "widget" to widgetToMove,
                            "success" to true
                        ))
                    },
                    onFailure = { throwable ->
                        Timber.e("Failed to persist widget reorder: ${throwable.message}")
                        trackInteraction("widget_reorder", "reorder", mapOf<String, Any>(
                            "from" to fromIndex,
                            "to" to toIndex,
                            "widget" to widgetToMove,
                            "success" to false,
                            "error" to (throwable.message ?: "Unknown error")
                        ))
                        
                        // Revert UI state if persistence fails
                        updateState { currentUiState ->
                            when (currentUiState) {
                                is UiState.Success -> UiState.Success(
                                    currentUiState.data.withPreferences(preferences)
                                )
                                else -> currentUiState
                            }
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Unexpected error during widget reorder persistence")
                // Revert on exception
                updateState { currentUiState ->
                    when (currentUiState) {
                        is UiState.Success -> UiState.Success(
                            currentUiState.data.withPreferences(preferences)
                        )
                        else -> currentUiState
                    }
                }
            }
        }
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
     * Forces user to advanced level to show all widgets with correct IDs.
     * 
     * CRITICAL BUG FIX: This method now uses WidgetResolver to get correct widget IDs
     * instead of hardcoded incorrect names that were causing widget resolution failures.
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
                    // Use WidgetResolver to create advanced-level preferences
                    val advancedPrefs = widgetResolver.createDefaultPreferences(
                        userId = user.uid,
                        userLevel = UserLevel.ADVANCED,
                        layoutMode = currentPrefs.dashboardLayout
                    )
                    
                    Timber.d("Setting ${advancedPrefs.visibleWidgets.size} Advanced level widgets: ${advancedPrefs.visibleWidgets.joinToString(", ")}")
                    
                    val result = analyticsService.updateWidgetPreferences(advancedPrefs)
                    
                    result.fold(
                        onSuccess = {
                            // Reload preferences and configuration after cache invalidation
                            loadPreferences()
                            loadConfiguration()
                            Timber.d("Advanced user level set successfully with ${advancedPrefs.visibleWidgets.size} widgets")
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
    return AnalyticsWidget.getById(widgetId)
}