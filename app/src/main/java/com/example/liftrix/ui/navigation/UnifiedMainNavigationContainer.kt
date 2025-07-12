package com.example.liftrix.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.liftrix.ui.common.LiveSessionBar
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.ui.workout.WorkoutScreen
import com.example.liftrix.ui.progress.ProgressDashboardScreen
import com.example.liftrix.ui.coach.CoachScreen
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 🔥 NEW: Unified main navigation container with persistent live session bar
 * 
 * This container replaces the complex MainNavigationContainer with a simplified
 * version that shows the LiveSessionBar whenever there's an active session.
 * The session bar persists across all screens and provides consistent access
 * to the current workout.
 * 
 * Key features:
 * - Persistent live session bar across all screens
 * - Single source of truth for session state
 * - Clean integration with navigation
 * - Proper session management
 */
@Composable
fun UnifiedMainNavigationContainer(
    navController: NavHostController = rememberNavController(),
    viewModel: UnifiedMainNavigationViewModel = hiltViewModel()
) {
    val currentSession by viewModel.currentSession.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentRoute = currentRoute
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 🔥 FIXED: Complete navigation with unified session-driven architecture
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("home") {
                    HomeScreen(
                        onNavigateToWorkout = {
                            navController.navigate("workout")
                        },
                        onNavigateToFriends = {
                            navController.navigate("friends")
                        }
                    )
                }
                composable("workout") {
                    WorkoutScreen(
                        onNavigateToActiveWorkout = { templateId ->
                            if (templateId != null) {
                                navController.navigate("unified_active_workout?templateId=$templateId")
                            } else {
                                navController.navigate("unified_active_workout?isBlankWorkout=true")
                            }
                        },
                        onNavigateToTemplateCreation = {
                            navController.navigate("template_creation")
                        }
                    )
                }
                composable("progress") {
                    ProgressDashboardScreen()
                }
                composable("coach") {
                    CoachScreen()
                }
                composable("friends") {
                    com.example.liftrix.ui.social.FriendsScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        }
                    )
                }
                
                // 🔥 NEW: Unified active workout screen (session-driven)
                composable(
                    route = "unified_active_workout?templateId={templateId}&isBlankWorkout={isBlankWorkout}",
                    arguments = listOf(
                        navArgument("templateId") { 
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("isBlankWorkout") { 
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val templateId = backStackEntry.arguments?.getString("templateId")
                    val isBlankWorkout = backStackEntry.arguments?.getBoolean("isBlankWorkout") ?: false
                    
                    timber.log.Timber.d("🔥 NAV-DEBUG: unified_active_workout route - templateId: $templateId, isBlankWorkout: $isBlankWorkout")
                    
                    // 🔥 FIX: Clean up templateId and determine proper isBlankWorkout state
                    val cleanTemplateId = templateId?.takeIf { it.isNotBlank() && it != "null" }
                    val actualIsBlankWorkout = cleanTemplateId == null || isBlankWorkout
                    
                    timber.log.Timber.d("🔥 NAV-DEBUG: After cleanup - cleanTemplateId: $cleanTemplateId, actualIsBlankWorkout: $actualIsBlankWorkout")
                    
                    com.example.liftrix.ui.workout.active.UnifiedActiveWorkoutScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onAddExercise = {
                            navController.navigate("exercise_selection")
                        },
                        onNavigateToExercise = { exerciseId: String ->
                            // Navigate to exercise detail if needed
                            // For now, this is a placeholder - exercise details can be handled
                            // via modal or separate screen as per app requirements
                        },
                        savedStateHandle = backStackEntry.savedStateHandle,
                        isBlankWorkout = actualIsBlankWorkout,
                        templateId = cleanTemplateId
                    )
                }
                
                // 🔥 NEW: Template creation screen
                composable("template_creation") { backStackEntry ->
                    com.example.liftrix.ui.workout.create.WorkoutTemplateCreationScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToExerciseSelection = {
                            navController.navigate("exercise_selection_for_template")
                        },
                        savedStateHandle = backStackEntry.savedStateHandle
                    )
                }
                
                // 🔥 NEW: Exercise selection for active workout
                composable("exercise_selection") {
                    com.example.liftrix.ui.workout.selection.ExerciseSelectionScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onExerciseSelected = { exerciseLibrary ->
                            // Add exercise to current session via session manager
                            viewModel.addExerciseToCurrentSession(exerciseLibrary)
                            navController.navigateUp()
                        },
                        onCreateCustomExercise = {
                            // TODO: Navigate to custom exercise creation when implemented
                        }
                    )
                }
                
                // 🔥 NEW: Exercise selection for template creation
                composable("exercise_selection_for_template") {
                    com.example.liftrix.ui.workout.selection.ExerciseSelectionScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onExerciseSelected = { exerciseLibrary ->
                            // Pass selected exercise back to template creation
                            navController.previousBackStackEntry?.savedStateHandle?.set("selected_exercise", exerciseLibrary)
                            navController.navigateUp()
                        },
                        onCreateCustomExercise = {
                            // TODO: Navigate to custom exercise creation when implemented
                        }
                    )
                }
            }
            
            // 🔥 KEY FEATURE: Persistent live session bar
            // Only show if session exists and not already on active workout screen
            if (currentSession?.isLive() == true &&
                currentRoute?.startsWith("unified_active_workout")?.let { !it == true } == true &&
                currentRoute?.startsWith("active_session")?.let { !it == true } == true
            ) {
                
                LiveSessionBar(
                    session = currentSession,
                    onBarClick = {
                        // 🔥 FIX: Navigate to active workout screen - continue existing session
                        // Since session already exists, we just need to show the screen (no new session creation)
                        val sessionTemplateId = currentSession!!.templateId ?: "null"
                        navController.navigate("unified_active_workout?templateId=$sessionTemplateId&isBlankWorkout=false") {
                            // Don't add to back stack if already on active workout
                            launchSingleTop = true
                        }
                    },
                    onPauseResume = {
                        viewModel.togglePauseResume()
                    },
                    onStopSession = {
                        viewModel.showStopSessionDialog()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Add padding to account for bottom navigation if needed
                        .padding(bottom = 80.dp)
                )
            }
        }
    }
    
    // Handle stop session dialog
    if (viewModel.showStopDialog) {
        StopWorkoutDialog(
            onConfirm = {
                viewModel.completeWorkout()
                viewModel.dismissStopDialog()
            },
            onDismiss = {
                viewModel.dismissStopDialog()
            },
            onDiscard = {
                viewModel.discardWorkout()
                viewModel.dismissStopDialog()
            }
        )
    }
}

