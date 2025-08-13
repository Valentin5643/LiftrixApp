package com.example.liftrix.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.liftrix.R
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

/**
 * Main social feed screen with tabs for Home and Discovery feeds
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Tab selector
        TabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            containerColor = LiftrixColorsV2.surface,
            contentColor = LiftrixColorsV2.onSurface,
            divider = {
                HorizontalDivider(
                    color = LiftrixColorsV2.outline.copy(alpha = 0.12f)
                )
            }
        ) {
            Tab(
                selected = uiState.selectedTab == FeedTab.HOME,
                onClick = { viewModel.onEvent(FeedEvent.SelectTab(FeedTab.HOME)) },
                text = { 
                    Text(
                        text = stringResource(R.string.feed_tab_home),
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                selectedContentColor = LiftrixColorsV2.primary,
                unselectedContentColor = LiftrixColorsV2.onSurfaceVariant
            )
            Tab(
                selected = uiState.selectedTab == FeedTab.DISCOVERY,
                onClick = { viewModel.onEvent(FeedEvent.SelectTab(FeedTab.DISCOVERY)) },
                text = { 
                    Text(
                        text = stringResource(R.string.feed_tab_discovery),
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                selectedContentColor = LiftrixColorsV2.primary,
                unselectedContentColor = LiftrixColorsV2.onSurfaceVariant
            )
        }
        
        // Feed content with pull-to-refresh
        SwipeRefresh(
            state = rememberSwipeRefreshState(posts.loadState.refresh is LoadState.Loading),
            onRefresh = { posts.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            FeedContent(
                posts = posts,
                uiState = uiState,
                onPostInteraction = { interaction ->
                    viewModel.onEvent(FeedEvent.PostInteraction(interaction))
                },
                navController = navController
            )
        }
    }
}

@Composable
private fun FeedContent(
    posts: LazyPagingItems<WorkoutPost>,
    uiState: FeedUiState,
    onPostInteraction: (PostInteraction) -> Unit,
    navController: NavController
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = LiftrixSpacing.small),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
        // Handle empty state for home feed
        if (posts.itemCount == 0 && posts.loadState.refresh !is LoadState.Loading) {
            item {
                EmptyFeedState(
                    feedType = uiState.selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.large)
                )
            }
        }

        // Post items
        items(
            count = posts.itemCount,
            key = posts.itemKey { it.id }
        ) { index ->
            posts[index]?.let { post ->
                WorkoutPostCard(
                    post = post,
                    isLiked = uiState.likedPosts.contains(post.id),
                    isSaved = uiState.savedPosts.contains(post.id),
                    onLikeClick = { 
                        onPostInteraction(PostInteraction.Like(post.id))
                    },
                    onCommentClick = { 
                        navController.navigate("feed/comments/${post.id}")
                    },
                    onShareClick = { 
                        onPostInteraction(PostInteraction.Share(post))
                    },
                    onSaveClick = { 
                        onPostInteraction(PostInteraction.Save(post.id))
                    },
                    onProfileClick = { 
                        navController.navigate("profile/${post.userId}")
                    },
                    onWorkoutCopyClick = {
                        onPostInteraction(PostInteraction.CopyWorkout(post))
                    },
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
                        error = (posts.loadState.append as LoadState.Error).error,
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
                    error = (posts.loadState.refresh as LoadState.Error).error,
                    onRetry = { posts.refresh() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.medium)
                )
            }
        }
    }
}

@Composable
private fun EmptyFeedState(
    feedType: FeedTab,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (feedType) {
                FeedTab.HOME -> stringResource(R.string.feed_empty_home_title)
                FeedTab.DISCOVERY -> stringResource(R.string.feed_empty_discovery_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = LiftrixColorsV2.onSurface
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        Text(
            text = when (feedType) {
                FeedTab.HOME -> stringResource(R.string.feed_empty_home_description)
                FeedTab.DISCOVERY -> stringResource(R.string.feed_empty_discovery_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LiftrixColorsV2.onSurfaceVariant
        )
    }
}