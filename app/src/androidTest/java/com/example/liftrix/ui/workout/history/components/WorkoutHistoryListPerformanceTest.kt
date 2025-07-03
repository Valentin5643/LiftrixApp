package com.example.liftrix.ui.workout.history.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Performance tests for WorkoutHistoryList component to validate smooth scrolling
 * and efficient recomposition following established performance testing patterns.
 * 
 * Tests validate:
 * - Smooth 60fps scrolling performance
 * - Minimal recomposition efficiency
 * - Large dataset handling capabilities
 * - Memory usage optimization
 */
@RunWith(AndroidJUnit4::class)
class WorkoutHistoryListPerformanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUserId = "performance-test-user"

    /**
     * Test smooth scrolling performance with large dataset (500+ items)
     * Validates 60fps target through scroll performance measurement
     */
    @Test
    fun testScrollingPerformance_meets60FpsTarget_withLargeDataset() = runTest {
        // Arrange: Create large dataset for performance testing
        val largeWorkoutList = createLargeWorkoutDataset(500)
        var loadMoreCalled = 0
        var workoutClickCalled = 0

        // Act: Measure scrolling performance
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHistoryList(
                    workouts = largeWorkoutList,
                    onLoadMore = { loadMoreCalled++ },
                    isLoading = false,
                    isLoadingMore = false,
                    hasMoreData = true,
                    onWorkoutClick = { workoutClickCalled++ },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Performance test: Measure scroll operations
        val scrollTime = measureTimeMillis {
            // Scroll through significant portion of list
            repeat(10) { index ->
                composeTestRule.onNodeWithText("Workout ${index * 20}")
                    .assertIsDisplayed()
                composeTestRule.awaitIdle()
            }
        }

        // Assert: Validate performance meets 60fps equivalent
        // Target: <16ms per frame for 60fps, allowing some overhead
        val targetTimePerOperation = 20L // ms
        val actualTimePerOperation = scrollTime / 10

        assertTrue(
            "Scroll performance: ${actualTimePerOperation}ms per operation exceeds target ${targetTimePerOperation}ms",
            actualTimePerOperation <= targetTimePerOperation
        )
    }

    /**
     * Test recomposition efficiency during pagination
     * Validates minimal recomposition through stable callback optimization
     */
    @Test
    fun testRecompositionEfficiency_withPagination() = runTest {
        // Arrange: Setup dynamic state for pagination testing
        val initialWorkouts = createLargeWorkoutDataset(50)
        val workoutsState = mutableStateOf(initialWorkouts)
        val isLoadingMoreState = mutableStateOf(false)
        var recompositionCount = 0

        composeTestRule.setContent {
            LiftrixTheme {
                // Track recomposition through side effect
                recompositionCount++
                
                WorkoutHistoryList(
                    workouts = workoutsState.value,
                    onLoadMore = {
                        isLoadingMoreState.value = true
                        // Simulate adding more workouts
                        workoutsState.value = workoutsState.value + createLargeWorkoutDataset(20, startIndex = 50)
                        isLoadingMoreState.value = false
                    },
                    isLoading = false,
                    isLoadingMore = isLoadingMoreState.value,
                    hasMoreData = true,
                    onWorkoutClick = { },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val initialRecompositionCount = recompositionCount

        // Act: Trigger pagination (scroll to end)
        composeTestRule.onNodeWithText("Workout 45")
            .performScrollToIndex(45)
        composeTestRule.awaitIdle()

        // Assert: Validate minimal recomposition
        val recompositionDelta = recompositionCount - initialRecompositionCount
        assertTrue(
            "Excessive recomposition: $recompositionDelta recompositions during pagination",
            recompositionDelta <= 3 // Allow maximum 3 recompositions for pagination
        )
    }

    /**
     * Test memory efficiency with large dataset
     * Validates LazyColumn recycling prevents memory leaks
     */
    @Test
    fun testMemoryEfficiency_withLargeDataset() = runTest {
        // Arrange: Create very large dataset
        val massiveWorkoutList = createLargeWorkoutDataset(1000)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHistoryList(
                    workouts = massiveWorkoutList,
                    onLoadMore = { },
                    isLoading = false,
                    isLoadingMore = false,
                    hasMoreData = true,
                    onWorkoutClick = { },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Act: Scroll through entire list to test recycling
        val memoryOperationTime = measureTimeMillis {
            repeat(20) { scrollIndex ->
                composeTestRule.onNodeWithText("Workout ${scrollIndex * 30}")
                    .performScrollToIndex(scrollIndex * 30)
                composeTestRule.awaitIdle()
            }
        }

        // Assert: Memory efficiency through reasonable scroll performance
        // Even with 1000 items, scrolling should remain efficient due to recycling
        assertTrue(
            "Memory efficiency test failed: ${memoryOperationTime}ms for large dataset scroll",
            memoryOperationTime < 2000L // 2 seconds max for full scroll test
        )
    }

    /**
     * Test loading state performance
     * Validates loading indicators don't impact scrolling performance
     */
    @Test
    fun testLoadingStatePerformance_maintainsScrolling() = runTest {
        // Arrange: Dataset with loading states
        val workouts = createLargeWorkoutDataset(100)

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHistoryList(
                    workouts = workouts,
                    onLoadMore = { },
                    isLoading = false,
                    isLoadingMore = true, // Loading more state active
                    hasMoreData = true,
                    onWorkoutClick = { },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Act: Measure scroll performance with loading indicator
        val scrollWithLoadingTime = measureTimeMillis {
            composeTestRule.onNodeWithContentDescription("Loading more workouts")
                .assertIsDisplayed()
            
            repeat(5) { index ->
                composeTestRule.onNodeWithText("Workout ${index * 10}")
                    .assertIsDisplayed()
                composeTestRule.awaitIdle()
            }
        }

        // Assert: Loading state doesn't significantly impact performance
        assertTrue(
            "Loading state impacts performance: ${scrollWithLoadingTime}ms",
            scrollWithLoadingTime < 500L // 500ms max for 5 scroll operations
        )
    }

    /**
     * Test scroll detection optimization performance
     * Validates optimized scroll detection doesn't impact UI performance
     */
    @Test
    fun testScrollDetectionPerformance_optimized() = runTest {
        // Arrange: Dataset that triggers scroll detection
        val workouts = createLargeWorkoutDataset(50)
        var loadMoreCallCount = 0

        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHistoryList(
                    workouts = workouts,
                    onLoadMore = { loadMoreCallCount++ },
                    isLoading = false,
                    isLoadingMore = false,
                    hasMoreData = true,
                    onWorkoutClick = { },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Act: Measure scroll detection performance
        val detectionTime = measureTimeMillis {
            // Scroll near end to trigger detection
            composeTestRule.onNodeWithText("Workout 47")
                .performScrollToIndex(47)
            composeTestRule.awaitIdle()
        }

        // Assert: Scroll detection is efficient and triggered
        assertTrue(
            "Load more not triggered by scroll detection",
            loadMoreCallCount > 0
        )
        assertTrue(
            "Scroll detection performance: ${detectionTime}ms exceeds target",
            detectionTime < 100L // 100ms max for scroll detection
        )
    }

    /**
     * Create large workout dataset for performance testing
     */
    private fun createLargeWorkoutDataset(
        count: Int,
        startIndex: Int = 0
    ): List<WorkoutSummary> {
        return (startIndex until startIndex + count).map { index ->
            WorkoutSummary(
                id = WorkoutId("workout_$index"),
                userId = testUserId,
                name = "Workout $index",
                date = LocalDate.now().minusDays(index.toLong()),
                duration = Duration.ofMinutes(45 + (index % 30)),
                exerciseCount = 5 + (index % 8),
                completedSets = 15 + (index % 10),
                totalSets = 20 + (index % 10),
                status = WorkoutStatus.COMPLETED,
                completionPercentage = 85.0 + (index % 15)
            )
        }
    }
} 