package com.example.liftrix.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.liftrix.domain.model.User
import com.example.liftrix.ui.components.WorkoutCreationFab
import com.example.liftrix.ui.components.WorkoutCreationModal

// Navigation graph imports
import com.example.liftrix.ui.navigation.homeGraph
import com.example.liftrix.ui.navigation.workoutGraph
import com.example.liftrix.ui.navigation.progressGraph
import com.example.liftrix.ui.navigation.coachGraph
import com.example.liftrix.ui.navigation.WorkoutDestinations
import com.example.liftrix.ui.navigation.HomeRoutes
import com.example.liftrix.ui.navigation.ProgressRoutes
import com.example.liftrix.ui.navigation.CoachRoutes
import com.example.liftrix.ui.settings.SettingsScreen
import com.example.liftrix.ui.social.SocialViewModel
import com.example.liftrix.ui.social.SocialEvent
import com.example.liftrix.ui.common.WorkoutNowBar
import com.example.liftrix.ui.common.WorkoutSessionRecoveryDialog
import com.example.liftrix.service.LiveWorkoutSessionManager

// Missing imports for ViewModel and events
import com.example.liftrix.ui.navigation.MainNavigationViewModel
import com.example.liftrix.ui.navigation.MainNavigationEvent
import com.example.liftrix.ui.navigation.LiveWorkoutSessionViewModel
import com.example.liftrix.ui.navigation.MainNavigationItem

