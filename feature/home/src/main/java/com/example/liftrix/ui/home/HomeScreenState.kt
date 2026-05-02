package com.example.liftrix.ui.home

import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.feature.home.model.HomeFeedWorkout
import com.example.liftrix.feature.home.model.HomeWorkout

data class HomeScreenData(
    val recentWorkouts: List<HomeWorkout> = emptyList(),
    val workoutFeedState: FeedState = FeedState.Loading,
    val recommendationsState: RecommendationsState = RecommendationsState.Loading,
    val showEndOfFeedMessage: Boolean = false,
    val isRefreshing: Boolean = false
) {
    val shouldShowEmptyState: Boolean
        get() = recentWorkouts.isEmpty() &&
            !isRefreshing &&
            workoutFeedState is FeedState.Success &&
            !workoutFeedState.hasData

    val shouldShowContent: Boolean
        get() = recentWorkouts.isNotEmpty() ||
            (workoutFeedState is FeedState.Success && workoutFeedState.hasData)

    val isAnyLoading: Boolean
        get() = isRefreshing ||
            workoutFeedState is FeedState.Loading ||
            recommendationsState is RecommendationsState.Loading
}

sealed class FeedState {
    data object Loading : FeedState()

    data class Success(
        val workouts: List<HomeFeedWorkout>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : FeedState() {
        val hasData: Boolean get() = workouts.isNotEmpty()
        val workoutCount: Int get() = workouts.size
    }

    data class Error(val message: String) : FeedState()
}

sealed class RecommendationsState {
    data object Loading : RecommendationsState()

    data class Success(
        val users: List<RecommendedUser>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : RecommendationsState() {
        val hasData: Boolean get() = users.isNotEmpty()
        val userCount: Int get() = users.size
        val isCacheValid: Boolean get() = users.all { it.isCacheValid }
    }

    data class Error(val message: String) : RecommendationsState()
}
