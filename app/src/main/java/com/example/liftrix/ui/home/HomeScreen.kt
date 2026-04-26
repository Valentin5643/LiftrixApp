package com.example.liftrix.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.home.components.*
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.state.HomeScreenData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.FeedState
import com.example.liftrix.ui.common.state.RecommendationsState
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.common.calculateWorkoutIntensity
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.feed.FeedViewModel
import com.example.liftrix.ui.feed.FeedEvent
import com.example.liftrix.ui.feed.FeedTab
import com.example.liftrix.ui.feed.PostInteraction
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import com.example.liftrix.ui.common.sync.SyncStatusIndicator
import com.example.liftrix.ui.common.sync.SyncStatusViewModel
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.navigation.navigateToEditWorkout
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
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
 * @param navController Navigation controller for navigating between screens
 * @param onNavigateToWorkout Callback to navigate to workout details screen
 * @param onNavigateToFriends Callback to navigate to friends screen
 * @param onNavigateToMyWorkouts Callback to navigate to personal workouts screen
 * @param modifier Modifier for styling the screen
 * @param viewModel HomeViewModel for state management (injectable for testing)
 * @param feedViewModel FeedViewModel for social feed management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToMyWorkouts: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    feedViewModel: FeedViewModel = hiltViewModel(),
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel()
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
                // Always show the main content which includes discover section
                // The individual sections will handle their own empty states
                EnhancedHomeContent(
                    navController = navController,
                    screenData = screenData,
                    onNavigateToWorkout = onNavigateToWorkout,
                    onNavigateToFriends = onNavigateToFriends,
                    onNavigateToMyWorkouts = onNavigateToMyWorkouts,
                    onEvent = { event -> viewModel.handleEvent(event) },
                    feedViewModel = feedViewModel,
                    syncStatusViewModel = syncStatusViewModel,
                    modifier = Modifier.fillMaxSize()
                )
                
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
            // Even in empty state, show the full content with discover section
            // Individual sections will handle their empty states appropriately
            val emptyScreenData = HomeScreenData()
            val pullRefreshState = rememberPullRefreshState(
                refreshing = false,
                onRefresh = { viewModel.handleEvent(HomeEvent.RefreshData) }
            )
            
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                EnhancedHomeContent(
                    navController = navController,
                    screenData = emptyScreenData,
                    onNavigateToWorkout = onNavigateToWorkout,
                    onNavigateToFriends = onNavigateToFriends,
                    onNavigateToMyWorkouts = onNavigateToMyWorkouts,
                    onEvent = { event -> viewModel.handleEvent(event) },
                    feedViewModel = feedViewModel,
                    syncStatusViewModel = syncStatusViewModel,
                    modifier = Modifier.fillMaxSize()
                )
                
                PullRefreshIndicator(
                    refreshing = false,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
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
    navController: NavController,
    screenData: HomeScreenData,
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToMyWorkouts: () -> Unit,
    onEvent: (HomeEvent) -> Unit,
    feedViewModel: FeedViewModel,
    syncStatusViewModel: SyncStatusViewModel,
    modifier: Modifier = Modifier
) {
    // Get feed state from FeedViewModel
    val feedUiState by feedViewModel.uiState.collectAsState()
    val posts = feedViewModel.posts.collectAsLazyPagingItems()
    
    // Create a local UI state for feed tab selection
    var selectedTab by remember { mutableStateOf(FeedTab.HOME) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Enhanced home screen with personal stats dashboard and social workout feed"
            },
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3),
        contentPadding = PaddingValues(vertical = GridSystem.spacing3)
    ) {
        // Enhanced Header Section with Sync Status
        item {
            EnhancedHomeHeader(
                syncStatusViewModel = syncStatusViewModel,
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
                onErrorDismissed = { onEvent(HomeEvent.RecommendationsErrorDismissed) },
                onUserClick = { userId -> 
                    navController.navigate(LiftrixRoute.PublicProfile(userId))
                }
            )
        }
        
        // Workout Feed Header with Dropdown Selector and View All
        item {
            var showDropdown by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = GridSystem.spacing3,
                        vertical = GridSystem.spacing2
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Dropdown selector for Following/Explore
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showDropdown = !showDropdown }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTab == FeedTab.HOME) "Following" else "Explore",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle dropdown",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Dropdown menu
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.width(150.dp)
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "Following",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selectedTab == FeedTab.HOME) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedTab = FeedTab.HOME
                                showDropdown = false
                                feedViewModel.handleEvent(FeedEvent.SelectTab(FeedTab.HOME))
                            },
                            leadingIcon = if (selectedTab == FeedTab.HOME) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "Explore",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selectedTab == FeedTab.DISCOVERY) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedTab = FeedTab.DISCOVERY
                                showDropdown = false
                                feedViewModel.handleEvent(FeedEvent.SelectTab(FeedTab.DISCOVERY))
                            },
                            leadingIcon = if (selectedTab == FeedTab.DISCOVERY) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
                
                // Right side: View All button
                TextButton(
                    onClick = onNavigateToMyWorkouts,
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
                        contentDescription = "View all workouts",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Social Feed Posts (from FeedViewModel - exactly like Feed screen)
        // Show shimmer loading state during initial load
        if (posts.loadState.refresh is LoadState.Loading && posts.itemCount == 0) {
            items(5) { // Show 5 shimmer placeholders
                FeedItemShimmer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LiftrixSpacing.medium, vertical = LiftrixSpacing.small)
                )
            }
        }
        
        // Handle empty state for home feed
        if (posts.itemCount == 0 && posts.loadState.refresh !is LoadState.Loading) {
            item {
                EmptyFeedState(
                    feedType = selectedTab,
                    onDiscoverPeople = {
                        navController.navigate(LiftrixRoute.UserSearch)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.large)
                )
            }
        }

        // Post items
        items(
            count = posts.itemCount,
            key = { index -> 
                // Use index-based key to guarantee uniqueness even with duplicate posts
                val post = posts[index]
                if (post != null) {
                    "${post.id}_${index}"
                } else {
                    "placeholder_$index"
                }
            }
        ) { index ->
            posts[index]?.let { post ->
                WorkoutPostCard(
                    post = post,
                    isLiked = feedUiState.likedPosts.contains(post.id),
                    isSaved = feedUiState.savedPosts.contains(post.id),
                    onLikeClick = {
                        feedViewModel.handleEvent(FeedEvent.HandlePostInteraction(PostInteraction.Like(post.id)))
                    },
                    onCommentClick = {
                        navController.navigate(LiftrixRoute.PostComments(post.id))
                    },
                    onShareClick = {
                        feedViewModel.handleEvent(FeedEvent.HandlePostInteraction(PostInteraction.Share(post)))
                    },
                    onSaveClick = {
                        feedViewModel.handleEvent(FeedEvent.HandlePostInteraction(PostInteraction.Save(post.id)))
                    },
                    onProfileClick = {
                        navController.navigate(LiftrixRoute.PublicProfile(post.userId))
                    },
                    onWorkoutCopyClick = {
                        feedViewModel.handleEvent(FeedEvent.HandlePostInteraction(PostInteraction.CopyWorkout(post)))
                    },
                    onWorkoutClick = {
                        // Navigate to workout details
                        navController.navigate(LiftrixRoute.WorkoutDetails(post.workoutId))
                    },
                    onEditWorkout = {
                        navController.navigateToEditWorkout(post.workoutId)
                    },
                    isOwnPost = feedUiState.currentUserId == post.userId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LiftrixSpacing.medium)
                )
            }
        }
        
        // Loading state
        when (posts.loadState.append) {
            is LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LiftrixSpacing.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
            }
            is LoadState.Error -> {
                item {
                    ErrorDisplay(
                        error = convertThrowableToLiftrixError((posts.loadState.append as LoadState.Error).error),
                        onRetry = { posts.retry() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LiftrixSpacing.medium)
                    )
                }
            }
            else -> {}
        }

        // Initial loading error
        if (posts.loadState.refresh is LoadState.Error && posts.itemCount == 0) {
            item {
                ErrorDisplay(
                    error = convertThrowableToLiftrixError((posts.loadState.refresh as LoadState.Error).error),
                    onRetry = { posts.refresh() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.medium)
                )
            }
        }
    }
}

