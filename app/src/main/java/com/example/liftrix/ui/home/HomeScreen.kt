package com.example.liftrix.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.home.components.*
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.theme.LiftrixTheme
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState

/**
 * Main home screen displaying streamlined social fitness feed.
 * 
 * Features:
 * - Discovery carousel for user recommendations with follow functionality
 * - Chronological workout feed with personal and friends' workouts
 * - Pull-to-refresh functionality for data updates
 * - Loading, error, and empty state handling
 * - Enhanced HomeViewModel integration with MVI pattern
 * - Analytics tracking for user interactions
 * 
 * @param onNavigateToWorkout Callback to navigate to workout details screen
 * @param onNavigateToFriends Callback to navigate to friends screen
 * @param modifier Modifier for styling the screen
 * @param viewModel HomeViewModel for state management (injectable for testing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = { viewModel.onEvent(HomeEvent.RefreshData) }
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        when {
            uiState.shouldShowError -> {
                ErrorState(
                    errorMessage = uiState.errorMessage ?: "Something went wrong",
                    onRetry = { viewModel.onEvent(HomeEvent.RefreshData) },
                    onDismiss = { viewModel.onEvent(HomeEvent.ErrorDismissed) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            uiState.shouldShowEmptyState -> {
                EmptyState(
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            else -> {
                // Use single LazyColumn to avoid nested scrolling
                FlattenedHomeContent(
                    workoutFeedState = uiState.workoutFeedState,
                    recommendationsState = uiState.recommendationsState,
                    showEndOfFeedMessage = uiState.showEndOfFeedMessage,
                    onNavigateToWorkout = onNavigateToWorkout,
                    onNavigateToFriends = onNavigateToFriends,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Flattened content using single LazyColumn to avoid nested scrolling constraints
 */
@Composable
private fun FlattenedHomeContent(
    workoutFeedState: FeedState,
    recommendationsState: RecommendationsState,
    showEndOfFeedMessage: Boolean,
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Home screen with user discovery and social workout feed"
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header Section
        item {
            HomeHeader(
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        
        // Discovery Section Header
        item {
            SectionHeader(
                title = "Discover People",
                onViewAllClick = onNavigateToFriends,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        
        // Discovery Carousel Section (Non-scrolling horizontal list)
        item {
            DiscoveryCarouselSection(
                recommendationsState = recommendationsState,
                onLoadMore = { onEvent(HomeEvent.LoadMoreRecommendations) },
                onFollowUser = { userId -> onEvent(HomeEvent.FollowUser(userId)) },
                onViewAllFriends = onNavigateToFriends,
                onErrorDismissed = { onEvent(HomeEvent.RecommendationsErrorDismissed) }
            )
        }
        
        // Workout Feed Header
        item {
            SectionHeader(
                title = "Recent Activity",
                onViewAllClick = null,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
        
        // Workout Feed Items (Individual items instead of nested LazyColumn)
        when (workoutFeedState) {
            is FeedState.Loading -> {
                items(5) {
                    FeedItemShimmer(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            
            is FeedState.Success -> {
                if (workoutFeedState.hasData) {
                    items(
                        items = workoutFeedState.workouts,
                        key = { feedWorkout -> feedWorkout.workout.id.value }
                    ) { feedWorkout ->
                        WorkoutFeedItem(
                            feedWorkout = feedWorkout,
                            onClick = {
                                onEvent(HomeEvent.FeedWorkoutOpened(feedWorkout))
                                onNavigateToWorkout(feedWorkout.workout.id.value)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    
                    // Loading more items
                    if (workoutFeedState.isLoadingMore) {
                        items(3) {
                            FeedItemShimmer(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    
                    // End of feed message
                    if (showEndOfFeedMessage) {
                        item {
                            FeedEndMessage(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    
                    // Load more trigger (invisible item for pagination)
                    if (workoutFeedState.hasMore && !workoutFeedState.isLoadingMore) {
                        item {
                            LaunchedEffect(Unit) {
                                onEvent(HomeEvent.LoadMoreWorkouts)
                            }
                        }
                    }
                } else {
                    item {
                        EmptyWorkoutFeedState(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            
            is FeedState.Error -> {
                item {
                    WorkoutFeedErrorState(
                        message = workoutFeedState.message,
                        onRetry = { onEvent(HomeEvent.LoadMoreWorkouts) },
                        onDismiss = { onEvent(HomeEvent.FeedErrorDismissed) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Discovery carousel section without nested scrolling
 */
@Composable
private fun DiscoveryCarouselSection(
    recommendationsState: RecommendationsState,
    onLoadMore: () -> Unit,
    onFollowUser: (String) -> Unit,
    onViewAllFriends: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (recommendationsState) {
        is RecommendationsState.Loading -> {
            DiscoveryCarousel(
                recommendedUsers = emptyList(),
                isLoading = true,
                hasMore = false,
                onLoadMore = onLoadMore,
                onFollowUser = onFollowUser,
                modifier = modifier
            )
        }
        
        is RecommendationsState.Success -> {
            if (recommendationsState.hasData) {
                DiscoveryCarousel(
                    recommendedUsers = recommendationsState.users,
                    isLoading = recommendationsState.isLoadingMore,
                    hasMore = recommendationsState.hasMore,
                    onLoadMore = onLoadMore,
                    onFollowUser = onFollowUser,
                    modifier = modifier
                )
            } else {
                EmptyDiscoveryState(
                    onViewAllFriends = onViewAllFriends,
                    modifier = modifier.padding(horizontal = 16.dp)
                )
            }
        }
        
        is RecommendationsState.Error -> {
            DiscoveryErrorState(
                message = recommendationsState.message,
                onRetry = onLoadMore,
                onDismiss = onErrorDismissed,
                modifier = modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Enhanced home screen header with social focus
 */
@Composable
private fun HomeHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Welcome back!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Discover new fitness friends and stay motivated",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Reusable section header component
 */
@Composable
private fun SectionHeader(
    title: String,
    onViewAllClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        onViewAllClick?.let { onClick ->
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Empty state for discovery section when no recommendations are available
 */
@Composable
private fun EmptyDiscoveryState(
    onViewAllFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No new people to discover right now",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Check back later or explore your existing connections",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            TextButton(onClick = onViewAllFriends) {
                Text("View Friends")
            }
        }
    }
}

/**
 * Error state for discovery section
 */
@Composable
private fun DiscoveryErrorState(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = "Unable to load recommendations",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Empty state for workout feed
 */
@Composable
private fun EmptyWorkoutFeedState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No workout activity yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Start working out or follow friends to see activity here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Error state for workout feed
 */
@Composable
private fun WorkoutFeedErrorState(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Unable to load workout feed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Loading state component for initial load
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading your social feed...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state component for global errors
 */
@Composable
private fun ErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LiftrixTheme {
        HomeScreen(
            onNavigateToWorkout = {},
            onNavigateToFriends = {}
        )
    }
}