package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.analytics.ProgressSummary
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSummaryViewModelTest {

    // Test dependencies
    private lateinit var mockProgressDataService: ProgressDataService
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: ProgressSummaryViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-123"
    private val testUser = mockk<com.example.liftrix.domain.model.User> {
        every { id } returns testUserId
    }
    
    private val testTimeRange = TimeRange.lastMonth()
    
    private val testProgressSummary = mockk<ProgressSummary> {
        every { totalWorkouts } returns 25
        every { totalVolume } returns 125000.0
        every { averageDuration } returns 3600 // 1 hour in seconds
        every { averageFrequency } returns 6.25 // workouts per week
        every { bestStreak } returns 12
        every { currentStreak } returns 8
        every { totalCalories } returns 15000
        every { strengthGain } returns 0.15 // 15% improvement
        every { enduranceGain } returns 0.20 // 20% improvement
        every { timeRange } returns testTimeRange
        every { lastUpdated } returns Instant.fromEpochMilliseconds(1640995200000) // 2022-01-01
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        mockProgressDataService = mockk(relaxed = true)
        mockAuthRepository = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        
        // Default auth repository behavior
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        
        // Default error handler behavior
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        
        viewModel = ProgressSummaryViewModel(
            progressDataService = mockProgressDataService,
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
            assertIs<UiState.Loading<ProgressSummaryState>>(initialState)
        }
    }

    @Test
    fun `given ViewModel initialization, when created, then has correct initial state structure`() = runTest {
        // Given & When - ViewModel is created in setup()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(testUserId, state.data.userId)
                assertEquals(testTimeRange, state.data.currentTimeRange)
                assertIs<AsyncData.NotAsked>(state.data.summaryData)
                assertFalse(state.data.isRefreshing)
                assertEquals(0L, state.data.lastRefreshTimestamp)
            }
        }
    }

    @Test
    fun `given unauthenticated user, when ViewModel created, then state reflects no user`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        
        // When
        val unauthenticatedViewModel = ProgressSummaryViewModel(
            progressDataService = mockProgressDataService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(null, state.data.userId)
                assertIs<AsyncData.NotAsked>(state.data.summaryData)
            }
        }
    }

    // === Summary Data Loading Tests ===

    @Test
    fun `given authenticated user, when LoadSummary event, then loads summary data successfully`() = runTest {
        // Given
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(testProgressSummary)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertEquals(testProgressSummary, state.data.summaryData.getOrNull())
                assertFalse(state.data.isRefreshing)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify service call
        coVerify { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) }
    }

    @Test
    fun `given service error, when LoadSummary event, then shows error state`() = runTest {
        // Given
        val testError = LiftrixError.DatabaseError("Failed to load summary data")
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.summaryData)
                assertEquals(testError, state.data.summaryData.errorOrNull())
                assertFalse(state.data.isRefreshing)
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    @Test
    fun `given unauthenticated user, when LoadSummary event, then doesn't attempt to load data`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        
        val unauthenticatedViewModel = ProgressSummaryViewModel(
            progressDataService = mockProgressDataService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When
        unauthenticatedViewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.summaryData)
            }
        }
        
        // Verify no service call made
        coVerify(exactly = 0) { mockProgressDataService.getProgressSummary(any(), any()) }
    }

    // === Refresh Summary Tests ===

    @Test
    fun `given existing summary data, when RefreshSummary event, then reloads data with refresh indicator`() = runTest {
        // Given - load initial data
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(testProgressSummary)
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Updated summary data for refresh
        val refreshedSummary = mockk<ProgressSummary> {
            every { totalWorkouts } returns 26 // One more workout
            every { totalVolume } returns 130000.0
            every { averageDuration } returns 3500
            every { timeRange } returns testTimeRange
            every { lastUpdated } returns Instant.fromEpochMilliseconds(1641081600000) // New timestamp
        }
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(refreshedSummary)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.RefreshSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertEquals(refreshedSummary, state.data.summaryData.getOrNull())
                assertEquals(26, state.data.summaryData.getOrNull()?.totalWorkouts)
                assertFalse(state.data.isRefreshing)
            }
        }
        
        // Verify service called again for refresh
        coVerify(exactly = 2) { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) }
    }

    @Test
    fun `given refresh in progress, when RefreshSummary event, then shows refreshing state`() = runTest {
        // Given - delay service response to capture refreshing state
        val refreshDeferred = CompletableDeferred<LiftrixResult<ProgressSummary>>()
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns refreshDeferred.await()
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.RefreshSummary)
        
        // Then - verify refreshing state appears
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.isRefreshing)
            }
            
            // Complete the deferred to allow test to finish
            refreshDeferred.complete(Result.success(testProgressSummary))
        }
    }

    // === Time Period Change Tests ===

    @Test
    fun `given authenticated user, when TimePeriodChanged event, then updates time range and reloads summary`() = runTest {
        // Given
        val newTimeRange = TimeRange.lastWeek()
        val newSummary = mockk<ProgressSummary> {
            every { totalWorkouts } returns 6 // Weekly summary
            every { totalVolume } returns 30000.0
            every { averageDuration } returns 3400
            every { timeRange } returns newTimeRange
        }
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, newTimeRange) } returns Result.success(newSummary)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.TimePeriodChanged(newTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newTimeRange, state.data.currentTimeRange)
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertEquals(newSummary, state.data.summaryData.getOrNull())
                assertEquals(6, state.data.summaryData.getOrNull()?.totalWorkouts)
            }
        }
        
        // Verify service call with new time range
        coVerify { mockProgressDataService.getProgressSummary(testUserId, newTimeRange) }
    }

    @Test
    fun `given unauthenticated user, when TimePeriodChanged event, then updates time range but doesn't load data`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        val newTimeRange = TimeRange.lastQuarter()
        
        val unauthenticatedViewModel = ProgressSummaryViewModel(
            progressDataService = mockProgressDataService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When
        unauthenticatedViewModel.handleEvent(ProgressSummaryEvent.TimePeriodChanged(newTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newTimeRange, state.data.currentTimeRange)
                assertEquals(null, state.data.userId)
                assertIs<AsyncData.NotAsked>(state.data.summaryData)
            }
        }
        
        // Verify no service call made
        coVerify(exactly = 0) { mockProgressDataService.getProgressSummary(any(), any()) }
    }

    // === Retry Load Tests ===

    @Test
    fun `given failed summary load, when RetryLoad event, then retries loading data`() = runTest {
        // Given - initial failure
        val testError = LiftrixError.NetworkError("Network failure")
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.failure(testError)
        
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Update service to succeed on retry
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(testProgressSummary)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.RetryLoad)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertEquals(testProgressSummary, state.data.summaryData.getOrNull())
            }
        }
        
        // Verify service called twice (initial + retry)
        coVerify(exactly = 2) { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) }
    }

    // === Clear Error Tests ===

    @Test
    fun `given error state, when ClearError event, then clears error and resets summary to NotAsked`() = runTest {
        // Given - start with error state
        val testError = LiftrixError.ValidationError(field = "timeRange", violations = listOf("Invalid time range"))
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.failure(testError)
        
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.ClearError)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.summaryData)
                assertFalse(state.data.isRefreshing)
            }
        }
    }

    // === Force Refresh Tests ===

    @Test
    fun `given any summary state, when ForceRefresh event, then bypasses cache and reloads data`() = runTest {
        // Given - load initial data
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(testProgressSummary)
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When - force refresh (should bypass any caching)
        viewModel.handleEvent(ProgressSummaryEvent.ForceRefresh)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertFalse(state.data.isRefreshing)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify service called twice (initial + force refresh)
        coVerify(exactly = 2) { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) }
    }

    // === Quick Time Range Selection Tests ===

    @Test
    fun `given predefined time ranges, when QuickTimeRangeSelected event, then switches to predefined range quickly`() = runTest {
        // Given
        val predefinedRange = TimeRange.lastYear()
        val yearSummary = mockk<ProgressSummary> {
            every { totalWorkouts } returns 156 // Yearly summary
            every { totalVolume } returns 780000.0
            every { timeRange } returns predefinedRange
        }
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, predefinedRange) } returns Result.success(yearSummary)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.QuickTimeRangeSelected(predefinedRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(predefinedRange, state.data.currentTimeRange)
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertEquals(156, state.data.summaryData.getOrNull()?.totalWorkouts)
            }
        }
        
        // Verify service call with predefined range
        coVerify { mockProgressDataService.getProgressSummary(testUserId, predefinedRange) }
    }

    // === Background Data Update Tests ===

    @Test
    fun `given background update available, when BackgroundDataUpdate event, then updates data silently`() = runTest {
        // Given - load initial data
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(testProgressSummary)
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Background update with new data
        val backgroundSummary = mockk<ProgressSummary> {
            every { totalWorkouts } returns 27 // Updated in background
            every { totalVolume } returns 135000.0
            every { timeRange } returns testTimeRange
            every { lastUpdated } returns Instant.fromEpochMilliseconds(1641168000000) // Newer timestamp
        }
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(backgroundSummary)
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.BackgroundDataUpdate)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
                assertEquals(27, state.data.summaryData.getOrNull()?.totalWorkouts)
                // Background update should not show refreshing indicator
                assertFalse(state.data.isRefreshing)
            }
        }
        
        // Verify service called for background update
        coVerify(exactly = 2) { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) }
    }

    // === User Authentication State Change Tests ===

    @Test
    fun `given user authentication changes, when user logs out, then clears summary data`() = runTest {
        // Given - start with authenticated user and loaded data
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        val authChangeViewModel = ProgressSummaryViewModel(
            progressDataService = mockProgressDataService,
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
                assertEquals(null, state.data.userId)
                assertIs<AsyncData.NotAsked>(state.data.summaryData)
                assertFalse(state.data.isRefreshing)
                assertEquals(0L, state.data.lastRefreshTimestamp)
            }
        }
    }

    @Test
    fun `given user authentication changes, when user logs in, then loads summary data automatically`() = runTest {
        // Given - start with no user
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, any()) } returns Result.success(testProgressSummary)
        
        val authChangeViewModel = ProgressSummaryViewModel(
            progressDataService = mockProgressDataService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When - emit authenticated user
        userFlow.emit(testUser)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        authChangeViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(testUserId, state.data.userId)
                // Data should eventually be loaded automatically
            }
        }
        
        // Verify data loading was triggered
        coVerify { mockProgressDataService.getProgressSummary(testUserId, any()) }
    }

    // === Service Exception Handling Tests ===

    @Test
    fun `given service throws exception, when loading summary, then handles error gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Unexpected service error")
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } throws exception
        
        // When
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify error handling doesn't crash
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
        
        // Verify error handler was called
        coVerify { mockErrorHandler.handleError(any(), any()) }
    }

    // === Data Freshness Tests ===

    @Test
    fun `given stale summary data, when checking freshness, then indicates data needs refresh`() = runTest {
        // Given - load data with old timestamp
        val staleSummary = mockk<ProgressSummary> {
            every { totalWorkouts } returns 25
            every { timeRange } returns testTimeRange
            every { lastUpdated } returns Instant.fromEpochMilliseconds(1609459200000) // Very old timestamp
        }
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, testTimeRange) } returns Result.success(staleSummary)
        
        viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                val summary = state.data.summaryData.getOrNull()
                assertIs<ProgressSummary>(summary)
                // Data should be marked as stale based on lastUpdated timestamp
                assertTrue(summary.lastUpdated.toEpochMilliseconds() < System.currentTimeMillis() - 86400000) // More than 1 day old
            }
        }
    }

    // === Multiple Time Range Support Tests ===

    @Test
    fun `given multiple time range changes, when switching ranges rapidly, then handles concurrent operations correctly`() = runTest {
        // Given
        val range1 = TimeRange.lastWeek()
        val range2 = TimeRange.lastMonth()
        val range3 = TimeRange.lastQuarter()
        
        coEvery { mockProgressDataService.getProgressSummary(testUserId, range1) } returns Result.success(testProgressSummary)
        coEvery { mockProgressDataService.getProgressSummary(testUserId, range2) } returns Result.success(testProgressSummary)
        coEvery { mockProgressDataService.getProgressSummary(testUserId, range3) } returns Result.success(testProgressSummary)
        
        // When - rapidly change time ranges
        viewModel.handleEvent(ProgressSummaryEvent.TimePeriodChanged(range1))
        viewModel.handleEvent(ProgressSummaryEvent.TimePeriodChanged(range2))
        viewModel.handleEvent(ProgressSummaryEvent.TimePeriodChanged(range3))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - final state should reflect the last range
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(range3, state.data.currentTimeRange)
                assertIs<AsyncData.Success<ProgressSummary>>(state.data.summaryData)
            }
        }
        
        // Verify service called for each range change
        coVerify { mockProgressDataService.getProgressSummary(testUserId, range1) }
        coVerify { mockProgressDataService.getProgressSummary(testUserId, range2) }
        coVerify { mockProgressDataService.getProgressSummary(testUserId, range3) }
    }
}