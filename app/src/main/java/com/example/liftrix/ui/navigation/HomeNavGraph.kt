package com.example.liftrix.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.ui.social.FriendsScreen

/**
 * Navigation graph for the Home tab.
 * 
 * Defines the navigation structure for the Home section of the app, including
 * the main home screen and any nested destinations within the home flow.
 * 
 * Features:
 * - Independent navigation stack for home functionality
 * - Deep linking support for home-related routes
 * - Placeholder implementation ready for HomeScreen integration
 * 
 * @param onNavigateToAuth Callback to navigate to authentication flow
 * @param onNavigateToWorkout Callback to navigate to workout creation
 */
fun NavGraphBuilder.homeGraph(
    onNavigateToAuth: () -> Unit,
    onNavigateToWorkout: () -> Unit = {},
    navController: NavHostController
) {
    navigation(
        startDestination = HomeRoutes.HOME_MAIN,
        route = MainNavigationItem.HOME.route
    ) {
        composable(HomeRoutes.HOME_MAIN) {
            HomeScreen(
                onNavigateToWorkout = { workoutId ->
                    // Navigate to workout details - this will be handled by the workout navigation
                    // For now, we'll navigate to the workout tab
                    onNavigateToWorkout()
                },
                onNavigateToFriends = {
                    navController.navigate(HomeRoutes.FRIENDS)
                }
            )
        }
        
        // Friends screen with deep link support for friend requests
        composable(
            route = HomeRoutes.FRIENDS,
            deepLinks = listOf(
                navDeepLink { 
                    uriPattern = "liftrix://friends/{requestId?}"
                }
            )
        ) {
            FriendsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Future home-related destinations can be added here
        // For example:
        // - Workout details from home
        // - Recent workout history
        // - User profile quick access
    }
}

/**
 * Route definitions for the Home navigation graph.
 */
object HomeRoutes {
    const val HOME_MAIN = "home/main"
    const val FRIENDS = "home/friends"
    const val WORKOUT_DETAIL = "home/workout_detail/{workoutId}"
    const val RECENT_WORKOUTS = "home/recent_workouts"
}

 