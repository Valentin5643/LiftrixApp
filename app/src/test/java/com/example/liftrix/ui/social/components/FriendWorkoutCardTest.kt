package com.example.liftrix.ui.social.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive UI tests for FriendWorkoutCard component.
 * 
 * Tests rendering, interactions, and accessibility as part of 
 * SOCIAL-TEST-003 task requirements.
 * 
 * Coverage includes:
 * - Workout data display (name, friend, duration, exercise count)
 * - User interactions (view workout, congratulate)
 * - Time formatting and display
 * - Friend avatar rendering
 * - Accessibility features and semantic markup
 * - Different workout scenarios and edge cases
 */
@RunWith(AndroidJUnit4::class)
class FriendWorkoutCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Mock callback functions
    private lateinit var mockOnViewWorkout: (SharedWorkout) -> Unit
    private lateinit var mockOnCongratulate: (SharedWorkout) -> Unit

    @Before
    fun setUp() {
        mockOnViewWorkout = mockk(relaxed = true)
        mockOnCongratulate = mockk(relaxed = true)
    }

    @Test
    fun friendWorkoutCard_displaysWorkoutDataCorrectly() {
        // Given - Test workout
        val testWorkout = createTestSharedWorkout(
            id = "1",
            friendName = "John Doe",
            workoutName = "Push Day",
            durationMinutes = 65,
            exerciseCount = 5
        )

        // When
        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - All workout data is displayed
        composeTestRule.onNodeWithText("John Doe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Push Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("1h 5m").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("just now").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_displaysShortDurationCorrectly() {
        // Given - Workout with short duration
        val testWorkout = createTestSharedWorkout(
            durationMinutes = 25,
            exerciseCount = 3
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Duration is displayed correctly without hours
        composeTestRule.onNodeWithText("25m").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_displaysLongWorkoutNameCorrectly() {
        // Given - Workout with long name
        val testWorkout = createTestSharedWorkout(
            workoutName = "Full Body Strength Training and Conditioning Session"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Long workout name is displayed (may be truncated with ellipsis)
        composeTestRule.onNodeWithText("Full Body Strength Training and Conditioning Session").assertExists()
    }

    @Test
    fun friendWorkoutCard_displaysShortNamesCorrectly() {
        // Given - Workout with short friend name
        val testWorkout = createTestSharedWorkout(
            friendName = "Jo"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Short name is displayed correctly with initials
        composeTestRule.onNodeWithText("Jo").assertIsDisplayed()
        // Avatar should show "J" initial
        composeTestRule.onNodeWithText("J").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_displaysMultiWordNamesCorrectly() {
        // Given - Workout with multi-word friend name
        val testWorkout = createTestSharedWorkout(
            friendName = "Sarah Martinez Johnson"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Name is displayed correctly and avatar shows first two initials
        composeTestRule.onNodeWithText("Sarah Martinez Johnson").assertIsDisplayed()
        composeTestRule.onNodeWithText("SM").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_clickTriggersViewWorkout() {
        // Given - Test workout
        val testWorkout = createTestSharedWorkout()

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // When - Click on the card
        composeTestRule.onNodeWithContentDescription(
            "Friend workout: Test Friend completed Test Workout, just now"
        ).performClick()

        // Then - View workout callback is triggered
        verify { mockOnViewWorkout(testWorkout) }
    }

    @Test
    fun friendWorkoutCard_congratulateButtonClick() {
        // Given - Test workout
        val testWorkout = createTestSharedWorkout()

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // When - Click congratulate button
        composeTestRule.onNodeWithContentDescription("Congratulate friend").performClick()

        // Then - Congratulate callback is triggered
        verify { mockOnCongratulate(testWorkout) }
    }

    @Test
    fun friendWorkoutCard_displaysTimeAgoCorrectly() {
        // Given - Workout shared some time ago
        val testWorkout = createTestSharedWorkout(
            sharedAt = Instant.now().minusSeconds(3600) // 1 hour ago
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Time ago is displayed correctly
        composeTestRule.onNodeWithText("1h ago").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_accessibility_hasCorrectContentDescription() {
        // Given - Test workout
        val testWorkout = createTestSharedWorkout(
            friendName = "Alice Smith",
            workoutName = "Morning Cardio"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Accessibility content description is correct
        composeTestRule.onNodeWithContentDescription(
            "Friend workout: Alice Smith completed Morning Cardio, just now"
        ).assertExists()
        
        composeTestRule.onNodeWithContentDescription("Congratulate friend").assertExists()
        composeTestRule.onNodeWithContentDescription("Friend avatar").assertExists()
    }

    @Test
    fun friendWorkoutCard_handlesZeroDurationCorrectly() {
        // Given - Workout with zero duration
        val testWorkout = createTestSharedWorkout(
            durationMinutes = 0
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Zero duration is displayed as "< 1m"
        composeTestRule.onNodeWithText("< 1m").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_handlesZeroExercisesCorrectly() {
        // Given - Workout with zero exercises
        val testWorkout = createTestSharedWorkout(
            exerciseCount = 0
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Zero exercises is displayed as "0"
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_handlesLongDurationCorrectly() {
        // Given - Workout with very long duration
        val testWorkout = createTestSharedWorkout(
            durationMinutes = 150 // 2h 30m
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Long duration is displayed correctly
        composeTestRule.onNodeWithText("2h 30m").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_handlesExactHourDurationCorrectly() {
        // Given - Workout with exact hour duration
        val testWorkout = createTestSharedWorkout(
            durationMinutes = 60 // 1h exact
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Exact hour duration is displayed correctly
        composeTestRule.onNodeWithText("1h").assertIsDisplayed()
    }

    @Test
    fun friendWorkoutCard_avatarFallback_displaysCorrectly() {
        // Given - Workout with empty friend name (edge case)
        val testWorkout = createTestSharedWorkout(
            friendName = ""
        )

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Avatar fallback icon is displayed
        composeTestRule.onNodeWithContentDescription("Friend avatar").assertExists()
    }

    @Test
    fun friendWorkoutCard_verifyLayoutConstraints() {
        // Given - Test workout
        val testWorkout = createTestSharedWorkout()

        composeTestRule.setContent {
            LiftrixTheme {
                FriendWorkoutCard(
                    sharedWorkout = testWorkout,
                    onViewWorkout = mockOnViewWorkout,
                    onCongratulate = mockOnCongratulate
                )
            }
        }

        // Then - Card has correct fixed width for horizontal scrolling
        composeTestRule.onRoot().assertWidthIsEqualTo(200.dp)
    }

    // Helper function to create test SharedWorkout instances
    private fun createTestSharedWorkout(
        id: String = "1",
        friendName: String = "Test Friend",
        workoutName: String = "Test Workout",
        durationMinutes: Long = 45,
        exerciseCount: Int = 8,
        sharedAt: Instant = Instant.now()
    ): SharedWorkout {
        return SharedWorkout(
            id = id,
            friendUserId = "user_$id",
            friendDisplayName = friendName,
            workoutName = workoutName,
            completedAt = sharedAt.minusSeconds(1800), // Completed 30 minutes before shared
            duration = Duration.ofMinutes(durationMinutes),
            exerciseCount = exerciseCount,
            sharedAt = sharedAt
        )
    }
}