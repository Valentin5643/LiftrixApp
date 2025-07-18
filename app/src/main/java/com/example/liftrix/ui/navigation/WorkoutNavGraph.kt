package com.example.liftrix.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.User
import com.example.liftrix.ui.workout.WorkoutScreen
import com.example.liftrix.ui.workout.WorkoutTemplateScreen
import com.example.liftrix.ui.workout.active.UnifiedActiveWorkoutScreen
import com.example.liftrix.ui.workout.active.UnifiedActiveWorkoutViewModel
import com.example.liftrix.ui.workout.selection.ExerciseSelectionScreen
import timber.log.Timber

/**
 * DEPRECATED: Legacy Workout Navigation Graph
 * 
 * This file is maintained for backward compatibility during the migration to
 * type-safe navigation with LiftrixRoute sealed classes. All navigation is now
 * handled centrally in UnifiedNavigationContainer.kt.
 * 
 * @deprecated Use UnifiedNavigationContainer with LiftrixRoute sealed classes instead
 * 
 * Migration Notes:
 * - WorkoutRoutes → LiftrixRoute sealed class variants
 * - String-based navigation replaced with type-safe navigation
 * - All workout flows now handled in UnifiedNavigationContainer
 * - Exercise selection, active workout, template creation all moved to unified system
 * 
 * This file can be removed once all consumers are migrated to the unified navigation system.
 * 
 * @param user The authenticated user (parameter maintained for compatibility)
 * @param onNavigateToAuth Callback to navigate to authentication flow
 * @param navController NavController for nested navigation within workout flow
 * @param onNavigateToExerciseLibrary Callback to navigate to exercise library
 */
