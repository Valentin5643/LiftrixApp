package com.example.liftrix.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.state.HomeScreenData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.FeedState
import com.example.liftrix.ui.common.state.RecommendationsState
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.common.calculateWorkoutIntensity
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixTheme
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState

/**
 * Enhanced home screen with modern social feed layout and personal stats dashboard.
 * 
 * Features:
 * - Personal performance stats dashboard with asymmetrical card layout
 * - Discovery carousel with enhanced user recommendations and smooth animations
 * - Chronological workout feed with enhanced card design and athletic styling
 * - Pull-to-refresh functionality with improved user feedback
 * - Loading, error, and empty state handling with modern design
 * - Enhanced HomeViewModel integration with MVI pattern
 * - Analytics tracking for user interactions and performance metrics
 * 
 * @param onNavigateToWorkout Callback to navigate to workout details screen
 * @param onNavigateToFriends Callback to navigate to friends screen
 * @param onNavigateToMyWorkouts Callback to navigate to personal workouts screen
 * @param modifier Modifier for styling the screen
 * @param viewModel HomeViewModel for state management (injectable for testing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToMyWorkouts: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when (uiState) {
        UiState.Loading -> {
            HomeLoadingScreen(modifier = modifier)
        }
        is UiState.Success -> {
            val successState = uiState as UiState.Success<HomeScreenData>
            val screenData = successState.data
            val pullRefreshState = rememberPullRefreshState(
                refreshing = screenData.isRefreshing,
                onRefresh = { viewModel.handleEvent(HomeEvent.RefreshData) }
            )
            
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    screenData.shouldShowEmptyState -> {
                        EmptyState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    else -> {
                        // Enhanced home content with modern card-based layout
                        EnhancedHomeContent(
                            screenData = screenData,
                            onNavigateToWorkout = onNavigateToWorkout,
                            onNavigateToFriends = onNavigateToFriends,
                            onNavigateToMyWorkouts = onNavigateToMyWorkouts,
                            onEvent = { event -> viewModel.handleEvent(event) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                PullRefreshIndicator(
                    refreshing = screenData.isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
        is UiState.Error -> {
            val errorState = uiState as UiState.Error<HomeScreenData>
            HomeErrorScreen(
                error = errorState.error,
                onRetry = { viewModel.handleEvent(HomeEvent.RefreshData) },
                modifier = modifier
            )
        }
        is UiState.Empty -> {
            HomeEmptyScreen(
                onCreateWorkout = onNavigateToMyWorkouts,
                modifier = modifier
            )
        }
        else -> {
            // Fallback for unknown states
            HomeLoadingScreen(modifier = modifier)
        }
    }
}

/**
 * Enhanced home content with modern card-based layout and personal stats dashboard
 */
@Composable
private fun EnhancedHomeContent(
    screenData: HomeScreenData,
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToMyWorkouts: () -> Unit,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Enhanced home screen with personal stats dashboard and social workout feed"
            },
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3),
        contentPadding = PaddingValues(vertical = GridSystem.spacing3)
    ) {
        // Enhanced Header Section  
        item {
            EnhancedHomeHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GridSystem.spacing3)
            )
        }
        
        // Discovery Section Header
        item {
            SectionHeader(
                title = "Discover People",
                onViewAllClick = onNavigateToFriends,
                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
            )
        }
        
        // Enhanced Discovery Carousel Section
        item {
            DiscoveryCarouselSection(
                recommendationsState = screenData.recommendationsState,
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
                onViewAllClick = onNavigateToMyWorkouts,
                modifier = Modifier.padding(
                    start = GridSystem.spacing3,
                    end = GridSystem.spacing3,
                    top = GridSystem.spacing2
                )
            )
        }
        
        // Enhanced Workout Feed Items
        when (screenData.workoutFeedState) {
            is FeedState.Loading -> {
                items(5) {
                    FeedItemShimmer(
                        modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                    )
                }
            }
            
            is FeedState.Success -> {
                if (screenData.workoutFeedState.hasData) {
                    items(
                        items = screenData.workoutFeedState.workouts,
                        key = { feedWorkout -> feedWorkout.workout.id.value }
                    ) { feedWorkout ->
                        val workoutIntensity = calculateWorkoutIntensityFromFeedWorkout(feedWorkout)
                        ContextualColorOverlay(
                            context = ColorContext.WorkoutIntensity(
                                intensity = workoutIntensity,
                                workoutType = com.example.liftrix.ui.common.WorkoutType.GENERAL
                            ),
                            alpha = 0.03f
                        ) {
                            WorkoutFeedItem(
                                feedWorkout = feedWorkout,
                                onClick = {
                                    onEvent(HomeEvent.FeedWorkoutOpened(feedWorkout))
                                    onNavigateToWorkout(feedWorkout.workout.id.value)
                                },
                                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                            )
                        }
                    }
                    
                    // Loading more items
                    if (screenData.workoutFeedState.isLoadingMore) {
                        items(3) {
                            FeedItemShimmer(
                                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                            )
                        }
                    }
                    
                    // End of feed message
                    if (screenData.showEndOfFeedMessage) {
                        item {
                            FeedEndMessage(
                                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                            )
                        }
                    }
                    
                    // Load more trigger (invisible item for pagination)
                    if (screenData.workoutFeedState.hasMore && !screenData.workoutFeedState.isLoadingMore) {
                        item {
                            LaunchedEffect(Unit) {
                                onEvent(HomeEvent.LoadMoreWorkouts)
                            }
                        }
                    }
                } else {
                    item {
                        EmptyWorkoutFeedState(
                            modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                        )
                    }
                }
            }
            
            is FeedState.Error -> {
                item {
                    WorkoutFeedErrorState(
                        message = screenData.workoutFeedState.message,
                        onRetry = { onEvent(HomeEvent.LoadMoreWorkouts) },
                        onDismiss = { onEvent(HomeEvent.FeedErrorDismissed) },
                        modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                    )
                }
            }
        }
    }
}

