package com.example.liftrix.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
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

/**
 * Main navigation container that provides the primary navigation structure for the app.
 * 
 * Uses Material3 Scaffold with NavigationBar for bottom navigation, FAB for workout creation,
 * and NavHost for managing navigation between the four main tabs: Home, Workout, Progress, and Coach.
 * 
 * Features:
 * - State restoration for navigation
 * - Proper Material3 styling and theming
 * - Accessibility support with content descriptions
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
@Composable
fun MainNavigationContainer(
    user: User,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainNavigationViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    MainNavigationItem.entries.forEach { item ->
                        val isSelected = currentDestination?.hierarchy?.any { 
                            it.route == item.route 
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.semantics {
                                        contentDescription = "${item.label} tab"
                                    }
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
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
                            },
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
            },
            floatingActionButton = {
                WorkoutCreationFab(
                    onWorkoutCreationClick = {
                        viewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal)
                    }
                )
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = MainNavigationItem.HOME.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                    navController = navController
                )

                // Progress tab navigation graph
                progressGraph(
                    onNavigateToAuth = onNavigateToAuth
                )

                // Coach tab navigation graph
                coachGraph(
                    onNavigateToAuth = onNavigateToAuth
                )
            }
        }
        
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
                if (navController.currentDestination?.route != WorkoutTabRoutes.TEMPLATE_MANAGEMENT) {
                    navController.navigate(WorkoutTabRoutes.TEMPLATE_MANAGEMENT)
                }
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
                // TODO: Add navigation to custom workout creation
                // This will be handled by the WorkoutScreen's internal navigation
            }
        )
    }
}

 