@Deprecated(
    message = "This legacy workout navigation graph is deprecated and will be removed. Use UnifiedNavigationContainer with type-safe LiftrixRoute sealed classes instead.",
    replaceWith = ReplaceWith("UnifiedNavigationContainer", "com.example.liftrix.ui.navigation.UnifiedNavigationContainer"),
    level = DeprecationLevel.WARNING
)
fun NavGraphBuilder.workoutGraph(
    user: User,
    onNavigateToAuth: () -> Unit,
    navController: NavHostController,
    onNavigateToExerciseLibrary: () -> Unit
) {
    navigation(
        startDestination = WorkoutDestinations.WORKOUT_MAIN,
        route = MainNavigationItem.WORKOUT.route
    ) {
        // Main Workout Screen - Entry point
        composable(WorkoutDestinations.WORKOUT_MAIN) {
            WorkoutScreen(
                onNavigateToActiveWorkout = { templateId ->
                    timber.log.Timber.d("🔥 WORKOUT-NAV-DEBUG: onNavigateToActiveWorkout called with templateId: $templateId")
                    if (templateId != null && templateId.isNotBlank()) {
                        val route = WorkoutDestinations.createActiveSessionRoute(templateId)
                        timber.log.Timber.d("🔥 WORKOUT-NAV-DEBUG: Navigating to template route: $route")
                        navController.navigate(route)
                    } else {
                        timber.log.Timber.d("🔥 WORKOUT-NAV-DEBUG: Navigating to blank workout session")
                        navController.navigate(WorkoutDestinations.BLANK_WORKOUT_SESSION)
                    }
                },
                onNavigateToTemplateCreation = {
                    navController.navigate(WorkoutDestinations.CREATE_TEMPLATE)
                }
            )
        }
        
        // Active Workout Session - Real-time workout tracking
        composable(
            route = WorkoutDestinations.ACTIVE_SESSION,
            arguments = listOf(
                navArgument("templateId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString("templateId")
            timber.log.Timber.d("🔥 NAV-DEBUG: ACTIVE_SESSION route - templateId: $templateId")
            
            // 🔥 FIX: If templateId is empty string, treat as null
            val actualTemplateId = templateId?.takeIf { it.isNotBlank() }
            timber.log.Timber.d("🔥 NAV-DEBUG: actualTemplateId after cleanup: $actualTemplateId")
            
            UnifiedActiveWorkoutScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onAddExercise = {
                    navController.navigate(WorkoutDestinations.ADD_EXERCISE_TO_SESSION)
                },
                onNavigateToExercise = { exerciseId ->
                    // Navigate to exercise detail if needed
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                savedStateHandle = backStackEntry.savedStateHandle,
                isBlankWorkout = actualTemplateId == null, // 🔥 FIX: If no valid templateId, treat as blank workout
                templateId = actualTemplateId // Pass cleaned templateId
            )
        }
        
        // Blank Workout Session - Start without template
        composable(WorkoutDestinations.BLANK_WORKOUT_SESSION) { backStackEntry ->
            UnifiedActiveWorkoutScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onAddExercise = {
                    navController.navigate(WorkoutDestinations.ADD_EXERCISE_TO_SESSION)
                },
                onNavigateToExercise = { exerciseId ->
                    // Navigate to exercise detail if needed
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                savedStateHandle = backStackEntry.savedStateHandle,
                isBlankWorkout = true
            )
        }
        
        // Add Exercise to Session
        composable(WorkoutDestinations.ADD_EXERCISE_TO_SESSION) {
            timber.log.Timber.d("🔥 ROUTE-DEBUG: Navigated to ADD_EXERCISE_TO_SESSION route")
            
            ExerciseSelectionScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onExerciseSelected = { exercise ->
                    timber.log.Timber.d("🔥 NAVIGATION-DEBUG: onExerciseSelected callback invoked")
                    timber.log.Timber.d("🔥 NAVIGATION-DEBUG: Received exercise - ID: ${exercise.id}, name: '${exercise.name}'")
                    timber.log.Timber.d("🔥 NAVIGATION-DEBUG: Exercise type: ${exercise::class.simpleName}")
                    
                    try {
                        // Navigate back with selected exercise
                        timber.log.Timber.i("🔥 NAVIGATION-SEND: Sending exercise selection - ID: ${exercise.id}, name: '${exercise.name}'")
                        
                        val previousBackStack = navController.previousBackStackEntry
                        timber.log.Timber.d("🔥 NAVIGATION-DEBUG: Previous back stack entry: ${previousBackStack?.destination?.route}")
                        
                        if (previousBackStack != null) {
                            previousBackStack.savedStateHandle.set("selected_exercise", exercise)
                            timber.log.Timber.d("🔥 NAVIGATION-DEBUG: Successfully set selected_exercise in savedStateHandle")
                        } else {
                            timber.log.Timber.e("🔥 NAVIGATION-DEBUG: Previous back stack entry is null!")
                        }
                        
                        timber.log.Timber.i("🔥 NAVIGATION-COMPLETE: NavigateUp called, returning to ActiveWorkoutScreen")
                        navController.navigateUp()
                        
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "🔥 NAVIGATION-DEBUG: Error in navigation callback")
                    }
                },
                onCreateCustomExercise = {
                    // TODO: Navigate to custom exercise creation
                }
            )
        }
        
        // Create Template
        composable(WorkoutDestinations.CREATE_TEMPLATE) { backStackEntry ->
            WorkoutTemplateScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onAddExercise = {
                    navController.navigate(WorkoutDestinations.EXERCISE_SELECTION_FOR_TEMPLATE)
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        
        // Exercise Selection for Template Creation
        composable(WorkoutDestinations.EXERCISE_SELECTION_FOR_TEMPLATE) {
            timber.log.Timber.d("🔥 ROUTE-DEBUG: Navigated to EXERCISE_SELECTION_FOR_TEMPLATE route")
            
            ExerciseSelectionScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onExerciseSelected = { exercise ->
                    timber.log.Timber.d("🔥 TEMPLATE-NAV-DEBUG: Template exercise selection callback invoked")
                    timber.log.Timber.d("🔥 TEMPLATE-NAV-DEBUG: Received exercise - ID: ${exercise.id}, name: '${exercise.name}'")
                    
                    try {
                        // Pass selected exercise back to template creation
                        val previousBackStack = navController.previousBackStackEntry
                        timber.log.Timber.d("🔥 TEMPLATE-NAV-DEBUG: Previous back stack entry: ${previousBackStack?.destination?.route}")
                        
                        if (previousBackStack != null) {
                            previousBackStack.savedStateHandle.set("selected_exercise", exercise)
                            timber.log.Timber.d("🔥 TEMPLATE-NAV-DEBUG: Successfully set selected_exercise in savedStateHandle")
                        } else {
                            timber.log.Timber.e("🔥 TEMPLATE-NAV-DEBUG: Previous back stack entry is null!")
                        }
                        
                        navController.navigateUp()
                        timber.log.Timber.d("🔥 TEMPLATE-NAV-DEBUG: NavigateUp completed")
                        
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "🔥 TEMPLATE-NAV-DEBUG: Error in template navigation callback")
                    }
                },
                onCreateCustomExercise = {
                    // TODO: Navigate to custom exercise creation
                }
            )
        }
    }
}

/**
 * Destinations for workout flow navigation
 */
object WorkoutDestinations {
    const val WORKOUT_MAIN = "workout_main"
    const val ACTIVE_SESSION = "active_session?templateId={templateId}"
    const val BLANK_WORKOUT_SESSION = "blank_workout_session"
    const val ADD_EXERCISE_TO_SESSION = "add_exercise_to_session"
    const val CREATE_TEMPLATE = "create_template"
    const val EXERCISE_SELECTION_FOR_TEMPLATE = "exercise_selection_for_template"
    
    fun createActiveSessionRoute(templateId: String): String {
        return "active_session?templateId=$templateId"
    }
}

/**
 * Home destinations for cross-navigation
 */
object HomeDestinations {
    const val HOME = "home"
}

