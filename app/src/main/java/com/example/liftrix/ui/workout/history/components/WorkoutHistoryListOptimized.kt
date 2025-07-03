package com.example.liftrix.ui.workout.history.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.liftrix.domain.model.WorkoutSummary

/**
 * Performance-optimized data class for WorkoutHistoryList callbacks
 * Uses @Stable annotation to mark parameters as stable for Compose optimization
 */
@Stable
data class WorkoutHistoryListCallbacks(
    val onLoadMore: () -> Unit,
    val onWorkoutClick: (WorkoutSummary) -> Unit
)

/**
 * Performance-optimized version of WorkoutHistoryList with @Stable callback wrapper
 * This demonstrates advanced Compose performance optimization patterns
 */
@Composable
fun WorkoutHistoryListOptimized(
    workouts: List<WorkoutSummary>,
    callbacks: WorkoutHistoryListCallbacks,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMoreData: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Remember stable callbacks to prevent recomposition
    val stableCallbacks = remember(callbacks) { callbacks }
    
    WorkoutHistoryList(
        workouts = workouts,
        onLoadMore = stableCallbacks.onLoadMore,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMoreData = hasMoreData,
        onWorkoutClick = stableCallbacks.onWorkoutClick,
        modifier = modifier
    )
}

/**
 * Convenience function to create stable callbacks
 */
@Composable
fun rememberWorkoutHistoryCallbacks(
    onLoadMore: () -> Unit,
    onWorkoutClick: (WorkoutSummary) -> Unit
): WorkoutHistoryListCallbacks {
    return remember(onLoadMore, onWorkoutClick) {
        WorkoutHistoryListCallbacks(
            onLoadMore = onLoadMore,
            onWorkoutClick = onWorkoutClick
        )
    }
} 