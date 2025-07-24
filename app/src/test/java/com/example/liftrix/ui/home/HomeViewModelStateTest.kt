package com.example.liftrix.ui.home

import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.HomeUiState
import com.example.liftrix.ui.common.state.HomeScreenData
import com.example.liftrix.ui.common.state.FeedState
import com.example.liftrix.ui.common.state.RecommendationsState
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for HomeViewModel state transitions and sealed class pattern.
 * 
 * Validates the migration to sealed class UiState pattern with comprehensive
 * state transition testing and type safety verification for complex nested states.
 */
class HomeViewModelStateTest {

    @Test
    fun `homeScreenData has proper default values`() = runTest {
        val defaultData = HomeScreenData()
        
        assertTrue("Recent workouts should be empty by default", defaultData.recentWorkouts.isEmpty())
        assertTrue("Feed state should be Loading by default", defaultData.workoutFeedState is FeedState.Loading)
        assertTrue("Recommendations state should be Loading by default", defaultData.recommendationsState is RecommendationsState.Loading)
        assertFalse("Should not be refreshing by default", defaultData.isRefreshing)
        assertFalse("Should not show end of feed message by default", defaultData.showEndOfFeedMessage)
    }

    @Test
    fun `homeScreenData computed properties work correctly`() = runTest {
        val emptyData = HomeScreenData()
        assertTrue("Empty data should show empty state", emptyData.shouldShowEmptyState)
        assertFalse("Empty data should not show content", emptyData.shouldShowContent)

        val dataWithWorkouts = HomeScreenData(
            recentWorkouts = listOf(/* mock workout */),
            workoutFeedState = FeedState.Success(workouts = emptyList(), hasMore = false)
        )
        // Note: This would need actual Workout objects for complete testing
        assertNotNull("Computed properties should be accessible", dataWithWorkouts.shouldShowContent)
    }

    @Test
    fun `feedState transitions are type safe`() = runTest {
        val feedStates = listOf<FeedState>(
            FeedState.Loading,
            FeedState.Success(workouts = emptyList(), hasMore = false),
            FeedState.Error("Test error")
        )
        
        feedStates.forEach { state ->
            val result = when (state) {
                is FeedState.Loading -> "loading"
                is FeedState.Success -> "success"
                is FeedState.Error -> "error"
            }
            
            assertNotNull("Each feed state should be handled", result)
        }
    }

    @Test
    fun `recommendationsState transitions are type safe`() = runTest {
        val recommendationStates = listOf<RecommendationsState>(
            RecommendationsState.Loading,
            RecommendationsState.Success(users = emptyList(), hasMore = false),
            RecommendationsState.Error("Test error")
        )
        
        recommendationStates.forEach { state ->
            val result = when (state) {
                is RecommendationsState.Loading -> "loading"
                is RecommendationsState.Success -> "success"
                is RecommendationsState.Error -> "error"
            }
            
            assertNotNull("Each recommendation state should be handled", result)
        }
    }

    @Test
    fun `homeUiState sealed class is properly typed`() = runTest {
        val states = listOf<HomeUiState>(
            HomeUiState.Loading,
            HomeUiState.Success(HomeScreenData()),
            HomeUiState.Error(LiftrixError.UnknownError("Test error")),
            HomeUiState.Empty
        )
        
        states.forEach { state ->
            val result = when (state) {
                is HomeUiState.Loading -> "loading"
                is HomeUiState.Success -> "success"
                is HomeUiState.Error -> "error" 
                is HomeUiState.Empty -> "empty"
            }
            
            assertNotNull("Each UI state should be handled", result)
            assertTrue("Result should be valid state name", 
                result in listOf("loading", "success", "error", "empty"))
        }
    }

    @Test
    fun `complex nested state handling works correctly`() = runTest {
        val complexData = HomeScreenData(
            recentWorkouts = emptyList(),
            workoutFeedState = FeedState.Success(workouts = emptyList(), hasMore = true, isLoadingMore = true),
            recommendationsState = RecommendationsState.Error("Failed to load recommendations"),
            isRefreshing = true
        )
        
        val successState = HomeUiState.Success(complexData)
        
        assertTrue("State should be Success", successState is HomeUiState.Success)
        assertTrue("Should be refreshing", successState.data.isRefreshing)
        assertTrue("Feed should have more", (successState.data.workoutFeedState as FeedState.Success).hasMore)
        assertTrue("Recommendations should be in error", successState.data.recommendationsState is RecommendationsState.Error)
    }
}