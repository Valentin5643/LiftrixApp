package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber

/**
 * ProgressDashboardCoordinator coordinates communication between specialized ViewModels.
 * 
 * This coordinator implements the Coordinator pattern to manage inter-ViewModel communication,
 * workout session coordination, and real-time update management for the Progress Dashboard.
 * 
 * Key Responsibilities:
 * - Coordinate communication between ProgressChartsViewModel, AnalyticsWidgetViewModel, etc.
 * - Manage workout completion events and trigger appropriate ViewModels refresh
 * - Handle real-time updates and broadcast them to relevant ViewModels
 * - Coordinate user authentication state changes across ViewModels
 * - Manage global error states and network connectivity changes
 * - Orchestrate cleanup and resource management
 * 
 * Architecture Pattern:
 * - Follows the Coordinator pattern for inter-component communication
 * - Uses SharedFlow for broadcasting events to multiple ViewModels
 * - Implements reactive state management with StateFlow
 * - Maintains single source of truth for coordinator state
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun ProgressDashboardScreen(
 *     coordinator: ProgressDashboardCoordinator = hiltViewModel()
 * ) {
 *     val coordinatorState by coordinator.uiState.collectAsStateWithLifecycle()
 *     
 *     // Handle coordinator events
 *     LaunchedEffect(Unit) {
 *         coordinator.coordinatorEvents.collect { event ->
 *             when (event) {
 *                 is CoordinatorEvent.WorkoutCompleted -> {
 *                     // Handle workout completion
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
@HiltViewModel
class ProgressDashboardCoordinator @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val authRepository: AuthRepository,
    private val getWeightUnitPreferenceUseCase: com.example.liftrix.domain.usecase.settings.GetWeightUnitPreferenceUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<UiState<CoordinatorState>, CoordinatorEvent>(errorHandler) {

    override val _uiState = MutableStateFlow<UiState<CoordinatorState>>(UiState.Loading)

    /**
     * Internal coordinator state for managing coordination logic.
     */
    private val coordinatorState = MutableStateFlow(CoordinatorState())

    /**
     * SharedFlow for broadcasting events to other ViewModels.
     * 
     * This flow allows other ViewModels to observe coordination events
     * and react accordingly. Uses replay(1) to ensure late subscribers
     * receive the most recent event.
     */
    private val _coordinatorEvents = MutableSharedFlow<CoordinatorEvent>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val coordinatorEvents: SharedFlow<CoordinatorEvent> = _coordinatorEvents.asSharedFlow()

    /**
     * Internal event processing queue for handling events sequentially.
     */
    private val _eventQueue = MutableSharedFlow<CoordinatorEvent>()

    init {
        initializeCoordinator()
    }

    /**
     * Initializes the coordinator by setting up reactive streams and starting monitoring.
     */
    private fun initializeCoordinator() {
        // Start monitoring auth state changes
        observeAuthenticationState()
        
        // Start monitoring workout session changes
        observeWorkoutSessionState()
        
        // Start monitoring weight unit preferences
        observeWeightUnitPreferences()
        
        // Process events from the queue
        processEventQueue()
        
        // Set initial state
        updateUiState()
        
        // Send initial time period event for default Month period
        // This ensures widget ViewModels receive the initial time period and start loading data
        viewModelScope.launch {
            // Small delay to ensure ViewModels are initialized
            kotlinx.coroutines.delay(500) // 500ms delay
            val defaultTimeRange = com.example.liftrix.domain.model.analytics.TimeRange.lastMonth()
            Timber.d("Coordinator: Sending initial time period event for default Month period")
            _coordinatorEvents.tryEmit(CoordinatorEvent.TimePeriodChanged(defaultTimeRange))
        }
        
        // Add initialization timeout safety
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000) // 5 second delay
            if (coordinatorState.value.currentUser is com.example.liftrix.ui.common.state.AsyncData.NotAsked) {
                Timber.w("Coordinator initialization timeout - forcing auth check")
                // Force auth state check
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    _coordinatorEvents.tryEmit(CoordinatorEvent.UserAuthChanged(currentUser.uid))
                }
            }
        }
        
        Timber.d("ProgressDashboardCoordinator initialized")
    }

    /**
     * Observes authentication state changes and updates coordinator state accordingly.
     */
    private fun observeAuthenticationState() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { exception ->
                    Timber.e(exception, "Error observing authentication state")
                    val error = LiftrixError.AuthenticationError(
                        errorMessage = "Failed to observe authentication state"
                    )
                    handleError(error)
                }
                .collect { user ->
                    coordinatorState.value = coordinatorState.value.copy(
                        currentUser = if (user != null) {
                            com.example.liftrix.ui.common.state.AsyncData.Success(user)
                        } else {
                            com.example.liftrix.ui.common.state.AsyncData.NotAsked
                        }
                    )
                    
                    // Broadcast user auth change event
                    _coordinatorEvents.tryEmit(CoordinatorEvent.UserAuthChanged(user?.uid))
                    
                    updateUiState()
                    
                    Timber.d("Authentication state updated: ${user?.uid}")
                }
        }
    }

    /**
     * Observes workout session state changes and coordinates ViewModels accordingly.
     */
    private fun observeWorkoutSessionState() {
        viewModelScope.launch {
            sessionManager.currentSession
                .catch { exception ->
                    Timber.e(exception, "Error observing workout session state")
                    val error = LiftrixError.UnknownError(
                        errorMessage = "Failed to observe workout session state"
                    )
                    handleError(error)
                }
                .collect { session ->
                    val sessionActive = session != null && session.isActive()
                    val sessionId = session?.id?.value
                    
                    coordinatorState.updateValue {
                        copy(
                            sessionActive = sessionActive,
                            currentSessionId = sessionId
                        )
                    }
                    
                    // Broadcast session state change event
                    _coordinatorEvents.tryEmit(
                        CoordinatorEvent.SessionStateChanged(
                            sessionActive = sessionActive,
                            sessionId = sessionId
                        )
                    )
                    
                    updateUiState()
                    
                    Timber.d("Workout session state updated: active=$sessionActive, id=$sessionId")
                }
        }
    }

    /**
     * Observes user weight unit preferences and updates coordinator state accordingly.
     */
    private fun observeWeightUnitPreferences() {
        viewModelScope.launch {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { user ->
                    getWeightUnitPreferenceUseCase(user.uid)
                }
                .catch { exception ->
                    Timber.e(exception, "Error observing weight unit preferences")
                    // Use system default on error
                    emit(com.example.liftrix.domain.model.WeightUnit.getSystemDefault())
                }
                .collect { weightUnit ->
                    coordinatorState.updateValue {
                        copy(
                            coordinatorPreferences = coordinatorPreferences + ("weightUnit" to weightUnit)
                        )
                    }
                    
                    updateUiState()
                    
                    Timber.d("Weight unit preference updated: $weightUnit")
                }
        }
    }

    /**
     * Processes events from the event queue sequentially.
     */
    private fun processEventQueue() {
        viewModelScope.launch {
            _eventQueue.collect { event ->
                try {
                    processCoordinatorEvent(event)
                } catch (exception: Exception) {
                    Timber.e(exception, "Error processing coordinator event: $event")
                    val error = LiftrixError.UnknownError(
                        errorMessage = "Failed to process coordinator event"
                    )
                    handleError(error)
                }
            }
        }
    }

    /**
     * Handles events from the UI and processes them through the coordinator.
     */
    override fun handleEvent(event: CoordinatorEvent) {
        viewModelScope.launch {
            _eventQueue.emit(event)
        }
    }

    /**
     * Processes coordinator events and updates state accordingly.
     */
    private suspend fun processCoordinatorEvent(event: CoordinatorEvent) {
        when (event) {
            is CoordinatorEvent.WorkoutCompleted -> handleWorkoutCompleted(event)
            is CoordinatorEvent.RefreshAllData -> handleRefreshAllData()
            is CoordinatorEvent.ToggleRealtimeUpdates -> handleToggleRealtimeUpdates(event)
            is CoordinatorEvent.UserAuthChanged -> handleUserAuthChanged(event)
            is CoordinatorEvent.BroadcastError -> handleBroadcastError(event)
            is CoordinatorEvent.SessionStateChanged -> handleSessionStateChanged(event)
            is CoordinatorEvent.PreferencesChanged -> handlePreferencesChanged(event)
            is CoordinatorEvent.RefreshSpecificData -> handleRefreshSpecificData(event)
            is CoordinatorEvent.NetworkConnectivityChanged -> handleNetworkConnectivityChanged(event)
            is CoordinatorEvent.TimePeriodChanged -> handleTimePeriodChanged(event)
            is CoordinatorEvent.ClearError -> handleClearError()
            is CoordinatorEvent.CleanupCoordinator -> handleCleanupCoordinator()
            is CoordinatorEvent.ExportToPdf -> handleExportToPdf()
            is CoordinatorEvent.ExportToCsv -> handleExportToCsv()
            is CoordinatorEvent.ExportRawData -> handleExportRawData(event)
            is CoordinatorEvent.CancelExport -> handleCancelExport()
        }
    }

    /**
     * Handles workout completion events and coordinates ViewModels refresh.
     */
    private suspend fun handleWorkoutCompleted(event: CoordinatorEvent.WorkoutCompleted) {
        coordinatorState.updateValue {
            copy(lastWorkoutCompletion = Clock.System.now())
        }
        
        // Broadcast workout completion to all ViewModels
        _coordinatorEvents.emit(event)
        
        // Trigger refresh of all data after workout completion
        _coordinatorEvents.emit(CoordinatorEvent.RefreshAllData)
        
        updateUiState()
        
        Timber.i("Workout completed coordinated: ${event.workoutId}")
    }

    /**
     * Handles refresh all data events and coordinates ViewModels refresh.
     */
    private suspend fun handleRefreshAllData() {
        coordinatorState.updateValue {
            copy(
                lastGlobalRefresh = Clock.System.now(),
                refreshingViewModels = setOf(
                    "ProgressChartsViewModel",
                    "AnalyticsWidgetViewModel", 
                    "ProgressSummaryViewModel",
                    "CalorieTrackingViewModel"
                )
            )
        }
        
        // Broadcast refresh event to all ViewModels
        _coordinatorEvents.emit(CoordinatorEvent.RefreshAllData)
        
        updateUiState()
        
        Timber.d("Global data refresh coordinated")
        
        // Clear refreshing state after a delay (ViewModels should report completion)
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000) // 5 second timeout
            coordinatorState.updateValue {
                copy(refreshingViewModels = emptySet())
            }
            updateUiState()
        }
    }

    /**
     * Handles toggle real-time updates events.
     */
    private suspend fun handleToggleRealtimeUpdates(event: CoordinatorEvent.ToggleRealtimeUpdates) {
        coordinatorState.updateValue {
            copy(realtimeUpdates = event.enabled)
        }
        
        // Broadcast real-time update toggle to ViewModels
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("Real-time updates toggled: ${event.enabled}")
    }

    /**
     * Handles user authentication changes.
     */
    private suspend fun handleUserAuthChanged(event: CoordinatorEvent.UserAuthChanged) {
        // Update connected ViewModels count based on auth state
        val connectedCount = if (event.userId != null) 4 else 0 // Assume 4 ViewModels when authenticated
        
        coordinatorState.updateValue {
            copy(connectedViewModels = connectedCount)
        }
        
        // Broadcast auth change to ViewModels
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("User auth change coordinated: ${event.userId}")
    }

    /**
     * Handles broadcast error events.
     */
    private suspend fun handleBroadcastError(event: CoordinatorEvent.BroadcastError) {
        // Special handling for permission denied errors
        val isPermissionError = event.error.contains("PERMISSION_DENIED", ignoreCase = true) ||
                               event.error.contains("Missing or insufficient permissions", ignoreCase = true) ||
                               event.error.contains("Permission denied", ignoreCase = true)
        
        if (isPermissionError) {
            Timber.w("Permission denied error detected - implementing graceful fallback: ${event.error}")
            
            // Instead of showing error to user, we'll silently handle it
            coordinatorState.updateValue {
                copy(
                    globalError = null, // Don't show permission errors to users
                    networkConnected = false // Treat as network issue for UI purposes
                )
            }
            
            // Broadcast a graceful fallback event instead of the error
            _coordinatorEvents.emit(
                CoordinatorEvent.NetworkConnectivityChanged(isConnected = false)
            )
            
            Timber.d("Permission error handled gracefully - analytics sync disabled")
        } else {
            // Handle non-permission errors normally
            coordinatorState.updateValue {
                copy(globalError = event.error)
            }
            
            // Broadcast error to specified ViewModels
            _coordinatorEvents.emit(event)
        }
        
        updateUiState()
        
        if (isPermissionError) {
            Timber.w("Permission error handled gracefully: ${event.error}")
        } else {
            Timber.w("Global error broadcasted: ${event.error}")
        }
    }

    /**
     * Handles session state changes.
     */
    private suspend fun handleSessionStateChanged(event: CoordinatorEvent.SessionStateChanged) {
        coordinatorState.updateValue {
            copy(
                sessionActive = event.sessionActive,
                currentSessionId = event.sessionId
            )
        }
        
        // Broadcast session state change
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("Session state change coordinated: active=${event.sessionActive}")
    }

    /**
     * Handles preferences changes.
     */
    private suspend fun handlePreferencesChanged(event: CoordinatorEvent.PreferencesChanged) {
        coordinatorState.updateValue {
            withUpdatedPreferences(event.preferencesChanged)
        }
        
        // Broadcast preferences change
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("Preferences change coordinated: ${event.preferencesChanged.keys}")
    }

    /**
     * Handles refresh specific data events.
     */
    private suspend fun handleRefreshSpecificData(event: CoordinatorEvent.RefreshSpecificData) {
        coordinatorState.updateValue {
            copy(refreshingViewModels = event.dataTypes)
        }
        
        // Broadcast specific data refresh
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("Specific data refresh coordinated: ${event.dataTypes}")
    }

    /**
     * Handles network connectivity changes.
     */
    private suspend fun handleNetworkConnectivityChanged(event: CoordinatorEvent.NetworkConnectivityChanged) {
        coordinatorState.updateValue {
            copy(networkConnected = event.isConnected)
        }
        
        // Broadcast network connectivity change
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("Network connectivity change coordinated: ${event.isConnected}")
    }

    /**
     * Handles time period change events and coordinates ViewModels to update accordingly.
     */
    private suspend fun handleTimePeriodChanged(event: CoordinatorEvent.TimePeriodChanged) {
        // Broadcast time period change to all ViewModels
        _coordinatorEvents.emit(event)
        
        updateUiState()
        
        Timber.d("Time period change coordinated: ${event.timeRange}")
    }

    /**
     * Handles clear error events and coordinates error dismissal across ViewModels.
     */
    private suspend fun handleClearError() {
        coordinatorState.updateValue {
            withClearedGlobalError()
        }
        
        // Broadcast clear error event to all ViewModels
        _coordinatorEvents.emit(CoordinatorEvent.ClearError)
        
        updateUiState()
        
        Timber.d("Global error cleared across ViewModels")
    }

    /**
     * Handles cleanup coordinator events.
     */
    private suspend fun handleCleanupCoordinator() {
        coordinatorState.updateValue {
            copy(
                isActive = false,
                realtimeUpdates = false,
                refreshingViewModels = emptySet(),
                connectedViewModels = 0
            )
        }
        
        // Broadcast cleanup event
        _coordinatorEvents.emit(CoordinatorEvent.CleanupCoordinator)
        
        updateUiState()
        
        Timber.d("Coordinator cleanup initiated")
    }

    /**
     * Updates the UI state based on current coordinator state.
     */
    private fun updateUiState() {
        val currentState = coordinatorState.value
        _uiState.value = if (currentState.isActive) {
            UiState.Success(currentState)
        } else {
            UiState.Loading
        }
    }

    /**
     * Convenience method to clear global error state.
     */
    fun clearGlobalError() {
        viewModelScope.launch {
            coordinatorState.updateValue {
                withClearedGlobalError()
            }
            updateUiState()
        }
    }

    /**
     * Convenience method to report ViewModel refresh completion.
     */
    fun reportViewModelRefreshComplete(viewModelName: String) {
        viewModelScope.launch {
            coordinatorState.updateValue {
                withoutRefreshingViewModel(viewModelName)
            }
            updateUiState()
        }
    }

    /**
     * Convenience method to check if coordinator can process events.
     */
    fun canProcessEvents(): Boolean {
        return coordinatorState.value.canProcessEvents()
    }

    /**
     * Handle error states by broadcasting them to ViewModels.
     */
    override fun updateErrorState(error: LiftrixError) {
        viewModelScope.launch {
            val errorEvent = CoordinatorEvent.BroadcastError(
                error = error.message,
                affectedViewModels = listOf("All")
            )
            handleEvent(errorEvent)
        }
    }

    /**
     * Cleanup resources when ViewModel is cleared.
     */
    override fun onCleared() {
        viewModelScope.launch {
            handleEvent(CoordinatorEvent.CleanupCoordinator)
        }
        super.onCleared()
    }
    
    /**
     * Handles PDF export request.
     */
    private suspend fun handleExportToPdf() {
        try {
            Timber.d("Exporting dashboard to PDF")
            // PDF export logic would be implemented here
            // This is a placeholder for the actual export functionality
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to export to PDF")
        }
    }
    
    /**
     * Handles CSV export request.
     */
    private suspend fun handleExportToCsv() {
        try {
            Timber.d("Exporting dashboard to CSV")
            // CSV export logic would be implemented here
            // This is a placeholder for the actual export functionality
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to export to CSV")
        }
    }
    
    /**
     * Handles raw data export requests.
     */
    private suspend fun handleExportRawData(event: CoordinatorEvent.ExportRawData) {
        try {
            Timber.d("Exporting raw data")
            // Raw data export logic would be implemented here
            // This is a placeholder for the actual export functionality
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to export raw data")
        }
    }
    
    /**
     * Handles export cancellation requests.
     */
    private suspend fun handleCancelExport() {
        try {
            Timber.d("Cancelling export operation")
            // Export cancellation logic would be implemented here
            // This is a placeholder for the actual cancellation functionality
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to cancel export")
        }
    }
}