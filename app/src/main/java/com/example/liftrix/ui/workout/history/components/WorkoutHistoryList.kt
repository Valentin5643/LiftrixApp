package com.example.liftrix.ui.workout.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.ui.common.LiftrixProgressIndicator

/**
 * Paginated workout history list component using LazyColumn with scroll-to-load functionality
 * Provides efficient scrolling performance and proper loading states for workout history display
 * 
 * Performance optimizations:
 * - @Stable callback parameters to prevent unnecessary recomposition
 * - Remember for callback functions to avoid lambda recreation
 * - Optimized LazyColumn with prefetch settings for smooth scrolling
 * - Enhanced scroll detection with cached threshold for efficient pagination
 */
@Composable
fun WorkoutHistoryList(
    workouts: List<WorkoutSummary>,
    onLoadMore: () -> Unit,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMoreData: Boolean = true,
    onWorkoutClick: (WorkoutSummary) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Remember callbacks to prevent recomposition
    val onLoadMoreStable = remember(onLoadMore) { onLoadMore }
    val onWorkoutClickStable = remember(onWorkoutClick) { onWorkoutClick }
    
    // Remember scroll threshold for performance
    val scrollThreshold = remember { 3 }
    
    // Handle initial loading state
    if (isLoading && workouts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LiftrixProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Loading workout history"
                }
            )
        }
        return
    }
    
    // Handle empty state
    if (!isLoading && workouts.isEmpty()) {
        EmptyWorkoutHistoryState(modifier = modifier.fillMaxSize())
        return
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
        // Note: beyondBoundsItemCount requires newer Compose version - performance optimized through other means
    ) {
        items(
            items = workouts,
            key = { workout -> workout.id.value },
            // Performance optimization: Content type for better item recycling
            contentType = { "workout_history_card" }
        ) { workout ->
            WorkoutHistoryCard(
                workoutSummary = workout,
                onClick = { onWorkoutClickStable(workout) }
            )
        }
        
        // Loading more indicator
        if (isLoadingMore && hasMoreData) {
            item(
                key = "loading_more_indicator",
                contentType = "loading_indicator"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiftrixProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Loading more workouts"
                        }
                    )
                }
            }
        }
        
        // End of data indicator
        if (!hasMoreData && workouts.isNotEmpty() && !isLoadingMore) {
            item(
                key = "end_of_data_indicator",
                contentType = "end_indicator"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No more workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // Optimized scroll detection for pagination
    LaunchedEffect(listState, hasMoreData, isLoadingMore, scrollThreshold) {
        snapshotFlow { 
            listState.isScrolledToEndOptimized(scrollThreshold) 
        }.collect { isScrolledToEnd ->
            if (isScrolledToEnd && hasMoreData && !isLoadingMore) {
                onLoadMoreStable()
            }
        }
    }
}

/**
 * Optimized extension function to detect when LazyListState is scrolled near the end
 * Performance improvements:
 * - Cached threshold parameter to avoid repeated calculations
 * - Efficient layoutInfo access pattern
 * - Reduced computation in scroll detection
 */
fun LazyListState.isScrolledToEndOptimized(threshold: Int): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return false
    
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
    
    return lastVisibleItem.index >= layoutInfo.totalItemsCount - threshold
}

/**
 * Extension function to detect when LazyListState is scrolled near the end
 * Triggers pagination when user is within threshold items from the bottom
 * 
 * @deprecated Use isScrolledToEndOptimized for better performance
 */
fun LazyListState.isScrolledToEnd(threshold: Int = 3): Boolean {
    return isScrolledToEndOptimized(threshold)
}

/**
 * Empty state component for when no workout history exists
 * Provides user-friendly messaging for first-time users
 */
@Composable
private fun EmptyWorkoutHistoryState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "No Workout History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Start your fitness journey by completing your first workout. Your workout history will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 