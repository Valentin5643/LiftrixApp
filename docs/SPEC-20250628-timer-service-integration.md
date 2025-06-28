# SPEC-20250628-timer-service-integration

## Executive Summary
**Feature**: Timer Service Integration for Active Workouts
**Impact**: Enable live workout tracking with session timing, rest period management, and background persistence to support the "Create workout from scratch" flow.
**Effort**: 2-3 developer-days
**Risk**: Medium - Background service implementation requires careful lifecycle management and notification handling
**Dependencies**: SPEC-20250628-floating-action-button-modal.md (custom workout creation flow), existing RestTimer domain model

## Product Specifications

### Elevator Pitch
A comprehensive timer service that tracks workout sessions and rest periods, running reliably in the background while providing real-time updates to the UI and notifications for rest period completion.

### Target Users
- **Primary**: Users creating custom workouts who need session timing and rest period tracking
- **Secondary**: Template workout users who want automated rest timing between sets

### Core Goals
1. **Reliability**: Timer continues running during app backgrounding and device rotation
2. **User Experience**: Clear visual feedback of workout duration and rest periods
3. **Performance**: Minimal battery impact while maintaining accurate timing

### Functional Requirements
- **FR-001**: Workout Session Timer
  - **Given**: User starts a custom workout from scratch
  - **When**: Workout session begins
  - **Then**: Session timer starts tracking total workout duration with second precision
  - **Acceptance**: Verified by integration test `test_workout_session_timer_starts_accurately`

- **FR-002**: Rest Period Timer
  - **Given**: User completes a set and configures rest period
  - **When**: Rest timer is started
  - **Then**: Countdown timer shows remaining rest time with notifications at completion
  - **Acceptance**: Verified by integration test `test_rest_timer_countdown_and_notification`

- **FR-003**: Background Persistence
  - **Given**: Timer is running and user backgrounds the app
  - **When**: App is backgrounded or device is locked
  - **Then**: Timer continues running and UI updates when app returns to foreground
  - **Acceptance**: Verified by UI test `test_timer_persists_during_background`

- **FR-004**: Session State Management
  - **Given**: Active workout session with timer running
  - **When**: User pauses, resumes, or ends workout
  - **Then**: Timer state updates accordingly and persists across app lifecycle
  - **Acceptance**: Verified by integration test `test_timer_state_management_and_persistence`

### User Stories
- **US-001**: As a user creating a custom workout, I want to see how long I've been exercising so that I can manage my time and track workout efficiency.
  - **Acceptance Criteria**:
    1. Workout timer displays in MM:SS format prominently in UI
    2. Timer updates every second while workout is active
    3. Timer pauses when workout is paused, resumes when continued
    4. Final workout duration is saved with workout record

- **US-002**: As a user taking rest between sets, I want automated rest timing with notifications so that I can optimize my workout pace without constantly checking the clock.
  - **Acceptance Criteria**:
    1. Rest timer can be started with predefined or custom durations
    2. Visual countdown shows remaining time clearly
    3. Audio/vibration notification when rest period ends
    4. Option to extend or skip rest period early

### Non-Goals
- **Advanced timer features like interval training** - Reason: Deferred to V2, focus on basic session/rest timing
- **Integration with external fitness trackers** - Reason: Out of scope for initial timer implementation
- **Complex workout analytics during session** - Reason: Analytics handled in Progress dashboard

## Technical Specifications

### System Architecture
- **Pattern**: Foreground Service with ViewModel integration, using Flow-based state emissions
- **Flow**: UI Event → ViewModel → TimerService → Notification → UI State Update
- **Security**: No additional security considerations beyond standard service permissions

### Database Design
Extension of existing RestTimer model, no schema changes required:
```kotlin
// Existing model extended with session timing
data class WorkoutSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val isPaused: Boolean = false,
    val pausedDuration: Duration = Duration.ZERO,
    val currentRestTimer: RestTimer? = null
)
```

### Service Design

