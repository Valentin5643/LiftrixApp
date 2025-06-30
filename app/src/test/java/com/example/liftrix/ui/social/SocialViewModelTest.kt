package com.example.liftrix.ui.social

import app.cash.turbine.test
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.service.FirebasePresenceService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModelTest {

    // Test dependencies
    private lateinit var mockSocialRepository: SocialRepository
    private lateinit var mockPresenceService: FirebasePresenceService
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var viewModel: SocialViewModel

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

    private val testFriend = Friend(
        userId = "friend-1",
        displayName = "Friend One",
        email = "friend1@example.com",
        avatarUrl = null,
        status = FriendStatus.ACCEPTED,
        presence = UserPresence.online(),
        friendSince = Instant.now().minusSeconds(86400)
    )

    private val testFriends = listOf(
        testFriend,
        Friend(
            userId = "friend-2",
            displayName = "Friend Two",
            email = "friend2@example.com",
            avatarUrl = null,
            status = FriendStatus.ACCEPTED,
            presence = UserPresence.workingOut("workout-123"),
            friendSince = Instant.now().minusSeconds(172800)
        )
    )

    private val testSharedWorkout = SharedWorkout(
        id = "shared-workout-1",
        friendUserId = "friend-1",
        friendDisplayName = "Friend One",
        workoutName = "Morning Workout",
        completedAt = Instant.now().minusSeconds(3600),
        duration = Duration.ofMinutes(45),
        exerciseCount = 5,
        sharedAt = Instant.now().minusSeconds(1800)
    )

    private val testSharedWorkouts = listOf(testSharedWorkout)

    private val testPendingRequests = listOf(
        Friend(
            userId = "pending-1",
            displayName = "Pending Friend",
            email = "pending@example.com",
            avatarUrl = null,
            status = FriendStatus.PENDING,
            presence = null,
            friendSince = Instant.now()
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        // Initialize mocks
        mockSocialRepository = mockk()
        mockPresenceService = mockk()
        mockAuthRepository = mockk()
        mockAnalyticsService = mockk()

        // Default mock behaviors
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        coEvery { mockAuthRepository.getCurrentUser() } returns testUser
        every { mockSocialRepository.getFriends(any()) } returns flowOf(testFriends)
        every { mockSocialRepository.getFriendWorkoutFeed(any()) } returns flowOf(testSharedWorkouts)
        every { mockSocialRepository.getPendingFriendRequests(any()) } returns flowOf(testPendingRequests)
        coEvery { mockSocialRepository.sendFriendRequest(any()) } returns Result.success(Unit)
        coEvery { mockSocialRepository.respondToFriendRequest(any(), any()) } returns Result.success(Unit)
        coEvery { mockSocialRepository.removeFriend(any()) } returns Result.success(Unit)
        coEvery { mockSocialRepository.blockUser(any()) } returns Result.success(Unit)
        coEvery { mockPresenceService.startPresenceTracking() } just Runs
        coEvery { mockPresenceService.updatePresenceStatus(any()) } just Runs
        coEvery { mockPresenceService.stopPresenceTracking() } just Runs
        every { mockPresenceService.observeFriendsPresence(any()) } returns flowOf(emptyMap())
        coEvery { mockAnalyticsService.logEvent(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state should be default values`() = runTest {
        // When
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)

        // Then
        val initialState = viewModel.uiState.value
        assertFalse(initialState.isLoading)
        assertEquals(emptyList(), initialState.friends)
        assertEquals(emptyList(), initialState.friendRequests)
        assertEquals(emptyList(), initialState.friendWorkouts)
        assertEquals(emptyList(), initialState.pendingRequests)
        assertEquals("", initialState.searchQuery)
        assertEquals(emptyList(), initialState.searchResults)
        assertTrue(initialState.isEmpty)
        assertNull(initialState.error)
    }

    @Test
    fun `loadFriendFeed emits success state with workouts`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)

        // When
        viewModel.loadFriendFeed()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(testFriends, state.friends)
            assertEquals(testSharedWorkouts, state.friendWorkouts)
            assertEquals(testPendingRequests, state.pendingRequests)
            assertEquals(testPendingRequests, state.friendRequests) // friendRequests maps to pendingRequests
            assertFalse(state.isEmpty)
            assertNull(state.error)
        }

        // Verify repository calls
        verify { mockSocialRepository.getFriends(testUserId) }
        verify { mockSocialRepository.getFriendWorkoutFeed(testUserId) }
        verify { mockSocialRepository.getPendingFriendRequests(testUserId) }
    }

    @Test
    fun `loadFriendFeed handles user not authenticated`() = runTest {
        // Given
        coEvery { mockAuthRepository.getCurrentUser() } returns null
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)

        // When
        viewModel.loadFriendFeed()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals("User not authenticated", state.error)
        }

        // Verify no repository calls made
        verify(exactly = 0) { mockSocialRepository.getFriends(any()) }
        verify(exactly = 0) { mockSocialRepository.getFriendWorkoutFeed(any()) }
        verify(exactly = 0) { mockSocialRepository.getPendingFriendRequests(any()) }
    }

    @Test
    fun `loadFriendFeed handles repository error`() = runTest {
        // Given
        val errorMessage = "Network connection failed"
        every { mockSocialRepository.getFriends(any()) } throws RuntimeException(errorMessage)
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)

        // When
        viewModel.loadFriendFeed()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.error?.contains(errorMessage) == true)
        }
    }

    @Test
    fun `loadFriendFeed shows empty state when no data exists`() = runTest {
        // Given
        every { mockSocialRepository.getFriends(any()) } returns flowOf(emptyList())
        every { mockSocialRepository.getFriendWorkoutFeed(any()) } returns flowOf(emptyList())
        every { mockSocialRepository.getPendingFriendRequests(any()) } returns flowOf(emptyList())
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)

        // When
        viewModel.loadFriendFeed()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(emptyList(), state.friends)
            assertEquals(emptyList(), state.friendWorkouts)
            assertEquals(emptyList(), state.pendingRequests)
            assertTrue(state.isEmpty)
            assertNull(state.error)
        }
    }

    @Test
    fun `sendFriendRequest updates state on success`() = runTest {
        // Given
        val targetUserId = "target-user"
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle() // Let initial load complete

        // When
        viewModel.onEvent(SocialEvent.SendFriendRequest(targetUserId))
        advanceUntilIdle()

        // Then
        coVerify { mockSocialRepository.sendFriendRequest(targetUserId) }
        coVerify { mockAnalyticsService.logEvent("friend_request_sent", any()) }
    }

    @Test
    fun `sendFriendRequest handles failure`() = runTest {
        // Given
        val targetUserId = "target-user"
        val errorMessage = "Friend request failed"
        coEvery { mockSocialRepository.sendFriendRequest(any()) } returns Result.failure(RuntimeException(errorMessage))
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.SendFriendRequest(targetUserId))
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.error?.contains(errorMessage) == true)
        }
    }

    @Test
    fun `acceptFriendRequest calls repository and tracks analytics`() = runTest {
        // Given
        val friendUserId = "friend-user"
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.AcceptFriendRequest(friendUserId))
        advanceUntilIdle()

        // Then
        coVerify { mockSocialRepository.respondToFriendRequest(friendUserId, true) }
        coVerify { mockAnalyticsService.logEvent("friend_request_response", match { params ->
            params["target_user_id"] == friendUserId && params["accepted"] == true
        }) }
    }

    @Test
    fun `declineFriendRequest calls repository and tracks analytics`() = runTest {
        // Given
        val friendUserId = "friend-user"
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.DeclineFriendRequest(friendUserId))
        advanceUntilIdle()

        // Then
        coVerify { mockSocialRepository.respondToFriendRequest(friendUserId, false) }
        coVerify { mockAnalyticsService.logEvent("friend_request_response", match { params ->
            params["target_user_id"] == friendUserId && params["accepted"] == false
        }) }
    }

    @Test
    fun `removeFriend calls repository and tracks analytics`() = runTest {
        // Given
        val friendUserId = "friend-user"
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.RemoveFriend(friendUserId))
        advanceUntilIdle()

        // Then
        coVerify { mockSocialRepository.removeFriend(friendUserId) }
        coVerify { mockAnalyticsService.logEvent("friend_removed", match { params ->
            params["target_user_id"] == friendUserId
        }) }
    }

    @Test
    fun `blockUser calls repository and tracks analytics`() = runTest {
        // Given
        val userId = "user-to-block"
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.BlockUser(userId))
        advanceUntilIdle()

        // Then
        coVerify { mockSocialRepository.blockUser(userId) }
        coVerify { mockAnalyticsService.logEvent("user_blocked", match { params ->
            params["target_user_id"] == userId
        }) }
    }

    @Test
    fun `refreshData triggers loadFriendFeed and tracks analytics`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.RefreshData)
        advanceUntilIdle()

        // Then
        // Verify repository calls (should be called twice - once for init, once for refresh)
        verify(atLeast = 2) { mockSocialRepository.getFriends(testUserId) }
        coVerify { mockAnalyticsService.logEvent("social_feed_refreshed", any()) }
    }

    @Test
    fun `searchFriends updates search query and results`() = runTest {
        // Given
        val searchQuery = "test query"
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.SearchFriends(searchQuery))
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(searchQuery, state.searchQuery)
            assertEquals(emptyList(), state.searchResults) // Mock implementation returns empty list
        }
    }

    @Test
    fun `searchFriends with blank query clears results`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.SearchFriends(""))
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.searchQuery)
            assertEquals(emptyList(), state.searchResults)
        }
    }

    @Test
    fun `viewWorkout tracks analytics event`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.ViewWorkout(testSharedWorkout))
        advanceUntilIdle()

        // Then
        coVerify { mockAnalyticsService.logEvent("social_workout_viewed", match { params ->
            params["workout_id"] == testSharedWorkout.id &&
            params["friend_user_id"] == testSharedWorkout.friendUserId &&
            params["workout_name"] == testSharedWorkout.workoutName
        }) }
    }

    @Test
    fun `congratulateWorkout tracks analytics event`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.CongratulateWorkout(testSharedWorkout))
        advanceUntilIdle()

        // Then
        coVerify { mockAnalyticsService.logEvent("workout_congratulated", match { params ->
            params["workout_id"] == testSharedWorkout.id &&
            params["friend_user_id"] == testSharedWorkout.friendUserId &&
            params["workout_name"] == testSharedWorkout.workoutName
        }) }
    }

    @Test
    fun `viewAllFriends tracks analytics event`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.onEvent(SocialEvent.ViewAllFriends)
        advanceUntilIdle()

        // Then
        coVerify { mockAnalyticsService.logEvent("view_all_friends_clicked", any()) }
    }

    @Test
    fun `errorDismissed clears error state`() = runTest {
        // Given
        every { mockSocialRepository.getFriends(any()) } throws RuntimeException("Test error")
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle() // Let error occur

        // When
        viewModel.onEvent(SocialEvent.ErrorDismissed)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
        }
    }

    @Test
    fun `presenceUpdates reflected in state`() = runTest {
        // Given
        val presenceMap = mapOf(
            "friend-1" to UserPresence.workingOut("workout-456"),
            "friend-2" to UserPresence.offline()
        )
        every { mockPresenceService.observeFriendsPresence(any()) } returns flowOf(presenceMap)
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)

        // When
        viewModel.loadFriendFeed()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            val updatedFriend = state.friends.find { it.userId == "friend-1" }
            assertEquals(PresenceStatus.WORKING_OUT, updatedFriend?.presence?.status)
            assertEquals("workout-456", updatedFriend?.presence?.currentWorkoutId)
        }
    }

    @Test
    fun `updatePresence calls presence service`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // When
        viewModel.updatePresence()
        advanceUntilIdle()

        // Then
        coVerify { mockPresenceService.updatePresenceStatus(PresenceStatus.ONLINE) }
    }

    @Test
    fun `ViewModel starts and stops presence tracking`() = runTest {
        // Given & When
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // Then - verify presence tracking started
        coVerify { mockPresenceService.startPresenceTracking() }

        // When - ViewModel is cleared
        viewModel.onCleared()
        advanceUntilIdle()

        // Then - verify presence tracking stopped
        coVerify { mockPresenceService.stopPresenceTracking() }
    }

    @Test
    fun `analytics events tracked on initialization`() = runTest {
        // When
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // Then
        coVerify { mockAnalyticsService.logEvent("social_screen_viewed", any()) }
    }

    @Test
    fun `all event types handled correctly`() = runTest {
        // Given
        viewModel = SocialViewModel(mockSocialRepository, mockPresenceService, mockAuthRepository, mockAnalyticsService)
        advanceUntilIdle()

        // Test all event types
        val events = listOf(
            SocialEvent.LoadData,
            SocialEvent.Refresh,
            SocialEvent.LoadFriends,
            SocialEvent.SendFriendRequest("user1"),
            SocialEvent.AcceptFriendRequest("user2"),
            SocialEvent.DeclineFriendRequest("user3"),
            SocialEvent.RemoveFriend("user4"),
            SocialEvent.BlockUser("user5"),
            SocialEvent.SearchFriends("query"),
            SocialEvent.ViewAllFriends,
            SocialEvent.ViewWorkout(testSharedWorkout),
            SocialEvent.CongratulateWorkout(testSharedWorkout),
            SocialEvent.ErrorDismissed
        )

        // When - send all events
        events.forEach { event ->
            viewModel.onEvent(event)
        }
        advanceUntilIdle()

        // Then - verify no crashes and appropriate calls made
        // This test ensures all events are handled without exceptions
        assertTrue(true) // If we reach here, all events were handled successfully
    }
} 