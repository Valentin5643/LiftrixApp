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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.liftrix.ui.settings.SettingsScreen
import com.example.liftrix.ui.social.SocialViewModel
import com.example.liftrix.ui.social.SocialEvent
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.ui.navigation.migration.NavigationMigrationHelper
import com.example.liftrix.ui.navigation.migration.LegacyNavigationWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 🔥 MIGRATION NOTE: Legacy Navigation Container with Migration Support
 * 
 * This container demonstrates the transition from string-based navigation to
 * type-safe LiftrixRoute sealed classes. It includes migration helper integration
 * to provide backward compatibility during the transition period.
 * 
 * MIGRATION STATUS: This file uses legacy string navigation and should be
 * gradually migrated to use UnifiedNavigationContainer.kt with type-safe routes.
 * 
 * Key features:
 * - Persistent live session bar across all screens
 * - Single source of truth for session state
 * - Migration helper integration for backward compatibility
 * - Deprecation warnings for legacy navigation calls
 * 
 * @deprecated Consider migrating to UnifiedNavigationContainer with LiftrixRoute sealed classes
 */
@Deprecated(
    message = "Consider migrating to UnifiedNavigationContainer with type-safe LiftrixRoute sealed classes",
    replaceWith = ReplaceWith("UnifiedNavigationContainer", "com.example.liftrix.ui.navigation.UnifiedNavigationContainer")
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMainNavigationContainer(
    navController: NavHostController = rememberNavController(),
    viewModel: UnifiedMainNavigationViewModel = hiltViewModel(),
    migrationHelper: NavigationMigrationHelper = hiltViewModel<UnifiedMainNavigationViewModel>().migrationHelper
) {
    val currentSession by viewModel.currentSession.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            NavigationAwareTopAppBar(
                navController = navController,
                currentRoute = currentRoute
            )
        },
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
                            // 🔄 MIGRATION: Using migration helper for backward compatibility
                            migrationHelper.navigateViaString(navController, "workout")
                        },
                        onNavigateToFriends = {
                            // 🔄 MIGRATION: Using migration helper for backward compatibility
                            migrationHelper.navigateViaString(navController, "friends")
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
                
                // Global settings screen accessible from all tabs
                composable("settings") {
                    SettingsScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToProfile = {
                            // TODO: Navigate to profile editing screen when implemented
                        },
                        onNavigateToAuth = {
                            // TODO: Navigate to auth when implemented
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
    private val sessionManager: UnifiedWorkoutSessionManager,
    val migrationHelper: NavigationMigrationHelper
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
 * Bottom navigation bar with main app tabs using type-safe navigation
 */
@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val items = listOf(
        LegacyBottomNavItem("home", "Home", Icons.Default.Home),
        LegacyBottomNavItem("workout", "Workout", Icons.Default.FitnessCenter),
        LegacyBottomNavItem("progress", "Progress", Icons.Default.TrendingUp),
        LegacyBottomNavItem("coach", "Coach", Icons.Default.Psychology)
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
 * Data class for bottom navigation items with string-based routes (legacy)
 */
private data class LegacyBottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Navigation-aware top app bar that dynamically shows content based on current route.
 * 
 * Features:
 * - Global top bar with settings button for main tabs
 * - Screen-specific top bars with back navigation for detail screens
 * - Screen titles for proper navigation hierarchy
 * - Unified top bar logic to prevent duplication
 * - Smooth transitions between different top bar configurations
 * 
 * @param navController Navigation controller for handling back navigation
 * @param currentRoute Current navigation route
 */
@Composable
private fun NavigationAwareTopAppBar(
    navController: NavHostController,
    currentRoute: String?
) {
    // Define screens that should show back navigation with custom titles
    val screenTitles = mapOf(
        "settings" to "Settings",
        "friends" to "Friends",
        "unified_active_workout" to "Active Workout",
        "template_creation" to "Create Template",
        "exercise_selection" to "Add Exercise",
        "exercise_selection_for_template" to "Add Exercise"
    )
    
    // Check if current route is a main tab (should show global top bar)
    val isMainTab = currentRoute in listOf(
        "home",
        "workout", 
        "progress",
        "coach"
    )
    
    // Check if current route should show back navigation
    val shouldShowBackNavigation = currentRoute in screenTitles.keys || 
                                  currentRoute?.startsWith("unified_active_workout") == true ||
                                  currentRoute?.startsWith("template_creation") == true ||
                                  currentRoute?.startsWith("exercise_selection") == true
    
    TopAppBar(
        title = {
            val title = when {
                shouldShowBackNavigation -> {
                    // For parameterized routes, extract the base route
                    val baseRoute = currentRoute?.split("?")?.firstOrNull()
                    screenTitles[baseRoute] ?: screenTitles[currentRoute] ?: "Back"
                }
                else -> ""
            }
            
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        navigationIcon = {
            if (shouldShowBackNavigation) {
                IconButton(
                    onClick = {
                        navController.navigateUp()
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Navigate back"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = {
            // Show refresh button specifically for Friends screen
            if (currentRoute == "friends") {
                // Get SocialViewModel instance for refresh functionality  
                val socialViewModel: SocialViewModel = hiltViewModel()
                
                IconButton(
                    onClick = {
                        socialViewModel.onEvent(SocialEvent.Refresh)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Refresh friends"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Show settings button on all screens EXCEPT when already in settings
            if (currentRoute != "settings") {
                IconButton(
                    onClick = {
                        navController.navigate("settings")
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Open settings"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