/**
 * Enhanced home screen header with modern typography and athletic branding
 */
@Composable
private fun EnhancedHomeHeader(
    modifier: Modifier = Modifier
) {
    Text(
        text = "Track your progress and stay motivated with friends",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = GridSystem.spacing2)
    )
}

/**
 * Enhanced section header with modern styling and improved accessibility
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
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        onViewAllClick?.let { onClick ->
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(
                    horizontal = GridSystem.spacing2,
                    vertical = GridSystem.spacing1
                )
            ) {
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Enhanced discovery carousel section with modern error handling
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
                    modifier = modifier.padding(horizontal = GridSystem.spacing3)
                )
            }
        }
        
        is RecommendationsState.Error -> {
            DiscoveryErrorState(
                message = recommendationsState.message,
                onRetry = onLoadMore,
                onDismiss = onErrorDismissed,
                modifier = modifier.padding(horizontal = GridSystem.spacing3)
            )
        }
    }
}

/**
 * Enhanced empty state for discovery section with modern card design
 */
@Composable
private fun EmptyDiscoveryState(
    onViewAllFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "",  // Empty title since we're rendering custom content
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GridSystem.spacing2),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Icon on the left
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = "No people to discover",
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = GridSystem.spacing3),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            
            // Text content on the right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Main title
                Text(
                    text = "No new people to discover right now",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Subtitle
                Text(
                    text = "Check back later or explore your existing connections",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Enhanced error state for discovery section with modern card design
 */
@Composable
private fun DiscoveryErrorState(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "Unable to load recommendations",
        subtitle = message,
        modifier = modifier,
        actions = {
            SecondaryActionButton(
                text = "Dismiss",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(GridSystem.spacing2))
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Enhanced empty state for workout feed with modern card design
 */
@Composable
private fun EmptyWorkoutFeedState(
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "No workout activity yet",
        subtitle = "Start working out or follow friends to see activity here",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "Fitness center icon",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Enhanced error state for workout feed with modern card design
 */
@Composable
private fun WorkoutFeedErrorState(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "Unable to load workout feed",
        subtitle = message,
        modifier = modifier,
        actions = {
            SecondaryActionButton(
                text = "Dismiss",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(GridSystem.spacing2))
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Enhanced end of feed message with modern card design
 */
@Composable
private fun FeedEndMessage(
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "You're all caught up! 🎉",
        modifier = modifier
    ) {
        // Empty content since title contains the message
    }
}

/**
 * Enhanced loading state component with modern design
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        UnifiedWorkoutCard(
            title = "Loading your social feed...",
            modifier = Modifier.padding(GridSystem.spacing5)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Enhanced error state component for global errors with modern design
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
            .padding(GridSystem.spacing5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        UnifiedWorkoutCard(
            title = "Something went wrong",
            subtitle = errorMessage,
            modifier = Modifier.padding(GridSystem.spacing5),
            actions = {
                SecondaryActionButton(
                    text = "Dismiss",
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(GridSystem.spacing2))
                PrimaryActionButton(
                    text = "Try Again",
                    onClick = onRetry
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error icon",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Enhanced empty state component with modern design and call-to-action
 */
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(GridSystem.spacing5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        UnifiedWorkoutCard(
            title = "Welcome to Liftrix!",
            subtitle = "Start your fitness journey by creating your first workout or connecting with friends",
            modifier = Modifier.padding(GridSystem.spacing5)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Fitness center icon",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Helper function to calculate workout intensity from FeedWorkout data
 * Used for contextual color overlays
 */
private fun calculateWorkoutIntensityFromFeedWorkout(feedWorkout: FeedWorkout): Float {
    val workout = feedWorkout.workout
    val completionPercentage = workout.getCompletionPercentage()
    val duration = workout.getDuration()?.toMinutes()
    
    return calculateWorkoutIntensity(
        completionPercentage = completionPercentage,
        duration = duration,
        workoutType = com.example.liftrix.ui.common.WorkoutType.GENERAL
    )
}

@Composable
private fun HomeLoadingScreen(modifier: Modifier = Modifier) {
    LoadingState(modifier = modifier)
}

@Composable
private fun HomeErrorScreen(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ErrorState(
        errorMessage = error.message,
        onRetry = onRetry,
        onDismiss = { /* Error is handled by retry */ },
        modifier = modifier
    )
}

@Composable
private fun HomeEmptyScreen(
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(modifier = modifier)
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