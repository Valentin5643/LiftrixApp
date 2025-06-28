package com.example.liftrix.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    onNavigateToWorkout: () -> Unit = {}
) {
    navigation(
        startDestination = HomeRoutes.HOME_MAIN,
        route = MainNavigationItem.HOME.route
    ) {
        composable(HomeRoutes.HOME_MAIN) {
            // Placeholder for HomeScreen - will be implemented in HOME-001
            HomeScreenPlaceholder(
                onNavigateToWorkout = onNavigateToWorkout
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
    const val WORKOUT_DETAIL = "home/workout_detail/{workoutId}"
    const val RECENT_WORKOUTS = "home/recent_workouts"
}

/**
 * Temporary placeholder for the HomeScreen.
 * This will be replaced by the actual HomeScreen implementation in task HOME-001.
 */
@Composable
private fun HomeScreenPlaceholder(
    onNavigateToWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Recent workouts and dashboard coming soon",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 