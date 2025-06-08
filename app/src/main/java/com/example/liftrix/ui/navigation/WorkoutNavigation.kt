package com.example.liftrix.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.ui.workout.creation.CustomExerciseCreationScreen
import com.example.liftrix.ui.workout.creation.CustomExerciseCreationViewModel
import com.example.liftrix.ui.workout.creation.ExerciseSelectionScreen
import com.example.liftrix.ui.workout.creation.ExerciseSelectionViewModel
import timber.log.Timber

/**
 * Navigation routes for workout creation flow.
 */
object WorkoutRoutes {
    const val EXERCISE_SELECTION = "workout/exercise_selection"
    const val CUSTOM_EXERCISE_CREATION = "workout/custom_exercise_creation"
    const val WORKOUT_CREATION = "workout/creation"
    const val WORKOUT_EXECUTION = "workout/execution"
}

/**
 * Main workout navigation composable.
 * Manages the workout creation and execution flow.
 */
@Composable
fun WorkoutNavigation(
    onNavigateBack: () -> Unit,
    onWorkoutComplete: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = WorkoutRoutes.EXERCISE_SELECTION,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        workoutGraph(
            navController = navController,
            onNavigateBack = onNavigateBack,
            onWorkoutComplete = onWorkoutComplete
        )
    }
}

/**
 * Defines the workout navigation graph.
 */
fun NavGraphBuilder.workoutGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onWorkoutComplete: () -> Unit
) {
    composable(WorkoutRoutes.EXERCISE_SELECTION) {
        val viewModel: ExerciseSelectionViewModel = hiltViewModel()
        
        ExerciseSelectionScreen(
            onNavigateBack = onNavigateBack,
            onExerciseSelected = { exercise: SearchableExercise ->
                // TODO: Navigate to workout creation with selected exercise
                Timber.d("Exercise selected: ${exercise.name}")
            },
            onNavigateToCustomExerciseCreation = {
                navController.navigateToCustomExerciseCreation()
            },
            viewModel = viewModel
        )
        
        // Handle custom exercise creation result
        LaunchedEffect(navController) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getStateFlow<CustomExercise?>("custom_exercise_result", null)
                ?.collect { customExercise: CustomExercise? ->
                    if (customExercise != null) {
                        viewModel.refreshExerciseList()
                        // Clear the result to prevent re-triggering
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("custom_exercise_result", null)
                        Timber.d("Custom exercise created: ${customExercise.name}")
                    }
                }
        }
    }
    
    composable(WorkoutRoutes.CUSTOM_EXERCISE_CREATION) {
        val viewModel: CustomExerciseCreationViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        
        CustomExerciseCreationScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onExerciseCreated = {
                navController.popBackStack()
            },
            viewModel = viewModel
        )
        
        // Handle successful creation
        LaunchedEffect(uiState.isCreationSuccessful) {
            if (uiState.isCreationSuccessful && uiState.createdExercise != null) {
                // Pass the created exercise back to the previous screen
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("custom_exercise_result", uiState.createdExercise)
                navController.popBackStack()
            }
        }
    }
}

/**
 * Extension function to navigate to custom exercise creation
 */
fun NavHostController.navigateToCustomExerciseCreation() {
    navigate(WorkoutRoutes.CUSTOM_EXERCISE_CREATION) {
        launchSingleTop = true
    }
}

/**
 * Extension function to navigate to exercise selection
 */
fun NavHostController.navigateToExerciseSelection() {
    navigate(WorkoutRoutes.EXERCISE_SELECTION) {
        launchSingleTop = true
    }
}

/**
 * Extension function to navigate back with result
 */
fun NavHostController.navigateBackWithResult(key: String, result: Any) {
    previousBackStackEntry?.savedStateHandle?.set(key, result)
    popBackStack()
} 