#### WorkoutTimerService
```kotlin
class WorkoutTimerService : Service() {
    
    private val binder = WorkoutTimerBinder()
    private val _sessionState = MutableStateFlow<TimerSessionState>(TimerSessionState.Idle)
    val sessionState: StateFlow<TimerSessionState> = _sessionState.asStateFlow()
    
    private var sessionTimer: Timer? = null
    private var restTimer: Timer? = null
    private var currentSession: WorkoutSession? = null
    
    override fun onBind(intent: Intent): IBinder = binder
    
    fun startWorkoutSession(): String {
        val sessionId = UUID.randomUUID().toString()
        currentSession = WorkoutSession(
            id = sessionId,
            startTime = Clock.System.now()
        )
        
        startForeground(NOTIFICATION_ID, createOngoingNotification())
        startSessionTimer()
        
        _sessionState.value = TimerSessionState.ActiveWorkout(
            sessionId = sessionId,
            duration = Duration.ZERO,
            restTimer = null
        )
        
        return sessionId
    }
    
    fun pauseWorkoutSession() {
        currentSession = currentSession?.copy(isPaused = true)
        sessionTimer?.cancel()
        updateNotification()
        
        _sessionState.value = when (val current = _sessionState.value) {
            is TimerSessionState.ActiveWorkout -> current.copy(isPaused = true)
            else -> current
        }
    }
    
    fun resumeWorkoutSession() {
        currentSession = currentSession?.copy(isPaused = false)
        startSessionTimer()
        updateNotification()
        
        _sessionState.value = when (val current = _sessionState.value) {
            is TimerSessionState.ActiveWorkout -> current.copy(isPaused = false)
            else -> current
        }
    }
    
    fun startRestTimer(durationSeconds: Int) {
        restTimer?.cancel()
        
        restTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                private var remaining = durationSeconds
                
                override fun run() {
                    if (remaining <= 0) {
                        onRestTimerComplete()
                        cancel()
                        return
                    }
                    
                    _sessionState.value = when (val current = _sessionState.value) {
                        is TimerSessionState.ActiveWorkout -> current.copy(
                            restTimer = RestTimerState.Running(remaining)
                        )
                        else -> current
                    }
                    
                    remaining--
                }
            }, 0, 1000)
        }
    }
    
    fun endWorkoutSession(): WorkoutSession? {
        val session = currentSession?.copy(
            endTime = Clock.System.now()
        )
        
        sessionTimer?.cancel()
        restTimer?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        _sessionState.value = TimerSessionState.Idle
        currentSession = null
        
        return session
    }
    
    private fun startSessionTimer() {
        sessionTimer?.cancel()
        
        sessionTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    currentSession?.let { session ->
                        if (!session.isPaused) {
                            val duration = Clock.System.now() - session.startTime - session.pausedDuration
                            
                            _sessionState.value = when (val current = _sessionState.value) {
                                is TimerSessionState.ActiveWorkout -> current.copy(duration = duration)
                                else -> current
                            }
                            
                            updateNotification()
                        }
                    }
                }
            }, 0, 1000)
        }
    }
    
    private fun onRestTimerComplete() {
        // Send notification and update UI
        sendRestCompleteNotification()
        vibrateDevice()
        
        _sessionState.value = when (val current = _sessionState.value) {
            is TimerSessionState.ActiveWorkout -> current.copy(
                restTimer = RestTimerState.Completed
            )
            else -> current
        }
    }
    
    inner class WorkoutTimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }
}

sealed class TimerSessionState {
    object Idle : TimerSessionState()
    
    data class ActiveWorkout(
        val sessionId: String,
        val duration: Duration,
        val restTimer: RestTimerState? = null,
        val isPaused: Boolean = false
    ) : TimerSessionState()
}

sealed class RestTimerState {
    data class Running(val remainingSeconds: Int) : RestTimerState()
    object Completed : RestTimerState()
}
```

#### TimerServiceManager
```kotlin
@Singleton
class TimerServiceManager @Inject constructor(
    private val context: Context
) {
    private var serviceBinder: WorkoutTimerService.WorkoutTimerBinder? = null
    private val _sessionState = MutableStateFlow<TimerSessionState>(TimerSessionState.Idle)
    val sessionState: StateFlow<TimerSessionState> = _sessionState.asStateFlow()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = binder as WorkoutTimerService.WorkoutTimerBinder
            
            // Start observing service state
            serviceBinder?.getService()?.sessionState?.let { serviceState ->
                CoroutineScope(Dispatchers.Main).launch {
                    serviceState.collect { state ->
                        _sessionState.value = state
                    }
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
        }
    }
    
    fun startWorkoutSession(): String? {
        bindToService()
        return serviceBinder?.getService()?.startWorkoutSession()
    }
    
    fun pauseWorkoutSession() {
        serviceBinder?.getService()?.pauseWorkoutSession()
    }
    
    fun resumeWorkoutSession() {
        serviceBinder?.getService()?.resumeWorkoutSession()
    }
    
    fun startRestTimer(durationSeconds: Int) {
        serviceBinder?.getService()?.startRestTimer(durationSeconds)
    }
    
    fun endWorkoutSession(): WorkoutSession? {
        val session = serviceBinder?.getService()?.endWorkoutSession()
        unbindFromService()
        return session
    }
    
    private fun bindToService() {
        val intent = Intent(context, WorkoutTimerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun unbindFromService() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            // Service may already be unbound
        }
    }
}
```

