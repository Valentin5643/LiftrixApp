package com.example.liftrix.ui.navigation

import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.liftrix.BuildConfig
import com.example.liftrix.feature.auth.navigation.authGraph
import com.example.liftrix.feature.chat.navigation.chatGraph
import com.example.liftrix.feature.home.navigation.homeGraph
import com.example.liftrix.feature.profile.navigation.profileGraph
import com.example.liftrix.feature.progress.navigation.progressGraph
import com.example.liftrix.feature.settings.navigation.settingsGraph
import com.example.liftrix.feature.social.navigation.socialGraph
import com.example.liftrix.feature.workout.navigation.activeWorkoutGraph
import com.example.liftrix.feature.workout.navigation.workoutGraph
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.ui.components.WorkoutCreationModal
import com.example.liftrix.ui.navigation.navigateToWorkout
import com.example.liftrix.ui.navigation.navigateToActiveWorkout
import com.example.liftrix.ui.navigation.navigateAndReplace
import com.example.liftrix.ui.navigation.popBackStackSafely
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel

/**
 * Unified Navigation Container with Type-Safe Routes
 * 
 * This container replaces string-based navigation with type-safe LiftrixRoute sealed classes,
 * providing compile-time route validation and eliminating runtime navigation errors.
 * 
 * Key features:
 * - Type-safe navigation using LiftrixRoute sealed classes
 * - Single NavHost handling all app navigation
 * - Extension functions for clean navigation API
 * - Deep linking support through kotlinx.serialization
 * - Persistent live session bar across all screens
 * - Global TopAppBar with settings and navigation
 * - Backward compatibility maintained during transition
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedNavigationContainer(
    navController: NavHostController = rememberNavController(),
    viewModel: UnifiedNavigationViewModel = hiltViewModel(),
    isAiAccessEnabled: Boolean = false,
    pendingWidgetWorkoutNavigation: Boolean = false,
    onWidgetWorkoutNavigationConsumed: () -> Unit = {}
) {
    // Session management handled by viewModel
    val currentSession by viewModel.currentSession.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val routeChrome = currentDestination.routeChrome()
    
    // State for workout creation modal
    var showWorkoutCreationModal by remember { mutableStateOf(false) }

    LaunchedEffect(pendingWidgetWorkoutNavigation) {
        if (pendingWidgetWorkoutNavigation) {
            val session = currentSession
            if (session?.isLive() == true) {
                navController.navigateToActiveWorkout(
                    templateId = session.templateId,
                    isBlankWorkout = false
                )
            } else {
                navController.navigateToWorkout()
            }
            onWidgetWorkoutNavigationConsumed()
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (routeChrome.showTopBar) {
                NavigationAwareTopAppBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    routeChrome = routeChrome
                )
            }
        },
        bottomBar = {
            if (routeChrome.showBottomBar) {
                BottomNavigationBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    isAiAccessEnabled = isAiAccessEnabled
                )
            }
        },
        floatingActionButton = {
            ConditionalWorkoutFab(
                onFabClick = {
                    showWorkoutCreationModal = true
                },
                currentDestination = currentDestination,
                routeChrome = routeChrome
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = LiftrixRoute.Home,
                modifier = Modifier.fillMaxSize()
            ) {
                homeGraph(navController)
                workoutGraph(
                    navController = navController,
                    onSessionExerciseSelected = viewModel::addExerciseToCurrentSession
                )
                activeWorkoutGraph(navController)
                progressGraph(navController)
                chatGraph(
                    navController = navController,
                    isAiAccessEnabled = isAiAccessEnabled
                )
                socialGraph(navController)
                profileGraph(navController)
                settingsGraph(navController)
                authGraph(
                    navController = navController,
                    googleClientId = BuildConfig.GOOGLE_CLIENT_ID
                )
            }
            
            // Persistent live session bar - only show if session exists and not on active workout screen
            if (currentSession?.isLive() == true &&
                !currentDestination.isRoute(LiftrixRoute.ActiveWorkout::class)) {
                
                LiveSessionBar(
                    session = currentSession,
                    onBarClick = {
                        // Navigate to active workout screen - continue existing session
                        val sessionTemplateId = currentSession!!.templateId
                        navController.navigateToActiveWorkout(
                            templateId = sessionTemplateId,
                            isBlankWorkout = false
                        )
                    },
                    onPauseResume = {
                        viewModel.togglePauseResume()
                    },
                    onStopSession = {
                        viewModel.showStopSessionDialog()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp) // Fixed: Use minimal padding above navigation bar
                )
            }
        }
    }
    
    // Workout creation modal
    WorkoutCreationModal(
        isVisible = showWorkoutCreationModal,
        onDismiss = { showWorkoutCreationModal = false },
        onStartFromTemplate = {
            showWorkoutCreationModal = false
            navController.navigateToWorkout()
        },
        onStartBlankWorkout = {
            showWorkoutCreationModal = false
            navController.navigateToActiveWorkout(isBlankWorkout = true)
        },
    )
    
    // Handle stop session dialog
    if (viewModel.showStopDialog) {
        StopWorkoutDialog(
            onConfirm = {
                viewModel.completeWorkout(onNavigateToHome = { navController.navigateAndReplace(LiftrixRoute.Home) })
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
 * Bottom navigation bar with main app tabs using type-safe navigation
 */
