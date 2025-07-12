package com.example.liftrix.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FitnessCenter
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
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.common.calculateWorkoutIntensity
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
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
                // Enhanced home content with modern card-based layout
                EnhancedHomeContent(
                    workoutFeedState = uiState.workoutFeedState,
                    recommendationsState = uiState.recommendationsState,
                    workoutStats = uiState.workoutStats,
                    showEndOfFeedMessage = uiState.showEndOfFeedMessage,
                    onNavigateToWorkout = onNavigateToWorkout,
                    onNavigateToFriends = onNavigateToFriends,
                    onNavigateToMyWorkouts = onNavigateToMyWorkouts,
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
 * Enhanced home content with modern card-based layout and personal stats dashboard
 */
@Composable
private fun EnhancedHomeContent(
    workoutFeedState: FeedState,
    recommendationsState: RecommendationsState,
    workoutStats: WorkoutStats?,
    showEndOfFeedMessage: Boolean,
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
            ElevatedLiftrixCard(
                modifier = Modifier.padding(horizontal = GridSystem.spacing3),
                contentPadding = PaddingValues(GridSystem.spacing4)
            ) {
                EnhancedHomeHeader(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Personal Stats Dashboard
        item {
            PersonalStatsCard(
                workoutStats = workoutStats,
                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
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
                onViewAllClick = onNavigateToMyWorkouts,
                modifier = Modifier.padding(
                    start = GridSystem.spacing3,
                    end = GridSystem.spacing3,
                    top = GridSystem.spacing2
                )
            )
        }
        
        // Enhanced Workout Feed Items
        when (workoutFeedState) {
            is FeedState.Loading -> {
                items(5) {
                    FeedItemShimmer(
                        modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                    )
                }
            }
            
            is FeedState.Success -> {
                if (workoutFeedState.hasData) {
                    items(
                        items = workoutFeedState.workouts,
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
                    if (workoutFeedState.isLoadingMore) {
                        items(3) {
                            FeedItemShimmer(
                                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
                            )
                        }
                    }
                    
                    // End of feed message
                    if (showEndOfFeedMessage) {
                        item {
                            FeedEndMessage(
                                modifier = Modifier.padding(horizontal = GridSystem.spacing3)
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
                            modifier = Modifier.padding(horizontal = GridSystem.spacing3)
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
    ) {
        Text(
            text = "Welcome back!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Track your progress and stay motivated with friends",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(GridSystem.spacing4)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            Text(
                text = "No new people to discover right now",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Check back later or explore your existing connections",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = onViewAllFriends,
                modifier = Modifier.padding(top = GridSystem.spacing1)
            ) {
                Text("View Friends")
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
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        contentPadding = PaddingValues(GridSystem.spacing3)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "Unable to load recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Dismiss")
                }
                
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(GridSystem.spacing1))
                    Text("Retry")
                }
            }
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
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(GridSystem.spacing5)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "No workout activity yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Start working out or follow friends to see activity here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        contentPadding = PaddingValues(GridSystem.spacing3)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "Unable to load workout feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Dismiss")
                }
                
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(GridSystem.spacing1))
                    Text("Retry")
                }
            }
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
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        contentPadding = PaddingValues(GridSystem.spacing3)
    ) {
        Text(
            text = "You're all caught up! 🎉",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
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
        LiftrixCard(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            contentPadding = PaddingValues(GridSystem.spacing5)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Loading your social feed...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
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
        LiftrixCard(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            contentPadding = PaddingValues(GridSystem.spacing5)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Dismiss")
                    }
                    
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(GridSystem.spacing1))
                        Text("Try Again")
                    }
                }
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
        LiftrixCard(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            contentPadding = PaddingValues(GridSystem.spacing5)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Welcome to Liftrix!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Start your fitness journey by creating your first workout or connecting with friends",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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