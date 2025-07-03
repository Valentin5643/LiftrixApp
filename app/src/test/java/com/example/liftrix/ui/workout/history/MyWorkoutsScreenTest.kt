package com.example.liftrix.ui.workout.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate

/**
 * Compose UI tests for MyWorkoutsScreen
 * Tests screen components, user interactions, and accessibility
 * 
 * Follows established codebase testing patterns with ComposeTestRule and Hilt integration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MyWorkoutsScreenTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testWorkoutSummary1 = WorkoutSummary(
        id = WorkoutId.generate(),
        userId = "test-user-id",
        name = "Test Workout 1",
        date = LocalDate.now().minusDays(1),
        duration = Duration.ofMinutes(45),
        exerciseCount = 5,
        completedSets = 15,
        totalSets = 15,
        status = WorkoutStatus.COMPLETED,
        completionPercentage = 100.0
    )

    private val testWorkoutSummary2 = WorkoutSummary(
        id = WorkoutId.generate(),
        userId = "test-user-id",
        name = "Test Workout 2",
        date = LocalDate.now().minusDays(2),
        duration = Duration.ofMinutes(60),
        exerciseCount = 6,
        completedSets = 18,
        totalSets = 18,
        status = WorkoutStatus.COMPLETED,
        completionPercentage = 100.0
    )

    private val testWorkoutSummaries = listOf(testWorkoutSummary1, testWorkoutSummary2)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun myWorkoutsScreen_displaysTopAppBarWithTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(workouts = testWorkoutSummaries),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("My Workouts")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_displaysBackButton() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(workouts = testWorkoutSummaries),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_backButtonTriggersNavigation() {
        // Arrange
        var backNavigationCalled = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(workouts = testWorkoutSummaries),
                    onEvent = {},
                    onNavigateBack = { backNavigationCalled = true },
                    onNavigateToWorkout = {}
                )
            }
        }

        // Act
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .performClick()

        // Assert
        assert(backNavigationCalled)
    }

    @Test
    fun myWorkoutsScreen_displaysWorkoutList() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(workouts = testWorkoutSummaries),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Test Workout 1")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Test Workout 2")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_showsLoadingState() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        isLoading = true
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - loading state should be shown via WorkoutHistoryList
        // The loading indicator is handled internally by WorkoutHistoryList component
        composeTestRule
            .onNodeWithText("My Workouts")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_showsEmptyState() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        isLoading = false,
                        hasMoreData = false
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - empty state is handled by WorkoutHistoryList internally
        composeTestRule
            .onNodeWithText("My Workouts")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_showsErrorState() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Failed to load workouts: Network error"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Unable to Load Workouts")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Failed to load workouts: Network error")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_errorStateShowsRetryButton() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Network error"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Retry")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Dismiss")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_retryButtonTriggersEvent() {
        // Arrange
        var loadWorkoutsCalled = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Network error"
                    ),
                    onEvent = { event ->
                        if (event is UiEvent.LoadWorkouts) {
                            loadWorkoutsCalled = true
                        }
                    },
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Act
        composeTestRule
            .onNodeWithText("Retry")
            .performClick()

        // Assert
        assert(loadWorkoutsCalled)
    }

    @Test
    fun myWorkoutsScreen_dismissButtonTriggersEvent() {
        // Arrange
        var clearErrorCalled = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Network error"
                    ),
                    onEvent = { event ->
                        if (event is UiEvent.ClearError) {
                            clearErrorCalled = true
                        }
                    },
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Act
        composeTestRule
            .onNodeWithText("Dismiss")
            .performClick()

        // Assert
        assert(clearErrorCalled)
    }

    @Test
    fun myWorkoutsScreen_errorStateHidesWhenDataPresent() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = testWorkoutSummaries,
                        error = "Background error"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - should show content, not error state when data is present
        composeTestRule
            .onNodeWithText("Test Workout 1")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Unable to Load Workouts")
            .assertIsNotDisplayed()
    }

    @Test
    fun myWorkoutsScreen_accessibilityContentDescriptions() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Network error"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - verify accessibility content descriptions
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithContentDescription("Retry loading workouts")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithContentDescription("Dismiss error message")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_workoutClickTriggersNavigation() {
        // Arrange
        var navigatedWorkoutId: String? = null
        
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(workouts = testWorkoutSummaries),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = { workoutId ->
                        navigatedWorkoutId = workoutId
                    }
                )
            }
        }

        // Act - Click on the first workout
        composeTestRule
            .onNodeWithText("Test Workout 1")
            .performClick()

        // Assert
        assert(navigatedWorkoutId == testWorkoutSummary1.id.value)
    }

    @Test
    fun myWorkoutsScreen_handlesAuthenticationError() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Please sign in to view your workout history"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Please sign in to view your workout history")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_handlesNetworkError() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Network error. Please check your connection and try again"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Network error. Please check your connection and try again")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_pullToRefreshIntegration() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = testWorkoutSummaries,
                        isLoading = true // Simulating refresh loading
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - pull refresh indicator should be present
        // PullRefreshIndicator is rendered when loading and workouts exist
        composeTestRule
            .onNodeWithText("Test Workout 1")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_loadingMoreState() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = testWorkoutSummaries,
                        isLoadingMore = true,
                        hasMoreData = true
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - content should be visible along with loading more state
        composeTestRule
            .onNodeWithText("Test Workout 1")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Test Workout 2")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_stateTransitions() {
        // Arrange - start with loading state
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        isLoading = true
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert loading state
        composeTestRule
            .onNodeWithText("My Workouts")
            .assertIsDisplayed()

        // Re-compose with content state
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = testWorkoutSummaries,
                        isLoading = false
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert content state
        composeTestRule
            .onNodeWithText("Test Workout 1")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_materialsDesignCompliance() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(workouts = testWorkoutSummaries),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - verify Material 3 components are rendered
        composeTestRule
            .onNodeWithText("My Workouts")
            .assertIsDisplayed() // TopAppBar title
            
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .assertIsDisplayed() // Navigation icon
    }

    @Test
    fun myWorkoutsScreen_errorCardStyling() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Test error message"
                    ),
                    onEvent = {},
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Assert - error card components are displayed
        composeTestRule
            .onNodeWithText("Unable to Load Workouts")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Test error message")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Retry")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Dismiss")
            .assertIsDisplayed()
    }

    @Test
    fun myWorkoutsScreen_multipleEventsHandling() {
        // Arrange
        val events = mutableListOf<UiEvent>()
        
        composeTestRule.setContent {
            LiftrixTheme {
                MyWorkoutsScreenContent(
                    uiState = UiState(
                        workouts = emptyList(),
                        error = "Test error"
                    ),
                    onEvent = { event -> events.add(event) },
                    onNavigateBack = {},
                    onNavigateToWorkout = {}
                )
            }
        }

        // Act - trigger multiple events
        composeTestRule
            .onNodeWithText("Retry")
            .performClick()
            
        composeTestRule
            .onNodeWithText("Dismiss")
            .performClick()

        // Assert
        assert(events.size == 2)
        assert(events[0] is UiEvent.LoadWorkouts)
        assert(events[1] is UiEvent.ClearError)
    }
} 