#### ActiveWorkoutViewModel Integration
```kotlin
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val timerServiceManager: TimerServiceManager,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()
    
    init {
        observeTimerState()
    }
    
    private fun observeTimerState() {
        viewModelScope.launch {
            timerServiceManager.sessionState.collect { timerState ->
                _uiState.update { currentState ->
                    when (timerState) {
                        is TimerSessionState.ActiveWorkout -> currentState.copy(
                            sessionDuration = timerState.duration,
                            isSessionActive = true,
                            isPaused = timerState.isPaused,
                            restTimer = timerState.restTimer
                        )
                        TimerSessionState.Idle -> currentState.copy(
                            isSessionActive = false,
                            sessionDuration = Duration.ZERO,
                            restTimer = null
                        )
                    }
                }
            }
        }
    }
    
    fun onEvent(event: ActiveWorkoutEvent) {
        when (event) {
            ActiveWorkoutEvent.StartWorkout -> {
                val sessionId = timerServiceManager.startWorkoutSession()
                _uiState.update { it.copy(sessionId = sessionId) }
            }
            ActiveWorkoutEvent.PauseWorkout -> timerServiceManager.pauseWorkoutSession()
            ActiveWorkoutEvent.ResumeWorkout -> timerServiceManager.resumeWorkoutSession()
            is ActiveWorkoutEvent.StartRestTimer -> {
                timerServiceManager.startRestTimer(event.durationSeconds)
            }
            ActiveWorkoutEvent.EndWorkout -> {
                val session = timerServiceManager.endWorkoutSession()
                session?.let { saveWorkoutSession(it) }
            }
        }
    }
    
    private fun saveWorkoutSession(session: WorkoutSession) {
        viewModelScope.launch {
            try {
                workoutRepository.saveWorkoutSession(session)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

data class ActiveWorkoutUiState(
    val sessionId: String? = null,
    val isSessionActive: Boolean = false,
    val sessionDuration: Duration = Duration.ZERO,
    val isPaused: Boolean = false,
    val restTimer: RestTimerState? = null,
    val exercises: List<Exercise> = emptyList()
)

sealed class ActiveWorkoutEvent {
    object StartWorkout : ActiveWorkoutEvent()
    object PauseWorkout : ActiveWorkoutEvent()
    object ResumeWorkout : ActiveWorkoutEvent()
    object EndWorkout : ActiveWorkoutEvent()
    data class StartRestTimer(val durationSeconds: Int) : ActiveWorkoutEvent()
}
```

### Notification Implementation
```kotlin
class TimerNotificationManager @Inject constructor(
    private val context: Context
) {
    
    fun createOngoingWorkoutNotification(duration: Duration, isPaused: Boolean): Notification {
        return NotificationCompat.Builder(context, WORKOUT_CHANNEL_ID)
            .setContentTitle(if (isPaused) "Workout Paused" else "Workout Active")
            .setContentText("Duration: ${duration.toFormattedString()}")
            .setSmallIcon(R.drawable.ic_fitness_center)
            .setOngoing(true)
            .setUsesChronometer(!isPaused)
            .setChronometerCountDown(false)
            .setContentIntent(createWorkoutPendingIntent())
            .addAction(
                if (isPaused) R.drawable.ic_play else R.drawable.ic_pause,
                if (isPaused) "Resume" else "Pause",
                createPauseResumePendingIntent()
            )
            .addAction(
                R.drawable.ic_stop,
                "End Workout",
                createEndWorkoutPendingIntent()
            )
            .build()
    }
    
    fun createRestCompleteNotification(): Notification {
        return NotificationCompat.Builder(context, REST_CHANNEL_ID)
            .setContentTitle("Rest Period Complete")
            .setContentText("Time to start your next set!")
            .setSmallIcon(R.drawable.ic_timer)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(createWorkoutPendingIntent())
            .build()
    }
}
```

