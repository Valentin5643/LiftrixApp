package com.example.liftrix.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.liftrix.ui.home.HomeScreen

@Composable
fun HomeRoute(
    onNavigateToWorkout: (String) -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToMyWorkouts: () -> Unit,
    onNavigateToPublicProfile: (String) -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToPostComments: (String) -> Unit,
    onNavigateToWorkoutDetails: (String, String?) -> Unit,
    onNavigateToEditWorkout: (String) -> Unit,
    syncStatusContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeScreen(
        onNavigateToWorkout = onNavigateToWorkout,
        onNavigateToFriends = onNavigateToFriends,
        onNavigateToMyWorkouts = onNavigateToMyWorkouts,
        onNavigateToPublicProfile = onNavigateToPublicProfile,
        onNavigateToUserSearch = onNavigateToUserSearch,
        onNavigateToPostComments = onNavigateToPostComments,
        onNavigateToWorkoutDetails = onNavigateToWorkoutDetails,
        onNavigateToEditWorkout = onNavigateToEditWorkout,
        syncStatusContent = syncStatusContent,
        modifier = modifier
    )
}
