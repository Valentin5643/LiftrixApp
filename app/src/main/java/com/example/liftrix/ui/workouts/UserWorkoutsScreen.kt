package com.example.liftrix.ui.workouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState

/**
 * Screen displaying all of the user's completed workouts in a feed-like format
 * with full social engagement metrics (likes, comments, saves).
 * 
 * This screen provides a comprehensive view of the user's workout history
 * with the same rich social features as the main feed, but filtered to show
 * only their own workout posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserWorkoutsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: UserWorkoutsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val posts = viewModel.userWorkouts.collectAsLazyPagingItems()
    
    // Pull-to-refresh state
    val isRefreshing = posts.loadState.refresh is LoadState.Loading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { posts.refresh() }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "My Workouts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.totalWorkouts} completed workouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = LiftrixSpacing.medium,
                    vertical = LiftrixSpacing.small
                ),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                // Loading state
                if (posts.loadState.refresh is LoadState.Loading && posts.itemCount == 0) {
                    items(5) {
                        FeedItemShimmer(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Error state - show UI state error first, then paging error
                uiState.error?.let { error ->
                    item {
                        ErrorDisplay(
                            error = error,
                            onRetry = { viewModel.handleEvent(UserWorkoutsEvent.RefreshWorkouts) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                }
                
                // Show paging load errors if no UI state error
                if (uiState.error == null) {
                    posts.loadState.refresh.let { loadState ->
                        if (loadState is LoadState.Error) {
                            item {
                                ErrorDisplay(
                                    error = com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                                        errorMessage = "Failed to load workouts"
                                    ),
                                    onRetry = { posts.retry() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp)
                                )
                            }
                        }
                    }
                }
                
                // Empty state
                if (posts.itemCount == 0 && posts.loadState.refresh !is LoadState.Loading) {
                    item {
                        EmptyWorkoutState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp)
                        )
                    }
                }
                
                // Workout posts
                items(
                    count = posts.itemCount,
                    key = { index -> 
                        val post = posts[index]
                        post?.id ?: "placeholder_$index"
                    }
                ) { index ->
                    posts[index]?.let { post ->
                        WorkoutPostCard(
                            post = post,
                            isLiked = uiState.likedPosts.contains(post.id),
                            isSaved = uiState.savedPosts.contains(post.id),
                            onLikeClick = { viewModel.toggleLike(post.id) },
                            onCommentClick = {
                                navController.navigate(
                                    LiftrixRoute.PostComments(postId = post.id)
                                )
                            },
                            onShareClick = { viewModel.shareWorkout(post.id) },
                            onSaveClick = { viewModel.toggleSave(post.id) },
                            onProfileClick = {
                                // Navigate to own profile
                                navController.navigate(LiftrixRoute.Profile())
                            },
                            onWorkoutCopyClick = {
                                // Navigate to create workout from this template
                                navController.navigate(
                                    LiftrixRoute.CreateWorkout(folderId = null)
                                )
                            },
                            onEditWorkout = {
                                // Navigate to edit workout
                                navController.navigate(
                                    LiftrixRoute.EditWorkout(workoutId = post.workoutId)
                                )
                            },
                            isOwnPost = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Load more indicator
                posts.loadState.append.let { loadState ->
                    when (loadState) {
                        is LoadState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                TextButton(
                                    onClick = { posts.retry() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Load more")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
            
            // Pull refresh indicator
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun EmptyWorkoutState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Workouts Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Complete your first workout to see it here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}