package com.example.liftrix.ui.workout.active

import app.cash.turbine.test
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.service.FirebasePresenceService
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    private lateinit var timerServiceManager: TimerServiceManager
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var presenceService: FirebasePresenceService
    private lateinit var viewModel: ActiveWorkoutViewModel
    
    private val testDispatcher = StandardTestDispatcher()
    private val testUser = User(id = "test-user", email = "test@example.com")
    private val testWorkout = Workout(
        id = WorkoutId.generate(),
        userId = testUser.id,
        name = "Test Workout",
        startTime = Clock.System.now(),
        exercises = emptyList(),
        isCompleted = false
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        timerServiceManager = mockk(relaxed = true)
        workoutRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        presenceService = mockk(relaxed = true)
        
        // Setup default mock behavior
        every { timerServiceManager.connectionState } returns flowOf(TimerServiceManager.ConnectionState.Connected)
        every { timerServiceManager.timerState } returns flowOf(WorkoutTimerService.TimerServiceState())
        every { authRepository.currentUser } returns flowOf(testUser)
        coEvery { timerServiceManager.bindService() } returns Result.success(Unit)
        coEvery { workoutRepository.getActiveWorkoutForUser(testUser.id) } returns null
        coEvery { presenceService.updateWorkoutStatus(any()) } returns Result.success(Unit)
        
        viewModel = ActiveWorkoutViewModel(
            timerServiceManager = timerServiceManager,
            workoutRepository = workoutRepository,
            authRepository = authRepository,
            presenceService = presenceService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            
            assertFalse(initialState.isLoading)
            assertFalse(initialState.isSaving)
            assertFalse(initialState.isTimerServiceConnected)
            assertFalse(initialState.isSessionActive)
            assertFalse(initialState.hasActiveWorkout)
            assertFalse(initialState.hasUnsavedChanges)
            assertNull(initialState.currentWorkout)
            assertEquals("00:00", initialState.formattedSessionTime)
            assertEquals("", initialState.formattedRestTime)
            assertNull(initialState.error)
            assertNull(initialState.connectionError)
            assertNull(initialState.successMessage)
        }
    }

    @Test
    fun `bindService is called on initialization`() = runTest {
        advanceUntilIdle()
        coVerify { timerServiceManager.bindService() }
    }

    @Test
    fun `loadActiveWorkout is called on initialization`() = runTest {
        advanceUntilIdle()
        coVerify { workoutRepository.getActiveWorkoutForUser(testUser.id) }
    }

    @Test
    fun `timer service connection state is observed`() = runTest {
        every { timerServiceManager.connectionState } returns flowOf(
            TimerServiceManager.ConnectionState.Connecting,
            TimerServiceManager.ConnectionState.Connected
        )
        
        val newViewModel = ActiveWorkoutViewModel(timerServiceManager, workoutRepository, authRepository, presenceService)
        
        newViewModel.uiState.test {
            skipItems(1) // skip initial state
            val connectedState = awaitItem()
            assertTrue(connectedState.isTimerServiceConnected)
        }
    }

    @Test
    fun `timer state is observed and formatted correctly`() = runTest {
        val sessionState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionRunning(Clock.System.now(), 90L),
            isRunning = true,
            sessionDurationSeconds = 90L
        )
        
        every { timerServiceManager.timerState } returns flowOf(sessionState)
        
        val newViewModel = ActiveWorkoutViewModel(timerServiceManager, workoutRepository, authRepository, presenceService)
        
        newViewModel.uiState.test {
            skipItems(1) // skip initial state
            val updatedState = awaitItem()
            
            assertTrue(updatedState.isSessionActive)
            assertEquals("1:30", updatedState.formattedSessionTime)
        }
    }

    @Test
    fun `startSession calls timer service and creates new workout`() = runTest {
        coEvery { timerServiceManager.startSession() } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        coVerify { timerServiceManager.startSession() }
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.currentWorkout)
            assertTrue(state.hasActiveWorkout)
            assertTrue(state.hasUnsavedChanges)
        }
    }

    @Test
    fun `startSession handles service error`() = runTest {
        val error = RuntimeException("Service error")
        coEvery { timerServiceManager.startSession() } returns Result.failure(error)
        
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to start session: Service error", state.error)
        }
    }

    @Test
    fun `pauseSession calls timer service`() = runTest {
        coEvery { timerServiceManager.pauseTimer() } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.PauseSession)
        advanceUntilIdle()
        
        coVerify { timerServiceManager.pauseTimer() }
    }

    @Test
    fun `resumeSession calls timer service`() = runTest {
        coEvery { timerServiceManager.resumeTimer() } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.ResumeSession)
        advanceUntilIdle()
        
        coVerify { timerServiceManager.resumeTimer() }
    }

    @Test
    fun `stopSession calls timer service and saves workout`() = runTest {
        // Setup existing workout
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        coEvery { timerServiceManager.stopTimer() } returns Result.success(Unit)
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.StopSession)
        advanceUntilIdle()
        
        coVerify { timerServiceManager.stopTimer() }
        coVerify { workoutRepository.saveWorkout(any()) }
    }

    @Test
    fun `startRest calls timer service with rest timer`() = runTest {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        coEvery { timerServiceManager.startRestTimer(restTimer) } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.StartRest(restTimer))
        advanceUntilIdle()
        
        coVerify { timerServiceManager.startRestTimer(restTimer) }
    }

    @Test
    fun `skipRest calls timer service`() = runTest {
        coEvery { timerServiceManager.skipRest() } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.SkipRest)
        advanceUntilIdle()
        
        coVerify { timerServiceManager.skipRest() }
    }

    @Test
    fun `addExercise adds exercise to workout`() = runTest {
        // Setup existing workout
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        val exercise = createTestExercise()
        viewModel.onEvent(ActiveWorkoutEvent.AddExercise(exercise))
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.currentWorkout?.exercises?.size)
            assertTrue(state.hasUnsavedChanges)
        }
    }

    @Test
    fun `removeExercise removes exercise from workout`() = runTest {
        // Setup workout with exercise
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        val exercise = createTestExercise()
        viewModel.onEvent(ActiveWorkoutEvent.AddExercise(exercise))
        viewModel.onEvent(ActiveWorkoutEvent.RemoveExercise(0))
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.currentWorkout?.exercises?.size)
            assertTrue(state.hasUnsavedChanges)
        }
    }

    @Test
    fun `addSet adds set to exercise`() = runTest {
        // Setup workout with exercise
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        val exercise = createTestExercise()
        viewModel.onEvent(ActiveWorkoutEvent.AddExercise(exercise))
        viewModel.onEvent(ActiveWorkoutEvent.AddSet(0))
        
        viewModel.uiState.test {
            val state = awaitItem()
            val exerciseWithSet = state.currentWorkout?.exercises?.get(0)
            assertEquals(1, exerciseWithSet?.sets?.size)
            assertTrue(state.hasUnsavedChanges)
        }
    }

    @Test
    fun `updateSet updates exercise set`() = runTest {
        // Setup workout with exercise and set
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        val exercise = createTestExercise()
        viewModel.onEvent(ActiveWorkoutEvent.AddExercise(exercise))
        viewModel.onEvent(ActiveWorkoutEvent.AddSet(0))
        
        val updatedSet = ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = 1,
            reps = Reps.of(10),
            weight = Weight.fromKilograms(50.0)
        )
        viewModel.onEvent(ActiveWorkoutEvent.UpdateSet(0, 0, updatedSet))
        
        viewModel.uiState.test {
            val state = awaitItem()
            val exerciseSet = state.currentWorkout?.exercises?.get(0)?.sets?.get(0)
            assertEquals(updatedSet, exerciseSet)
            assertTrue(state.hasUnsavedChanges)
        }
    }

    @Test
    fun `removeSet removes set from exercise`() = runTest {
        // Setup workout with exercise and set
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        val exercise = createTestExercise()
        viewModel.onEvent(ActiveWorkoutEvent.AddExercise(exercise))
        viewModel.onEvent(ActiveWorkoutEvent.AddSet(0))
        viewModel.onEvent(ActiveWorkoutEvent.RemoveSet(0, 0))
        
        viewModel.uiState.test {
            val state = awaitItem()
            val exerciseSets = state.currentWorkout?.exercises?.get(0)?.sets
            assertEquals(0, exerciseSets?.size)
            assertTrue(state.hasUnsavedChanges)
        }
    }

    @Test
    fun `saveWorkout saves to repository`() = runTest {
        // Setup existing workout
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.SaveWorkout)
        advanceUntilIdle()
        
        coVerify { workoutRepository.saveWorkout(any()) }
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.hasUnsavedChanges)
            assertEquals("Workout saved successfully", state.successMessage)
        }
    }

    @Test
    fun `saveWorkout handles repository error`() = runTest {
        // Setup existing workout
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        val error = RuntimeException("Save error")
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.failure(error)
        
        viewModel.onEvent(ActiveWorkoutEvent.SaveWorkout)
        advanceUntilIdle()
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to save workout: Save error", state.error)
        }
    }

    @Test
    fun `loadWorkout loads from repository`() = runTest {
        val workoutId = WorkoutId.generate()
        coEvery { workoutRepository.getWorkoutByIdForUser(workoutId, testUser.id) } returns testWorkout
        
        viewModel.onEvent(ActiveWorkoutEvent.LoadWorkout(workoutId))
        advanceUntilIdle()
        
        coVerify { workoutRepository.getWorkoutByIdForUser(workoutId, testUser.id) }
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testWorkout, state.currentWorkout)
            assertTrue(state.hasActiveWorkout)
        }
    }

    @Test
    fun `clearError clears error state`() = runTest {
        // Set error state first
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        coEvery { timerServiceManager.startSession() } returns Result.failure(RuntimeException("Error"))
        advanceUntilIdle()
        
        viewModel.onEvent(ActiveWorkoutEvent.ClearError)
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
            assertNull(state.connectionError)
        }
    }

    @Test
    fun `dismissMessage clears success message`() = runTest {
        // Setup workflow to get success message
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        viewModel.onEvent(ActiveWorkoutEvent.SaveWorkout)
        advanceUntilIdle()
        
        viewModel.onEvent(ActiveWorkoutEvent.DismissMessage)
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.successMessage)
        }
    }

    @Test
    fun `time formatting works correctly`() = runTest {
        // Test session time formatting
        val sessionState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.SessionRunning(Clock.System.now(), 3665L), // 1:01:05
            isRunning = true,
            sessionDurationSeconds = 3665L
        )
        
        every { timerServiceManager.timerState } returns flowOf(sessionState)
        
        val newViewModel = ActiveWorkoutViewModel(timerServiceManager, workoutRepository, authRepository, presenceService)
        
        newViewModel.uiState.test {
            skipItems(1) // skip initial state
            val state = awaitItem()
            assertEquals("1:01:05", state.formattedSessionTime)
        }
    }

    @Test
    fun `rest time formatting works correctly`() = runTest {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        val restState = WorkoutTimerService.TimerServiceState(
            timerState = WorkoutTimerService.TimerState.RestActive(restTimer, 45),
            isRunning = true,
            restRemainingSeconds = 45
        )
        
        every { timerServiceManager.timerState } returns flowOf(restState)
        
        val newViewModel = ActiveWorkoutViewModel(timerServiceManager, workoutRepository, authRepository, presenceService)
        
        newViewModel.uiState.test {
            skipItems(1) // skip initial state
            val state = awaitItem()
            assertEquals("0:45", state.formattedRestTime)
        }
    }

    @Test
    fun `onCleared unbinds timer service`() = runTest {
        viewModel.onCleared()
        verify { timerServiceManager.unbindService() }
    }

    @Test
    fun `authentication error is handled properly`() = runTest {
        every { authRepository.currentUser } returns flowOf(null)
        
        val newViewModel = ActiveWorkoutViewModel(timerServiceManager, workoutRepository, authRepository, presenceService)
        
        newViewModel.uiState.test {
            skipItems(1) // skip initial state
            val state = awaitItem()
            assertEquals("User not authenticated", state.error)
        }
    }

    @Test
    fun `timer service connection error is handled`() = runTest {
        val connectionError = RuntimeException("Connection failed")
        every { timerServiceManager.connectionState } returns flowOf(
            TimerServiceManager.ConnectionState.Error(connectionError)
        )
        
        val newViewModel = ActiveWorkoutViewModel(timerServiceManager, workoutRepository, authRepository, presenceService)
        
        newViewModel.uiState.test {
            skipItems(1) // skip initial state
            val state = awaitItem()
            assertEquals("Connection failed", state.connectionError)
        }
    }

    @Test
    fun `startSession updates presence status to WORKING_OUT`() = runTest {
        coEvery { timerServiceManager.startSession() } returns Result.success(Unit)
        coEvery { presenceService.updateWorkoutStatus(any()) } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        // Verify presence service was called with workout ID
        coVerify { presenceService.updateWorkoutStatus(any()) }
    }

    @Test
    fun `startSession handles presence service error gracefully`() = runTest {
        coEvery { timerServiceManager.startSession() } returns Result.success(Unit)
        coEvery { presenceService.updateWorkoutStatus(any()) } returns Result.failure(RuntimeException("Presence error"))
        
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        // Workout should still be created despite presence error
        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.currentWorkout)
            assertTrue(state.hasActiveWorkout)
        }
    }

    @Test
    fun `stopSession clears presence status`() = runTest {
        // Setup active workout first
        coEvery { timerServiceManager.startSession() } returns Result.success(Unit)
        coEvery { timerServiceManager.stopTimer() } returns Result.success(Unit)
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        viewModel.onEvent(ActiveWorkoutEvent.StopSession)
        advanceUntilIdle()
        
        // Verify presence service was called with null to clear status
        coVerify { presenceService.updateWorkoutStatus(null) }
    }

    @Test
    fun `pauseSession updates presence to ONLINE`() = runTest {
        coEvery { timerServiceManager.pauseTimer() } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.PauseSession)
        advanceUntilIdle()
        
        coVerify { presenceService.updateWorkoutStatus(null) }
    }

    @Test
    fun `resumeSession updates presence back to WORKING_OUT`() = runTest {
        // Setup active workout first
        coEvery { timerServiceManager.startSession() } returns Result.success(Unit)
        coEvery { timerServiceManager.resumeTimer() } returns Result.success(Unit)
        
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        viewModel.onEvent(ActiveWorkoutEvent.ResumeSession)
        advanceUntilIdle()
        
        // Should be called twice - once for start, once for resume
        coVerify(exactly = 2) { presenceService.updateWorkoutStatus(any()) }
    }

    @Test
    fun `presence service errors do not block workout functionality`() = runTest {
        coEvery { timerServiceManager.startSession() } returns Result.success(Unit)
        coEvery { timerServiceManager.stopTimer() } returns Result.success(Unit)
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        coEvery { presenceService.updateWorkoutStatus(any()) } returns Result.failure(RuntimeException("Network error"))
        
        // Start workout
        viewModel.onEvent(ActiveWorkoutEvent.StartSession)
        advanceUntilIdle()
        
        // Stop workout
        viewModel.onEvent(ActiveWorkoutEvent.StopSession)
        advanceUntilIdle()
        
        // Verify workout functionality still works
        coVerify { timerServiceManager.startSession() }
        coVerify { timerServiceManager.stopTimer() }
        coVerify { workoutRepository.saveWorkout(any()) }
    }

    /**
     * Helper function to create a test Exercise object
     */
    private fun createTestExercise(): Exercise {
        val libraryExercise = ExerciseLibrary(
            id = "test-exercise",
            name = "Test Exercise",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "Test Movement",
            difficultyLevel = 3,
            instructions = "Test instructions",
            isCompound = false,
            searchableTerms = listOf("test")
        )
        
        return Exercise(
            id = ExerciseId.generate(),
            workoutId = WorkoutId.generate(),
            libraryExercise = libraryExercise,
            orderIndex = 0,
            targetSets = null,
            targetReps = null,
            targetWeight = null,
            targetTime = null,
            targetDistance = null,
            sets = emptyList(),
            notes = null,
            createdAt = java.time.Instant.now()
        )
    }
}