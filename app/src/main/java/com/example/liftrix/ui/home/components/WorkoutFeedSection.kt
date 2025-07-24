package com.example.liftrix.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.domain.model.PerformanceMetrics
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.common.getWorkoutFeedScrollConfig
import com.example.liftrix.ui.common.state.FeedState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Main feed component displaying chronological workout timeline with lazy loading,
 * shimmer placeholders, and end-of-feed messaging
 * 
 * Supports initial load of 10-15 workouts, pagination in chunks of 10,
 * and displays maximum 30-40 workouts before showing end message.
 */
@Composable
fun WorkoutFeedSection(
    feedState: FeedState,
    showEndMessage: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (feedState) {
        is FeedState.Loading -> {
            FeedLoadingPlaceholder(modifier = modifier)
        }
        is FeedState.Success -> {
            WorkoutFeedContent(
                feedState = feedState,
                showEndMessage = showEndMessage,
                onLoadMore = onLoadMore,
                modifier = modifier
            )
        }
        is FeedState.Error -> {
            FeedErrorContent(
                message = feedState.message,
                modifier = modifier
            )
        }
    }
}

/**
 * Main feed content with workout items and pagination support
 * Enhanced with performance optimizations for 60fps scrolling
 */
@Composable
private fun WorkoutFeedContent(
    feedState: FeedState.Success,
    showEndMessage: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Performance optimization: remember stable callbacks and config
    val stableOnLoadMore = remember(onLoadMore) { onLoadMore }
    val scrollConfig = remember(feedState.workouts.size) { 
        getWorkoutFeedScrollConfig(feedState.workouts.size) 
    }
    
    // Optimized pagination with performance monitoring
    LaunchedEffect(listState, feedState.workouts.size, feedState.hasMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            
            // Trigger load more when near the end (within 3 items)
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 3
        }.distinctUntilChanged().collect { shouldLoadMore ->
            if (shouldLoadMore && feedState.hasMore && !feedState.isLoadingMore) {
                stableOnLoadMore()
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("workout_feed"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Workout feed items with optimized keys and content types
        items(
            items = feedState.workouts,
            key = { feedWorkout -> 
                "workout_${feedWorkout.workout.id.value}"
            },
            contentType = { "workout_feed_item" }
        ) { feedWorkout ->
            WorkoutFeedItem(
                feedWorkout = feedWorkout,
                modifier = Modifier.testTag("workout_feed_item")
            )
        }
    
        // Loading more placeholders with optimized keys
        if (feedState.isLoadingMore) {
            items(
                count = 3,
                key = { index -> "loading_shimmer_$index" },
                contentType = { "loading_shimmer" }
            ) { index ->
                FeedItemShimmer()
            }
        }
        
        // End of feed message with stable key
        if (showEndMessage) {
            item(
                key = "feed_end_message",
                contentType = "end_message"
            ) {
                FeedEndMessage()
            }
        }
    }
}

/**
 * Loading placeholder for initial feed load
 */
@Composable
private fun FeedLoadingPlaceholder(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            FeedItemShimmer()
        }
    }
}

/**
 * Error content for feed loading failures
 */
@Composable
private fun FeedErrorContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Unable to load workout feed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}