@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isAiAccessEnabled: Boolean
) {
    val items = buildList {
        add(BottomNavItem(LiftrixRoute.Home, "Home", Icons.Default.Home))
        add(BottomNavItem(LiftrixRoute.Workout, "Workout", Icons.Default.FitnessCenter))
        add(BottomNavItem(LiftrixRoute.Progress, "Progress", Icons.Default.TrendingUp))
        if (isAiAccessEnabled) {
            add(BottomNavItem(LiftrixRoute.Coach, "Coach", Icons.Default.Psychology))
        }
    }
    
    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination.isRoute(item.route::class)
            
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
                selected = selected,
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
 * Data class for bottom navigation items with type-safe routes
 */
private data class BottomNavItem(
    val route: LiftrixRoute,
    val label: String,
    val icon: ImageVector
)

/**
 * ViewModel for unified navigation container
 */
@HiltViewModel
class UnifiedNavigationViewModel @Inject constructor(
    val sessionManager: UnifiedWorkoutSessionManager,  // Made public for WorkoutScreen access
    private val authQueryUseCase: com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
) : ViewModel() {
    
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
            com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> {
                // Do nothing - session failed to save, cannot resume
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
     * 
     * @param onNavigateToHome Callback to navigate to Home screen after successful completion
     */
    fun completeWorkout(onNavigateToHome: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val success = sessionManager.completeSession()
                if (success) {
                    timber.log.Timber.i("Workout completed from navigation")
                    // Navigate to Home after successful completion
                    try {
                        onNavigateToHome?.invoke()
                    } catch (navigationError: Exception) {
                        timber.log.Timber.w(navigationError, "Navigation to Home failed after workout completion")
                        // Navigation failed, but workout was completed successfully
                        // Don't show error to user as the main action succeeded
                    }
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
            // Use SessionExercise.createBlank() to ensure proper validation
            val sessionExercise = com.example.liftrix.domain.model.SessionExercise.createBlank(
                exerciseId = com.example.liftrix.domain.model.ExerciseId(exerciseLibrary.id),
                name = exerciseLibrary.name,
                category = exerciseLibrary.primaryMuscleGroup,
                primaryMuscle = exerciseLibrary.primaryMuscleGroup,
                equipment = exerciseLibrary.equipment,
                orderIndex = 0, // Will be set by session manager
                initialSets = 1 // Create one default set
            )
            sessionManager.addExerciseToSession(sessionExercise)
        }
    }
    
    /**
     * Gets the current authenticated user ID securely
     * @return Current user ID or null if not authenticated
     */
    suspend fun getCurrentUserId(): String? {
        return try {
            val result = authQueryUseCase(waitForAuth = false)
            result.fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error getting current user ID")
            null
        }
    }
}

/**
 * Navigation-aware top app bar that dynamically shows content based on current route.
 * 
 * Features:
 * - Global top bar with settings button for main tabs
 * - Screen-specific top bars with back navigation for detail screens
 * - Screen titles for proper navigation hierarchy
 * - Refresh functionality for Friends screen
 * - Type-safe route handling with LiftrixRoute sealed classes
 * 
 * @param navController Navigation controller for handling back navigation
 * @param currentDestination Current navigation destination
 */
@Composable
private fun NavigationAwareTopAppBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    routeChrome: RouteChrome
) {
    val metadata = currentDestination.routeMetadata()
    
    TopAppBar(
        title = {
            if (routeChrome.title.isNotEmpty()) {
                Text(
                    text = routeChrome.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (routeChrome.showBack) {
                IconButton(
                    onClick = {
                        navController.popBackStackSafely()
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
            // Show actions specific to PublicProfile screen
            // Note: Profile-specific actions are handled within the screen itself
            // to maintain proper ViewModel scoping
            
            // Show search button specifically for Friends screen
            if (metadata?.showSearchAction == true) {
                IconButton(
                    onClick = {
                        navController.navigateToUserSearch()
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Search users"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Show social button on all screens EXCEPT when already in social
            if (!currentDestination.isRoute(LiftrixRoute.Friends::class)) {
                IconButton(
                    onClick = {
                        navController.navigate(LiftrixRoute.Friends)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Open social"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Social",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Show settings button on all screens EXCEPT when already in settings
            if (metadata?.hideSettingsAction != true) {
                IconButton(
                    onClick = {
                        navController.navigate(LiftrixRoute.Settings)
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
