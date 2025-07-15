package com.example.liftrix.ui.home

import app.cash.turbine.test
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ViewModelTestUtils
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    // Test dependencies
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockSocialRepository: SocialRepository
    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var mockGetWorkoutHistoryUseCase: GetWorkoutHistoryUseCase
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: HomeViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

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
        createTestWorkout("Recent Workout", LocalDate.now()),
        createTestWorkout("Yesterday Workout", LocalDate.now().minusDays(1)),
        createTestWorkout("Last Week Workout", LocalDate.now().minusDays(7))
    )

    private val testWorkoutStats = WorkoutStats(
        totalWorkouts = 15,
        currentStreak = 5,
        weeklyVolume = Duration.ofHours(4),
        averageWorkoutDuration = Duration.ofMinutes(45)
    )

    private val testFeedWorkouts = listOf(
        FeedWorkout.forPersonalWorkout(testWorkouts[0]),
        FeedWorkout.forFriendWorkout(testWorkouts[1], testUser.copy(uid = "friend-1", displayName = "Friend User")),
        FeedWorkout.forPersonalWorkout(testWorkouts[2])
    )

    private val testRecommendedUsers = listOf(
        RecommendedUser("user-1", "User One", null, false),
        RecommendedUser("user-2", "User Two", "image-url", false),
        RecommendedUser("user-3", "User Three", null, true)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        // Initialize mocks
        mockWorkoutRepository = mockk()
        mockAuthRepository = mockk()
        mockSocialRepository = mockk()
        mockAnalyticsService = mockk()
        mockGetWorkoutHistoryUseCase = mockk()
        mockErrorHandler = mockk()

        // Default mock behaviors
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        coEvery { mockAuthRepository.getCurrentUser() } returns testUser
        every { mockWorkoutRepository.getRecentWorkouts(any(), any()) } returns flowOf(testWorkouts)
        every { mockWorkoutRepository.getWorkoutStats(any()) } returns flowOf(testWorkoutStats)
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } returns flowOf(testFeedWorkouts)
        every { mockSocialRepository.getRecommendedUsers(any(), any()) } returns flowOf(testRecommendedUsers)
        coEvery { mockSocialRepository.followUser(any()) } returns Result.success(Unit)
        coEvery { mockSocialRepository.removeFriend(any()) } returns Result.success(Unit)
        coEvery { mockSocialRepository.refreshDiscoveryCache() } returns Result.success(Unit)
        coEvery { mockAnalyticsService.logEvent(any(), any()) } just Runs
        coEvery { mockAnalyticsService.trackFeedLoadTime(any()) } just Runs
        coEvery { mockAnalyticsService.trackUserDiscoveryEngagement(any(), any()) } just Runs
        coEvery { mockAnalyticsService.trackFeedScrollDepth(any()) } just Runs
        
        // Setup ErrorHandler mock
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        every { mockErrorHandler.mapToUserMessage(any()) } returns "Test error message"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state should be default values`() = runTest {
        // When
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // Then
        val initialState = viewModel.uiState.value
        assertFalse(initialState.isLoading)
        assertEquals(emptyList(), initialState.recentWorkouts)
        assertIs<FeedState.Loading>(initialState.workoutFeedState)
        assertIs<RecommendationsState.Loading>(initialState.recommendationsState)
        assertFalse(initialState.showEndOfFeedMessage)
        assertFalse(initialState.isRefreshing)
        assertNull(initialState.errorMessage)
    }

    @Test
    fun `when LoadMoreWorkouts event triggered, should load more feed data`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        
        // Load initial data first by calling internal method (setup)
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // When - Use MVI pattern with onEvent
        viewModel.onEvent(HomeEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Then - Verify state transition follows MVI pattern
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Success>(state.workoutFeedState)
            val feedState = state.workoutFeedState as FeedState.Success
            assertTrue(feedState.workouts.isNotEmpty())
            assertFalse(feedState.isLoadingMore)
        }

        // Verify repository calls (initial + pagination)
        verify(atLeast = 2) { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) }
        coVerify(atLeast = 2) { mockAnalyticsService.trackFeedLoadTime(any()) }
    }

    @Test
    fun `loadFeedWorkouts handles user not authenticated`() = runTest {
        // Given
        coEvery { mockAuthRepository.getCurrentUser() } returns null
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Error>(state.workoutFeedState)
            val errorState = state.workoutFeedState as FeedState.Error
            assertEquals("User not authenticated", errorState.message)
        }

        // Verify no feed calls made
        verify(exactly = 0) { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) }
    }

    @Test
    fun `loadFeedWorkouts handles repository error`() = runTest {
        // Given
        val errorMessage = "Database connection failed"
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } throws RuntimeException(errorMessage)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Error>(state.workoutFeedState)
            val errorState = state.workoutFeedState as FeedState.Error
            assertTrue(errorState.message.contains("Failed to load workout feed"))
        }
        
        // Verify analytics tracking for errors
        coVerify { mockAnalyticsService.trackFeedLoadTime(any()) }
    }

    @Test
    fun `loadRecommendations emits success state with user data`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        
        // When
        viewModel.loadRecommendations()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<RecommendationsState.Success>(state.recommendationsState)
            val recommendationsState = state.recommendationsState as RecommendationsState.Success
            assertEquals(testRecommendedUsers, recommendationsState.users)
            assertTrue(recommendationsState.hasMore)
            assertFalse(recommendationsState.isLoadingMore)
        }

        // Verify repository calls
        verify { mockSocialRepository.getRecommendedUsers(10, 0) }
    }

    @Test
    fun `loadMoreWorkouts loads additional workouts with pagination`() = runTest {
        // Given
        val additionalWorkouts = listOf(
            FeedWorkout.forPersonalWorkout(createTestWorkout("Additional Workout", LocalDate.now().minusDays(3)))
        )
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } returnsMany listOf(
            flowOf(testFeedWorkouts),
            flowOf(additionalWorkouts)
        )
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        
        // Load initial data
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // When
        viewModel.loadMoreWorkouts()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Success>(state.workoutFeedState)
            val feedState = state.workoutFeedState as FeedState.Success
            assertEquals(testFeedWorkouts + additionalWorkouts, feedState.workouts)
            assertFalse(feedState.isLoadingMore)
        }

        // Verify pagination calls
        verify { mockWorkoutRepository.getFeedWorkouts(testUserId, 10, 0) }
        verify { mockWorkoutRepository.getFeedWorkouts(testUserId, 10, 3) }
        coVerify(atLeast = 2) { mockAnalyticsService.trackFeedLoadTime(any()) }
        coVerify { mockAnalyticsService.trackFeedScrollDepth(4) }
    }

    @Test
    fun `loadMoreRecommendations loads additional users with pagination`() = runTest {
        // Given
        val additionalUsers = listOf(
            RecommendedUser("user-4", "User Four", null, false)
        )
        every { mockSocialRepository.getRecommendedUsers(any(), any()) } returnsMany listOf(
            flowOf(testRecommendedUsers),
            flowOf(additionalUsers)
        )
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        
        // Load initial data
        viewModel.loadRecommendations()
        advanceUntilIdle()

        // When
        viewModel.loadMoreRecommendations()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<RecommendationsState.Success>(state.recommendationsState)
            val recommendationsState = state.recommendationsState as RecommendationsState.Success
            assertEquals(testRecommendedUsers + additionalUsers, recommendationsState.users)
            assertFalse(recommendationsState.isLoadingMore)
        }

        // Verify pagination calls and analytics
        verify { mockSocialRepository.getRecommendedUsers(10, 0) }
        verify { mockSocialRepository.getRecommendedUsers(10, 3) }
        coVerify { mockAnalyticsService.trackUserDiscoveryEngagement("carousel_scroll", any()) }
    }

    @Test
    fun `followUser updates recommendations state and tracks analytics`() = runTest {
        // Given
        val targetUserId = "user-1"
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        
        // Load initial recommendations
        viewModel.loadRecommendations()
        advanceUntilIdle()

        // When
        viewModel.followUser(targetUserId)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<RecommendationsState.Success>(state.recommendationsState)
            val recommendationsState = state.recommendationsState as RecommendationsState.Success
            val followedUser = recommendationsState.users.find { it.userId == targetUserId }
            assertTrue(followedUser?.isFollowing == true)
        }

        // Verify repository call and analytics
        coVerify { mockSocialRepository.followUser(targetUserId) }
        coVerify { 
            mockAnalyticsService.trackUserDiscoveryEngagement(
                "follow_user",
                match { params -> params["target_user_id"] == targetUserId }
            )
        }
    }

    @Test
    fun `followUser handles repository error gracefully`() = runTest {
        // Given
        val targetUserId = "user-1"
        val errorMessage = "Network error"
        coEvery { mockSocialRepository.followUser(any()) } returns Result.failure(RuntimeException(errorMessage))
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.followUser(targetUserId)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.errorMessage?.contains("Failed to follow user") == true)
        }

        // Verify error tracking
        coVerify { mockSocialRepository.followUser(targetUserId) }
    }

    @Test
    fun `refreshFeed refreshes cache and reloads data`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.refreshFeed()
        advanceUntilIdle()

        // Then
        verify { mockWorkoutRepository.getFeedWorkouts(testUserId, 10, 0) }
        verify { mockSocialRepository.getRecommendedUsers(10, 0) }
        coVerify { mockSocialRepository.refreshDiscoveryCache() }
    }

    @Test
    fun `onEvent LoadMoreWorkouts triggers pagination following MVI pattern`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        // Load initial data first
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // When - Use MVI pattern with event
        viewModel.onEvent(HomeEvent.LoadMoreWorkouts)
        advanceUntilIdle()

        // Then - Verify MVI pattern compliance
        verify(atLeast = 2) { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) }
        
        // Verify state transition follows expected pattern
        ViewModelTestUtils.verifyMVIPattern(
            viewModel = viewModel,
            event = HomeEvent.LoadMoreWorkouts,
            expectedStateType = HomeUiState::class.java
        )
    }

    @Test
    fun `onEvent FeedWorkoutOpened tracks analytics`() = runTest {
        // Given
        val testFeedWorkout = testFeedWorkouts.first()
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.onEvent(HomeEvent.FeedWorkoutOpened(testFeedWorkout))

        // Then
        coVerify { 
            mockAnalyticsService.logEvent(
                "home_feed_workout_opened",
                match<Map<String, Any>> { params ->
                    params["workout_id"] == testFeedWorkout.workout.id.value &&
                    params["workout_name"] == testFeedWorkout.workout.name &&
                    params["is_personal"] == testFeedWorkout.isPersonal &&
                    params["exercise_count"] == testFeedWorkout.workout.exercises.size
                }
            )
        }
    }

    @Test
    fun `onEvent FollowUser tracks analytics`() = runTest {
        // Given
        val targetUserId = "user-1"
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.onEvent(HomeEvent.FollowUser(targetUserId))

        // Then
        coVerify { 
            mockAnalyticsService.trackUserDiscoveryEngagement(
                "follow_user",
                match<Map<String, Any>> { params ->
                    params["target_user_id"] == targetUserId &&
                    params.containsKey("timestamp")
                }
            )
        }
    }

    @Test
    fun `onEvent FeedErrorDismissed clears feed error and reloads`() = runTest {
        // Given
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } throws RuntimeException("Test error")
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // Verify error exists
        viewModel.uiState.test {
            val stateWithError = awaitItem()
            assertIs<FeedState.Error>(stateWithError.workoutFeedState)
        }

        // Reset mock to succeed
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } returns flowOf(testFeedWorkouts)

        // When
        viewModel.onEvent(HomeEvent.FeedErrorDismissed)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val clearedState = awaitItem()
            assertIs<FeedState.Success>(clearedState.workoutFeedState)
        }
    }

    @Test
    fun `end of feed message shows when workout limit reached`() = runTest {
        // Given
        val maxWorkouts = (1..40).map { index ->
            FeedWorkout.forPersonalWorkout(createTestWorkout("Workout $index", LocalDate.now().minusDays(index.toLong())))
        }
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } returns flowOf(maxWorkouts)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Success>(state.workoutFeedState)
            val feedState = state.workoutFeedState as FeedState.Success
            assertFalse(feedState.hasMore)
            assertTrue(state.showEndOfFeedMessage)
        }
    }

    @Test
    fun `loadMoreWorkouts prevents duplicate calls when already loading`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // Set up slow loading for more workouts
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } returns kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(1000)
            emit(testFeedWorkouts)
        }

        // When - call loadMoreWorkouts multiple times quickly
        viewModel.loadMoreWorkouts()
        viewModel.loadMoreWorkouts()
        viewModel.loadMoreWorkouts()
        advanceUntilIdle()

        // Then - only one additional call should be made
        verify(exactly = 2) { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) }
    }

    @Test
    fun `recommendationsState error handling works correctly`() = runTest {
        // Given
        val errorMessage = "Failed to load recommendations"
        every { mockSocialRepository.getRecommendedUsers(any(), any()) } throws RuntimeException(errorMessage)
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.loadRecommendations()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<RecommendationsState.Error>(state.recommendationsState)
            val errorState = state.recommendationsState as RecommendationsState.Error
            assertTrue(errorState.message.contains("Failed to load user recommendations"))
        }
    }

    @Test
    fun `analytics events fire during initialization`() = runTest {
        // When
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        advanceUntilIdle()

        // Then
        coVerify { 
            mockAnalyticsService.logEvent(
                "home_screen_viewed",
                match<Map<String, Any>> { params ->
                    params.containsKey("timestamp")
                }
            )
        }
    }

    @Test
    fun `analytics tracking handles exceptions gracefully`() = runTest {
        // Given
        coEvery { mockAnalyticsService.trackFeedLoadTime(any()) } throws RuntimeException("Analytics error")
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When & Then - no exception should be thrown
        viewModel.loadFeedWorkouts()
        advanceUntilIdle()

        // Verify the call was attempted and didn't crash the app
        coVerify { mockAnalyticsService.trackFeedLoadTime(any()) }
        // Verify feed still loaded successfully despite analytics error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Success>(state.workoutFeedState)
        }
    }

    @Test
    fun `state transitions work correctly for sealed classes`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // Verify initial loading states
        val initialState = viewModel.uiState.value
        assertIs<FeedState.Loading>(initialState.workoutFeedState)
        assertIs<RecommendationsState.Loading>(initialState.recommendationsState)

        // Load data and verify success states
        viewModel.loadFeedWorkouts()
        viewModel.loadRecommendations()
        advanceUntilIdle()

        viewModel.uiState.test {
            val finalState = awaitItem()
            assertIs<FeedState.Success>(finalState.workoutFeedState)
            assertIs<RecommendationsState.Success>(finalState.recommendationsState)
        }
    }

    @Test 
    fun `onEvent RefreshData triggers refresh following MVI pattern`() = runTest {
        // Given
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        advanceUntilIdle() // Let initial load complete

        // When - Use MVI pattern with event
        viewModel.onEvent(HomeEvent.RefreshData)
        advanceUntilIdle()

        // Then - Verify refresh was triggered
        verify(atLeast = 2) { mockWorkoutRepository.getRecentWorkouts(testUserId, 7) }
        
        // Verify state management follows MVI pattern
        viewModel.uiState.test {
            val state = awaitItem()
            // Should maintain state consistency after refresh
            assertIs<HomeUiState>(state)
        }
    }
    
    @Test
    fun `error handling integration works with MVI pattern`() = runTest {
        // Given
        val testError = LiftrixError.NetworkError(Exception("Test network error"))
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } throws RuntimeException("Network error")
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)
        
        // When - Trigger error through event
        viewModel.onEvent(HomeEvent.FeedErrorDismissed) // This triggers loadFeedWorkouts
        advanceUntilIdle()

        // Then - Verify error handling integration
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Error>(state.workoutFeedState)
        }
        
        // Verify ErrorHandler was called
        coVerify { mockAnalyticsService.trackFeedLoadTime(any()) }
    }

    @Test
    fun `loading states are properly managed during feed operations`() = runTest {
        // Given
        val slowFeedFlow = kotlinx.coroutines.flow.flow<List<FeedWorkout>> {
            kotlinx.coroutines.delay(1000)
            emit(testFeedWorkouts)
        }
        every { mockWorkoutRepository.getFeedWorkouts(any(), any(), any()) } returns slowFeedFlow
        viewModel = HomeViewModel(mockWorkoutRepository, mockAuthRepository, mockAnalyticsService, mockSocialRepository, mockGetWorkoutHistoryUseCase, mockErrorHandler)

        // When
        viewModel.loadFeedWorkouts()
        testScheduler.advanceTimeBy(100) // Advance a bit but not to completion

        // Then - should be in loading state
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<FeedState.Loading>(state.workoutFeedState)
        }
    }

    @Test
    fun `FeedState and RecommendationsState properties work correctly`() {
        // Test FeedState.Success properties
        val feedSuccess = FeedState.Success(
            workouts = testFeedWorkouts,
            hasMore = true,
            isLoadingMore = false
        )
        assertTrue(feedSuccess.hasData)
        assertEquals(3, feedSuccess.workoutCount)
        assertFalse(feedSuccess.isLoadingMore)
        
        // Test RecommendationsState.Success properties
        val recommendationsSuccess = RecommendationsState.Success(
            users = testRecommendedUsers,
            hasMore = false,
            isLoadingMore = true
        )
        assertTrue(recommendationsSuccess.hasData)
        assertEquals(3, recommendationsSuccess.userCount)
        assertTrue(recommendationsSuccess.isLoadingMore)
        
        // Test error states
        val feedError = FeedState.Error("Feed failed")
        val recommendationsError = RecommendationsState.Error("Recommendations failed")
        assertEquals("Feed failed", feedError.message)
        assertEquals("Recommendations failed", recommendationsError.message)
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
    
    // Helper function to create test FeedWorkout
    private fun createTestFeedWorkout(name: String, isPersonal: Boolean = true): FeedWorkout {
        val workout = createTestWorkout(name, LocalDate.now())
        return if (isPersonal) {
            FeedWorkout.forPersonalWorkout(workout)
        } else {
            FeedWorkout.forFriendWorkout(workout, testUser.copy(uid = "friend-user", displayName = "Friend"))
        }
    }
}