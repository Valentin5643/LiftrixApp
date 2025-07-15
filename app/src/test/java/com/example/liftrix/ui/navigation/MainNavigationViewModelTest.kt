package com.example.liftrix.ui.navigation

import app.cash.turbine.test
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainNavigationViewModelTest {

    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var viewModel: MainNavigationViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val mockUser = User(
        uid = "test-uid",
        email = "test@example.com",
        displayName = "Test User",
        isAnonymous = false
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockAuthRepository = mockk()
        mockWorkoutRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be loading with default values`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(null)

        // Act
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)

        // Assert
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(AuthenticationState.Loading, initialState.authenticationState)
            assertEquals(MainNavigationItem.HOME, initialState.selectedTab)
            assertFalse(initialState.isWorkoutCreationModalVisible)
            assertNull(initialState.navigationDestination)
            assertTrue(initialState.isLoading)
            assertNull(initialState.error)
        }
    }

    @Test
    fun `should update auth state to authenticated when user is present`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)

        // Act
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AuthenticationState.Authenticated(mockUser), state.authenticationState)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `should update auth state to unauthenticated when user is null`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(null)

        // Act
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AuthenticationState.Unauthenticated, state.authenticationState)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `onEvent NavigateToTab should update selected tab and clear navigation destination`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.NavigateToTab(MainNavigationItem.WORKOUT))
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(MainNavigationItem.WORKOUT, state.selectedTab)
            assertFalse(state.isWorkoutCreationModalVisible) // Should hide modal when navigating
            assertNull(state.navigationDestination) // Should clear navigation destination
        }
    }

    @Test
    fun `onEvent NavigateToTab should hide workout creation modal`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        
        // First show the modal
        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.NavigateToTab(MainNavigationItem.PROGRESS))
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(MainNavigationItem.PROGRESS, state.selectedTab)
            assertFalse(state.isWorkoutCreationModalVisible)
        }
    }

    @Test
    fun `onEvent ShowWorkoutCreationModal should make modal visible`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isWorkoutCreationModalVisible)
        }
    }

    @Test
    fun `onEvent HideWorkoutCreationModal should make modal invisible`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        
        // First show the modal
        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.HideWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isWorkoutCreationModalVisible)
        }
    }

    @Test
    fun `onEvent StartTemplateWorkout should hide modal and set navigation destination`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        
        // First show the modal
        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.StartTemplateWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isWorkoutCreationModalVisible)
            assertEquals(WorkoutCreationDestination.TemplateWorkout, state.navigationDestination)
        }
    }

    @Test
    fun `onEvent StartCustomWorkout should hide modal and set navigation destination`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        
        // First show the modal
        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.StartCustomWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isWorkoutCreationModalVisible)
            assertEquals(WorkoutCreationDestination.CustomWorkout, state.navigationDestination)
        }
    }

    @Test
    fun `onEvent ClearNavigationDestination should clear navigation destination`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        
        // First set a navigation destination
        viewModel.onEvent(MainNavigationEvent.StartTemplateWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.ClearNavigationDestination)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.navigationDestination)
        }
    }

    @Test
    fun `onEvent SignOut should call repository signOut and handle success`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        coEvery { mockAuthRepository.signOut() } returns Result.success(Unit)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.SignOut)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        coVerify { mockAuthRepository.signOut() }
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
        }
    }

    @Test
    fun `onEvent SignOut should handle failure and set error`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        val errorMessage = "Network error"
        coEvery { mockAuthRepository.signOut() } returns Result.failure(Exception(errorMessage))
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.SignOut)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        coVerify { mockAuthRepository.signOut() }
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to sign out: $errorMessage", state.error)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `onEvent ClearError should remove error from state`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        coEvery { mockAuthRepository.signOut() } returns Result.failure(Exception("Test error"))
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        
        // First create an error
        viewModel.onEvent(MainNavigationEvent.SignOut)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.onEvent(MainNavigationEvent.ClearError)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
        }
    }

    @Test
    fun `onEvent NavigateToAuth should not change state`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val stateBefore = viewModel.uiState.value

        // Act
        viewModel.onEvent(MainNavigationEvent.NavigateToAuth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val stateAfter = viewModel.uiState.value
        assertEquals(stateBefore, stateAfter)
    }

    @Test
    fun `should handle auth state changes reactively`() = runTest {
        // Arrange
        val userFlow = flowOf(null, mockUser, null)
        every { mockAuthRepository.currentUser } returns userFlow

        // Act
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AuthenticationState.Unauthenticated, state.authenticationState)
        }
    }

    @Test
    fun `signOut should set loading state during operation`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        coEvery { mockAuthRepository.signOut() } returns Result.success(Unit)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act & Assert
        viewModel.uiState.test {
            // Skip initial state
            skipItems(1)
            
            viewModel.onEvent(MainNavigationEvent.SignOut)
            
            // Should see loading state
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            assertNull(loadingState.error)
        }
    }

    @Test
    fun `all navigation items should be selectable`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act & Assert
        MainNavigationItem.values().forEach { item ->
            viewModel.onEvent(MainNavigationEvent.NavigateToTab(item))
            testDispatcher.scheduler.advanceUntilIdle()
            
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(item, state.selectedTab)
            }
        }
    }

    @Test
    fun `workout creation events should work independently of modal state`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act & Assert - Template workout without showing modal first
        viewModel.onEvent(MainNavigationEvent.StartTemplateWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(WorkoutCreationDestination.TemplateWorkout, state.navigationDestination)
            assertFalse(state.isWorkoutCreationModalVisible)
        }

        // Act & Assert - Custom workout
        viewModel.onEvent(MainNavigationEvent.StartCustomWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(WorkoutCreationDestination.CustomWorkout, state.navigationDestination)
            assertFalse(state.isWorkoutCreationModalVisible)
        }
    }

    @Test
    fun `navigation destination should persist until explicitly cleared`() = runTest {
        // Arrange
        every { mockAuthRepository.currentUser } returns flowOf(mockUser)
        viewModel = MainNavigationViewModel(mockAuthRepository, mockWorkoutRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act - Set navigation destination
        viewModel.onEvent(MainNavigationEvent.StartTemplateWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert - Destination persists through other state changes
        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(WorkoutCreationDestination.TemplateWorkout, state.navigationDestination)
            assertTrue(state.isWorkoutCreationModalVisible)
        }

        // Act - Clear destination
        viewModel.onEvent(MainNavigationEvent.ClearNavigationDestination)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.navigationDestination)
        }
    }
} 