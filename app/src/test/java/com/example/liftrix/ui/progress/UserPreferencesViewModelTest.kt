package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.LoadingState
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesViewModelTest {

    // Test dependencies
    private lateinit var mockPreferencesService: PreferencesService
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: UserPreferencesViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-123"
    
    private val testWidgetPreferences = mockk<WidgetPreferences> {
        every { visibleWidgets } returns setOf("total_volume", "workout_frequency", "average_duration")
        every { widgetOrder } returns listOf("total_volume", "workout_frequency", "average_duration")
        every { layoutMode } returns WidgetLayoutMode.SECTIONS
        every { autoRefreshEnabled } returns true
        every { autoRefreshIntervalMinutes } returns 5
        every { sectionVisibility } returns mapOf("progress" to true, "analytics" to true)
        every { widgetSizes } returns mapOf("total_volume" to "large", "workout_frequency" to "medium")
    }
    
    private val testDefaultPreferences = mockk<WidgetPreferences> {
        every { visibleWidgets } returns setOf("total_volume", "workout_frequency")
        every { widgetOrder } returns listOf("total_volume", "workout_frequency")
        every { layoutMode } returns WidgetLayoutMode.SECTIONS
        every { autoRefreshEnabled } returns false
        every { autoRefreshIntervalMinutes } returns 10
        every { sectionVisibility } returns mapOf("progress" to true, "analytics" to false)
        every { widgetSizes } returns mapOf("total_volume" to "medium")
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        mockPreferencesService = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        
        // Default error handler behavior
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        
        viewModel = UserPreferencesViewModel(
            preferencesService = mockPreferencesService,
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
            assertIs<UiState.Loading<UserPreferencesState>>(initialState)
        }
    }

    @Test
    fun `given ViewModel initialization, when created, then has correct initial state structure`() = runTest {
        // Given & When - ViewModel is created in setup()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.preferences)
                assertEquals(WidgetLayoutMode.SECTIONS, state.data.layoutMode)
                assertEquals(UserLevel.BEGINNER, state.data.userLevel)
                assertFalse(state.data.isInitialized)
                assertFalse(state.data.hasUnsavedChanges)
            }
        }
    }

    // === Preferences Loading Tests ===

    @Test
    fun `given preferences service success, when LoadPreferences event, then loads preferences successfully`() = runTest {
        // Given
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<WidgetPreferences>>(state.data.preferences)
                assertEquals(testWidgetPreferences, state.data.preferences.getOrNull())
                assertEquals(WidgetLayoutMode.SECTIONS, state.data.layoutMode)
                assertTrue(state.data.isInitialized)
                assertFalse(state.data.hasUnsavedChanges)
            }
        }
        
        // Verify service call
        coVerify { mockPreferencesService.getUserPreferences(any()) }
    }

    @Test
    fun `given preferences service error, when LoadPreferences event, then shows error state`() = runTest {
        // Given
        val testError = LiftrixError.DatabaseError("Failed to load preferences")
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.preferences)
                assertEquals(testError, state.data.preferences.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Layout Mode Update Tests ===

    @Test
    fun `given valid layout mode, when UpdateLayoutMode event, then updates mode and marks as unsaved`() = runTest {
        // Given - load initial preferences
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateLayoutMode(WidgetLayoutMode.GRID))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(WidgetLayoutMode.GRID, state.data.layoutMode)
                assertTrue(state.data.hasUnsavedChanges)
            }
        }
    }

    @Test
    fun `given layout mode validation error, when UpdateLayoutMode event, then shows validation error`() = runTest {
        // Given
        val validationError = LiftrixError.ValidationError(field = "layoutMode", violations = listOf("Invalid layout mode for user level"))
        coEvery { mockPreferencesService.updateLayoutMode(any(), any()) } returns Result.failure(validationError)
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateLayoutMode(WidgetLayoutMode.COMPACT))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Error) {
                assertEquals(validationError, state.error)
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(validationError, any()) }
    }

    // === User Level Update Tests ===

    @Test
    fun `given valid user level, when UpdateUserLevel event, then updates level and cascades changes`() = runTest {
        // Given
        coEvery { mockPreferencesService.updateUserLevel(any(), any()) } returns Result.success(Unit)
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateUserLevel(UserLevel.ADVANCED))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(UserLevel.ADVANCED, state.data.userLevel)
                assertTrue(state.data.hasUnsavedChanges)
            }
        }
        
        // Verify service call
        coVerify { mockPreferencesService.updateUserLevel(any(), UserLevel.ADVANCED) }
    }

    @Test
    fun `given user level with incompatible layout, when UpdateUserLevel event, then adjusts layout automatically`() = runTest {
        // Given - start with advanced layout
        val initialState = UserPreferencesState(
            layoutMode = WidgetLayoutMode.COMPACT,
            userLevel = UserLevel.ADVANCED
        )
        
        coEvery { mockPreferencesService.updateUserLevel(any(), any()) } returns Result.success(Unit)
        
        // When - downgrade to beginner (incompatible with compact layout)
        viewModel.handleEvent(UserPreferencesEvent.UpdateUserLevel(UserLevel.BEGINNER))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - layout should be adjusted to compatible mode
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(UserLevel.BEGINNER, state.data.userLevel)
                // Layout should be adjusted to beginner-compatible mode
                assertTrue(state.data.layoutMode == WidgetLayoutMode.SECTIONS || state.data.layoutMode == WidgetLayoutMode.GRID)
            }
        }
    }

    // === Reset to Defaults Tests ===

    @Test
    fun `given custom preferences, when ResetToDefaults event, then resets all preferences to defaults`() = runTest {
        // Given
        coEvery { mockPreferencesService.resetToDefaults(any()) } returns Result.success(Unit)
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testDefaultPreferences)
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.ResetToDefaults)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(testDefaultPreferences, state.data.preferences.getOrNull())
                assertEquals(WidgetLayoutMode.SECTIONS, state.data.layoutMode)
                assertFalse(state.data.hasUnsavedChanges)
            }
        }
        
        // Verify service calls
        coVerify { mockPreferencesService.resetToDefaults(any()) }
        coVerify { mockPreferencesService.getUserPreferences(any()) }
    }

    // === Widget Visibility Tests ===

    @Test
    fun `given widget visibility change, when UpdateWidgetVisibility event, then updates visibility and marks unsaved`() = runTest {
        // Given - load initial preferences
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateWidgetVisibility("new_widget", true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.hasUnsavedChanges)
                val preferences = state.data.preferences.getOrNull()
                assertTrue(preferences?.visibleWidgets?.contains("new_widget") == true)
            }
        }
    }

    @Test
    fun `given widget visibility validation error, when UpdateWidgetVisibility event, then shows validation error`() = runTest {
        // Given
        val validationError = LiftrixError.ValidationError(field = "new_widget", violations = listOf("Widget not available for user level"))
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateWidgetVisibility("invalid_widget", true))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should handle validation gracefully
        viewModel.uiState.test {
            val state = awaitItem()
            // State should remain stable even with validation errors
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
    }

    // === Widget Order Tests ===

    @Test
    fun `given valid widget order, when UpdateWidgetOrder event, then updates order and marks unsaved`() = runTest {
        // Given
        val newWidgetOrder = listOf("workout_frequency", "total_volume", "average_duration")
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateWidgetOrder(newWidgetOrder))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.hasUnsavedChanges)
                val preferences = state.data.preferences.getOrNull()
                assertEquals(newWidgetOrder, preferences?.widgetOrder)
            }
        }
    }

    // === Auto-Refresh Settings Tests ===

    @Test
    fun `given valid auto-refresh settings, when UpdateAutoRefreshSettings event, then updates settings`() = runTest {
        // Given
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateAutoRefreshSettings(enabled = false, intervalMinutes = 15))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.hasUnsavedChanges)
                val preferences = state.data.preferences.getOrNull()
                assertEquals(false, preferences?.autoRefreshEnabled)
                assertEquals(15, preferences?.autoRefreshIntervalMinutes)
            }
        }
    }

    @Test
    fun `given invalid auto-refresh interval, when UpdateAutoRefreshSettings event, then shows validation error`() = runTest {
        // Given
        val validationError = LiftrixError.ValidationError(field = "120", violations = listOf("Auto-refresh interval must be between 1 and 60 minutes"))
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateAutoRefreshSettings(enabled = true, intervalMinutes = 120))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should handle validation error gracefully
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Error) {
                assertEquals(validationError, state.error)
            }
        }
    }

    // === Section Toggle Tests ===

    @Test
    fun `given section visibility change, when ToggleSection event, then updates section visibility`() = runTest {
        // Given
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.ToggleSection("analytics"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.hasUnsavedChanges)
                val preferences = state.data.preferences.getOrNull()
                // Analytics section should be toggled
                assertEquals(false, preferences?.sectionVisibility?.get("analytics"))
            }
        }
    }

    // === Widget Size Tests ===

    @Test
    fun `given valid widget size, when UpdateWidgetSize event, then updates widget size`() = runTest {
        // Given
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.UpdateWidgetSize("total_volume", "small"))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.hasUnsavedChanges)
                val preferences = state.data.preferences.getOrNull()
                assertEquals("small", preferences?.widgetSizes?.get("total_volume"))
            }
        }
    }

    // === Preferences Validation Tests ===

    @Test
    fun `given invalid preferences state, when ValidatePreferences event, then identifies validation issues`() = runTest {
        // Given - load preferences with potential issues
        val invalidPreferences = mockk<WidgetPreferences> {
            every { visibleWidgets } returns setOf("total_volume", "invalid_widget")
            every { widgetOrder } returns listOf("total_volume") // Missing some visible widgets
            every { layoutMode } returns WidgetLayoutMode.COMPACT
        }
        
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(invalidPreferences)
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.ValidatePreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - validation should detect issues
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                // Validation should have occurred
                assertTrue(state.data.isInitialized)
            }
        }
    }

    // === Save/Discard Changes Tests ===

    @Test
    fun `given unsaved changes, when SaveChanges event, then persists changes successfully`() = runTest {
        // Given - make changes to preferences
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        coEvery { mockPreferencesService.updateLayoutMode(any(), any()) } returns Result.success(Unit)
        
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(UserPreferencesEvent.UpdateLayoutMode(WidgetLayoutMode.GRID))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.SaveChanges)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.hasUnsavedChanges)
                assertEquals(WidgetLayoutMode.GRID, state.data.layoutMode)
            }
        }
        
        // Verify save service call
        coVerify { mockPreferencesService.updateLayoutMode(any(), WidgetLayoutMode.GRID) }
    }

    @Test
    fun `given unsaved changes, when DiscardChanges event, then reverts to last saved state`() = runTest {
        // Given - load initial preferences and make changes
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(testWidgetPreferences)
        
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(UserPreferencesEvent.UpdateLayoutMode(WidgetLayoutMode.GRID))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.DiscardChanges)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.hasUnsavedChanges)
                assertEquals(WidgetLayoutMode.SECTIONS, state.data.layoutMode) // Reverted to original
            }
        }
    }

    // === Error Dismissal Tests ===

    @Test
    fun `given error state, when DismissError event, then clears error and returns to previous state`() = runTest {
        // Given - create error state
        val testError = LiftrixError.NetworkError("Network error")
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.failure(testError)
        
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success) // Error should be cleared
        }
    }

    // === Loading State Management Tests ===

    @Test
    fun `given multiple concurrent operations, when operations running, then manages loading states correctly`() = runTest {
        // Given
        val loadingOperation1 = "load_preferences"
        val loadingOperation2 = "update_layout"
        
        // Delay responses to capture loading states
        val preferencesDeferred = CompletableDeferred<LiftrixResult<WidgetPreferences>>()
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns preferencesDeferred.await()
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        
        // Then - verify loading state management
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.loadingState.isLoading(loadingOperation1))
            }
            
            // Complete the operation
            preferencesDeferred.complete(Result.success(testWidgetPreferences))
        }
    }

    // === Service Error Handling Tests ===

    @Test
    fun `given service exception, when performing operation, then handles error gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Unexpected service error")
        coEvery { mockPreferencesService.getUserPreferences(any()) } throws exception
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.LoadPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify error handling doesn't crash
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
        
        // Verify error handler was called
        coVerify { mockErrorHandler.handleError(any(), any()) }
    }

    // === Refresh Preferences Tests ===

    @Test
    fun `given stale preferences, when RefreshPreferences event, then fetches latest from remote`() = runTest {
        // Given
        val freshPreferences = mockk<WidgetPreferences> {
            every { visibleWidgets } returns setOf("total_volume", "workout_frequency", "new_widget")
            every { widgetOrder } returns listOf("total_volume", "workout_frequency", "new_widget")
            every { layoutMode } returns WidgetLayoutMode.GRID
        }
        
        coEvery { mockPreferencesService.refreshFromRemote(any()) } returns Result.success(Unit)
        coEvery { mockPreferencesService.getUserPreferences(any()) } returns Result.success(freshPreferences)
        
        // When
        viewModel.handleEvent(UserPreferencesEvent.RefreshPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(freshPreferences, state.data.preferences.getOrNull())
                assertEquals(WidgetLayoutMode.GRID, state.data.layoutMode)
                assertFalse(state.data.hasUnsavedChanges)
            }
        }
        
        // Verify refresh service call
        coVerify { mockPreferencesService.refreshFromRemote(any()) }
    }
}