### Testing Strategy
- **Test Scenarios**:
  1. "Verify workout session timer starts accurately and updates every second"
  2. "Confirm rest timer countdown completes and sends notification"
  3. "Validate timer persists during app backgrounding and device rotation"
  4. "Test pause/resume functionality maintains accurate time tracking"
  5. "Verify service properly handles app termination and restoration"

## Implementation Plan

### Task Breakdown

#### Service Layer (SVC-XXX)
- [ ] **SVC-001**: Create WorkoutTimerService [Estimate: 6hr]
  - **Files**: `service/WorkoutTimerService.kt`
  - **Details**: Foreground service with timer management and notification support

- [ ] **SVC-002**: Create TimerServiceManager [Estimate: 4hr]
  - **Files**: `service/TimerServiceManager.kt`
  - **Details**: Service binding wrapper for ViewModel integration

- [ ] **SVC-003**: Create TimerNotificationManager [Estimate: 3hr]
  - **Files**: `service/TimerNotificationManager.kt`
  - **Details**: Notification creation and management for timer states

#### ViewModel Integration (VM-XXX)
- [ ] **VM-001**: Create ActiveWorkoutViewModel [Estimate: 4hr]
  - **Files**: `ui/workout/active/ActiveWorkoutViewModel.kt`
  - **Details**: ViewModel integration with timer service and state management

- [ ] **VM-002**: Update existing ViewModels for timer integration [Estimate: 2hr]
  - **Files**: `ui/navigation/MainNavigationViewModel.kt`
  - **Details**: Add timer state awareness to navigation components

#### UI Components (UI-XXX)
- [ ] **UI-001**: Create ActiveWorkoutScreen [Estimate: 5hr]
  - **Files**: `ui/workout/active/ActiveWorkoutScreen.kt`
  - **Details**: UI for active workout with timer display and controls

- [ ] **UI-002**: Create TimerDisplay components [Estimate: 3hr]
  - **Files**: `ui/workout/active/components/SessionTimerDisplay.kt`, `ui/workout/active/components/RestTimerDisplay.kt`
  - **Details**: Timer UI components with proper formatting and visual feedback

#### Permissions & Manifest (PERM-XXX)
- [ ] **PERM-001**: Add service permissions and declarations [Estimate: 1hr]
  - **Files**: `AndroidManifest.xml`
  - **Details**: Add foreground service permission and service declaration

- [ ] **PERM-002**: Add notification channel setup [Estimate: 1hr]
  - **Files**: `application/LiftrixApplication.kt`
  - **Details**: Create notification channels for workout and rest timers

#### Dependency Injection (DI-XXX)
- [ ] **DI-001**: Create TimerModule [Estimate: 1hr]
  - **Files**: `di/TimerModule.kt`
  - **Details**: DI bindings for timer service and related components

#### Testing (TEST-XXX)
- [ ] **TEST-001**: Service unit tests [Estimate: 4hr]
  - **Files**: `service/WorkoutTimerServiceTest.kt`
  - **Details**: Test timer accuracy, state management, and lifecycle

- [ ] **TEST-002**: ViewModel integration tests [Estimate: 3hr]
  - **Files**: `ui/workout/active/ActiveWorkoutViewModelTest.kt`
  - **Details**: Test ViewModel-service integration and state synchronization

- [ ] **TEST-003**: Background persistence tests [Estimate: 3hr]
  - **Files**: `service/TimerBackgroundTest.kt`
  - **Details**: Test timer continuation during app lifecycle changes

### Dependencies
- SVC-002 depends on SVC-001
- VM-001 depends on SVC-002
- UI-001 depends on VM-001
- UI-002 depends on UI-001
- TEST-001 depends on SVC-001, SVC-003
- TEST-002 depends on VM-001
- TEST-003 depends on SVC-001, SVC-002

## Success Metrics
- **Timer Accuracy**: ±1 second accuracy over 60-minute sessions (tested with stopwatch comparison)
- **Background Reliability**: 99% timer continuation success rate during app backgrounding (measured via analytics)
- **Battery Impact**: <5% additional battery drain during 60-minute workout (measured via battery historian)

## Timeline
**Total Effort**: 32 hours (4 developer-days, can be reduced to 3 days with parallel UI/service work)
**Critical Path**: SVC-001 → SVC-002 → VM-001 → UI-001 → Testing (minimum 2.5 days)