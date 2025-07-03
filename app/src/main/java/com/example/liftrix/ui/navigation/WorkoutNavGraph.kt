package com.example.liftrix.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.User
import com.example.liftrix.service.WorkoutSessionPersistenceService
import com.example.liftrix.ui.workout.WorkoutTemplatesDashboard
import com.example.liftrix.ui.workout.WorkoutTemplatesDashboardViewModel
import com.example.liftrix.ui.workout.active.ActiveWorkoutScreen
import com.example.liftrix.ui.workout.active.ActiveWorkoutViewModel
import com.example.liftrix.ui.workout.templates.WorkoutTemplateSelectionScreen
import com.example.liftrix.ui.workout.create.WorkoutTemplateCreationScreen
import com.example.liftrix.ui.workout.selection.ExerciseSelectionScreen

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
 * - Modern ActiveWorkoutScreen for real-time session tracking
 * 
 * @param user The authenticated user
 * @param onNavigateToAuth Callback to navigate to authentication flow
 * @param navController NavController for nested navigation within workout flow
 * @param onNavigateToExerciseLibrary Callback to navigate to exercise library
 */
fun NavGraphBuilder.workoutGraph(
    user: User,
    onNavigateToAuth: () -> Unit,
    navController: NavHostController,
    onNavigateToExerciseLibrary: () -> Unit
) {
    navigation(
        startDestination = WorkoutDestinations.TEMPLATES_DASHBOARD,
        route = MainNavigationItem.WORKOUT.route
    ) {
        // Template Dashboard - Main Workout Tab (Modern UI)
        composable(WorkoutDestinations.TEMPLATES_DASHBOARD) {
            val viewModel: WorkoutTemplatesDashboardViewModel = hiltViewModel()
            
            WorkoutTemplatesDashboard(
                onStartWorkout = { template ->
                    // Record template usage and navigate to session
                    viewModel.recordTemplateUsage(template)
                    navController.navigate(
                        WorkoutDestinations.createActiveSessionRoute(template.id.value)
                    )
                },
                onCreateTemplate = {
                    navController.navigate(WorkoutDestinations.CREATE_TEMPLATE)
                },
                onEditTemplate = { template ->
                    navController.navigate(
                        WorkoutDestinations.createEditTemplateRoute(template.id.value)
                    )
                },
                onCreateBlankWorkout = {
                    navController.navigate(WorkoutDestinations.BLANK_WORKOUT_SESSION)
                },
                viewModel = viewModel
            )
        }
        
        // Active Workout Session - Real-time workout tracking
        composable(
            route = WorkoutDestinations.ACTIVE_SESSION,
            arguments = listOf(
                navArgument("templateId") { 
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString("templateId")
            val viewModel: ActiveWorkoutViewModel = hiltViewModel()
            
            // Handle selected exercise from navigation result
            val selectedExerciseId = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<String>("selected_exercise_id")
                ?.observeAsState(initial = null)?.value
            val isCustomExercise = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<Boolean>("is_custom_exercise")
                ?.observeAsState(initial = false)?.value ?: false
            
            LaunchedEffect(selectedExerciseId) {
                selectedExerciseId?.let { exerciseId ->
                    viewModel.addExerciseById(exerciseId, isCustomExercise)
                    // Clear the saved state after handling
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_exercise_id")
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("is_custom_exercise")
                }
            }
            
            // Start session from template
            LaunchedEffect(templateId) {
                if (templateId != null) {
                    viewModel.startSessionFromTemplate(templateId)
                }
            }
            
            // Start background persistence
            LaunchedEffect(Unit) {
                WorkoutSessionPersistenceService.startPersistence(navController.context)
            }
            
            ActiveWorkoutScreen(
                onNavigateBack = {
                    // Handle back navigation with confirmation if workout in progress
                    if (viewModel.hasUnsavedChanges()) {
                        // Show confirmation dialog
                        viewModel.showExitConfirmation()
                    } else {
                        // Stop background persistence and navigate back
                        WorkoutSessionPersistenceService.stopPersistence(navController.context)
                        navController.navigateUp()
                    }
                },
                onAddExercise = {
                    viewModel.sessionId?.let { sessionId ->
                        navController.navigate(
                            WorkoutDestinations.createAddExerciseRoute(sessionId)
                        )
                    }
                },
                navController = navController,
                isFromTemplate = templateId != null
            )
        }
        
        // Blank Workout Session - Start without template
        composable(WorkoutDestinations.BLANK_WORKOUT_SESSION) {
            val viewModel: ActiveWorkoutViewModel = hiltViewModel()
            
            // Handle selected exercise from navigation result
            val selectedExerciseId = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<String>("selected_exercise_id")
                ?.observeAsState(initial = null)?.value
            val isCustomExercise = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<Boolean>("is_custom_exercise")
                ?.observeAsState(initial = false)?.value ?: false
            
            LaunchedEffect(selectedExerciseId) {
                selectedExerciseId?.let { exerciseId ->
                    viewModel.addExerciseById(exerciseId, isCustomExercise)
                    // Clear the saved state after handling
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_exercise_id")
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("is_custom_exercise")
                }
            }
            
            // Start blank session
            LaunchedEffect(Unit) {
                viewModel.startBlankSession()
                WorkoutSessionPersistenceService.startPersistence(navController.context)
            }
            
            ActiveWorkoutScreen(
                onNavigateBack = {
                    if (viewModel.hasUnsavedChanges()) {
                        viewModel.showExitConfirmation()
                    } else {
                        // Stop background persistence and navigate back
                        WorkoutSessionPersistenceService.stopPersistence(navController.context)
                        navController.navigateUp()
                    }
                },
                onAddExercise = {
                    viewModel.sessionId?.let { sessionId ->
                        navController.navigate(
                            WorkoutDestinations.createAddExerciseRoute(sessionId)
                        )
                    }
                },
                navController = navController,
                isFromTemplate = false
            )
        }
        
        // Resume Active Session - For recovery scenarios
        composable(
            route = WorkoutDestinations.RESUME_SESSION,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
                ?: throw IllegalArgumentException("Session ID required")
            
            val viewModel: ActiveWorkoutViewModel = hiltViewModel()
            
            // Handle selected exercise from navigation result
            val selectedExerciseId = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<String>("selected_exercise_id")
                ?.observeAsState(initial = null)?.value
            val isCustomExercise = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<Boolean>("is_custom_exercise")
                ?.observeAsState(initial = false)?.value ?: false
            
            LaunchedEffect(selectedExerciseId) {
                selectedExerciseId?.let { exerciseId ->
                    viewModel.addExerciseById(exerciseId, isCustomExercise)
                    // Clear the saved state after handling
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_exercise_id")
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("is_custom_exercise")
                }
            }
            
            // Resume existing session
            LaunchedEffect(sessionId) {
                viewModel.resumeSession(sessionId)
                WorkoutSessionPersistenceService.startPersistence(navController.context)
            }
            
            ActiveWorkoutScreen(
                onNavigateBack = {
                    if (viewModel.hasUnsavedChanges()) {
                        viewModel.showExitConfirmation()
                    } else {
                        // Stop background persistence and navigate back
                        WorkoutSessionPersistenceService.stopPersistence(navController.context)
                        navController.navigateUp()
                    }
                },
                onAddExercise = {
                    navController.navigate(
                        WorkoutDestinations.createAddExerciseRoute(sessionId)
                    )
                },
                navController = navController,
                isFromTemplate = false
            )
        }
        
        // Add Exercise to Session
        composable(
            route = WorkoutDestinations.ADD_EXERCISE_TO_SESSION,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
                ?: throw IllegalArgumentException("Session ID required")
            
            ExerciseSelectionScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onExerciseSelected = { exercise ->
                    // Navigate back with selected exercise ID
                    // The exercise will be resolved by the calling screen
                    // Note: This comes from ExerciseSelectionScreen which already converts SearchableExercise to ExerciseLibrary
                    // Custom exercises are converted to ExerciseLibrary format in the screen
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_exercise_id", exercise.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set("is_custom_exercise", exercise.id.startsWith("custom_") || exercise.movementPattern == "Custom Exercise")
                    navController.navigateUp()
                },
                onCreateCustomExercise = {
                    // TODO: Navigate to custom exercise creation
                    // For now, this is a placeholder
                }
            )
        }
        
        // Create Template
        composable(WorkoutDestinations.CREATE_TEMPLATE) { backStackEntry ->
            // Handle selected exercise from navigation result
            val selectedExerciseId = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<String>("selected_exercise_id")
                ?.observeAsState(initial = null)?.value
            val isCustomExercise = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getLiveData<Boolean>("is_custom_exercise")
                ?.observeAsState(initial = false)?.value ?: false
            
            LaunchedEffect(selectedExerciseId) {
                selectedExerciseId?.let { exerciseId ->
                    // TODO: Handle exercise selection for template creation
                    // Clear the saved state after handling
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_exercise_id")
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("is_custom_exercise")
                }
            }
            
            WorkoutTemplateCreationScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onNavigateToExerciseSelection = {
                    navController.navigate(WorkoutDestinations.EXERCISE_SELECTION_FOR_TEMPLATE)
                },
                selectedExerciseId = selectedExerciseId,
                isCustomExercise = isCustomExercise
            )
        }
        
        // Exercise Selection for Template Creation
        composable(WorkoutDestinations.EXERCISE_SELECTION_FOR_TEMPLATE) {
            ExerciseSelectionScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onExerciseSelected = { exercise ->
                    // Pass selected exercise ID back to template creation
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_exercise_id", exercise.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set("is_custom_exercise", exercise.id.startsWith("custom_") || exercise.movementPattern == "Custom Exercise")
                    navController.navigateUp()
                },
                onCreateCustomExercise = {
                    // TODO: Navigate to custom exercise creation
                    // For now, this is a placeholder
                }
            )
        }
        
        // Edit Template
        composable(
            route = WorkoutDestinations.EDIT_TEMPLATE,
            arguments = listOf(
                navArgument("templateId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString("templateId")
                ?: throw IllegalArgumentException("Template ID required")
            
            // TODO: Implement template editing screen
            // For now, navigate back
            LaunchedEffect(Unit) {
                navController.navigateUp()
            }
                 }
    }
}

/**
 * Destinations for workout flow navigation
 */
object WorkoutDestinations {
    const val WORKOUT_FLOW = "workout_flow"
    const val TEMPLATES_DASHBOARD = "templates_dashboard"
    const val ACTIVE_SESSION = "active_session?templateId={templateId}"
    const val BLANK_WORKOUT_SESSION = "blank_workout_session"
    const val RESUME_SESSION = "resume_session/{sessionId}"
    const val ADD_EXERCISE_TO_SESSION = "add_exercise/{sessionId}"
    const val CREATE_TEMPLATE = "create_template"
    const val EXERCISE_SELECTION_FOR_TEMPLATE = "exercise_selection_for_template"
    const val EDIT_TEMPLATE = "edit_template/{templateId}"
    
    fun createActiveSessionRoute(templateId: String): String {
        return "active_session?templateId=$templateId"
    }
    
    fun createResumeSessionRoute(sessionId: String): String {
        return "resume_session/$sessionId"
    }
    
    fun createAddExerciseRoute(sessionId: String): String {
        return "add_exercise/$sessionId"
    }
    
    fun createEditTemplateRoute(templateId: String): String {
        return "edit_template/$templateId"
    }
}

/**
 * Home destinations for cross-navigation
 */
object HomeDestinations {
    const val HOME = "home"
}

