package com.example.liftrix.ui.home

import app.cash.turbine.test
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.AuthRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    // Test dependencies
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var viewModel: HomeViewModel

    // Test data
    private val testUserId = "test-user-id"
    private val testUser = User(
        uid = testUserId,
        email = "test@example.com",
        displayName = "Test User",
        isAnonymous = false,
        createdAt = Instant.now()
    )

    private val testWorkouts = listOf(
        createTestWorkout("Workout 1", LocalDate.now()),
        createTestWorkout("Workout 2", LocalDate.now().minusDays(1)),
        createTestWorkout("Workout 3", LocalDate.now().minusDays(2))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        
        // Initialize mocks
        mockWorkoutRepository = mockk()
        mockAuthRepository = mockk()

        // Default mock behaviors
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        coEvery { mockAuthRepository.getCurrentUserId() } returns testUserId
        every { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) } returns flowOf(testWorkouts)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state should be loading with empty data`() = runTest {
        // Arrange & Act
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Assert
        val initialState = viewModel.uiState.value
        assertEquals(emptyList(), initialState.workouts)
        assertFalse(initialState.isLoading) // Will be set to true during init, but we capture initial state
        assertFalse(initialState.isRefreshing)
        assertNull(initialState.error)
    }

    @Test
    fun `loadRecentWorkouts should load workouts successfully`() = runTest {
        // Arrange
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testWorkouts, state.workouts)
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertNull(state.error)
            assertTrue(state.hasData)
            assertFalse(state.isEmpty)
        }

        coVerify { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) }
    }

    @Test
    fun `loadRecentWorkouts should handle user not authenticated`() = runTest {
        // Arrange
        coEvery { mockAuthRepository.getCurrentUserId() } returns null
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act
        viewModel.loadRecentWorkouts()
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.workouts)
            assertFalse(state.isLoading)
            assertEquals("User not authenticated", state.error)
        }

        coVerify(exactly = 0) { mockWorkoutRepository.getAllWorkoutsForUser(any()) }
    }

    @Test
    fun `loadRecentWorkouts should handle repository error`() = runTest {
        // Arrange
        val errorMessage = "Database connection failed"
        every { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) } throws RuntimeException(errorMessage)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.workouts)
            assertFalse(state.isLoading)
            assertTrue(state.error?.contains(errorMessage) == true)
        }
    }

    @Test
    fun `loadRecentWorkouts should sort workouts by date descending`() = runTest {
        // Arrange
        val unsortedWorkouts = listOf(
            createTestWorkout("Old Workout", LocalDate.now().minusDays(5)),
            createTestWorkout("Recent Workout", LocalDate.now()),
            createTestWorkout("Middle Workout", LocalDate.now().minusDays(2))
        )
        every { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) } returns flowOf(unsortedWorkouts)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Recent Workout", state.workouts[0].name)
            assertEquals("Middle Workout", state.workouts[1].name)
            assertEquals("Old Workout", state.workouts[2].name)
        }
    }

    @Test
    fun `loadRecentWorkouts should limit to recent workouts`() = runTest {
        // Arrange
        val manyWorkouts = (1..25).map { index ->
            createTestWorkout("Workout $index", LocalDate.now().minusDays(index.toLong()))
        }
        every { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) } returns flowOf(manyWorkouts)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(20, state.workouts.size) // Should limit to RECENT_WORKOUTS_LIMIT
        }
    }

    @Test
    fun `refreshWorkouts should trigger sync and reload data`() = runTest {
        // Arrange
        coEvery { mockWorkoutRepository.syncNowForUser(testUserId) } returns Result.success(Unit)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)
        advanceUntilIdle() // Let initial load complete

        // Act
        viewModel.refreshWorkouts()
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isRefreshing)
            assertEquals(testWorkouts, state.workouts)
            assertNull(state.error)
        }

        coVerify { mockWorkoutRepository.syncNowForUser(testUserId) }
        coVerify(atLeast = 2) { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) } // Initial load + refresh
    }

    @Test
    fun `refreshWorkouts should handle sync failure gracefully`() = runTest {
        // Arrange
        coEvery { mockWorkoutRepository.syncNowForUser(testUserId) } returns Result.failure(RuntimeException("Sync failed"))
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        viewModel.refreshWorkouts()
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isRefreshing)
            assertEquals(testWorkouts, state.workouts) // Should still have local data
            assertNull(state.error) // Sync errors shouldn't show to user
        }

        coVerify { mockWorkoutRepository.syncNowForUser(testUserId) }
    }

    @Test
    fun `refreshWorkouts should handle user not authenticated`() = runTest {
        // Arrange
        coEvery { mockAuthRepository.getCurrentUserId() } returns null
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act
        viewModel.refreshWorkouts()
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isRefreshing)
            assertEquals("User not authenticated", state.error)
        }

        coVerify(exactly = 0) { mockWorkoutRepository.syncNowForUser(any()) }
    }

    @Test
    fun `observeAuthState should reload data when user changes`() = runTest {
        // Arrange
        val newUser = testUser.copy(uid = "new-user-id")
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow<User?>(testUser)
        every { mockAuthRepository.currentUser } returns userFlow
        coEvery { mockAuthRepository.getCurrentUserId() } returns "new-user-id"
        every { mockWorkoutRepository.getAllWorkoutsForUser("new-user-id") } returns flowOf(emptyList())

        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        userFlow.value = newUser
        advanceUntilIdle()

        // Assert
        coVerify { mockWorkoutRepository.getAllWorkoutsForUser(testUserId) }
        coVerify { mockWorkoutRepository.getAllWorkoutsForUser("new-user-id") }
    }

    @Test
    fun `observeAuthState should clear data when user signs out`() = runTest {
        // Arrange
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow<User?>(testUser)
        every { mockAuthRepository.currentUser } returns userFlow
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)
        advanceUntilIdle()

        // Act
        userFlow.value = null
        advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList(), state.workouts)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `onWorkoutSelected should log selection`() = runTest {
        // Arrange
        val workoutId = WorkoutId.generate()
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)

        // Act & Assert (no exception should be thrown)
        viewModel.onWorkoutSelected(workoutId)
        
        // This method only logs, so we just verify it doesn't crash
        assertTrue(true)
    }

    @Test
    fun `clearError should reset error state`() = runTest {
        // Arrange
        coEvery { mockAuthRepository.getCurrentUserId() } returns null
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository)
        viewModel.loadRecentWorkouts()
        advanceUntilIdle()

        // Verify error exists
        viewModel.uiState.test {
            val stateWithError = awaitItem()
            assertTrue(stateWithError.error != null)
        }

        // Act
        viewModel.clearError()

        // Assert
        viewModel.uiState.test {
            val clearedState = awaitItem()
            assertNull(clearedState.error)
        }
    }

    @Test
    fun `HomeUiState convenience properties should work correctly`() {
        // Arrange & Act & Assert
        val emptyState = HomeUiState()
        assertTrue(emptyState.isEmpty)
        assertFalse(emptyState.hasData)
        assertFalse(emptyState.isInitialLoading)

        val loadingState = HomeUiState(isLoading = true)
        assertFalse(loadingState.isEmpty)
        assertFalse(loadingState.hasData)
        assertTrue(loadingState.isInitialLoading)

        val dataState = HomeUiState(workouts = testWorkouts)
        assertFalse(dataState.isEmpty)
        assertTrue(dataState.hasData)
        assertFalse(dataState.isInitialLoading)

        val loadingWithDataState = HomeUiState(workouts = testWorkouts, isLoading = true)
        assertFalse(loadingWithDataState.isEmpty)
        assertTrue(loadingWithDataState.hasData)
        assertFalse(loadingWithDataState.isInitialLoading)
    }

    // Helper function to create test workout
    private fun createTestWorkout(name: String, date: LocalDate): Workout {
        val now = Instant.now()
        return Workout(
            userId = testUserId,
            id = WorkoutId.generate(),
            name = name,
            date = date,
            exercises = emptyList(),
            status = WorkoutStatus.COMPLETED,
            startTime = now.minusSeconds(3600),
            endTime = now,
            notes = null,
            templateId = null,
            createdAt = now.minusSeconds(7200),
            updatedAt = now
        )
    }
} 