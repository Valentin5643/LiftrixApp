package com.example.liftrix.ui.home.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.ui.social.SocialEvent
import com.example.liftrix.ui.social.SocialUiState
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.MockKAnnotations
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive UI tests for SocialFeedSection component.
 * 
 * Tests cover rendering, state management, user interactions, and accessibility
 * following existing testing patterns with MockK and ComposeTestRule.
 */
@RunWith(AndroidJUnit4::class)
class SocialFeedSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockOnEvent: (SocialEvent) -> Unit

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockOnEvent = mockk(relaxed = true)
    }

    @Test
    fun socialFeedSection_rendersWithFriendWorkouts_displaysCorrectly() {
        // Arrange
        val testWorkouts = listOf(
            createTestSharedWorkout("1", "John Doe", "Push Day"),
            createTestSharedWorkout("2", "Jane Smith", "Pull Day")
        )
        val uiState = SocialUiState(
            friendWorkouts = testWorkouts,
            isLoading = false
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Friends Activity").assertIsDisplayed()
        composeTestRule.onNodeWithText("View All").assertIsDisplayed()
        composeTestRule.onNodeWithText("John Doe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jane Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("Push Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pull Day").assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_emptyState_displaysCorrectly() {
        // Arrange
        val uiState = SocialUiState(
            friendWorkouts = emptyList(),
            isLoading = false
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("No Friend Activity Yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add friends to see their workout updates and stay motivated together!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Friends").assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_loadingState_displaysCorrectly() {
        // Arrange
        val uiState = SocialUiState(
            isLoading = true,
            friendWorkouts = emptyList()
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Loading friend activity...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading friend activity").assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_errorState_displaysCorrectly() {
        // Arrange
        val errorMessage = "Network connection failed"
        val uiState = SocialUiState(
            isLoading = false,
            error = errorMessage,
            friendWorkouts = emptyList()
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Failed to load friend activity").assertIsDisplayed()
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_viewAllButtonClick_triggersCorrectEvent() {
        // Arrange
        val uiState = SocialUiState(
            friendWorkouts = listOf(createTestSharedWorkout()),
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Act
        composeTestRule.onNodeWithText("View All").performClick()

        // Assert
        verify { mockOnEvent(SocialEvent.ViewAllFriends) }
    }

    @Test
    fun socialFeedSection_addFriendsButtonClick_triggersCorrectEvent() {
        // Arrange
        val uiState = SocialUiState(
            friendWorkouts = emptyList(),
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Act
        composeTestRule.onNodeWithText("Add Friends").performClick()

        // Assert
        verify { mockOnEvent(SocialEvent.ViewAllFriends) }
    }

    @Test
    fun socialFeedSection_retryButtonClick_triggersCorrectEvent() {
        // Arrange
        val uiState = SocialUiState(
            isLoading = false,
            errorMessage = "Test error",
            friendWorkouts = emptyList()
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Act
        composeTestRule.onNodeWithText("Try Again").performClick()

        // Assert
        verify { mockOnEvent(SocialEvent.RefreshData) }
    }

    @Test
    fun socialFeedSection_friendWorkoutCardClick_triggersCorrectEvent() {
        // Arrange
        val testWorkout = createTestSharedWorkout("1", "John Doe", "Push Day")
        val uiState = SocialUiState(
            friendWorkouts = listOf(testWorkout),
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Act
        composeTestRule.onNodeWithContentDescription(
            "Friend workout: John Doe completed Push Day, just now"
        ).performClick()

        // Assert
        verify { mockOnEvent(SocialEvent.ViewWorkout(testWorkout)) }
    }

    @Test
    fun socialFeedSection_horizontalScrolling_worksCorrectly() {
        // Arrange
        val testWorkouts = (1..5).map { index ->
            createTestSharedWorkout(
                id = index.toString(),
                friendName = "Friend $index",
                workoutName = "Workout $index"
            )
        }
        val uiState = SocialUiState(
            friendWorkouts = testWorkouts,
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Act & Assert - Verify first and last items exist
        composeTestRule.onNodeWithText("Friend 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout 1").assertIsDisplayed()
        
        // Scroll to see more items
        composeTestRule.onNodeWithContentDescription(
            "Horizontal list of ${testWorkouts.size} friend workouts"
        ).performScrollToIndex(4)
        
        composeTestRule.onNodeWithText("Friend 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout 5").assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_accessibility_hasCorrectContentDescriptions() {
        // Arrange
        val testWorkout = createTestSharedWorkout("1", "John Doe", "Push Day")
        val uiState = SocialUiState(
            friendWorkouts = listOf(testWorkout),
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription(
            "Friends activity section with recent workout updates"
        ).assertIsDisplayed()
        
        composeTestRule.onNodeWithContentDescription(
            "Horizontal list of 1 friend workouts"
        ).assertIsDisplayed()
        
        composeTestRule.onNodeWithContentDescription(
            "Friend workout: John Doe completed Push Day, just now"
        ).assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_emptyStateAccessibility_hasCorrectContentDescription() {
        // Arrange
        val uiState = SocialUiState(
            friendWorkouts = emptyList(),
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription(
            "No friend activity available, add friends to see their workouts"
        ).assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_errorStateAccessibility_hasCorrectContentDescription() {
        // Arrange
        val errorMessage = "Network error"
        val uiState = SocialUiState(
            isLoading = false,
            error = errorMessage,
            friendWorkouts = emptyList()
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription(
            "Error loading friend activity: $errorMessage"
        ).assertIsDisplayed()
    }

    @Test
    fun socialFeedSection_multipleWorkouts_rendersAllCorrectly() {
        // Arrange
        val testWorkouts = listOf(
            createTestSharedWorkout("1", "Alice", "Morning Cardio"),
            createTestSharedWorkout("2", "Bob", "Strength Training"),
            createTestSharedWorkout("3", "Charlie", "Yoga Session")
        )
        val uiState = SocialUiState(
            friendWorkouts = testWorkouts,
            isLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SocialFeedSection(
                    socialUiState = uiState,
                    onEvent = mockOnEvent
                )
            }
        }

        // Assert all workouts are displayed
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Morning Cardio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
        composeTestRule.onNodeWithText("Strength Training").assertIsDisplayed()
        composeTestRule.onNodeWithText("Charlie").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yoga Session").assertIsDisplayed()
    }

    /**
     * Helper function to create test SharedWorkout instances
     */
    private fun createTestSharedWorkout(
        id: String = "1",
        friendName: String = "Test Friend",
        workoutName: String = "Test Workout"
    ): SharedWorkout {
        return SharedWorkout(
            id = id,
            friendUserId = "user_$id",
            friendDisplayName = friendName,
            workoutName = workoutName,
            completedAt = Instant.now().minusSeconds(1800),
            duration = Duration.ofMinutes(45),
            exerciseCount = 8,
            sharedAt = Instant.now()
        )
    }
}