package com.example.liftrix.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.ui.navigation.LiftrixRoute

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

fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    composable<LiftrixRoute.Home> {
        HomeRoute(
            onNavigateToWorkout = {
                navController.navigate(LiftrixRoute.Workout)
            },
            onNavigateToFriends = {
                navController.navigate(LiftrixRoute.Friends)
            },
            onNavigateToMyWorkouts = {
                navController.navigate(LiftrixRoute.UserWorkouts)
            },
            onNavigateToPublicProfile = { userId ->
                navController.navigate(LiftrixRoute.PublicProfile(userId))
            },
            onNavigateToUserSearch = {
                navController.navigate(LiftrixRoute.UserSearch)
            },
            onNavigateToPostComments = { postId ->
                navController.navigate(LiftrixRoute.PostComments(postId))
            },
            onNavigateToWorkoutDetails = { workoutId, ownerId ->
                navController.navigate(LiftrixRoute.WorkoutDetails(workoutId, ownerId))
            },
            onNavigateToEditWorkout = { workoutId ->
                navController.navigate(LiftrixRoute.EditWorkout(workoutId))
            },
            syncStatusContent = {}
        )
    }
}
