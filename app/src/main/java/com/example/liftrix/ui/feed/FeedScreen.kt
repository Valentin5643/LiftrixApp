package com.example.liftrix.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.navigation.navigateToEditWorkout
import com.example.liftrix.R
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState

/**
 * Main social feed screen with tabs for Following and Explore feeds
 * Following shows posts from people you follow, Explore shows all public posts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    feedViewModel: FeedViewModel = hiltViewModel()
) {
    val feedUiState by feedViewModel.uiState.collectAsState()
    val posts = feedViewModel.posts.collectAsLazyPagingItems()
    val context = LocalContext.current
    
    // Create a local UI state for feed-specific functionality
    var selectedTab by remember { mutableStateOf(FeedTab.HOME) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Tab selector
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = LiftrixColorsV2.surface,
            contentColor = LiftrixColorsV2.onSurface,
            divider = {
                HorizontalDivider(
                    color = LiftrixColorsV2.outline.copy(alpha = 0.12f)
                )
            }
        ) {
            Tab(
                selected = selectedTab == FeedTab.HOME,
                onClick = { 
                    selectedTab = FeedTab.HOME
                    feedViewModel.onEvent(FeedEvent.SelectTab(FeedTab.HOME))
                },
                text = { 
                    Text(
                        text = "Following",
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                selectedContentColor = LiftrixColorsV2.primary,
                unselectedContentColor = LiftrixColorsV2.onSurfaceVariant
            )
            Tab(
                selected = selectedTab == FeedTab.DISCOVERY,
                onClick = { 
                    selectedTab = FeedTab.DISCOVERY
                    feedViewModel.onEvent(FeedEvent.SelectTab(FeedTab.DISCOVERY))
                },
                text = { 
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                selectedContentColor = LiftrixColorsV2.primary,
                unselectedContentColor = LiftrixColorsV2.onSurfaceVariant
            )
        }
        
        // Feed content with pull-to-refresh
        val pullRefreshState = rememberPullRefreshState(
            refreshing = posts.loadState.refresh is LoadState.Loading,
            onRefresh = { posts.refresh() }
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            FeedContent(
                posts = posts,
                selectedTab = selectedTab,
                likedPosts = feedUiState.likedPosts,
                savedPosts = feedUiState.savedPosts,
                currentUserId = feedUiState.currentUserId,
                urlResolver = feedViewModel.urlResolver,
                onPostInteraction = { interaction ->
                    // Map feed interactions to social events
                    feedViewModel.onEvent(FeedEvent.HandlePostInteraction(interaction))
                },
                navController = navController
            )
            
            PullRefreshIndicator(
                refreshing = posts.loadState.refresh is LoadState.Loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun FeedContent(
    posts: LazyPagingItems<WorkoutPost>,
    selectedTab: FeedTab,
    likedPosts: Set<String>,
    savedPosts: Set<String>,
    currentUserId: String?,
    onPostInteraction: (PostInteraction) -> Unit,
    navController: NavController,
    urlResolver: FirebaseStorageUrlResolver
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = LiftrixSpacing.small),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
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
                // This prevents crashes when the same post ID appears multiple times
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
                    isLiked = post.isLikedByViewer || likedPosts.contains(post.id),
                    isSaved = post.isSavedByViewer || savedPosts.contains(post.id),
                    onLikeClick = { 
                        onPostInteraction(PostInteraction.Like(post.id))
                    },
                    onCommentClick = { 
                        navController.navigate(LiftrixRoute.PostComments(post.id))
                    },
                    onShareClick = { 
                        onPostInteraction(PostInteraction.Share(post))
                    },
                    onSaveClick = { 
                        onPostInteraction(PostInteraction.Save(post.id))
                    },
                    onProfileClick = { 
                        navController.navigate(LiftrixRoute.PublicProfile(post.userId))
                    },
                    onWorkoutCopyClick = {
                        onPostInteraction(PostInteraction.CopyWorkout(post))
                    },
                    onEditWorkout = {
                        navController.navigateToEditWorkout(post.workoutId)
                    },
                    isOwnPost = currentUserId == post.userId,
                    urlResolver = urlResolver,
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
 * Converts a generic Throwable to LiftrixError for consistent error handling
 */
private fun convertThrowableToLiftrixError(throwable: Throwable): LiftrixError {
    return when (throwable) {
        is LiftrixError -> throwable
        else -> LiftrixError.NetworkError(
            errorMessage = throwable.message ?: "An unknown error occurred"
        )
    }
}

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