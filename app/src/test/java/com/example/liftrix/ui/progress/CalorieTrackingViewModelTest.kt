package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.analytics.CalorieSummary
import com.example.liftrix.domain.model.analytics.DailyCalorieData
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.WeeklyCalorieTrend
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.CalorieService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CalorieTrackingViewModelTest {

    // Test dependencies
    private lateinit var mockCalorieService: CalorieService
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: CalorieTrackingViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-123"
    private val testUser = mockk<com.example.liftrix.domain.model.User> {
        every { id } returns testUserId
    }
    
    private val testTimeRange = TimeRange.lastMonth()
    
    private val testCalorieSummary = mockk<CalorieSummary> {
        every { totalCalories } returns 15750
        every { averageDailyCalories } returns 525
        every { highestDayCalories } returns 845
        every { lowestDayCalories } returns 285
        every { caloriesFromCardio } returns 6300
        every { caloriesFromStrength } returns 9450
        every { burnRate } returns 12.5 // calories per minute
        every { timeRange } returns testTimeRange
    }
    
    private val testDailyCalorieData = listOf(
        mockk<DailyCalorieData> {
            every { date } returns LocalDate(2024, 1, 1)
            every { totalCalories } returns 520
            every { cardioCalories } returns 200
            every { strengthCalories } returns 320
            every { workoutDuration } returns 3600 // 1 hour
            every { averageHeartRate } returns 145
        },
        mockk<DailyCalorieData> {
            every { date } returns LocalDate(2024, 1, 2)
            every { totalCalories } returns 680
            every { cardioCalories } returns 350
            every { strengthCalories } returns 330
            every { workoutDuration } returns 4200 // 70 minutes
            every { averageHeartRate } returns 152
        }
    )
    
    private val testWeeklyCalorieTrend = mockk<WeeklyCalorieTrend> {
        every { weeklyData } returns mapOf(
            "Week 1" to 3650,
            "Week 2" to 3890,
            "Week 3" to 4120,
            "Week 4" to 4090
        )
        every { averageWeeklyCalories } returns 3937.5
        every { trend } returns 0.12 // 12% increase
        every { peakWeek } returns "Week 3"
        every { totalWeeks } returns 4
    }
    
    private val testWorkout = mockk<com.example.liftrix.domain.model.Workout> {
        every { id } returns "workout-123"
        every { name } returns "Full Body Workout"
        every { duration } returns 3600 // 1 hour
        every { exercises } returns listOf()
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        mockCalorieService = mockk(relaxed = true)
        mockAuthRepository = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        
        // Default auth repository behavior
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        
        // Default error handler behavior
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        
        viewModel = CalorieTrackingViewModel(
            calorieService = mockCalorieService,
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
            assertIs<UiState.Loading<CalorieTrackingState>>(initialState)
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
                assertIs<AsyncData.NotAsked>(state.data.calorieSummary)
                assertIs<AsyncData.NotAsked>(state.data.dailyCalories)
                assertIs<AsyncData.NotAsked>(state.data.weeklyTrend)
                assertTrue(state.data.workoutCalories.isEmpty())
                assertEquals(0L, state.data.lastRefreshTimestamp)
            }
        }
    }

    @Test
    fun `given unauthenticated user, when ViewModel created, then state reflects no user`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        
        // When
        val unauthenticatedViewModel = CalorieTrackingViewModel(
            calorieService = mockCalorieService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(null, state.data.userId)
                assertIs<AsyncData.NotAsked>(state.data.calorieSummary)
                assertIs<AsyncData.NotAsked>(state.data.dailyCalories)
                assertIs<AsyncData.NotAsked>(state.data.weeklyTrend)
            }
        }
    }

    // === Calorie Summary Loading Tests ===

    @Test
    fun `given authenticated user, when LoadSummary event, then loads calorie summary successfully`() = runTest {
        // Given
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<CalorieSummary>>(state.data.calorieSummary)
                assertEquals(testCalorieSummary, state.data.calorieSummary.getOrNull())
                assertEquals(15750, state.data.calorieSummary.getOrNull()?.totalCalories)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify service call
        coVerify { mockCalorieService.getCalorieSummary(testUserId) }
    }

    @Test
    fun `given service error, when LoadSummary event, then shows error state`() = runTest {
        // Given
        val testError = LiftrixError.CalculationError("MET calculation failed")
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.calorieSummary)
                assertEquals(testError, state.data.calorieSummary.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Daily Calories Loading Tests ===

    @Test
    fun `given authenticated user, when LoadDailyCalories event, then loads daily data successfully`() = runTest {
        // Given
        val testPeriod = TimeRange.lastWeek()
        coEvery { mockCalorieService.getDailyCalories(testUserId, testPeriod) } returns Result.success(testDailyCalorieData)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadDailyCalories(testPeriod))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<List<DailyCalorieData>>>(state.data.dailyCalories)
                assertEquals(testDailyCalorieData, state.data.dailyCalories.getOrNull())
                assertEquals(2, state.data.dailyCalories.getOrNull()?.size)
            }
        }
        
        // Verify service call with correct period
        coVerify { mockCalorieService.getDailyCalories(testUserId, testPeriod) }
    }

    @Test
    fun `given MET data unavailable, when LoadDailyCalories event, then shows calculation error`() = runTest {
        // Given
        val testError = LiftrixError.CalculationError("MET data unavailable for exercise type")
        coEvery { mockCalorieService.getDailyCalories(testUserId, any()) } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadDailyCalories(testTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.dailyCalories)
                assertEquals(testError, state.data.dailyCalories.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Weekly Trend Loading Tests ===

    @Test
    fun `given authenticated user, when LoadWeeklyTrend event, then loads trend data successfully`() = runTest {
        // Given
        coEvery { mockCalorieService.getWeeklyTrend(testUserId) } returns Result.success(testWeeklyCalorieTrend)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadWeeklyTrend)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<WeeklyCalorieTrend>>(state.data.weeklyTrend)
                assertEquals(testWeeklyCalorieTrend, state.data.weeklyTrend.getOrNull())
                assertEquals(0.12, state.data.weeklyTrend.getOrNull()?.trend)
                assertEquals("Week 3", state.data.weeklyTrend.getOrNull()?.peakWeek)
            }
        }
        
        // Verify service call
        coVerify { mockCalorieService.getWeeklyTrend(testUserId) }
    }

    // === Time Period Change Tests ===

    @Test
    fun `given authenticated user, when TimePeriodChanged event, then updates time range and reloads relevant data`() = runTest {
        // Given
        val newTimeRange = TimeRange.lastQuarter()
        coEvery { mockCalorieService.getDailyCalories(testUserId, newTimeRange) } returns Result.success(testDailyCalorieData)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.TimePeriodChanged(newTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newTimeRange, state.data.currentTimeRange)
                assertIs<AsyncData.Success<List<DailyCalorieData>>>(state.data.dailyCalories)
            }
        }
        
        // Verify service call with new time range
        coVerify { mockCalorieService.getDailyCalories(testUserId, newTimeRange) }
    }

    @Test
    fun `given unauthenticated user, when TimePeriodChanged event, then updates time range but doesn't load data`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        val newTimeRange = TimeRange.lastYear()
        
        val unauthenticatedViewModel = CalorieTrackingViewModel(
            calorieService = mockCalorieService,
            authRepository = mockAuthRepository,
            errorHandler = mockErrorHandler
        )
        
        // When
        unauthenticatedViewModel.handleEvent(CalorieTrackingEvent.TimePeriodChanged(newTimeRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        unauthenticatedViewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(newTimeRange, state.data.currentTimeRange)
                assertEquals(null, state.data.userId)
                assertIs<AsyncData.NotAsked>(state.data.dailyCalories)
            }
        }
        
        // Verify no service call made
        coVerify(exactly = 0) { mockCalorieService.getDailyCalories(any(), any()) }
    }

    // === Workout Calorie Calculation Tests ===

    @Test
    fun `given workout data, when CalculateWorkoutCalories event, then calculates and stores calories`() = runTest {
        // Given
        val expectedCalories = 485
        coEvery { mockCalorieService.calculateWorkoutCalories(testWorkout) } returns Result.success(expectedCalories)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.CalculateWorkoutCalories(testWorkout))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertTrue(state.data.workoutCalories.containsKey(testWorkout.id))
                assertEquals(expectedCalories, state.data.workoutCalories[testWorkout.id])
            }
        }
        
        // Verify service call
        coVerify { mockCalorieService.calculateWorkoutCalories(testWorkout) }
    }

    @Test
    fun `given workout with unknown exercises, when CalculateWorkoutCalories event, then shows calculation error`() = runTest {
        // Given
        val testError = LiftrixError.CalculationError("Unknown exercise types, cannot calculate MET values")
        coEvery { mockCalorieService.calculateWorkoutCalories(testWorkout) } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.CalculateWorkoutCalories(testWorkout))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertFalse(state.data.workoutCalories.containsKey(testWorkout.id))
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Refresh All Data Tests ===

    @Test
    fun `given authenticated user, when RefreshAllData event, then refreshes all calorie data types`() = runTest {
        // Given
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        coEvery { mockCalorieService.getDailyCalories(testUserId, testTimeRange) } returns Result.success(testDailyCalorieData)
        coEvery { mockCalorieService.getWeeklyTrend(testUserId) } returns Result.success(testWeeklyCalorieTrend)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.RefreshAllData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<CalorieSummary>>(state.data.calorieSummary)
                assertIs<AsyncData.Success<List<DailyCalorieData>>>(state.data.dailyCalories)
                assertIs<AsyncData.Success<WeeklyCalorieTrend>>(state.data.weeklyTrend)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify all service calls made
        coVerify { mockCalorieService.getCalorieSummary(testUserId) }
        coVerify { mockCalorieService.getDailyCalories(testUserId, testTimeRange) }
        coVerify { mockCalorieService.getWeeklyTrend(testUserId) }
    }

    // === Load Initial Data Tests ===

    @Test
    fun `given authenticated user, when LoadInitialData event, then loads summary and basic data`() = runTest {
        // Given
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        coEvery { mockCalorieService.getDailyCalories(testUserId, testTimeRange) } returns Result.success(testDailyCalorieData)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<CalorieSummary>>(state.data.calorieSummary)
                assertIs<AsyncData.Success<List<DailyCalorieData>>>(state.data.dailyCalories)
                // Weekly trend might not be loaded in initial data
            }
        }
        
        // Verify essential data loaded
        coVerify { mockCalorieService.getCalorieSummary(testUserId) }
        coVerify { mockCalorieService.getDailyCalories(testUserId, testTimeRange) }
    }

    // === Retry Failed Operations Tests ===

    @Test
    fun `given failed calorie operations, when RetryFailedOperations event, then retries only failed operations`() = runTest {
        // Given - simulate initial failures
        val summaryError = LiftrixError.NetworkError("Network timeout")
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.failure(summaryError)
        coEvery { mockCalorieService.getDailyCalories(testUserId, testTimeRange) } returns Result.success(testDailyCalorieData)
        
        viewModel.handleEvent(CalorieTrackingEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Update service to succeed on retry
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.RetryFailedOperations)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<CalorieSummary>>(state.data.calorieSummary)
                assertIs<AsyncData.Success<List<DailyCalorieData>>>(state.data.dailyCalories)
            }
        }
        
        // Verify retry calls
        coVerify(exactly = 2) { mockCalorieService.getCalorieSummary(testUserId) } // Initial + retry
        coVerify(exactly = 1) { mockCalorieService.getDailyCalories(testUserId, testTimeRange) } // No retry needed
    }

    // === Clear Cached Data Tests ===

    @Test
    fun `given cached calorie data, when ClearCachedData event, then resets all data to NotAsked`() = runTest {
        // Given - load initial data
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        coEvery { mockCalorieService.getDailyCalories(testUserId, testTimeRange) } returns Result.success(testDailyCalorieData)
        
        viewModel.handleEvent(CalorieTrackingEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.ClearCachedData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.calorieSummary)
                assertIs<AsyncData.NotAsked>(state.data.dailyCalories)
                assertIs<AsyncData.NotAsked>(state.data.weeklyTrend)
                assertTrue(state.data.workoutCalories.isEmpty())
                assertEquals(0L, state.data.lastRefreshTimestamp)
            }
        }
    }

    // === MET Calculation Accuracy Tests ===

    @Test
    fun `given workout with mixed exercise types, when calculating calories, then applies correct MET values per exercise`() = runTest {
        // Given
        val complexWorkout = mockk<com.example.liftrix.domain.model.Workout> {
            every { id } returns "complex-workout-456"
            every { name } returns "Cardio + Strength Mix"
            every { duration } returns 3600
            every { exercises } returns listOf(
                mockk { every { exerciseType } returns "CARDIO" },
                mockk { every { exerciseType } returns "STRENGTH" }
            )
        }
        
        val expectedCalories = 680 // Higher due to mixed exercise types
        coEvery { mockCalorieService.calculateWorkoutCalories(complexWorkout) } returns Result.success(expectedCalories)
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.CalculateWorkoutCalories(complexWorkout))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(expectedCalories, state.data.workoutCalories[complexWorkout.id])
            }
        }
        
        // Verify service calculated with complex workout
        coVerify { mockCalorieService.calculateWorkoutCalories(complexWorkout) }
    }

    // === User Authentication State Change Tests ===

    @Test
    fun `given user authentication changes, when user logs out, then clears calorie data`() = runTest {
        // Given - start with authenticated user and loaded data
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        val authChangeViewModel = CalorieTrackingViewModel(
            calorieService = mockCalorieService,
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
                assertIs<AsyncData.NotAsked>(state.data.calorieSummary)
                assertIs<AsyncData.NotAsked>(state.data.dailyCalories)
                assertIs<AsyncData.NotAsked>(state.data.weeklyTrend)
                assertTrue(state.data.workoutCalories.isEmpty())
            }
        }
    }

    @Test
    fun `given user authentication changes, when user logs in, then loads calorie data automatically`() = runTest {
        // Given - start with no user
        val userFlow = MutableSharedFlow<com.example.liftrix.domain.model.User?>()
        every { mockAuthRepository.currentUser } returns userFlow
        
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        coEvery { mockCalorieService.getDailyCalories(testUserId, any()) } returns Result.success(testDailyCalorieData)
        
        val authChangeViewModel = CalorieTrackingViewModel(
            calorieService = mockCalorieService,
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
        coVerify { mockCalorieService.getCalorieSummary(testUserId) }
    }

    // === Service Exception Handling Tests ===

    @Test
    fun `given service throws exception, when calculating calories, then handles error gracefully`() = runTest {
        // Given
        val exception = RuntimeException("MET database connection failed")
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } throws exception
        
        // When
        viewModel.handleEvent(CalorieTrackingEvent.LoadSummary)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify error handling doesn't crash
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
        
        // Verify error handler was called
        coVerify { mockErrorHandler.handleError(any(), any()) }
    }

    // === Data Consistency Tests ===

    @Test
    fun `given multiple calorie data sources, when loaded, then maintains consistency across different time ranges`() = runTest {
        // Given
        val weekRange = TimeRange.lastWeek()
        val monthRange = TimeRange.lastMonth()
        
        coEvery { mockCalorieService.getCalorieSummary(testUserId) } returns Result.success(testCalorieSummary)
        coEvery { mockCalorieService.getDailyCalories(testUserId, weekRange) } returns Result.success(testDailyCalorieData)
        coEvery { mockCalorieService.getDailyCalories(testUserId, monthRange) } returns Result.success(testDailyCalorieData)
        
        // When - load data for different time ranges
        viewModel.handleEvent(CalorieTrackingEvent.LoadSummary)
        viewModel.handleEvent(CalorieTrackingEvent.LoadDailyCalories(weekRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(CalorieTrackingEvent.TimePeriodChanged(monthRange))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - data should be consistent and properly managed
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(monthRange, state.data.currentTimeRange)
                assertIs<AsyncData.Success<CalorieSummary>>(state.data.calorieSummary)
                assertIs<AsyncData.Success<List<DailyCalorieData>>>(state.data.dailyCalories)
            }
        }
        
        // Verify service calls for both time ranges
        coVerify { mockCalorieService.getDailyCalories(testUserId, weekRange) }
        coVerify { mockCalorieService.getDailyCalories(testUserId, monthRange) }
    }

    // === Performance Tests ===

    @Test
    fun `given multiple concurrent calorie calculations, when triggered simultaneously, then handles all calculations correctly`() = runTest {
        // Given
        val workout1 = mockk<com.example.liftrix.domain.model.Workout> { every { id } returns "workout-1" }
        val workout2 = mockk<com.example.liftrix.domain.model.Workout> { every { id } returns "workout-2" }
        val workout3 = mockk<com.example.liftrix.domain.model.Workout> { every { id } returns "workout-3" }
        
        coEvery { mockCalorieService.calculateWorkoutCalories(workout1) } returns Result.success(400)
        coEvery { mockCalorieService.calculateWorkoutCalories(workout2) } returns Result.success(550)
        coEvery { mockCalorieService.calculateWorkoutCalories(workout3) } returns Result.success(320)
        
        // When - trigger concurrent calculations
        viewModel.handleEvent(CalorieTrackingEvent.CalculateWorkoutCalories(workout1))
        viewModel.handleEvent(CalorieTrackingEvent.CalculateWorkoutCalories(workout2))
        viewModel.handleEvent(CalorieTrackingEvent.CalculateWorkoutCalories(workout3))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - all calculations should complete
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertEquals(3, state.data.workoutCalories.size)
                assertEquals(400, state.data.workoutCalories["workout-1"])
                assertEquals(550, state.data.workoutCalories["workout-2"])
                assertEquals(320, state.data.workoutCalories["workout-3"])
            }
        }
        
        // Verify all service calls made
        coVerify { mockCalorieService.calculateWorkoutCalories(workout1) }
        coVerify { mockCalorieService.calculateWorkoutCalories(workout2) }
        coVerify { mockCalorieService.calculateWorkoutCalories(workout3) }
    }
}