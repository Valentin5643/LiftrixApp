package com.example.liftrix.ui.workout.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.ui.workout.history.components.WorkoutHistoryList
import com.example.liftrix.ui.theme.LiftrixTheme
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState

/**
 * Main screen component for My Workouts feature with complete UI.
 * 
 * Features:
 * - Complete workout history view with pagination
 * - Material 3 design with TopAppBar and back navigation
 * - Pull-to-refresh functionality for data updates
 * - Comprehensive state handling (loading, error, empty, content)
 * - MVI pattern integration with MyWorkoutsViewModel
 * - Proper accessibility support and semantic structure
 * - Integration with WorkoutHistoryList component
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param onNavigateToWorkout Callback to navigate to workout details screen
 * @param modifier Modifier for styling the screen
 * @param viewModel MyWorkoutsViewModel for state management (injectable for testing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWorkoutsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWorkout: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MyWorkoutsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    MyWorkoutsScreenContent(
        uiState = uiState,
        onEvent = viewModel::handleEvent,
        onNavigateBack = onNavigateBack,
        onNavigateToWorkout = onNavigateToWorkout,
        modifier = modifier
    )
}

/**
 * Content component for My Workouts screen with complete state management
 * Separated for better testability and reusability
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWorkoutsScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading && uiState.workouts.isNotEmpty(),
        onRefresh = { onEvent(UiEvent.RefreshWorkouts) }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Workouts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Navigate back"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                uiState.shouldShowError -> {
                    ErrorState(
                        errorMessage = uiState.error ?: "Something went wrong",
                        onRetry = { onEvent(UiEvent.LoadWorkouts) },
                        onDismiss = { onEvent(UiEvent.ClearError) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                uiState.shouldShowContent -> {
                    WorkoutHistoryList(
                        workouts = uiState.workouts,
                        onLoadMore = { onEvent(UiEvent.LoadMoreWorkouts) },
                        isLoading = uiState.isLoading,
                        isLoadingMore = uiState.isLoadingMore,
                        hasMoreData = uiState.hasMoreData,
                        onWorkoutClick = { workout ->
                            onNavigateToWorkout(workout.id.value)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                uiState.shouldShowEmptyState -> {
                    // WorkoutHistoryList handles empty state internally
                    WorkoutHistoryList(
                        workouts = emptyList(),
                        onLoadMore = { onEvent(UiEvent.LoadMoreWorkouts) },
                        isLoading = uiState.isLoading,
                        isLoadingMore = uiState.isLoadingMore,
                        hasMoreData = uiState.hasMoreData,
                        onWorkoutClick = { workout ->
                            onNavigateToWorkout(workout.id.value)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            PullRefreshIndicator(
                refreshing = uiState.isLoading && uiState.workouts.isNotEmpty(),
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Error state component for My Workouts screen
 * Provides user-friendly error messaging with retry functionality
 */
@Composable
private fun ErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Unable to Load Workouts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Dismiss error message"
                    }
                ) {
                    Text("Dismiss")
                }
                
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Retry loading workouts"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}

// Preview functions for development and testing
@Preview(showBackground = true)
@Composable
private fun MyWorkoutsScreenContentPreview() {
    LiftrixTheme {
        MyWorkoutsScreenContent(
            uiState = UiState(
                workouts = emptyList(),
                isLoading = false,
                hasMoreData = false
            ),
            onEvent = {},
            onNavigateBack = {},
            onNavigateToWorkout = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MyWorkoutsScreenContentErrorPreview() {
    LiftrixTheme {
        MyWorkoutsScreenContent(
            uiState = UiState(
                workouts = emptyList(),
                error = "Network connection failed. Please check your internet connection and try again."
            ),
            onEvent = {},
            onNavigateBack = {},
            onNavigateToWorkout = {}
        )
    }
} 