/**
 * Main navigation container that provides the primary navigation structure for the app.
 * 
 * Uses Material3 Scaffold with enhanced NavigationBar for bottom navigation, FAB for workout creation,
 * and NavHost for managing navigation between the four main tabs: Home, Workout, Progress, and Coach.
 * 
 * Features:
 * - Enhanced Material 3 styling with brand color integration
 * - Line-based icons with circular containers for consistency
 * - Improved visual hierarchy with proper spacing and elevation
 * - State restoration for navigation
 * - Accessibility support with enhanced content descriptions
 * - Tab switching with proper back stack management
 * - Workout creation FAB with modal selection
 * - Integration with MainNavigationViewModel for state management
 * 
 * @param user The authenticated user
 * @param onNavigateToAuth Callback to navigate to authentication flow
 * @param modifier Modifier for styling
 * @param navController NavController for navigation management (can be injected for testing)
 * @param viewModel ViewModel for navigation state management (can be injected for testing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationContainer(
    user: User,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainNavigationViewModel = hiltViewModel(),
    liveWorkoutSessionViewModel: LiveWorkoutSessionViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    // Observe live workout session state
    val liveWorkoutSessionManager = liveWorkoutSessionViewModel.liveWorkoutSessionManager
    val liveSessionState by liveWorkoutSessionManager.liveSessionState.collectAsState()
    val sessionDuration by liveWorkoutSessionManager.sessionDuration.collectAsState()
    val recoveryState by liveWorkoutSessionManager.recoveryState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                NavigationAwareTopAppBar(
                    navController = navController,
                    currentDestination = currentDestination
                )
            },
            bottomBar = {
                EnhancedBottomNavigation(
                    currentDestination = currentDestination,
                    onNavigate = { route: String ->
                        // Enhanced haptic feedback for navigation action
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        navController.navigate(route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                )
            },
            floatingActionButton = {
                // Only show FAB when no live workout session is active
                if (liveSessionState !is LiveWorkoutSessionManager.LiveSessionState.ActiveSession) {
                    WorkoutCreationFab(
                        onWorkoutCreationClick = {
                            viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = MainNavigationItem.HOME.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                // Home tab navigation graph
                homeGraph(
                    onNavigateToAuth = onNavigateToAuth,
                    onNavigateToWorkout = {
                        navController.navigate(MainNavigationItem.WORKOUT.route)
                    },
                    navController = navController
                )

                // Workout tab navigation graph
                workoutGraph(
                    user = user,
                    onNavigateToAuth = onNavigateToAuth,
                    navController = navController,
                    onNavigateToExerciseLibrary = {
                        // TODO: Navigate to exercise library when implemented
                        // For now, placeholder to satisfy the interface
                    }
                )

                // Progress tab navigation graph
                progressGraph(
                    onNavigateToAuth = onNavigateToAuth
                )

                // Coach tab navigation graph
                coachGraph(
                    onNavigateToAuth = onNavigateToAuth
                )
                
                // Global settings screen accessible from all tabs
                composable(HomeRoutes.SETTINGS) {
                    SettingsScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onNavigateToProfile = {
                            // TODO: Navigate to profile editing screen when implemented
                        },
                        onNavigateToAuth = {
                            onNavigateToAuth()
                        }
                    )
                }
                }
                
                // Persistent WorkoutNowBar overlay
                WorkoutNowBar(
                    sessionState = liveSessionState,
                    sessionDuration = sessionDuration,
                    onBarClick = {
                        // Navigate to active workout screen
                        navController.navigate(WorkoutDestinations.ACTIVE_SESSION)
                    },
                    onPauseResume = {
                        val currentState = liveSessionState
                        when (currentState) {
                            is LiveWorkoutSessionManager.LiveSessionState.ActiveSession -> {
                                if (currentState.isRunning) {
                                    liveWorkoutSessionManager.pauseLiveSession()
                                } else {
                                    liveWorkoutSessionManager.resumeLiveSession()
                                }
                            }
                            else -> { /* No-op */ }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                
                // Workout creation modal
                WorkoutCreationModal(
                    isVisible = uiState.isWorkoutCreationModalVisible,
                    onDismiss = {
                        viewModel.onEvent(MainNavigationEvent.HideWorkoutCreationModal)
                    },
                    onTemplateWorkout = {
                        // Navigate to workout tab and trigger template-based creation
                        navController.navigate(MainNavigationItem.WORKOUT.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                        // Navigate to template selection screen within workout tab
                        if (navController.currentDestination?.route != WorkoutDestinations.WORKOUT_MAIN) {
                            navController.navigate(WorkoutDestinations.WORKOUT_MAIN)
                        }
                        // Hide the modal
                        viewModel.onEvent(MainNavigationEvent.HideWorkoutCreationModal)
                    },
                    onCustomWorkout = {
                        // Navigate to workout tab and trigger custom workout creation
                        navController.navigate(MainNavigationItem.WORKOUT.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                        // Navigate directly to blank workout session using modern WorkoutFlow
                        navController.navigate("blank_workout_session")
                        // Hide the modal
                        viewModel.onEvent(MainNavigationEvent.HideWorkoutCreationModal)
                    }
                )
                
                // Workout session recovery dialog
                val currentRecoveryState = recoveryState
                WorkoutSessionRecoveryDialog(
                    isVisible = currentRecoveryState is LiveWorkoutSessionManager.RecoveryState.CorruptedSession,
                    sessionName = when (currentRecoveryState) {
                        is LiveWorkoutSessionManager.RecoveryState.CorruptedSession -> currentRecoveryState.sessionName
                        else -> ""
                    },
                    onRestore = {
                        liveWorkoutSessionManager.restoreCorruptedSession()
                    },
                    onDiscard = {
                        liveWorkoutSessionManager.discardCorruptedSession()
                    },
                    onDismiss = {
                        // For now, same as discard - but could be extended to allow dismissing temporarily
                        liveWorkoutSessionManager.discardCorruptedSession()
                    }
                )
            }
        }
    }
}

/**
 * Navigation-aware top app bar that dynamically shows content based on current route.
 * 
 * Features:
 * - Global top bar with settings button for main tabs
 * - Screen-specific top bars with back navigation for detail screens
 * - Friends screen refresh functionality integrated into global top bar
 * - Unified top bar logic to prevent duplication
 * - Smooth transitions between different top bar configurations
 * 
 * @param navController Navigation controller for handling back navigation
 * @param currentDestination Current navigation destination
 */
