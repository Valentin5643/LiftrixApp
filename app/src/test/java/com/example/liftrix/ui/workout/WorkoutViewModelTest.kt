package com.example.liftrix.ui.workout

import app.cash.turbine.test
import com.example.liftrix.TestDataFactory
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.SaveWorkoutUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ViewModelTestUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var saveWorkoutUseCase: SaveWorkoutUseCase
    private lateinit var syncManager: SyncManager
    private lateinit var workoutViewModel: WorkoutViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        workoutRepository = mockk()
        authRepository = mockk()
        saveWorkoutUseCase = mockk()
        syncManager = mockk()
        
        // Default mock behaviors
        every { authRepository.currentUser } returns flowOf(TestDataFactory.testUser)
        every { workoutRepository.getAllWorkoutsForUser(any()) } returns flowOf(emptyList())
        every { syncManager.getSyncStatus() } returns flowOf(SyncStatus.Idle)
        coEvery { workoutRepository.getUnsyncedCountForUser(any()) } returns 0
        
        workoutViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
    }

    @Test
    fun `initial state should be loading`() = runTest {
        workoutViewModel.uiState.test {
            val initialState = awaitItem()
            
            assertEquals(emptyList<Workout>(), initialState.workouts)
            assertEquals(SyncStatus.Idle, initialState.syncStatus)
            assertEquals(0, initialState.unsyncedCount)
            assertEquals(true, initialState.isLoading)
            assertEquals(false, initialState.isSaving)
            assertNull(initialState.errorMessage)
        }
    }

    @Test
    fun `should observe authenticated user and load workouts`() = runTest {
        val workouts = listOf(TestDataFactory.sampleWorkout, TestDataFactory.completedWorkout)
        every { workoutRepository.getAllWorkoutsForUser(TestDataFactory.testUser.uid) } returns flowOf(workouts)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            val loadedState = awaitItem()
            assertEquals(workouts, loadedState.workouts)
            assertEquals(false, loadedState.isLoading)
        }
    }

    @Test
    fun `should clear workouts when user logs out`() = runTest {
        every { authRepository.currentUser } returns flowOf(null)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            val state = awaitItem()
            
            assertEquals(emptyList<Workout>(), state.workouts)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun `should update sync status`() = runTest {
        every { syncManager.getSyncStatus() } returns flowOf(SyncStatus.Syncing)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            val state = awaitItem()
            assertEquals(SyncStatus.Syncing, state.syncStatus)
        }
    }

    @Test
    fun `should handle sync success`() = runTest {
        val successStatus = SyncStatus.Success(5)
        every { syncManager.getSyncStatus() } returns flowOf(successStatus)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            val state = awaitItem()
            assertEquals(successStatus, state.syncStatus)
        }
    }

    @Test
    fun `should handle sync error`() = runTest {
        val errorStatus = SyncStatus.Error("Network error")
        every { syncManager.getSyncStatus() } returns flowOf(errorStatus)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            val state = awaitItem()
            assertEquals(errorStatus, state.syncStatus)
            assertEquals("Sync failed: Network error", state.errorMessage)
        }
    }

    @Test
    fun `saveWorkout should call use case and update UI state`() = runTest {
        val workout = TestDataFactory.sampleWorkout
        coEvery { saveWorkoutUseCase(workout) } returns Result.success(Unit)
        
        workoutViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            workoutViewModel.saveWorkout(workout)
            
            // Should show saving state
            val savingState = awaitItem()
            assertEquals(true, savingState.isSaving)
            
            // Should complete saving
            val completedState = awaitItem()
            assertEquals(false, completedState.isSaving)
            assertNull(completedState.errorMessage)
        }
        
        coVerify { saveWorkoutUseCase(workout) }
    }

    @Test
    fun `saveWorkout should handle failure`() = runTest {
        val workout = TestDataFactory.sampleWorkout
        val exception = Exception("Save failed")
        coEvery { saveWorkoutUseCase(workout) } returns Result.failure(exception)
        
        workoutViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            workoutViewModel.saveWorkout(workout)
            
            // Should show saving state
            val savingState = awaitItem()
            assertEquals(true, savingState.isSaving)
            
            // Should show error
            val errorState = awaitItem()
            assertEquals(false, errorState.isSaving)
            assertEquals("Failed to save workout: Save failed", errorState.errorMessage)
        }
    }

    @Test
    fun `syncNow should call repository when user is authenticated`() = runTest {
        coEvery { workoutRepository.syncNowForUser(TestDataFactory.testUser.uid) } returns Result.success(Unit)
        
        workoutViewModel.syncNow()
        
        coVerify { workoutRepository.syncNowForUser(TestDataFactory.testUser.uid) }
    }

    @Test
    fun `syncNow should show error when user is not authenticated`() = runTest {
        every { authRepository.currentUser } returns flowOf(null)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            newViewModel.syncNow()
            
            val state = awaitItem()
            assertEquals("Cannot sync: user not authenticated", state.errorMessage)
        }
    }

    @Test
    fun `syncNow should handle sync failure`() = runTest {
        val exception = Exception("Sync failed")
        coEvery { workoutRepository.syncNowForUser(TestDataFactory.testUser.uid) } returns Result.failure(exception)
        
        workoutViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            workoutViewModel.syncNow()
            
            val state = awaitItem()
            assertEquals("Failed to start sync: Sync failed", state.errorMessage)
        }
    }

    @Test
    fun `getUnsyncedCount should update unsynced count`() = runTest {
        coEvery { workoutRepository.getUnsyncedCountForUser(TestDataFactory.testUser.uid) } returns 3
        
        workoutViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            workoutViewModel.getUnsyncedCount()
            
            val state = awaitItem()
            assertEquals(3, state.unsyncedCount)
        }
    }

    @Test
    fun `clearError should clear error message`() = runTest {
        // First set an error via sync status
        val errorStatus = SyncStatus.Error("Test error")
        every { syncManager.getSyncStatus() } returns flowOf(errorStatus)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            val errorState = awaitItem()
            assertEquals("Sync failed: Test error", errorState.errorMessage)
            
            newViewModel.clearError()
            
            val clearedState = awaitItem()
            assertNull(clearedState.errorMessage)
        }
    }

    @Test
    fun `currentUser should reflect auth repository state`() = runTest {
        workoutViewModel.currentUser.test {
            val user = awaitItem()
            assertEquals(TestDataFactory.testUser, user)
        }
    }

    @Test
    fun `currentUser should be null when not authenticated`() = runTest {
        every { authRepository.currentUser } returns flowOf(null)
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.currentUser.test {
            val user = awaitItem()
            assertNull(user)
        }
    }

    @Test
    fun `should handle user authentication state changes`() = runTest {
        val userFlow = flowOf(null, TestDataFactory.testUser, null)
        every { authRepository.currentUser } returns userFlow
        
        val newViewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            saveWorkoutUseCase = saveWorkoutUseCase,
            syncManager = syncManager
        )
        
        newViewModel.currentUser.test {
            assertNull(awaitItem()) // Initially null
            assertEquals(TestDataFactory.testUser, awaitItem()) // Then authenticated
            assertNull(awaitItem()) // Then logged out
        }
    }
} 