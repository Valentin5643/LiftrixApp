package com.example.liftrix.ui.workouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
 * Screen displaying all of the user's completed workouts in social feed format.
 * 
 * This screen shows ALL workouts (even those without social posts) converted
 * to WorkoutPost format with default social values (0 likes, etc.).
 * This ensures users can see their complete workout history in a familiar social feed UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserWorkoutsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: UserWorkoutsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val workoutPosts by viewModel.userWorkouts.collectAsState(initial = emptyList())
    
    // Pull-to-refresh state
    val isRefreshing = uiState.isLoading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.handleEvent(UserWorkoutsEvent.RefreshWorkouts) }
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
                if (uiState.isLoading && workoutPosts.isEmpty()) {
                    items(5) {
                        FeedItemShimmer(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Error state
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
                
                // Empty state
                if (workoutPosts.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    item {
                        EmptyWorkoutState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp)
                        )
                    }
                }
                
                // Workout posts (converted from workouts)
                items(
                    items = workoutPosts,
                    key = { post -> post.id }
                ) { post ->
                    WorkoutPostCard(
                        post = post,
                        isLiked = uiState.likedPosts.contains(post.id),
                        isSaved = uiState.savedPosts.contains(post.id),
                        onLikeClick = { viewModel.handleEvent(UserWorkoutsEvent.ToggleLike(post.id)) },
                        onCommentClick = {
                            // For workout-generated posts, navigate to workout details instead
                            if (post.id.startsWith("post_")) {
                                navController.navigate(
                                    LiftrixRoute.WorkoutDetails(workoutId = post.workoutId)
                                )
                            } else {
                                navController.navigate(
                                    LiftrixRoute.PostComments(postId = post.id)
                                )
                            }
                        },
                        onShareClick = { viewModel.handleEvent(UserWorkoutsEvent.ShareWorkout(post.workoutId)) },
                        onSaveClick = { viewModel.handleEvent(UserWorkoutsEvent.ToggleSave(post.id)) },
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
                        onWorkoutClick = {
                            // Navigate to workout details
                            navController.navigate(
                                LiftrixRoute.WorkoutDetails(workoutId = post.workoutId)
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
                
                // Workout count summary
                if (workoutPosts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Total: ${workoutPosts.size} workouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center
                        )
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