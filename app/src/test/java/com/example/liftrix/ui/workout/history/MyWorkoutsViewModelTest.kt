package com.example.liftrix.ui.workout.history

import app.cash.turbine.test
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for MyWorkoutsViewModel
 * Tests MVI pattern, state management, pagination, and error handling
 * 
 * Follows established codebase testing patterns with MockK, Turbine, and coroutines testing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyWorkoutsViewModelTest {

    private lateinit var mockGetWorkoutHistoryUseCase: GetWorkoutHistoryUseCase
    private lateinit var viewModel: MyWorkoutsViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testWorkoutSummary1 = WorkoutSummary(
        id = WorkoutId.generate(),
        userId = "test-user-id",
        name = "Test Workout 1",
        date = LocalDate.now().minusDays(1),
        duration = Duration.ofMinutes(45),
        exerciseCount = 5,
        completedSets = 15,
        totalSets = 15,
        status = WorkoutStatus.COMPLETED,
        completionPercentage = 100.0
    )

    private val testWorkoutSummary2 = WorkoutSummary(
        id = WorkoutId.generate(),
        userId = "test-user-id",
        name = "Test Workout 2",
        date = LocalDate.now().minusDays(2),
        duration = Duration.ofMinutes(60),
        exerciseCount = 6,
        completedSets = 18,
        totalSets = 18,
        status = WorkoutStatus.COMPLETED,
        completionPercentage = 100.0
    )

    private val testWorkoutSummaries = listOf(testWorkoutSummary1, testWorkoutSummary2)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockGetWorkoutHistoryUseCase = mockk()
        
        // Default successful behavior
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.success(testWorkoutSummaries))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state should be loading with default values`() = runTest {
        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)

        // Assert
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(emptyList(), initialState.workouts)
            assertTrue(initialState.isLoading)
            assertFalse(initialState.isLoadingMore)
            assertTrue(initialState.hasMoreData)
            assertNull(initialState.error)
            assertFalse(initialState.shouldShowEmptyState)
            assertFalse(initialState.shouldShowError)
            assertFalse(initialState.shouldShowContent)
            assertEquals(0, initialState.workoutCount)
        }
    }

    @Test
    fun `initialization should call loadWorkouts automatically`() = runTest {
        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        coVerify { mockGetWorkoutHistoryUseCase.execute(20, 0) }
    }

    @Test
    fun `successful initialization should load workouts and update state`() = runTest {
        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testWorkoutSummaries, state.workouts)
            assertFalse(state.isLoading)
            assertFalse(state.isLoadingMore)
            assertTrue(state.hasMoreData) // PAGE_SIZE workouts returned = has more
            assertNull(state.error)
            assertTrue(state.shouldShowContent)
            assertFalse(state.shouldShowEmptyState)
            assertFalse(state.shouldShowError)
            assertEquals(2, state.workoutCount)
        }
    }

    @Test
    fun `handleEvent LoadWorkouts should reload data`() = runTest {
        // Arrange
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act
        viewModel.handleEvent(UiEvent.LoadWorkouts)
        advanceUntilIdle()

        // Assert
        coVerify(atLeast = 2) { mockGetWorkoutHistoryUseCase.execute(20, 0) }
    }

    @Test
    fun `handleEvent RefreshWorkouts should refresh data from beginning`() = runTest {
        // Arrange
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act
        viewModel.handleEvent(UiEvent.RefreshWorkouts)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testWorkoutSummaries, state.workouts)
            assertFalse(state.isLoading)
            assertFalse(state.isLoadingMore)
        }
        
        coVerify(atLeast = 2) { mockGetWorkoutHistoryUseCase.execute(20, 0) }
    }

    @Test
    fun `handleEvent LoadMoreWorkouts should load next page`() = runTest {
        // Arrange
        val nextPageWorkouts = listOf(
            testWorkoutSummary1.copy(
                id = WorkoutId.generate(),
                name = "Next Page Workout"
            )
        )
        
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 0) 
        } returns flowOf(Result.success(testWorkoutSummaries))
        
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 20) 
        } returns flowOf(Result.success(nextPageWorkouts))

        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act
        viewModel.handleEvent(UiEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testWorkoutSummaries + nextPageWorkouts, state.workouts)
            assertFalse(state.isLoading)
            assertFalse(state.isLoadingMore)
            assertEquals(3, state.workoutCount)
        }
        
        coVerify { mockGetWorkoutHistoryUseCase.execute(20, 20) }
    }

    @Test
    fun `handleEvent LoadMoreWorkouts should not load if already loading`() = runTest {
        // Arrange
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Set loading state
        viewModel.handleEvent(UiEvent.RefreshWorkouts)

        // Act - try to load more while loading
        viewModel.handleEvent(UiEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Assert - should not call use case for pagination
        coVerify(exactly = 0) { mockGetWorkoutHistoryUseCase.execute(20, 20) }
    }

    @Test
    fun `handleEvent LoadMoreWorkouts should not load if no more data`() = runTest {
        // Arrange - return empty list indicating no more data
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.success(emptyList()))

        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act
        viewModel.handleEvent(UiEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.hasMoreData)
        }
    }

    @Test
    fun `handleEvent ClearError should remove error from state`() = runTest {
        // Arrange - create error state
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.failure(Exception("Test error")))

        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Verify error exists
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertTrue(errorState.error?.contains("Test error") == true)
        }

        // Act
        viewModel.handleEvent(UiEvent.ClearError)

        // Assert
        viewModel.uiState.test {
            val clearedState = awaitItem()
            assertNull(clearedState.error)
        }
    }

    @Test
    fun `authentication error should show user-friendly message`() = runTest {
        // Arrange
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.failure(IllegalStateException("User not authenticated")))

        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Please sign in to view your workout history", state.error)
            assertTrue(state.shouldShowError)
            assertFalse(state.shouldShowContent)
            assertFalse(state.shouldShowEmptyState)
        }
    }

    @Test
    fun `network error should show connectivity message`() = runTest {
        // Arrange
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.failure(Exception("network connectivity failed")))

        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Network error. Please check your connection and try again", state.error)
        }
    }

    @Test
    fun `generic error should show error with message`() = runTest {
        // Arrange
        val errorMessage = "Database error"
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.failure(Exception(errorMessage)))

        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to load workouts: $errorMessage", state.error)
        }
    }

    @Test
    fun `flow error should be handled gracefully`() = runTest {
        // Arrange
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flow { throw RuntimeException("Flow error") }

        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.error?.contains("Flow error") == true)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `empty workout list should show empty state`() = runTest {
        // Arrange
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(any(), any()) 
        } returns flowOf(Result.success(emptyList()))

        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.workouts)
            assertFalse(state.isLoading)
            assertFalse(state.hasMoreData)
            assertNull(state.error)
            assertTrue(state.shouldShowEmptyState)
            assertFalse(state.shouldShowError)
            assertFalse(state.shouldShowContent)
        }
    }

    @Test
    fun `pagination hasMoreData logic should work correctly`() = runTest {
        // Arrange - return exactly PAGE_SIZE (20) workouts to indicate more data
        val fullPageWorkouts = (1..20).map { index ->
            testWorkoutSummary1.copy(
                id = WorkoutId.generate(),
                name = "Workout $index"
            )
        }
        
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 0) 
        } returns flowOf(Result.success(fullPageWorkouts))

        // Act
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.hasMoreData) // Full page indicates more data
            assertEquals(20, state.workoutCount)
        }
    }

    @Test
    fun `pagination should update offset correctly`() = runTest {
        // Arrange
        val firstPage = listOf(testWorkoutSummary1)
        val secondPage = listOf(testWorkoutSummary2)
        
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 0) 
        } returns flowOf(Result.success(firstPage))
        
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 20) 
        } returns flowOf(Result.success(secondPage))

        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act - load more
        viewModel.handleEvent(UiEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Assert
        coVerify { mockGetWorkoutHistoryUseCase.execute(20, 0) }
        coVerify { mockGetWorkoutHistoryUseCase.execute(20, 20) }
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(firstPage + secondPage, state.workouts)
        }
    }

    @Test
    fun `loading more workouts should show isLoadingMore state`() = runTest {
        // Arrange
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act & Assert
        viewModel.uiState.test {
            // Skip initial loaded state
            skipItems(1)
            
            viewModel.handleEvent(UiEvent.LoadMoreWorkouts)
            
            // Should see loading more state
            val loadingMoreState = awaitItem()
            assertTrue(loadingMoreState.isLoadingMore)
            assertFalse(loadingMoreState.isLoading)
        }
    }

    @Test
    fun `refresh should reset offset and clear existing workouts`() = runTest {
        // Arrange
        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act
        viewModel.handleEvent(UiEvent.RefreshWorkouts)

        // Assert
        viewModel.uiState.test {
            // Should see loading state during refresh
            val refreshState = awaitItem()
            assertTrue(refreshState.isLoading)
            assertEquals(emptyList(), refreshState.workouts) // Cleared during refresh
        }
    }

    @Test
    fun `error during load more should not affect existing workouts`() = runTest {
        // Arrange
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 0) 
        } returns flowOf(Result.success(testWorkoutSummaries))
        
        coEvery { 
            mockGetWorkoutHistoryUseCase.execute(20, 20) 
        } returns flowOf(Result.failure(Exception("Load more error")))

        viewModel = MyWorkoutsViewModel(mockGetWorkoutHistoryUseCase)
        advanceUntilIdle()

        // Act
        viewModel.handleEvent(UiEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testWorkoutSummaries, state.workouts) // Original workouts preserved
            assertFalse(state.isLoadingMore)
            assertTrue(state.error?.contains("Failed to load more workouts") == true)
        }
    }

    @Test
    fun `UiState computed properties should work correctly`() = runTest {
        // Test shouldShowEmptyState
        val emptyState = UiState(
            workouts = emptyList(),
            isLoading = false,
            error = null
        )
        assertTrue(emptyState.shouldShowEmptyState)
        assertFalse(emptyState.shouldShowError)
        assertFalse(emptyState.shouldShowContent)

        // Test shouldShowError
        val errorState = UiState(
            workouts = emptyList(),
            isLoading = false,
            error = "Test error"
        )
        assertFalse(errorState.shouldShowEmptyState)
        assertTrue(errorState.shouldShowError)
        assertFalse(errorState.shouldShowContent)

        // Test shouldShowContent
        val contentState = UiState(
            workouts = testWorkoutSummaries,
            isLoading = false,
            error = null
        )
        assertFalse(contentState.shouldShowEmptyState)
        assertFalse(contentState.shouldShowError)
        assertTrue(contentState.shouldShowContent)
        assertEquals(2, contentState.workoutCount)
    }
} 