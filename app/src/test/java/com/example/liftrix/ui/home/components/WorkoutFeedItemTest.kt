package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class WorkoutFeedItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockExercise = Exercise(
        id = ExerciseId("exercise1"),
        name = "Push-ups",
        sets = emptyList(),
        restTime = null,
        notes = null,
        equipmentUsed = emptyList()
    )

    private val mockPersonalWorkout = Workout(
        userId = "user123",
        id = WorkoutId("workout1"),
        name = "Morning Workout",
        date = LocalDate.now(),
        exercises = listOf(mockExercise),
        status = WorkoutStatus.COMPLETED,
        startTime = Instant.now().minus(60, ChronoUnit.MINUTES),
        endTime = Instant.now(),
        notes = "Great workout today!",
        templateId = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private val mockFriendUser = User(
        uid = "friend123",
        email = "friend@example.com",
        displayName = "John Doe",
        photoUrl = "https://example.com/photo.jpg"
    )

    private val mockFriendWorkout = Workout(
        userId = "friend123",
        id = WorkoutId("workout2"),
        name = "Evening Strength Training",
        date = LocalDate.now().minusDays(1),
        exercises = listOf(mockExercise, mockExercise.copy(id = ExerciseId("exercise2"), name = "Squats")),
        status = WorkoutStatus.COMPLETED,
        startTime = Instant.now().minus(90, ChronoUnit.MINUTES),
        endTime = Instant.now().minus(30, ChronoUnit.MINUTES),
        notes = null,
        templateId = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun workoutFeedItem_displaysPersonalWorkout_correctly() {
        // Given
        val personalFeedWorkout = FeedWorkout.forPersonalWorkout(mockPersonalWorkout)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = personalFeedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Morning Workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Today")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("1 exercise")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Great workout today!")
            .assertIsDisplayed()

        // Should not display friend header for personal workout
        composeTestRule
            .onNodeWithText("John Doe")
            .assertIsNotDisplayed()
    }

    @Test
    fun workoutFeedItem_displaysFriendWorkout_correctly() {
        // Given
        val friendFeedWorkout = FeedWorkout.forFriendWorkout(mockFriendWorkout, mockFriendUser)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = friendFeedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()

        // Should display friend header
        composeTestRule
            .onNodeWithText("John Doe")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Evening Strength Training")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Yesterday")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("2 exercises")
            .assertIsDisplayed()

        // Should display initials for friend avatar
        composeTestRule
            .onNodeWithText("JD")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_displaysWorkoutDuration_whenAvailable() {
        // Given
        val workoutWithDuration = mockPersonalWorkout.copy(
            startTime = Instant.now().minus(75, ChronoUnit.MINUTES),
            endTime = Instant.now().minus(15, ChronoUnit.MINUTES)
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithDuration)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display duration (60 minutes = 1h)
        composeTestRule
            .onNodeWithText("1h")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesWorkoutWithoutDuration() {
        // Given
        val workoutWithoutDuration = mockPersonalWorkout.copy(
            startTime = null,
            endTime = null
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithoutDuration)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should not crash and should display other information
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Morning Workout")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesWorkoutWithoutNotes() {
        // Given
        val workoutWithoutNotes = mockPersonalWorkout.copy(notes = null)
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithoutNotes)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should not display notes section
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Great workout today!")
            .assertIsNotDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesEmptyNotes() {
        // Given
        val workoutWithEmptyNotes = mockPersonalWorkout.copy(notes = "")
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithEmptyNotes)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should not display empty notes
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Morning Workout")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_displaysSingularExercise_correctly() {
        // Given
        val workoutWithOneExercise = mockPersonalWorkout.copy(
            exercises = listOf(mockExercise)
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithOneExercise)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("1 exercise")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_displaysPluralExercises_correctly() {
        // Given
        val workoutWithMultipleExercises = mockPersonalWorkout.copy(
            exercises = listOf(
                mockExercise,
                mockExercise.copy(id = ExerciseId("exercise2"), name = "Squats"),
                mockExercise.copy(id = ExerciseId("exercise3"), name = "Pull-ups")
            )
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithMultipleExercises)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("3 exercises")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesLongWorkoutName() {
        // Given
        val workoutWithLongName = mockPersonalWorkout.copy(
            name = "This is a very long workout name that should be truncated when displayed in the feed item component"
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithLongName)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display without crashing (ellipsis handling)
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesUserWithoutDisplayName() {
        // Given
        val userWithoutName = mockFriendUser.copy(displayName = null)
        val feedWorkout = FeedWorkout.forFriendWorkout(mockFriendWorkout, userWithoutName)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display "Unknown User" fallback
        composeTestRule
            .onNodeWithText("Unknown User")
            .assertIsDisplayed()

        // Should show person icon instead of initials
        composeTestRule
            .onNodeWithContentDescription("User avatar")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesShortDuration() {
        // Given
        val workoutWithShortDuration = mockPersonalWorkout.copy(
            startTime = Instant.now().minus(30, ChronoUnit.SECONDS),
            endTime = Instant.now()
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithShortDuration)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display "< 1m" for very short duration
        composeTestRule
            .onNodeWithText("< 1m")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_handlesHourAndMinutesDuration() {
        // Given
        val workoutWithHoursAndMinutes = mockPersonalWorkout.copy(
            startTime = Instant.now().minus(95, ChronoUnit.MINUTES),
            endTime = Instant.now()
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(workoutWithHoursAndMinutes)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display "1h 35m" for 95 minutes
        composeTestRule
            .onNodeWithText("1h 35m")
            .assertIsDisplayed()
    }

    @Test
    fun workoutFeedItem_rendersCorrectlyWithMinimalData() {
        // Given - Workout with minimal required data
        val minimalWorkout = Workout(
            userId = "user123",
            id = WorkoutId("minimal"),
            name = "Quick Workout",
            date = LocalDate.now(),
            exercises = emptyList(),
            status = WorkoutStatus.COMPLETED,
            startTime = null,
            endTime = null,
            notes = null,
            templateId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val feedWorkout = FeedWorkout.forPersonalWorkout(minimalWorkout)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutFeedItem(
                    feedWorkout = feedWorkout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should render without crashing
        composeTestRule
            .onNodeWithTag("workout_feed_item")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Quick Workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("0 exercises")
            .assertIsDisplayed()
    }
}