/**
 * Enhanced home screen header with modern typography, athletic branding, and sync status
 */
@Composable
private fun EnhancedHomeHeader(
    syncStatusViewModel: SyncStatusViewModel,
    modifier: Modifier = Modifier
) {
    // Collect sync status for display
    val syncStatus by syncStatusViewModel.syncStatus.collectAsState(initial = com.example.liftrix.sync.SyncStatus.Idle)
    
    Column(
        modifier = modifier.padding(vertical = GridSystem.spacing2),
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        // Main header text
        Text(
            text = "Your fitness journey starts here",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Sync status indicator - only show when relevant
        SyncStatusIndicator(
            syncStatus = syncStatus,
            showText = true,
            autoHideSuccess = true,
            contentDescription = "Firebase sync status"
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
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "View all",
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
    onUserClick: ((String) -> Unit)? = null,
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
                onUserClick = onUserClick,
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
                    onUserClick = onUserClick,
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
                .padding(
                    horizontal = GridSystem.spacing2,
                    vertical = 6.dp  // Subtle reduction from 12dp for ~1mm height savings
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Large icon on the left (keeping the bigger size)
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = "No people to discover",
                modifier = Modifier
                    .size(96.dp)
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
 * Converts a generic Throwable to LiftrixError for consistent error handling
 */
private fun convertThrowableToLiftrixError(throwable: Throwable): com.example.liftrix.domain.model.error.LiftrixError {
    return when (throwable) {
        is com.example.liftrix.domain.model.error.LiftrixError -> throwable
        else -> com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
            errorMessage = throwable.message ?: "An unknown error occurred"
        )
    }
}

/**
 * Empty state for feed sections
 */
@Composable
private fun EmptyFeedState(
    feedType: FeedTab,
    onDiscoverPeople: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (feedType) {
                FeedTab.HOME -> "No posts from people you follow"
                FeedTab.DISCOVERY -> "No posts to explore"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = LiftrixColorsV2.onSurface
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        Text(
            text = when (feedType) {
                FeedTab.HOME -> "Follow people to see their workout posts here"
                FeedTab.DISCOVERY -> "Start exploring the community to discover new workouts"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LiftrixColorsV2.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.large))
        
        // Add "Discover People" CTA button
        Button(
            onClick = onDiscoverPeople,
            colors = ButtonDefaults.buttonColors(
                containerColor = LiftrixColorsV2.primary,
                contentColor = LiftrixColorsV2.onPrimary
            )
        ) {
            Text(
                text = "Discover People",
                style = MaterialTheme.typography.labelLarge
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


@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LiftrixTheme {
        // Preview doesn't need actual navigation
        // HomeScreen(
        //     navController = rememberNavController(),
        //     onNavigateToWorkout = {},
        //     onNavigateToFriends = {}
        // )
    }
}
