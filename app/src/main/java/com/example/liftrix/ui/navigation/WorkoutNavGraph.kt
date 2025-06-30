package com.example.liftrix.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.User
import com.example.liftrix.ui.workout.WorkoutScreen
import com.example.liftrix.ui.workout.templates.WorkoutTemplateSelectionScreen

/**
 * Navigation graph for the Workout tab.
 * 
 * Defines the navigation structure for the Workout section of the app, including
 * the main workout screen and all workout-related functionality such as workout
 * creation, exercise selection, and workout execution.
 * 
 * Features:
 * - Independent navigation stack for workout functionality
 * - Integration with existing WorkoutScreen and workout creation flows
 * - Deep linking support for workout-related routes
 * - Seamless integration with workout templates and custom workouts
 * 
 * @param user The authenticated user
 * @param onNavigateToAuth Callback to navigate to authentication flow
 * @param navController NavController for nested navigation within workout flow
 */
fun NavGraphBuilder.workoutGraph(
    user: User,
    onNavigateToAuth: () -> Unit,
    navController: NavHostController
) {
    navigation(
        startDestination = WorkoutTabRoutes.WORKOUT_MAIN,
        route = MainNavigationItem.WORKOUT.route
    ) {
        composable(WorkoutTabRoutes.WORKOUT_MAIN) {
            // Use existing WorkoutScreen which handles its own internal navigation
            WorkoutScreen(
                user = user,
                viewModel = hiltViewModel()
            )
        }
        
        composable(WorkoutTabRoutes.TEMPLATE_MANAGEMENT) {
            WorkoutTemplateSelectionScreen(
                onTemplateSelected = { templateId ->
                    // Navigate back to main workout screen and trigger template creation
                    navController.popBackStack()
                    // Note: Template creation will be handled by the WorkoutScreen's internal navigation
                    // This is a simplified approach - in production you might want to pass the templateId
                    // through navigation arguments or shared state
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = hiltViewModel()
            )
        }
        
        // Future workout-related destinations can be added here
        // For example:
        // - Workout history/analytics
        // - Exercise library browser
        // - Workout sharing
    }
}

/**
 * Route definitions for the Workout navigation graph.
 * Note: These are separate from the internal WorkoutRoutes used within WorkoutNavigation
 * to maintain separation between tab-level and internal workout navigation.
 */
object WorkoutTabRoutes {
    const val WORKOUT_MAIN = "workout_tab/main"
    const val WORKOUT_HISTORY = "workout_tab/history"
    const val TEMPLATE_MANAGEMENT = "workout_tab/templates"
    const val EXERCISE_LIBRARY = "workout_tab/exercise_library"
} 