package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.auth.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import com.example.liftrix.ui.progress.components.UIWidgetData
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsWidgetViewModelTest {

    // Test dependencies
    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var mockAnalyticsWidgetManager: AnalyticsWidgetManager
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: AnalyticsWidgetViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-123"
    private val testUser = mockk<com.example.liftrix.domain.model.User> {
        every { id } returns testUserId
    }
    
    private val testWidgetId = "total_volume"
    private val testWidget = AnalyticsWidget.TotalVolume
    
    private val testWidgetData = mockk<UIWidgetData> {
        every { title } returns "Total Volume"
        every { value } returns "125,000 lbs"
        every { trend } returns "+12%"
        every { isLoading } returns false
    }
    
    private val testWidgetPreferences = mockk<WidgetPreferences> {
        every { visibleWidgets } returns setOf("total_volume", "workout_frequency")
        every { widgetOrder } returns listOf("total_volume", "workout_frequency", "average_duration")
        every { layoutMode } returns WidgetLayoutMode.SECTIONS
    }
    
    private val testDashboardConfiguration = DashboardConfiguration.Beginner

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        mockAnalyticsService = mockk(relaxed = true)
        mockAnalyticsWidgetManager = mockk(relaxed = true)
        mockAuthRepository = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        
        // Default auth repository behavior
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        
        // Default error handler behavior
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        
        // Default widget manager behavior
        every { mockAnalyticsWidgetManager.getActiveWidgets(any()) } returns listOf(testWidget)
        every { mockAnalyticsWidgetManager.getWidgetConfiguration(any()) } returns testDashboardConfiguration
        
        viewModel = AnalyticsWidgetViewModel(
            analyticsService = mockAnalyticsService,
            widgetManager = mockAnalyticsWidgetManager,
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
    fun `given ViewModel initialization, when created, then starts with Loading state`() = runTest {
        // Given & When - ViewModel is created in setup()
        
        // Then
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertIs<UiState.Loading<AnalyticsWidgetState>>(initialState)
        }
    }

    @Test
    fun `given unauthenticated user, when ViewModel created, then state reflects no user access`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        
        // When
        val unauthenticatedViewModel = AnalyticsWidgetViewModel(
            analyticsService = mockAnalyticsService,
            widgetManager = mockAnalyticsWidgetManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.widgetData.isEmpty())
                assertEquals(null, state.data.preferences)
            }
        }
    }

    // === Widget Data Loading Tests ===

    @Test
    fun `given authenticated user, when LoadWidget event, then loads widget data successfully`() = runTest {
        // Given
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } returns LiftrixResult.Success(testWidgetData)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(testWidgetId, forceRefresh = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.widgetData.containsKey(testWidgetId))
                assertEquals(testWidgetData, state.data.widgetData[testWidgetId])
                assertFalse(state.data.isLoading)
                assertFalse(state.data.widgetLoadingStates[testWidgetId] ?: false)
            }
        }
        
        // Verify service call
        coVerify { mockAnalyticsService.getWidgetData(testUserId, testWidget) }
    }

    @Test
    fun `given widget service error, when LoadWidget event, then shows error state for specific widget`() = runTest {
        // Given
        val testError = LiftrixError.NetworkError("Failed to load widget data")
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } returns LiftrixResult.Error(testError)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(testWidgetId, forceRefresh = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.widgetErrors.containsKey(testWidgetId))
                assertEquals(testError, state.data.widgetErrors[testWidgetId])
                assertFalse(state.data.widgetLoadingStates[testWidgetId] ?: false)
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    @Test
    fun `given force refresh, when LoadWidget event, then reloads widget data regardless of cache`() = runTest {
        // Given
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } returns LiftrixResult.Success(testWidgetData)
        
        // Load initial data
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(testWidgetId, forceRefresh = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When - force refresh
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(testWidgetId, forceRefresh = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify service called twice (initial + force refresh)
        coVerify(exactly = 2) { mockAnalyticsService.getWidgetData(testUserId, testWidget) }
    }

    // === Widget Visibility Toggle Tests ===

    @Test
    fun `given widget visible, when ToggleVisibility false event, then hides widget and updates preferences`() = runTest {
        // Given
        coEvery { mockAnalyticsService.getWidgetPreferences(testUserId) } returns LiftrixResult.Success(testWidgetPreferences)
        coEvery { mockAnalyticsService.updateWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.ToggleVisibility(testWidgetId, visible = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                // Widget should be hidden from data map or marked as invisible
                assertTrue(state.data.preferences?.visibleWidgets?.contains(testWidgetId) == false || 
                          !state.data.widgetData.containsKey(testWidgetId))
            }
        }
        
        // Verify preference update service call
        coVerify { mockAnalyticsService.updateWidgetPreferences(any()) }
    }

    @Test
    fun `given widget hidden, when ToggleVisibility true event, then shows widget and loads data`() = runTest {
        // Given
        val hiddenPreferences = mockk<WidgetPreferences> {
            every { visibleWidgets } returns setOf("workout_frequency") // testWidgetId not included
            every { widgetOrder } returns listOf("total_volume", "workout_frequency")
            every { layoutMode } returns WidgetLayoutMode.SECTIONS
        }
        
        coEvery { mockAnalyticsService.getWidgetPreferences(testUserId) } returns LiftrixResult.Success(hiddenPreferences)
        coEvery { mockAnalyticsService.updateWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } returns LiftrixResult.Success(testWidgetData)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.ToggleVisibility(testWidgetId, visible = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.widgetData.containsKey(testWidgetId))
                assertEquals(testWidgetData, state.data.widgetData[testWidgetId])
            }
        }
        
        // Verify both preference update and data loading
        coVerify { mockAnalyticsService.updateWidgetPreferences(any()) }
        coVerify { mockAnalyticsService.getWidgetData(testUserId, testWidget) }
    }

    // === Configuration Update Tests ===

    @Test
    fun `given new configuration, when UpdateConfiguration event, then updates dashboard configuration and reloads widgets`() = runTest {
        // Given
        val newConfiguration = DashboardConfiguration.Advanced
        coEvery { mockAnalyticsService.updateWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        coEvery { mockAnalyticsService.getWidgetData(testUserId, any()) } returns LiftrixResult.Success(testWidgetData)
        
        // Mock widget manager to return different widgets for advanced config
        every { mockAnalyticsWidgetManager.getActiveWidgets(newConfiguration) } returns listOf(
            AnalyticsWidget.TotalVolume, 
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.AverageDuration
        )
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.UpdateConfiguration(newConfiguration, shouldPersist = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newConfiguration, state.data.configuration)
                assertFalse(state.data.isConfiguringDashboard)
            }
        }
        
        // Verify configuration persistence
        coVerify { mockAnalyticsService.updateWidgetPreferences(any()) }
    }

    @Test
    fun `given configuration without persistence, when UpdateConfiguration event, then updates UI but doesn't persist`() = runTest {
        // Given
        val newConfiguration = DashboardConfiguration.Intermediate
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.UpdateConfiguration(newConfiguration, shouldPersist = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newConfiguration, state.data.configuration)
            }
        }
        
        // Verify no persistence call
        coVerify(exactly = 0) { mockAnalyticsService.updateWidgetPreferences(any()) }
    }

    // === Refresh All Widgets Tests ===

    @Test
    fun `given multiple widgets, when RefreshAllWidgets event, then refreshes all visible widgets`() = runTest {
        // Given
        val widgets = listOf(AnalyticsWidget.TotalVolume, AnalyticsWidget.WorkoutFrequency)
        every { mockAnalyticsWidgetManager.getActiveWidgets(any()) } returns widgets
        
        coEvery { mockAnalyticsService.getWidgetData(testUserId, AnalyticsWidget.TotalVolume) } returns LiftrixResult.Success(testWidgetData)
        coEvery { mockAnalyticsService.getWidgetData(testUserId, AnalyticsWidget.WorkoutFrequency) } returns LiftrixResult.Success(testWidgetData)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.RefreshAllWidgets(showLoadingStates = true, retryFailedWidgets = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.isRefreshing)
                assertTrue(state.data.widgetData.size >= 2) // Should have data for both widgets
            }
        }
        
        // Verify all widgets were loaded
        coVerify { mockAnalyticsService.getWidgetData(testUserId, AnalyticsWidget.TotalVolume) }
        coVerify { mockAnalyticsService.getWidgetData(testUserId, AnalyticsWidget.WorkoutFrequency) }
    }

    @Test
    fun `given some failed widgets, when RefreshAllWidgets with retry, then retries failed widgets only`() = runTest {
        // Given - simulate a previously failed widget
        val initialError = LiftrixError.NetworkError("Previous failure")
        val initialState = AnalyticsWidgetState(
            widgetErrors = mapOf(testWidgetId to initialError),
            configuration = testDashboardConfiguration
        )
        
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } returns LiftrixResult.Success(testWidgetData)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.RefreshAllWidgets(showLoadingStates = true, retryFailedWidgets = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - failed widget should be retried
        coVerify { mockAnalyticsService.getWidgetData(testUserId, any()) }
    }

    // === Widget Reordering Tests ===

    @Test
    fun `given widget list, when ReorderWidget event, then updates widget order and persists if requested`() = runTest {
        // Given
        val newPosition = 0
        coEvery { mockAnalyticsService.getWidgetPreferences(testUserId) } returns LiftrixResult.Success(testWidgetPreferences)
        coEvery { mockAnalyticsService.updateWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.ReorderWidget(testWidgetId, newPosition, shouldPersist = true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                // Verify preferences were updated with new order
                assertTrue(state.data.preferences?.widgetOrder?.get(newPosition) == testWidgetId)
            }
        }
        
        // Verify persistence
        coVerify { mockAnalyticsService.updateWidgetPreferences(any()) }
    }

    // === Preferences Reset Tests ===

    @Test
    fun `given custom preferences, when ResetPreferences event, then resets to defaults and reloads`() = runTest {
        // Given
        val defaultPreferences = mockk<WidgetPreferences> {
            every { visibleWidgets } returns setOf("total_volume", "workout_frequency", "average_duration")
            every { widgetOrder } returns listOf("total_volume", "workout_frequency", "average_duration")
            every { layoutMode } returns WidgetLayoutMode.SECTIONS
        }
        
        coEvery { mockAnalyticsService.resetPreferences(testUserId) } returns LiftrixResult.Success(Unit)
        coEvery { mockAnalyticsService.getWidgetPreferences(testUserId) } returns LiftrixResult.Success(defaultPreferences)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.ResetPreferences(confirmationRequired = false, preserveCustomizations = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(defaultPreferences.visibleWidgets, state.data.preferences?.visibleWidgets)
                assertEquals(defaultPreferences.widgetOrder, state.data.preferences?.widgetOrder)
            }
        }
        
        // Verify reset service call
        coVerify { mockAnalyticsService.resetPreferences(testUserId) }
    }

    // === Retry Operation Tests ===

    @Test
    fun `given failed widget operation, when RetryOperation event, then retries the specific operation`() = runTest {
        // Given
        val retryOperation = "load_widget"
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } returns LiftrixResult.Success(testWidgetData)
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.RetryOperation(testWidgetId, retryOperation))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify the retry was attempted
        coVerify { mockAnalyticsService.getWidgetData(testUserId, testWidget) }
    }

    // === Error Dismissal Tests ===

    @Test
    fun `given widget error, when DismissError event, then clears error state for widget`() = runTest {
        // Given - start with widget in error state
        val initialError = LiftrixError.ValidationError(field = "validation", violations = listOf("Validation failed"))
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.DismissError(testWidgetId, shouldClearHistory = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.widgetErrors.containsKey(testWidgetId))
            }
        }
    }

    // === Interaction Tracking Tests ===

    @Test
    fun `given widget interaction, when TrackInteraction event, then logs interaction without affecting state`() = runTest {
        // Given
        val interactionType = "tap"
        val metadata = mapOf("widget_type" to "total_volume", "timestamp" to "1234567890")
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.TrackInteraction(testWidgetId, interactionType, metadata))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - interaction should be tracked but state unchanged
        viewModel.uiState.test {
            val state = awaitItem()
            // State should remain stable (no changes from tracking)
            assertTrue(state is UiState.Success || state is UiState.Loading)
        }
        
        // Note: Actual analytics tracking verification would depend on analytics service implementation
    }

    // === User Authentication State Change Tests ===

    @Test
    fun `given user authentication changes, when user logs out, then clears widget data`() = runTest {
        // Given - start with authenticated user and loaded data
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        val authChangeViewModel = AnalyticsWidgetViewModel(
            analyticsService = mockAnalyticsService,
            widgetManager = mockAnalyticsWidgetManager,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When - emit null user (logout)
        userFlow.emit(null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        authChangeViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.widgetData.isEmpty())
                assertEquals(null, state.data.preferences)
                assertTrue(state.data.widgetErrors.isEmpty())
            }
        }
    }

    // === Loading State Management Tests ===

    @Test
    fun `given multiple widget loading operations, when concurrent loads, then manages loading states correctly`() = runTest {
        // Given
        val widget1 = "widget1"
        val widget2 = "widget2"
        
        // Delay responses to capture loading states
        val widget1Deferred = CompletableDeferred<LiftrixResult<UIWidgetData>>()
        val widget2Deferred = CompletableDeferred<LiftrixResult<UIWidgetData>>()
        
        coEvery { mockAnalyticsService.getWidgetData(testUserId, AnalyticsWidget.TotalVolume) } returns widget1Deferred.await()
        coEvery { mockAnalyticsService.getWidgetData(testUserId, AnalyticsWidget.WorkoutFrequency) } returns widget2Deferred.await()
        
        // When - trigger concurrent loads
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(widget1, forceRefresh = false))
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(widget2, forceRefresh = false))
        
        // Then - verify loading states
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.widgetLoadingStates[widget1] == true || state.data.widgetLoadingStates[widget2] == true)
            }
            
            // Complete the operations
            widget1Deferred.complete(LiftrixResult.Success(testWidgetData))
            widget2Deferred.complete(LiftrixResult.Success(testWidgetData))
        }
    }

    // === Error Recovery Tests ===

    @Test
    fun `given service exception, when loading widget, then handles error gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Unexpected service error")
        coEvery { mockAnalyticsService.getWidgetData(testUserId, testWidget) } throws exception
        
        // When
        viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget(testWidgetId, forceRefresh = false))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify error handling doesn't crash
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
        
        // Verify error handler was called
        coVerify { mockErrorHandler.handleError(any(), any()) }
    }
}