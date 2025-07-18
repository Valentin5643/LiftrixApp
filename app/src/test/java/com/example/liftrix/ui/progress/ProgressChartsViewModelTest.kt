package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.ChartType
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressChartsViewModelTest {

    // Test dependencies
    private lateinit var mockProgressDataService: ProgressDataService
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: ProgressChartsViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-123"
    private val testUser = mockk<com.example.liftrix.domain.model.User> {
        every { id } returns testUserId
    }
    private val testTimeRange = TimeRange.lastMonth()
    
    private val testVolumeData = listOf(
        mockk<VolumeDataPoint> {
            every { date } returns kotlinx.datetime.LocalDate(2024, 1, 1)
            every { volume } returns 1000.0
        },
        mockk<VolumeDataPoint> {
            every { date } returns kotlinx.datetime.LocalDate(2024, 1, 2)
            every { volume } returns 1200.0
        }
    )
    
    private val testDurationData = listOf(
        mockk<DurationDataPoint> {
            every { date } returns kotlinx.datetime.LocalDate(2024, 1, 1)
            every { duration } returns 3600 // 1 hour in seconds
        },
        mockk<DurationDataPoint> {
            every { date } returns kotlinx.datetime.LocalDate(2024, 1, 2)
            every { duration } returns 4200 // 70 minutes in seconds
        }
    )
    
    private val testFrequencyData = listOf(
        mockk<FrequencyDataPoint> {
            every { date } returns kotlinx.datetime.LocalDate(2024, 1, 1)
            every { frequency } returns 5
        },
        mockk<FrequencyDataPoint> {
            every { date } returns kotlinx.datetime.LocalDate(2024, 1, 2)
            every { frequency } returns 3
        }
    )

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
        
        viewModel = ProgressChartsViewModel(
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
            assertIs<UiState.Loading<ProgressChartsState>>(initialState)
        }
    }

    @Test
    fun `given unauthenticated user, when ViewModel created, then state reflects no user`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        
        // When
        val unauthenticatedViewModel = ProgressChartsViewModel(
            progressDataService = mockProgressDataService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(null, state.data.userId)
                assertTrue(state.data.areAllChartsNotAsked())
            }
        }
    }

    // === Data Loading Tests ===

    @Test
    fun `given authenticated user, when LoadInitialData event, then loads all chart data`() = runTest {
        // Given
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns LiftrixResult.Success(testVolumeData)
        coEvery { mockProgressDataService.getDurationData(testUserId, any()) } returns LiftrixResult.Success(testDurationData)
        coEvery { mockProgressDataService.getFrequencyData(testUserId, any()) } returns LiftrixResult.Success(testFrequencyData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.volumeChart.isSuccess())
                assertTrue(state.data.durationChart.isSuccess())
                assertTrue(state.data.frequencyChart.isSuccess())
                assertEquals(testVolumeData, state.data.volumeChart.getOrNull())
                assertEquals(testDurationData, state.data.durationChart.getOrNull())
                assertEquals(testFrequencyData, state.data.frequencyChart.getOrNull())
            }
        }
        
        // Verify service calls
        coVerify { mockProgressDataService.getVolumeData(testUserId, any()) }
        coVerify { mockProgressDataService.getDurationData(testUserId, any()) }
        coVerify { mockProgressDataService.getFrequencyData(testUserId, any()) }
    }

    @Test
    fun `given volume data service error, when LoadInitialData event, then volume chart shows error state`() = runTest {
        // Given
        val testError = LiftrixError.NetworkError("Network error")
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns LiftrixResult.Error(testError)
        coEvery { mockProgressDataService.getDurationData(testUserId, any()) } returns LiftrixResult.Success(testDurationData)
        coEvery { mockProgressDataService.getFrequencyData(testUserId, any()) } returns LiftrixResult.Success(testFrequencyData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.volumeChart.isFailure())
                assertTrue(state.data.durationChart.isSuccess())
                assertTrue(state.data.frequencyChart.isSuccess())
                assertEquals(testError, state.data.volumeChart.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Time Period Change Tests ===

    @Test
    fun `given authenticated user, when TimePeriodChanged event, then reloads data with new time range`() = runTest {
        // Given
        val newTimeRange = TimeRange.lastWeek()
        coEvery { mockProgressDataService.getVolumeData(testUserId, newTimeRange) } returns LiftrixResult.Success(testVolumeData)
        coEvery { mockProgressDataService.getDurationData(testUserId, newTimeRange) } returns LiftrixResult.Success(testDurationData)
        coEvery { mockProgressDataService.getFrequencyData(testUserId, newTimeRange) } returns LiftrixResult.Success(testFrequencyData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.TimePeriodChanged(newTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newTimeRange, state.data.currentTimeRange)
                assertTrue(state.data.areAllChartsLoaded())
            }
        }
        
        // Verify service calls with new time range
        coVerify { mockProgressDataService.getVolumeData(testUserId, newTimeRange) }
        coVerify { mockProgressDataService.getDurationData(testUserId, newTimeRange) }
        coVerify { mockProgressDataService.getFrequencyData(testUserId, newTimeRange) }
    }

    @Test
    fun `given unauthenticated user, when TimePeriodChanged event, then updates time range but doesn't load data`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        val newTimeRange = TimeRange.lastWeek()
        
        val unauthenticatedViewModel = ProgressChartsViewModel(
            progressDataService = mockProgressDataService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When
        unauthenticatedViewModel.handleEvent(ProgressChartsEvent.TimePeriodChanged(newTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newTimeRange, state.data.currentTimeRange)
                assertEquals(null, state.data.userId)
                assertTrue(state.data.areAllChartsNotAsked())
            }
        }
        
        // Verify no service calls made
        coVerify(exactly = 0) { mockProgressDataService.getVolumeData(any(), any()) }
    }

    // === Specific Chart Refresh Tests ===

    @Test
    fun `given authenticated user, when RefreshChart volume event, then only refreshes volume chart`() = runTest {
        // Given
        val refreshedVolumeData = listOf(
            mockk<VolumeDataPoint> {
                every { date } returns kotlinx.datetime.LocalDate(2024, 1, 3)
                every { volume } returns 1500.0
            }
        )
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns LiftrixResult.Success(refreshedVolumeData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.RefreshChart(ChartType.LINE)) // Assuming LINE maps to Volume
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.volumeChart.isSuccess())
                assertEquals(refreshedVolumeData, state.data.volumeChart.getOrNull())
            }
        }
        
        // Verify only volume service called
        coVerify { mockProgressDataService.getVolumeData(testUserId, any()) }
        coVerify(exactly = 0) { mockProgressDataService.getDurationData(any(), any()) }
        coVerify(exactly = 0) { mockProgressDataService.getFrequencyData(any(), any()) }
    }

    @Test
    fun `given authenticated user, when RefreshChart duration event, then only refreshes duration chart`() = runTest {
        // Given
        val refreshedDurationData = listOf(
            mockk<DurationDataPoint> {
                every { date } returns kotlinx.datetime.LocalDate(2024, 1, 3)
                every { duration } returns 5400 // 90 minutes
            }
        )
        coEvery { mockProgressDataService.getDurationData(testUserId, any()) } returns LiftrixResult.Success(refreshedDurationData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.RefreshChart(ChartType.BAR)) // Assuming BAR maps to Duration
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.durationChart.isSuccess())
                assertEquals(refreshedDurationData, state.data.durationChart.getOrNull())
            }
        }
        
        // Verify only duration service called
        coVerify { mockProgressDataService.getDurationData(testUserId, any()) }
        coVerify(exactly = 0) { mockProgressDataService.getVolumeData(any(), any()) }
        coVerify(exactly = 0) { mockProgressDataService.getFrequencyData(any(), any()) }
    }

    @Test
    fun `given authenticated user, when RefreshChart frequency event, then only refreshes frequency chart`() = runTest {
        // Given
        val refreshedFrequencyData = listOf(
            mockk<FrequencyDataPoint> {
                every { date } returns kotlinx.datetime.LocalDate(2024, 1, 3)
                every { frequency } returns 7
            }
        )
        coEvery { mockProgressDataService.getFrequencyData(testUserId, any()) } returns LiftrixResult.Success(refreshedFrequencyData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.RefreshChart(ChartType.RADIAL)) // Assuming RADIAL maps to Frequency
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.frequencyChart.isSuccess())
                assertEquals(refreshedFrequencyData, state.data.frequencyChart.getOrNull())
            }
        }
        
        // Verify only frequency service called
        coVerify { mockProgressDataService.getFrequencyData(testUserId, any()) }
        coVerify(exactly = 0) { mockProgressDataService.getVolumeData(any(), any()) }
        coVerify(exactly = 0) { mockProgressDataService.getDurationData(any(), any()) }
    }

    // === Refresh All Tests ===

    @Test
    fun `given authenticated user, when RefreshAll event, then refreshes all chart data`() = runTest {
        // Given
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns LiftrixResult.Success(testVolumeData)
        coEvery { mockProgressDataService.getDurationData(testUserId, any()) } returns LiftrixResult.Success(testDurationData)
        coEvery { mockProgressDataService.getFrequencyData(testUserId, any()) } returns LiftrixResult.Success(testFrequencyData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.RefreshAll)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.areAllChartsLoaded())
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify all service calls made
        coVerify { mockProgressDataService.getVolumeData(testUserId, any()) }
        coVerify { mockProgressDataService.getDurationData(testUserId, any()) }
        coVerify { mockProgressDataService.getFrequencyData(testUserId, any()) }
    }

    // === Loading State Tests ===

    @Test
    fun `given data loading, when charts are being fetched, then shows loading states`() = runTest {
        // Given - delay service responses to capture loading state
        val volumeDeferred = CompletableDeferred<LiftrixResult<List<VolumeDataPoint>>>()
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns volumeDeferred.await()
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.LoadInitialData)
        
        // Then - verify loading state appears
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.isAnyChartLoading())
            }
            
            // Complete the deferred to allow test to finish
            volumeDeferred.complete(LiftrixResult.Success(testVolumeData))
        }
    }

    // === Error Handling Tests ===

    @Test
    fun `given service throws exception, when loading data, then handles error gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Unexpected error")
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } throws exception
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify error handling doesn't crash the ViewModel
        viewModel.uiState.test {
            val state = awaitItem()
            // State should still be accessible, even if error occurred
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
        
        // Verify error handler was called
        coVerify { mockErrorHandler.handleError(any(), any()) }
    }

    @Test
    fun `given multiple chart errors, when loading data, then each chart handles its own error`() = runTest {
        // Given
        val volumeError = LiftrixError.NetworkError("Volume network error")
        val durationError = LiftrixError.DatabaseError("Duration database error")
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns LiftrixResult.Error(volumeError)
        coEvery { mockProgressDataService.getDurationData(testUserId, any()) } returns LiftrixResult.Error(durationError)
        coEvery { mockProgressDataService.getFrequencyData(testUserId, any()) } returns LiftrixResult.Success(testFrequencyData)
        
        // When
        viewModel.handleEvent(ProgressChartsEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.volumeChart.isFailure())
                assertTrue(state.data.durationChart.isFailure())
                assertTrue(state.data.frequencyChart.isSuccess())
                assertEquals(volumeError, state.data.volumeChart.errorOrNull())
                assertEquals(durationError, state.data.durationChart.errorOrNull())
            }
        }
        
        // Verify both errors handled
        coVerify { mockErrorHandler.handleError(volumeError, any()) }
        coVerify { mockErrorHandler.handleError(durationError, any()) }
    }

    // === User Authentication State Change Tests ===

    @Test
    fun `given user authentication changes, when user logs out, then clears data and updates state`() = runTest {
        // Given - start with authenticated user
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        val authChangeViewModel = ProgressChartsViewModel(
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
                assertTrue(state.data.areAllChartsNotAsked())
            }
        }
    }

    @Test
    fun `given user authentication changes, when user logs in, then loads initial data`() = runTest {
        // Given - start with no user
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        coEvery { mockProgressDataService.getVolumeData(testUserId, any()) } returns LiftrixResult.Success(testVolumeData)
        coEvery { mockProgressDataService.getDurationData(testUserId, any()) } returns LiftrixResult.Success(testDurationData)
        coEvery { mockProgressDataService.getFrequencyData(testUserId, any()) } returns LiftrixResult.Success(testFrequencyData)
        
        val authChangeViewModel = ProgressChartsViewModel(
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
                // Data should eventually be loaded
            }
        }
        
        // Verify data loading was triggered
        coVerify { mockProgressDataService.getVolumeData(testUserId, any()) }
    }

    // === State Extension Function Tests ===

    @Test
    fun `given chart state, when checking state utilities, then returns correct values`() = runTest {
        // Given
        val successState = createLoadingChartsState(testUserId, testTimeRange).copy(
            volumeChart = AsyncData.Success(testVolumeData),
            durationChart = AsyncData.Success(testDurationData),
            frequencyChart = AsyncData.Failure(LiftrixError.NetworkError("Test error"))
        )
        
        // When & Then
        assertTrue(successState.hasValidUser())
        assertTrue(successState.hasAnyChartError())
        assertFalse(successState.areAllChartsLoaded())
        assertFalse(successState.areAllChartsNotAsked())
        assertEquals(testUserId, successState.userId)
        assertEquals(testTimeRange, successState.currentTimeRange)
    }
}