/**
 * Dialog for stopping/completing workout
 */
@Composable
private fun StopWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material3.Text("Stop Workout")
        },
        text = {
            androidx.compose.material3.Text(
                "What would you like to do with your current workout?"
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onConfirm
            ) {
                androidx.compose.material3.Text("Complete & Save")
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(
                    onClick = onDiscard
                ) {
                    androidx.compose.material3.Text("Discard")
                }
                
                androidx.compose.material3.TextButton(
                    onClick = onDismiss
                ) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        }
    )
}

/**
 * ViewModel for unified main navigation
 */
@HiltViewModel
class UnifiedMainNavigationViewModel @javax.inject.Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager
) : androidx.lifecycle.ViewModel() {
    
    // Direct access to current session
    val currentSession = sessionManager.currentSession
    
    // Stop dialog state
    private val _showStopDialog = androidx.compose.runtime.mutableStateOf(false)
    val showStopDialog: Boolean by _showStopDialog
    
    /**
     * Toggles pause/resume for the current session
     */
    fun togglePauseResume() {
        val session = currentSession.value
        if (session == null) {
            timber.log.Timber.w("Cannot toggle pause/resume - no active session")
            return
        }
        
        when (session.sessionStatus) {
            com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.ACTIVE -> {
                sessionManager.pauseSession()
            }
            com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.PAUSED -> {
                sessionManager.resumeSession()
            }
            com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.COMPLETED -> {
                // Do nothing - session already completed
            }
        }
    }
    
    /**
     * Shows the stop session dialog
     */
    fun showStopSessionDialog() {
        _showStopDialog.value = true
    }
    
    /**
     * Dismisses the stop session dialog
     */
    fun dismissStopDialog() {
        _showStopDialog.value = false
    }
    
    /**
     * Completes the current workout
     */
    fun completeWorkout() {
        viewModelScope.launch {
            try {
                val success = sessionManager.completeSession()
                if (success) {
                    timber.log.Timber.i("Workout completed from navigation")
                } else {
                    timber.log.Timber.w("Failed to complete workout from navigation")
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error completing workout from navigation")
            }
        }
    }
    
    /**
     * Discards the current workout
     */
    fun discardWorkout() {
        viewModelScope.launch {
            try {
                sessionManager.discardSession()
                timber.log.Timber.i("Workout discarded from navigation")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error discarding workout from navigation")
            }
        }
    }
    
    /**
     * Adds an exercise to the current active session
     */
    fun addExerciseToCurrentSession(exerciseLibrary: com.example.liftrix.domain.model.ExerciseLibrary) {
        viewModelScope.launch {
            try {
                // Convert ExerciseLibrary to SessionExercise
                val sessionExercise = com.example.liftrix.domain.model.SessionExercise(
                    exerciseId = com.example.liftrix.domain.model.ExerciseId(exerciseLibrary.id),
                    name = exerciseLibrary.name,
                    category = exerciseLibrary.primaryMuscleGroup,
                    primaryMuscle = exerciseLibrary.primaryMuscleGroup,
                    equipment = exerciseLibrary.equipment,
                    secondaryMuscles = exerciseLibrary.secondaryMuscleGroups.toSet(),
                    sets = emptyList(),
                    orderIndex = 0 // Will be set by session manager
                )
                sessionManager.addExerciseToSession(sessionExercise)
                timber.log.Timber.i("Added exercise to current session: ${exerciseLibrary.name}")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error adding exercise to session: ${exerciseLibrary.name}")
            }
        }
    }
}
/**
 * Bottom navigation bar with main app tabs
 */
@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val items = listOf(
        BottomNavItem("home", "Home", Icons.Default.Home),
        BottomNavItem("workout", "Workout", Icons.Default.FitnessCenter),
        BottomNavItem("progress", "Progress", Icons.Default.TrendingUp),
        BottomNavItem("coach", "Coach", Icons.Default.Psychology)
    )
    
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(item.label)
                },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

/**
 * Data class for bottom navigation items
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
