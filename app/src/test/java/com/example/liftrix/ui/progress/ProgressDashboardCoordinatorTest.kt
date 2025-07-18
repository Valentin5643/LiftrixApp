package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressDashboardCoordinatorTest {

    // Test dependencies
    private lateinit var mockSessionManager: UnifiedWorkoutSessionManager
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var coordinator: ProgressDashboardCoordinator

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-456"
    private val testUser = mockk<User> {
        every { id } returns testUserId
        every { email } returns "test@example.com"
        every { displayName } returns "Test User"
    }
    
    private val testWorkoutId = WorkoutId("workout-789")
    private val testSession = mockk<UnifiedWorkoutSession> {
        every { id } returns testWorkoutId
        every { isActive() } returns true
        every { userId } returns testUserId
    }
    
    private val testInactiveSession = mockk<UnifiedWorkoutSession> {
        every { id } returns testWorkoutId
        every { isActive() } returns false
        every { userId } returns testUserId
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        mockSessionManager = mockk(relaxed = true)
        mockAuthRepository = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        
        // Default auth repository behavior
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        
        // Default session manager behavior
        every { mockSessionManager.currentSession } returns flowOf(null)
        
        // Default error handler behavior
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        
        coordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // === Initial State Tests ===

    @Test
    fun `given coordinator initialization, when created, then starts with Loading state`() = runTest {
        // Given & When - coordinator is created in setup()
        
        // Then
        coordinator.uiState.test {
            val initialState = awaitItem()
            assertIs<UiState.Loading<CoordinatorState>>(initialState)
        }
    }

    @Test
    fun `given coordinator initialization, when created, then initializes with correct default state structure`() = runTest {
        // Given & When - coordinator is created in setup()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.isActive)
                assertFalse(state.data.realtimeUpdates)
                assertFalse(state.data.sessionActive)
                assertNull(state.data.currentSessionId)
                assertTrue(state.data.networkConnected)
                assertNull(state.data.globalError)
                assertTrue(state.data.refreshingViewModels.isEmpty())
            }
        }
    }

    // === Authentication State Coordination Tests ===

    @Test
    fun `given authenticated user, when auth state changes, then updates coordinator state and broadcasts event`() = runTest {
        // Given
        val userFlow = MutableSharedFlow<User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        val authCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When
        userFlow.emit(testUser)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        authCoordinator.coordinatorEvents.test {
            val event = awaitItem()
            assertIs<CoordinatorEvent.UserAuthChanged>(event)
            assertEquals(testUserId, event.userId)
        }
        
        authCoordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<User>>(state.data.currentUser)
                assertEquals(testUser, state.data.currentUser.getOrNull())
            }
        }
    }

    @Test
    fun `given user logout, when auth state changes to null, then clears user state and broadcasts logout event`() = runTest {
        // Given
        val userFlow = MutableSharedFlow<User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        val authCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // Start with authenticated user
        userFlow.emit(testUser)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When - user logs out
        userFlow.emit(null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        authCoordinator.coordinatorEvents.test {
            // Skip initial auth event
            skipItems(1)
            val logoutEvent = awaitItem()
            assertIs<CoordinatorEvent.UserAuthChanged>(logoutEvent)
            assertNull(logoutEvent.userId)
        }
        
        authCoordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.currentUser)
            }
        }
    }

    // === Workout Session Coordination Tests ===

    @Test
    fun `given workout session starts, when session state changes, then updates coordinator state and broadcasts session event`() = runTest {
        // Given
        val sessionFlow = MutableSharedFlow<UnifiedWorkoutSession?>()
        every { mockSessionManager.currentSession } returns sessionFlow
        
        val sessionCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When
        sessionFlow.emit(testSession)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        sessionCoordinator.coordinatorEvents.test {
            val event = awaitItem()
            assertIs<CoordinatorEvent.SessionStateChanged>(event)
            assertTrue(event.sessionActive)
            assertEquals(testWorkoutId.value, event.sessionId)
        }
        
        sessionCoordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.sessionActive)
                assertEquals(testWorkoutId.value, state.data.currentSessionId)
            }
        }
    }

    @Test
    fun `given active session ends, when session becomes inactive, then updates session state and broadcasts end event`() = runTest {
        // Given
        val sessionFlow = MutableSharedFlow<UnifiedWorkoutSession?>()
        every { mockSessionManager.currentSession } returns sessionFlow
        
        val sessionCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // Start with active session
        sessionFlow.emit(testSession)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When - session becomes inactive
        sessionFlow.emit(testInactiveSession)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        sessionCoordinator.coordinatorEvents.test {
            // Skip initial session start event
            skipItems(1)
            val endEvent = awaitItem()
            assertIs<CoordinatorEvent.SessionStateChanged>(endEvent)
            assertFalse(endEvent.sessionActive)
        }
    }

    // === Workout Completion Coordination Tests ===

    @Test
    fun `given workout completion event, when WorkoutCompleted event handled, then coordinates refresh and broadcasts events`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.WorkoutCompleted("test-workout-123"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val completionEvent = awaitItem()
            assertIs<CoordinatorEvent.WorkoutCompleted>(completionEvent)
            assertEquals("test-workout-123", completionEvent.workoutId)
            
            val refreshEvent = awaitItem()
            assertIs<CoordinatorEvent.RefreshAllData>(refreshEvent)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertNotNull(state.data.lastWorkoutCompletion)
            }
        }
    }

    // === Data Refresh Coordination Tests ===

    @Test
    fun `given RefreshAllData event, when handled, then coordinates ViewModels refresh and updates state`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.RefreshAllData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val refreshEvent = awaitItem()
            assertIs<CoordinatorEvent.RefreshAllData>(refreshEvent)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.refreshingViewModels.contains("ProgressChartsViewModel"))
                assertTrue(state.data.refreshingViewModels.contains("AnalyticsWidgetViewModel"))
                assertTrue(state.data.refreshingViewModels.contains("ProgressSummaryViewModel"))
                assertTrue(state.data.refreshingViewModels.contains("CalorieTrackingViewModel"))
                assertNotNull(state.data.lastGlobalRefresh)
            }
        }
    }

    @Test
    fun `given RefreshSpecificData event, when handled, then refreshes only specified data types`() = runTest {
        // Given
        val specificDataTypes = setOf("charts", "widgets")
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.RefreshSpecificData(specificDataTypes))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val refreshEvent = awaitItem()
            assertIs<CoordinatorEvent.RefreshSpecificData>(refreshEvent)
            assertEquals(specificDataTypes, refreshEvent.dataTypes)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(specificDataTypes, state.data.refreshingViewModels)
            }
        }
    }

    // === Real-time Updates Coordination Tests ===

    @Test
    fun `given ToggleRealtimeUpdates event, when enabling real-time updates, then updates state and broadcasts toggle event`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.ToggleRealtimeUpdates(enabled = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val toggleEvent = awaitItem()
            assertIs<CoordinatorEvent.ToggleRealtimeUpdates>(toggleEvent)
            assertTrue(toggleEvent.enabled)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.realtimeUpdates)
            }
        }
    }

    @Test
    fun `given real-time updates enabled, when disabling, then updates state and broadcasts disable event`() = runTest {
        // Given - enable real-time updates first
        testDispatcher.scheduler.advanceUntilIdle()
        coordinator.handleEvent(CoordinatorEvent.ToggleRealtimeUpdates(enabled = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.ToggleRealtimeUpdates(enabled = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            // Skip initial enable event
            skipItems(1)
            val disableEvent = awaitItem()
            assertIs<CoordinatorEvent.ToggleRealtimeUpdates>(disableEvent)
            assertFalse(disableEvent.enabled)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.realtimeUpdates)
            }
        }
    }

    // === Error Coordination Tests ===

    @Test
    fun `given BroadcastError event, when handled, then updates error state and broadcasts to affected ViewModels`() = runTest {
        // Given
        val testError = "Network connection failed"
        val affectedViewModels = listOf("ProgressChartsViewModel", "AnalyticsWidgetViewModel")
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.BroadcastError(testError, affectedViewModels))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val errorEvent = awaitItem()
            assertIs<CoordinatorEvent.BroadcastError>(errorEvent)
            assertEquals(testError, errorEvent.error)
            assertEquals(affectedViewModels, errorEvent.affectedViewModels)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(testError, state.data.globalError)
            }
        }
    }

    @Test
    fun `given global error state, when clearGlobalError called, then clears error state`() = runTest {
        // Given - set an error first
        testDispatcher.scheduler.advanceUntilIdle()
        coordinator.handleEvent(CoordinatorEvent.BroadcastError("Test error"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.clearGlobalError()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertNull(state.data.globalError)
            }
        }
    }

    // === Network Connectivity Coordination Tests ===

    @Test
    fun `given NetworkConnectivityChanged event, when network disconnects, then updates connectivity state and broadcasts event`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.NetworkConnectivityChanged(isConnected = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val connectivityEvent = awaitItem()
            assertIs<CoordinatorEvent.NetworkConnectivityChanged>(connectivityEvent)
            assertFalse(connectivityEvent.isConnected)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.networkConnected)
            }
        }
    }

    @Test
    fun `given network disconnected, when network reconnects, then updates connectivity state and broadcasts reconnection event`() = runTest {
        // Given - start with disconnected network
        testDispatcher.scheduler.advanceUntilIdle()
        coordinator.handleEvent(CoordinatorEvent.NetworkConnectivityChanged(isConnected = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.NetworkConnectivityChanged(isConnected = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            // Skip initial disconnect event
            skipItems(1)
            val reconnectEvent = awaitItem()
            assertIs<CoordinatorEvent.NetworkConnectivityChanged>(reconnectEvent)
            assertTrue(reconnectEvent.isConnected)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.networkConnected)
            }
        }
    }

    // === Preferences Coordination Tests ===

    @Test
    fun `given PreferencesChanged event, when handled, then updates preferences and broadcasts change event`() = runTest {
        // Given
        val preferencesUpdate = mapOf(
            "theme" to "dark",
            "refresh_interval" to 30,
            "auto_sync" to true
        )
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.PreferencesChanged(preferencesUpdate))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val preferencesEvent = awaitItem()
            assertIs<CoordinatorEvent.PreferencesChanged>(preferencesEvent)
            assertEquals(preferencesUpdate, preferencesEvent.preferencesChanged)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.coordinatorPreferences.containsKey("theme"))
                assertEquals("dark", state.data.coordinatorPreferences["theme"])
            }
        }
    }

    // === Cleanup Coordination Tests ===

    @Test
    fun `given CleanupCoordinator event, when handled, then deactivates coordinator and broadcasts cleanup event`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.handleEvent(CoordinatorEvent.CleanupCoordinator)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val cleanupEvent = awaitItem()
            assertIs<CoordinatorEvent.CleanupCoordinator>(cleanupEvent)
        }
        
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.isActive)
                assertFalse(state.data.realtimeUpdates)
                assertTrue(state.data.refreshingViewModels.isEmpty())
                assertEquals(0, state.data.connectedViewModels)
            }
        }
    }

    // === ViewModel Refresh Management Tests ===

    @Test
    fun `given ViewModel refresh completion, when reportViewModelRefreshComplete called, then removes ViewModel from refreshing set`() = runTest {
        // Given - start a global refresh
        testDispatcher.scheduler.advanceUntilIdle()
        coordinator.handleEvent(CoordinatorEvent.RefreshAllData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.reportViewModelRefreshComplete("ProgressChartsViewModel")
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.refreshingViewModels.contains("ProgressChartsViewModel"))
                // Other ViewModels should still be refreshing
                assertTrue(state.data.refreshingViewModels.contains("AnalyticsWidgetViewModel"))
            }
        }
    }

    // === State Utility Methods Tests ===

    @Test
    fun `given coordinator active and user authenticated, when canProcessEvents called, then returns true`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When & Then
        assertTrue(coordinator.canProcessEvents())
    }

    @Test
    fun `given unauthenticated user, when canProcessEvents called, then returns false`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        val unauthCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When & Then
        assertFalse(unauthCoordinator.canProcessEvents())
    }

    // === Error Handling Tests ===

    @Test
    fun `given authentication error, when observing auth state, then handles error gracefully and broadcasts error event`() = runTest {
        // Given
        val authError = RuntimeException("Authentication service unavailable")
        every { mockAuthRepository.currentUser } returns kotlinx.coroutines.flow.flow {
            throw authError
        }
        
        // When
        val errorCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should not crash and should call error handler
        coVerify { mockErrorHandler.handleError(any<LiftrixError.AuthenticationError>(), any()) }
    }

    @Test
    fun `given session manager error, when observing session state, then handles error gracefully`() = runTest {
        // Given
        val sessionError = RuntimeException("Session manager connection failed")
        every { mockSessionManager.currentSession } returns kotlinx.coroutines.flow.flow {
            throw sessionError
        }
        
        // When
        val errorCoordinator = ProgressDashboardCoordinator(
            sessionManager = mockSessionManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should not crash and should call error handler
        coVerify { mockErrorHandler.handleError(any<LiftrixError.UnknownError>(), any()) }
    }

    // === Event Queue Processing Tests ===

    @Test
    fun `given multiple events triggered rapidly, when processed through event queue, then handles all events sequentially`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When - trigger multiple events rapidly
        coordinator.handleEvent(CoordinatorEvent.ToggleRealtimeUpdates(true))
        coordinator.handleEvent(CoordinatorEvent.WorkoutCompleted("workout-1"))
        coordinator.handleEvent(CoordinatorEvent.NetworkConnectivityChanged(false))
        coordinator.handleEvent(CoordinatorEvent.RefreshAllData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - all events should be processed and reflected in state
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.realtimeUpdates) // From ToggleRealtimeUpdates
                assertNotNull(state.data.lastWorkoutCompletion) // From WorkoutCompleted
                assertFalse(state.data.networkConnected) // From NetworkConnectivityChanged
                assertNotNull(state.data.lastGlobalRefresh) // From RefreshAllData
            }
        }
    }

    // === Coordinator State Checks Tests ===

    @Test
    fun `given coordinator state, when checking state helper methods, then returns correct values`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        coordinator.handleEvent(CoordinatorEvent.ToggleRealtimeUpdates(true))
        coordinator.handleEvent(CoordinatorEvent.WorkoutCompleted("test-workout"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When & Then
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.canProcessEvents())
                assertTrue(state.data.shouldEnableRealtimeUpdates())
                assertFalse(state.data.hasGlobalError())
                assertEquals(testUserId, state.data.getCurrentUserId())
                assertNotNull(state.data.getTimeSinceLastWorkoutCompletion())
            }
        }
    }

    // === Performance and Concurrency Tests ===

    @Test
    fun `given concurrent event processing, when multiple ViewModels report completion simultaneously, then handles all completions correctly`() = runTest {
        // Given - start global refresh
        testDispatcher.scheduler.advanceUntilIdle()
        coordinator.handleEvent(CoordinatorEvent.RefreshAllData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When - multiple ViewModels report completion concurrently
        coordinator.reportViewModelRefreshComplete("ProgressChartsViewModel")
        coordinator.reportViewModelRefreshComplete("AnalyticsWidgetViewModel")
        coordinator.reportViewModelRefreshComplete("ProgressSummaryViewModel")
        coordinator.reportViewModelRefreshComplete("CalorieTrackingViewModel")
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - all ViewModels should be removed from refreshing set
        coordinator.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.refreshingViewModels.isEmpty())
            }
        }
    }

    // === Resource Management Tests ===

    @Test
    fun `given coordinator lifecycle, when onCleared called, then triggers cleanup process`() = runTest {
        // Given
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        coordinator.onCleared()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        coordinator.coordinatorEvents.test {
            val cleanupEvent = awaitItem()
            assertIs<CoordinatorEvent.CleanupCoordinator>(cleanupEvent)
        }
    }
}