@Composable
fun NavigationAwareTopAppBar(
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?
) {
    val currentRoute = currentDestination?.route
    
    // Define screens that should show back navigation with custom titles
    val screenTitles = mapOf(
        HomeRoutes.SETTINGS to "Settings",
        HomeRoutes.FRIENDS to "Friends",
        WorkoutDestinations.WORKOUT_MAIN to "Workout",
        ProgressRoutes.PROGRESS_DASHBOARD to "Progress Dashboard",
        CoachRoutes.COACH_MAIN to "Coach",
        WorkoutDestinations.ACTIVE_SESSION to "Active Workout",
        WorkoutDestinations.CREATE_TEMPLATE to "Create Template",
        WorkoutDestinations.ADD_EXERCISE_TO_SESSION to "Add Exercise"
    )
    
    // Check if current route is a main tab (should show global top bar)
    val isMainTab = currentRoute in listOf(
        MainNavigationItem.HOME.route,
        MainNavigationItem.WORKOUT.route,
        MainNavigationItem.PROGRESS.route,
        MainNavigationItem.COACH.route
    )
    
    // Check if current route should show back navigation
    val shouldShowBackNavigation = currentRoute in screenTitles.keys || 
                                  currentRoute?.startsWith("workout/") == true ||
                                  currentRoute?.startsWith("progress/") == true ||
                                  currentRoute?.startsWith("coach/") == true
    
    TopAppBar(
        title = {
            val title = when {
                shouldShowBackNavigation -> screenTitles[currentRoute] ?: "Back"
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
                        navController.popBackStack()
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
            if (currentRoute == HomeRoutes.FRIENDS) {
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
            if (currentRoute != HomeRoutes.SETTINGS) {
                IconButton(
                    onClick = {
                        navController.navigate(HomeRoutes.SETTINGS)
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

/**
 * Enhanced bottom navigation with modern styling and brand color integration
 * Features line-based icons with circular containers and improved visual hierarchy
 */
@Composable
fun EnhancedBottomNavigation(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 12.dp // Increased elevation for better visual hierarchy
    ) {
        MainNavigationItem.entries.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { destination -> 
                destination.route == item.route 
            } == true
            
            val interactionSource = remember { MutableInteractionSource() }
            
            // Enhanced animated colors for smooth brand color transitions
            val animatedIconColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = LiftrixAnimations.fastColorTransitionSpec,
                label = "${item.label}_icon_color"
            )
            
            val animatedLabelColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = LiftrixAnimations.fastColorTransitionSpec,
                label = "${item.label}_label_color"
            )
            
            // Enhanced container background animation
            val animatedContainerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
                animationSpec = LiftrixAnimations.fastColorTransitionSpec,
                label = "${item.label}_container_color"
            )
            
            // Micro-scaling animation for athletic feel
            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.1f else 1.0f,
                animationSpec = LiftrixAnimations.athleticMicroSpring,
                label = "${item.label}_scale"
            )

            NavigationBarItem(
                icon = {
                    // Enhanced icon with circular container
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(animatedScale)
                            .clip(CircleShape)
                            .background(animatedContainerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = animatedIconColor,
                            modifier = Modifier
                                .size(20.dp)
                                .semantics {
                                    contentDescription = "${item.label} tab"
                                }
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedLabelColor
                    )
                },
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Transparent, // Handled by custom container
                    selectedTextColor = Color.Transparent, // Handled by custom animation
                    indicatorColor = Color.Transparent, // Using custom circular container
                    unselectedIconColor = Color.Transparent, // Handled by custom animation
                    unselectedTextColor = Color.Transparent // Handled by custom animation
                ),
                interactionSource = interactionSource,
                modifier = Modifier.semantics {
                    contentDescription = if (isSelected) {
                        "${item.label} tab selected"
                    } else {
                        "Navigate to ${item.label} tab"
                    }
                }
            )
        }
    }
}