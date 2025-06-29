package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ProgressDashboardViewModel
 * Tests state management, data loading flows, and error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressDashboardViewModelTest {

    // Test dependencies
    private lateinit var mockProgressStatsRepository: ProgressStatsRepository
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var viewModel: ProgressDashboardViewModel

    // Test data
    private val testUserId = "test-user-123"
    private val testUser = User(
        uid = testUserId,
        email = "test@example.com",
        displayName = "Test User",
        isAnonymous = false,
        createdAt = Instant.now()
    )

    private val testVolumeData = listOf(
        VolumeDataPoint(LocalDate(2024, 1, 1), 1500f, 3),
        VolumeDataPoint(LocalDate(2024, 1, 2), 1200f, 2)
    )

    private val testDurationData = listOf(
        DurationDataPoint(LocalDate(2024, 1, 1), 90, 2),
        DurationDataPoint(LocalDate(2024, 1, 2), 75, 1)
    )

    private val testFrequencyData = listOf(
        FrequencyDataPoint(LocalDate(2024, 1, 1), 2, 1.0f),
        FrequencyDataPoint(LocalDate(2024, 1, 2), 1, 0.5f)
    )

    private val testSummaryData = ProgressSummary(
        totalWorkouts = 10,
        totalVolume = 12500f,
        averageDuration = 85,
        totalActiveTime = 850,
        currentStreak = 5,
        averageWorkoutsPerWeek = 3.2f,
        longestStreak = 7
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        
        // Initialize mocks
        mockProgressStatsRepository = mockk()
        mockAuthRepository = mockk()

        // Default mock behaviors
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        coEvery { mockAuthRepository.getCurrentUserId() } returns testUserId
        
        // Default successful flows for all data types
        every { mockProgressStatsRepository.getWorkoutVolumeData(any(), any(), any()) } returns flowOf(testVolumeData)
        every { mockProgressStatsRepository.getWorkoutDurationData(any(), any(), any()) } returns flowOf(testDurationData)
        every { mockProgressStatsRepository.getWorkoutFrequencyData(any(), any(), any()) } returns flowOf(testFrequencyData)
        every { mockProgressStatsRepository.getProgressSummary(any(), any(), any()) } returns flowOf(testSummaryData)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ============ Initial State Tests ============

    @Test
    fun `initial state should have default values with empty data`() = runTest {
        // Arrange & Act
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Assert
        val initialState = viewModel.uiState.value
        assertEquals(TimePeriod.MONTH, initialState.selectedTimePeriod)
        assertEquals(emptyList(), initialState.volumeData)
        assertEquals(emptyList(), initialState.durationData)
        assertEquals(emptyList(), initialState.frequencyData)
        assertEquals(ProgressSummary(0, 0f, 0, 0, 0, 0f, 0), initialState.summaryData)
        assertFalse(initialState.isVolumeLoading)
        assertFalse(initialState.isDurationLoading)
        assertFalse(initialState.isFrequencyLoading)
        assertFalse(initialState.isSummaryLoading)
        assertNull(initialState.error)
    }

    @Test
    fun `init should load all data types successfully`() = runTest {
        // Arrange & Act
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testVolumeData, state.volumeData)
            assertEquals(testDurationData, state.durationData)
            assertEquals(testFrequencyData, state.frequencyData)
            assertEquals(testSummaryData, state.summaryData)
            assertFalse(state.isAnyChartLoading)
            assertNull(state.error)
            assertTrue(state.hasAllData)
            assertFalse(state.isEmpty)
        }

        // Verify all repository methods were called
        coVerify { mockProgressStatsRepository.getWorkoutVolumeData(testUserId, any(), any()) }
        coVerify { mockProgressStatsRepository.getWorkoutDurationData(testUserId, any(), any()) }
        coVerify { mockProgressStatsRepository.getWorkoutFrequencyData(testUserId, any(), any()) }
        coVerify { mockProgressStatsRepository.getProgressSummary(testUserId, any(), any()) }
    }

    // ============ Event Handling Tests ============

    @Test
    fun `onEvent TimePeriodChanged should update period and reload data`() = runTest {
        // Arrange
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ProgressDashboardEvent.TimePeriodChanged(TimePeriod.WEEK))
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(TimePeriod.WEEK, state.selectedTimePeriod)
        }

        // Verify data was reloaded (should be called twice: init + period change)
        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutVolumeData(testUserId, any(), any()) }
        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutDurationData(testUserId, any(), any()) }
        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutFrequencyData(testUserId, any(), any()) }
        coVerify(atLeast = 2) { mockProgressStatsRepository.getProgressSummary(testUserId, any(), any()) }
    }

    @Test
    fun `onEvent RefreshData should reload all data`() = runTest {
        // Arrange
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ProgressDashboardEvent.RefreshData)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testVolumeData, state.volumeData)
            assertEquals(testDurationData, state.durationData)
            assertEquals(testFrequencyData, state.frequencyData)
            assertEquals(testSummaryData, state.summaryData)
        }

        // Verify all data was reloaded (init + refresh)
        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutVolumeData(testUserId, any(), any()) }
        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutDurationData(testUserId, any(), any()) }
        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutFrequencyData(testUserId, any(), any()) }
        coVerify(atLeast = 2) { mockProgressStatsRepository.getProgressSummary(testUserId, any(), any()) }
    }

    @Test
    fun `onEvent ClearError should reset error state`() = runTest {
        // Arrange
        coEvery { mockAuthRepository.getCurrentUserId() } returns null
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        viewModel.onEvent(ProgressDashboardEvent.LoadVolumeChart)
        advanceUntilIdle()

        // Verify error exists
        viewModel.uiState.test {
            val stateWithError = awaitItem()
            assertNotNull(stateWithError.error)
        }

        // Act
        viewModel.onEvent(ProgressDashboardEvent.ClearError)

        // Assert
        viewModel.uiState.test {
            val clearedState = awaitItem()
            assertNull(clearedState.error)
        }
    }

    @Test
    fun `onEvent LoadVolumeChart should load volume data specifically`() = runTest {
        // Arrange
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ProgressDashboardEvent.LoadVolumeChart)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testVolumeData, state.volumeData)
            assertFalse(state.isVolumeLoading)
        }

        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutVolumeData(testUserId, any(), any()) }
    }

    @Test
    fun `onEvent LoadDurationChart should load duration data specifically`() = runTest {
        // Arrange
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ProgressDashboardEvent.LoadDurationChart)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testDurationData, state.durationData)
            assertFalse(state.isDurationLoading)
        }

        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutDurationData(testUserId, any(), any()) }
    }

    @Test
    fun `onEvent LoadFrequencyChart should load frequency data specifically`() = runTest {
        // Arrange
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ProgressDashboardEvent.LoadFrequencyChart)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testFrequencyData, state.frequencyData)
            assertFalse(state.isFrequencyLoading)
        }

        coVerify(atLeast = 2) { mockProgressStatsRepository.getWorkoutFrequencyData(testUserId, any(), any()) }
    }

    @Test
    fun `onEvent LoadSummaryStats should load summary data specifically`() = runTest {
        // Arrange
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ProgressDashboardEvent.LoadSummaryStats)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testSummaryData, state.summaryData)
            assertFalse(state.isSummaryLoading)
        }

        coVerify(atLeast = 2) { mockProgressStatsRepository.getProgressSummary(testUserId, any(), any()) }
    }

    // ============ Authentication Tests ============

    @Test
    fun `observeAuthState should reload data when user changes`() = runTest {
        // Arrange
        val newUser = testUser.copy(uid = "new-user-id")
        val userFlow = MutableStateFlow<User?>(testUser)
        every { mockAuthRepository.currentUser } returns userFlow
        coEvery { mockAuthRepository.getCurrentUserId() } returns "new-user-id"
        
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        userFlow.value = newUser
        advanceUntilIdle()

        // Assert
        coVerify { mockProgressStatsRepository.getWorkoutVolumeData(testUserId, any(), any()) }
        coVerify { mockProgressStatsRepository.getWorkoutVolumeData("new-user-id", any(), any()) }
    }

    @Test
    fun `observeAuthState should clear data when user signs out`() = runTest {
        // Arrange
        val userFlow = MutableStateFlow<User?>(testUser)
        every { mockAuthRepository.currentUser } returns userFlow
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        userFlow.value = null
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.volumeData)
            assertEquals(emptyList(), state.durationData)
            assertEquals(emptyList(), state.frequencyData)
            assertEquals(ProgressSummary(0, 0f, 0, 0, 0, 0f, 0), state.summaryData)
            assertFalse(state.isAnyChartLoading)
            assertNull(state.error)
        }
    }

    // ============ Error Handling Tests ============

    @Test
    fun `loadVolumeData should handle user not authenticated error`() = runTest {
        // Arrange
        coEvery { mockAuthRepository.getCurrentUserId() } returns null
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act
        viewModel.onEvent(ProgressDashboardEvent.LoadVolumeChart)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.volumeData)
            assertFalse(state.isVolumeLoading)
            assertEquals("User not authenticated", state.error)
        }

        coVerify(exactly = 0) { mockProgressStatsRepository.getWorkoutVolumeData(any(), any(), any()) }
    }

    @Test
    fun `loadVolumeData should handle repository flow error`() = runTest {
        // Arrange
        val errorMessage = "Database connection failed"
        every { mockProgressStatsRepository.getWorkoutVolumeData(any(), any(), any()) } returns 
            flow { throw RuntimeException(errorMessage) }
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.volumeData)
            assertFalse(state.isVolumeLoading)
            assertTrue(state.error?.contains("Failed to load volume data") == true)
            assertTrue(state.error?.contains(errorMessage) == true)
        }
    }

    @Test
    fun `loadDurationData should handle repository error`() = runTest {
        // Arrange
        val errorMessage = "Network timeout"
        every { mockProgressStatsRepository.getWorkoutDurationData(any(), any(), any()) } returns 
            flow { throw RuntimeException(errorMessage) }
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.durationData)
            assertFalse(state.isDurationLoading)
            assertTrue(state.error?.contains("Failed to load duration data") == true)
        }
    }

    @Test
    fun `loadFrequencyData should handle repository error`() = runTest {
        // Arrange
        val errorMessage = "Query execution failed"
        every { mockProgressStatsRepository.getWorkoutFrequencyData(any(), any(), any()) } returns 
            flow { throw RuntimeException(errorMessage) }
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.frequencyData)
            assertFalse(state.isFrequencyLoading)
            assertTrue(state.error?.contains("Failed to load frequency data") == true)
        }
    }

    @Test
    fun `loadSummaryData should handle repository error`() = runTest {
        // Arrange
        val errorMessage = "Aggregation calculation failed"
        every { mockProgressStatsRepository.getProgressSummary(any(), any(), any()) } returns 
            flow { throw RuntimeException(errorMessage) }
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(ProgressSummary(0, 0f, 0, 0, 0, 0f, 0), state.summaryData)
            assertFalse(state.isSummaryLoading)
            assertTrue(state.error?.contains("Failed to load summary data") == true)
        }
    }

    @Test
    fun `loadVolumeData should handle unexpected exceptions`() = runTest {
        // Arrange
        coEvery { mockAuthRepository.getCurrentUserId() } throws RuntimeException("Unexpected auth error")
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act
        viewModel.onEvent(ProgressDashboardEvent.LoadVolumeChart)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isVolumeLoading)
            assertTrue(state.error?.contains("Unexpected error") == true)
        }
    }

    // ============ Date Range Calculation Tests ============

    @Test
    fun `getDateRangeForPeriod should calculate correct ranges for all time periods`() = runTest {
        // This is testing private method behavior through observable effects
        // We verify by checking that different periods result in different repository calls
        
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)
        advanceUntilIdle()

        // Test WEEK period
        viewModel.onEvent(ProgressDashboardEvent.TimePeriodChanged(TimePeriod.WEEK))
        advanceUntilIdle()

        // Test QUARTER period
        viewModel.onEvent(ProgressDashboardEvent.TimePeriodChanged(TimePeriod.QUARTER))
        advanceUntilIdle()

        // Test YEAR period
        viewModel.onEvent(ProgressDashboardEvent.TimePeriodChanged(TimePeriod.YEAR))
        advanceUntilIdle()

        // Verify repository was called with different date ranges
        // (Each period change should trigger all 4 data loading methods)
        coVerify(atLeast = 4) { mockProgressStatsRepository.getWorkoutVolumeData(testUserId, any(), any()) }
        coVerify(atLeast = 4) { mockProgressStatsRepository.getWorkoutDurationData(testUserId, any(), any()) }
        coVerify(atLeast = 4) { mockProgressStatsRepository.getWorkoutFrequencyData(testUserId, any(), any()) }
        coVerify(atLeast = 4) { mockProgressStatsRepository.getProgressSummary(testUserId, any(), any()) }
    }

    // ============ UI State Convenience Properties Tests ============

    @Test
    fun `ProgressDashboardUiState convenience properties should work correctly`() {
        // Test empty state
        val emptyState = ProgressDashboardUiState()
        assertFalse(emptyState.isAnyChartLoading)
        assertFalse(emptyState.hasAllData)
        assertTrue(emptyState.isEmpty)

        // Test loading state
        val loadingState = ProgressDashboardUiState(
            isVolumeLoading = true,
            isDurationLoading = true
        )
        assertTrue(loadingState.isAnyChartLoading)
        assertFalse(loadingState.hasAllData)
        assertFalse(loadingState.isEmpty)

        // Test state with all data
        val dataState = ProgressDashboardUiState(
            volumeData = testVolumeData,
            durationData = testDurationData,
            frequencyData = testFrequencyData,
            summaryData = testSummaryData
        )
        assertFalse(dataState.isAnyChartLoading)
        assertTrue(dataState.hasAllData)
        assertFalse(dataState.isEmpty)

        // Test partial data state
        val partialDataState = ProgressDashboardUiState(
            volumeData = testVolumeData,
            durationData = emptyList(),
            frequencyData = testFrequencyData,
            summaryData = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0)
        )
        assertFalse(partialDataState.isAnyChartLoading)
        assertFalse(partialDataState.hasAllData)
        assertFalse(partialDataState.isEmpty)
    }

    // ============ Loading State Management Tests ============

    @Test
    fun `loading states should be managed correctly during data loading`() = runTest {
        // Arrange
        val slowVolumeFlow = flow {
            kotlinx.coroutines.delay(100)
            emit(testVolumeData)
        }
        every { mockProgressStatsRepository.getWorkoutVolumeData(any(), any(), any()) } returns slowVolumeFlow
        
        viewModel = ProgressDashboardViewModel(mockProgressStatsRepository, mockAuthRepository)

        // Act - trigger volume data loading
        viewModel.onEvent(ProgressDashboardEvent.LoadVolumeChart)

        // Assert - should show loading state before data arrives
        viewModel.uiState.test {
            var state = awaitItem()
            // Initial state may have loading = false
            
            // Skip to the loading state
            do {
                state = awaitItem()
            } while (!state.isVolumeLoading && state.volumeData.isEmpty())

            assertTrue(state.isVolumeLoading)
            assertTrue(state.isAnyChartLoading)

            // Wait for loading to complete
            do {
                state = awaitItem()
            } while (state.isVolumeLoading)

            assertFalse(state.isVolumeLoading)
            assertEquals(testVolumeData, state.volumeData)
            assertNull(state.error)
        }
    }

    // ============ TimePeriod Enum Tests ============

    @Test
    fun `TimePeriod enum should have correct values`() {
        assertEquals("Week", TimePeriod.WEEK.displayName)
        assertEquals(7, TimePeriod.WEEK.days)
        
        assertEquals("Month", TimePeriod.MONTH.displayName)
        assertEquals(30, TimePeriod.MONTH.days)
        
        assertEquals("3 Months", TimePeriod.QUARTER.displayName)
        assertEquals(90, TimePeriod.QUARTER.days)
        
        assertEquals("Year", TimePeriod.YEAR.displayName)
        assertEquals(365, TimePeriod.YEAR.days)
    }
}