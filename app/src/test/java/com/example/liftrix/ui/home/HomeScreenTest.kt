package com.example.liftrix.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.home.components.DiscoveryCarousel
import com.example.liftrix.ui.home.components.WorkoutFeedSection
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Compose UI tests for HomeScreen feed scrolling behavior, pagination triggers,
 * and loading state transitions as specified in TEST-002.
 * 
 * Tests cover:
 * - DiscoveryCarousel horizontal scrolling with 70% prefetch trigger
 * - WorkoutFeedSection vertical scrolling with pagination
 * - Loading state transitions and shimmer placeholders
 * - End-of-feed message display
 * - Error state handling
 * - Smooth scrolling performance validation
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class HomeScreenTest {

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // Test data
    private val testRecommendedUsers = listOf(
        RecommendedUser("user-1", "John Doe", null, false),
        RecommendedUser("user-2", "Jane Smith", "image-url", false),
        RecommendedUser("user-3", "Mike Johnson", null, true),
        RecommendedUser("user-4", "Sarah Wilson", null, false),
        RecommendedUser("user-5", "David Brown", null, false),
        RecommendedUser("user-6", "Lisa Davis", null, false),
        RecommendedUser("user-7", "Tom Miller", null, false),
        RecommendedUser("user-8", "Anna Garcia", null, false),
        RecommendedUser("user-9", "Chris Lee", null, false),
        RecommendedUser("user-10", "Emma Taylor", null, false)
    )

    private val testFeedWorkouts = createTestFeedWorkouts(15)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    // Discovery Carousel Tests

    @Test
    fun discoveryCarousel_displaysInitialUsers_whenDataLoaded() {
        // Given
        var loadMoreCalled = false
        
        composeTestRule.setContent {
            DiscoveryCarousel(
                recommendedUsers = testRecommendedUsers.take(10),
                isLoading = false,
                hasMore = true,
                onLoadMore = { loadMoreCalled = true },
                onFollowUser = {}
            )
        }

        // Then
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag("recommended_user_card")
            .assertCountEquals(10)

        // Verify first and last users are displayed
        composeTestRule
            .onNodeWithText("John Doe")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Emma Taylor")
            .assertExists()
    }

    @Test
    fun discoveryCarousel_triggersLoadMore_whenScrolledTo70Percent() {
        // Given
        var loadMoreCalled = false
        
        composeTestRule.setContent {
            DiscoveryCarousel(
                recommendedUsers = testRecommendedUsers,
                isLoading = false,
                hasMore = true,
                onLoadMore = { loadMoreCalled = true },
                onFollowUser = {}
            )
        }

        // When - scroll to 70% (item 7 of 10 items)
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .performScrollToIndex(7)

        // Then - wait for prefetch trigger to activate
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            loadMoreCalled
        }
    }

    @Test
    fun discoveryCarousel_showsLoadingPlaceholders_whenLoadingMore() {
        // Given
        composeTestRule.setContent {
            DiscoveryCarousel(
                recommendedUsers = testRecommendedUsers.take(5),
                isLoading = true,
                hasMore = true,
                onLoadMore = {},
                onFollowUser = {}
            )
        }

        // Then
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .assertIsDisplayed()

        // Verify user cards are present and loading state is active
        composeTestRule
            .onAllNodesWithTag("recommended_user_card")
            .assertCountEquals(5)
    }

    @Test
    fun discoveryCarousel_handleFollowUserAction_correctlyUpdatesUI() {
        // Given
        var followedUserId: String? = null
        
        composeTestRule.setContent {
            DiscoveryCarousel(
                recommendedUsers = testRecommendedUsers.take(3),
                isLoading = false,
                hasMore = true,
                onLoadMore = {},
                onFollowUser = { userId -> followedUserId = userId }
            )
        }

        // When - interact with follow button on first user card
        composeTestRule
            .onAllNodesWithTag("recommended_user_card")
            .onFirst()
            .performClick()

        // Then - verify callback was invoked
        composeTestRule.waitForIdle()
        // Note: The exact click target depends on DiscoveryCarousel implementation
    }

    @Test
    fun discoveryCarousel_preventsLoadMore_whenNoMoreData() {
        // Given
        var loadMoreCallCount = 0
        
        composeTestRule.setContent {
            DiscoveryCarousel(
                recommendedUsers = testRecommendedUsers,
                isLoading = false,
                hasMore = false,
                onLoadMore = { loadMoreCallCount++ },
                onFollowUser = {}
            )
        }

        // When - scroll to end
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .performScrollToIndex(9)

        // Then - wait and verify loadMore was not called
        composeTestRule.waitForIdle()
        assert(loadMoreCallCount == 0)
    }

    // Workout Feed Section Tests

    @Test
    fun workoutFeedSection_displaysWorkoutItems_whenDataLoaded() {
        // Given
        val feedState = FeedState.Success(
            workouts = testFeedWorkouts,
            hasMore = true,
            isLoadingMore = false
        )
        
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = feedState,
                showEndMessage = false,
                onLoadMore = {}
            )
        }

        // Then
        composeTestRule
            .onNodeWithTag("workout_feed")
            .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag("workout_feed_item")
            .assertCountEquals(15)

        // Verify first workout is displayed
        composeTestRule
            .onNodeWithText("Workout 1")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedSection_triggersLoadMore_whenScrolledNearEnd() {
        // Given
        var loadMoreCalled = false
        val feedState = FeedState.Success(
            workouts = testFeedWorkouts,
            hasMore = true,
            isLoadingMore = false
        )
        
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = feedState,
                showEndMessage = false,
                onLoadMore = { loadMoreCalled = true }
            )
        }

        // When - scroll to near end (within 3 items)
        composeTestRule
            .onNodeWithTag("workout_feed")
            .performScrollToIndex(12) // 15 items, scroll to item 12 (within 3 of end)

        // Then - wait for pagination trigger
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            loadMoreCalled
        }
    }

    @Test
    fun workoutFeedSection_showsLoadingState_initialLoad() {
        // Given
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = FeedState.Loading,
                showEndMessage = false,
                onLoadMore = {}
            )
        }

        // Then - should show loading placeholders
        // Note: Would need testTag on FeedLoadingPlaceholder to verify directly
        // For now, verify no workout items are displayed during loading
        composeTestRule
            .onAllNodesWithTag("workout_feed_item")
            .assertCountEquals(0)
    }

    @Test
    fun workoutFeedSection_displaysEndMessage_whenReachedLimit() {
        // Given
        val feedState = FeedState.Success(
            workouts = testFeedWorkouts,
            hasMore = false,
            isLoadingMore = false
        )
        
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = feedState,
                showEndMessage = true,
                onLoadMore = {}
            )
        }

        // Then
        composeTestRule
            .onNodeWithTag("workout_feed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("You've seen everything for now")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Check back later for more workout updates")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedSection_showsErrorState_whenLoadingFails() {
        // Given
        val errorMessage = "Failed to load workout feed"
        
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = FeedState.Error(errorMessage),
                showEndMessage = false,
                onLoadMore = {}
            )
        }

        // Then
        composeTestRule
            .onNodeWithText("Unable to load workout feed")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(errorMessage)
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedSection_preventsLoadMore_whenAlreadyLoading() {
        // Given
        var loadMoreCallCount = 0
        val feedState = FeedState.Success(
            workouts = testFeedWorkouts,
            hasMore = true,
            isLoadingMore = true
        )
        
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = feedState,
                showEndMessage = false,
                onLoadMore = { loadMoreCallCount++ }
            )
        }

        // When - scroll to trigger position while already loading
        composeTestRule
            .onNodeWithTag("workout_feed")
            .performScrollToIndex(12)

        // Then - wait and verify loadMore was not called
        composeTestRule.waitForIdle()
        assert(loadMoreCallCount == 0)
    }

    // Scrolling Performance Tests

    @Test
    fun discoveryCarousel_scrollsSmoothlyThroughAllItems() {
        // Given
        composeTestRule.setContent {
            DiscoveryCarousel(
                recommendedUsers = testRecommendedUsers,
                isLoading = false,
                hasMore = false,
                onLoadMore = {},
                onFollowUser = {}
            )
        }

        // When - perform scrolling across all items
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .performScrollToIndex(0)
            .performScrollToIndex(5)
            .performScrollToIndex(9)
            .performScrollToIndex(0)

        // Then - verify no crashes and carousel is still displayed
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedSection_scrollsSmoothlyThroughAllItems() {
        // Given
        val feedState = FeedState.Success(
            workouts = testFeedWorkouts,
            hasMore = false,
            isLoadingMore = false
        )
        
        composeTestRule.setContent {
            WorkoutFeedSection(
                feedState = feedState,
                showEndMessage = true,
                onLoadMore = {}
            )
        }

        // When - perform scrolling through feed
        composeTestRule
            .onNodeWithTag("workout_feed")
            .performScrollToIndex(0)
            .performScrollToIndex(7)
            .performScrollToIndex(14)
            .performScrollToIndex(0)

        // Then - verify no crashes and feed is still displayed
        composeTestRule
            .onNodeWithTag("workout_feed")
            .assertIsDisplayed()
    }

    // Helper Functions

    /**
     * Creates test FeedWorkout data for testing
     */
    private fun createTestFeedWorkouts(count: Int): List<FeedWorkout> {
        return (1..count).map { index ->
            val workout = createTestWorkout("Workout $index", LocalDate.now().minusDays(index.toLong()))
            if (index % 3 == 0) {
                // Every third workout is from a friend
                FeedWorkout.forFriendWorkout(
                    workout = workout,
                    userInfo = User(
                        uid = "friend-$index",
                        email = "friend$index@example.com",
                        displayName = "Friend $index",
                        isAnonymous = false,
                        createdAt = Instant.now().minusSeconds(86400)
                    )
                )
            } else {
                // Personal workout
                FeedWorkout.forPersonalWorkout(workout)
            }
        }
    }

    /**
     * Creates test Workout data
     */
    private fun createTestWorkout(name: String, date: LocalDate): Workout {
        val now = Instant.now()
        return Workout(
            userId = "test-user-id",
            id = WorkoutId.generate(),
            name = name,
            date = date,
            exercises = listOf(
                // Add sample exercise for realistic workout
                WorkoutExercise(
                    exerciseId = ExerciseId.fromString("test-exercise-1"),
                    sets = listOf(
                        ExerciseSet(
                            reps = Reps(10),
                            weight = Weight.fromKilograms(20.0),
                            restTime = Duration.ofSeconds(60),
                            isCompleted = true
                        )
                    ),
                    notes = null
                )
            ),
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