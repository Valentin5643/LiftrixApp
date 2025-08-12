package com.example.liftrix.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.WorkoutId
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.liftrix.ui.common.LiveSessionBar
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.ui.workout.WorkoutScreen
import com.example.liftrix.ui.workout.active.RedesignedActiveWorkoutScreen
import com.example.liftrix.ui.workout.create.RedesignedCreateTemplateScreen
import com.example.liftrix.ui.workout.edit.RedesignedEditWorkoutScreen
import com.example.liftrix.ui.progress.ProgressDashboardScreen
import com.example.liftrix.ui.progress.detail.WorkoutFrequencyDetailScreen
import com.example.liftrix.ui.coach.CoachScreen
import com.example.liftrix.ui.social.SocialViewModel
import com.example.liftrix.ui.social.SocialEvent
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.ui.components.ConditionalWorkoutFab
import com.example.liftrix.ui.components.WorkoutCreationModal
import com.example.liftrix.ui.navigation.navigateToEditWorkout
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
    viewModel: UnifiedNavigationViewModel = hiltViewModel()
) {
    // Session management handled by viewModel
    val currentSession by viewModel.currentSession.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // State for workout creation modal
    var showWorkoutCreationModal by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            NavigationAwareTopAppBar(
                navController = navController,
                currentDestination = currentDestination
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentDestination = currentDestination
            )
        },
        floatingActionButton = {
            ConditionalWorkoutFab(
                onFabClick = {
                    showWorkoutCreationModal = true
                },
                currentDestination = currentDestination
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Type-safe navigation with LiftrixRoute sealed classes
            NavHost(
                navController = navController,
                startDestination = LiftrixRoute.Home,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<LiftrixRoute.Home> {
                    Column {
                        // Guest session indicator for anonymous users
                        com.example.liftrix.ui.guest.GuestSessionIndicator(
                            onClick = {
                                navController.navigateToGuestConversion(source = "nudge")
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        HomeScreen(
                            onNavigateToWorkout = {
                                navController.navigateToWorkout()
                            },
                            onNavigateToFriends = {
                                navController.navigateToFriends()
                            }
                        )
                    }
                }
                
                composable<LiftrixRoute.Workout> {
                    WorkoutScreen(
                        onNavigateToActiveWorkout = { templateId ->
                            navController.navigateToActiveWorkout(
                                templateId = templateId, 
                                isBlankWorkout = templateId == null
                            )
                        },
                        onNavigateToWorkoutCreation = { folderId ->
                            // Direct navigation to template creation (not modal)
                            navController.navigate(LiftrixRoute.TemplateCreation(folderId))
                        },
                        onNavigateToEditWorkout = { workoutId ->
                            navController.navigateToEditWorkout(workoutId)
                        }
                    )
                }
                
                composable<LiftrixRoute.Progress> {
                    ProgressDashboardScreen(
                        onNavigateToVolumeDetail = {
                            navController.navigate(LiftrixRoute.VolumeAnalysisDetail)
                        },
                        onNavigateToOneRmDetail = {
                            navController.navigate(LiftrixRoute.OneRmDetail)
                        },
                        onNavigateToMuscleGroupDetail = {
                            navController.navigate(LiftrixRoute.MuscleGroupDetail)
                        },
                        onNavigateToFrequencyDetail = {
                            navController.navigate(LiftrixRoute.WorkoutFrequencyDetail)
                        }
                    )
                }
                
                composable<LiftrixRoute.Coach> {
                    CoachScreen()
                }
                
                composable<LiftrixRoute.Friends> {
                    com.example.liftrix.ui.social.FriendsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // Social Discovery Routes
                composable<LiftrixRoute.UserSearch> {
                    com.example.liftrix.ui.social.UserSearchScreen(
                        onNavigateToProfile = { userId ->
                            navController.navigateToPublicProfile(userId)
                        }
                    )
                }
                
                composable<LiftrixRoute.PublicProfile> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.PublicProfile>()
                    com.example.liftrix.ui.social.PublicProfileScreen(
                        userId = route.userId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToQRCode = { userId ->
                            navController.navigateToQRCodeDisplay(userId)
                        }
                    )
                }
                
                composable<LiftrixRoute.QRCodeDisplay> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.QRCodeDisplay>()
                    val coroutineScope = rememberCoroutineScope()
                    
                    // Secure authentication check - redirect to auth if no valid user
                    LaunchedEffect(route.userId) {
                        if (route.userId == null) {
                            coroutineScope.launch {
                                val currentUserId = viewModel.getCurrentUserId()
                                if (currentUserId == null) {
                                    // User not authenticated - redirect to sign in
                                    navController.navigateAndReplace(LiftrixRoute.AuthSignIn)
                                    return@launch
                                }
                                // If we have a valid user ID, navigate to QR code with the user ID
                                navController.navigateAndReplace(LiftrixRoute.QRCodeDisplay(currentUserId))
                            }
                        }
                    }
                    
                    // Only render screen if we have a valid userId
                    route.userId?.let { userId ->
                        com.example.liftrix.ui.social.QRCodeDisplayScreen(
                            userId = userId,
                            onNavigateBack = {
                                navController.popBackStackSafely()
                            }
                        )
                    }
                }
                
                composable<LiftrixRoute.WorkoutDetails> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.WorkoutDetails>()
                    // TODO: Implement WorkoutDetailsScreen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Workout Details Screen")
                            Text("Workout ID: ${route.workoutId}")
                            Button(
                                onClick = { navController.popBackStackSafely() }
                            ) {
                                Text("Back")
                            }
                        }
                    }
                }
                
                composable<LiftrixRoute.ExerciseSelection> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ExerciseSelection>()
                    com.example.liftrix.ui.workout.selection.ExerciseSelectionScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onExerciseSelected = { exerciseLibrary ->
                            if (route.isForTemplate) {
                                if (route.replaceExerciseIndex != null) {
                                    // Pass replacement data back to edit screen
                                    navController.previousBackStackEntry?.savedStateHandle?.set(
                                        "replace_exercise",
                                        Pair(route.replaceExerciseIndex, exerciseLibrary)
                                    )
                                } else {
                                    // Pass selected exercise back to template creation (add new)
                                    navController.previousBackStackEntry?.savedStateHandle?.set(
                                        "selected_exercise", 
                                        exerciseLibrary
                                    )
                                }
                                navController.popBackStackSafely()
                            } else {
                                // Add exercise to current session
                                viewModel.addExerciseToCurrentSession(exerciseLibrary)
                                navController.popBackStackSafely()
                            }
                        },
                        onCreateCustomExercise = {
                            // TODO: Navigate to custom exercise creation when implemented
                        }
                    )
                }
                
                composable<LiftrixRoute.ActiveWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ActiveWorkout>()
                    com.example.liftrix.ui.workout.active.RedesignedActiveWorkoutScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToExerciseLibrary = {
                            navController.navigateToExerciseSelection()
                        },
                        isBlankWorkout = route.isBlankWorkout,
                        templateId = route.templateId
                    )
                }
                
                composable<LiftrixRoute.TemplateCreation> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.TemplateCreation>()
                    com.example.liftrix.ui.workout.create.RedesignedCreateTemplateScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToExerciseSelection = {
                            navController.navigateToExerciseSelection(isForTemplate = true)
                        },
                        editTemplateId = null,
                        navBackStackEntry = backStackEntry
                    )
                }
                
                composable<LiftrixRoute.ExerciseDetails> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ExerciseDetails>()
                    // TODO: Implement ExerciseDetailsScreen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Exercise Details Screen")
                            Text("Exercise ID: ${route.exerciseId}")
                            Button(
                                onClick = { navController.popBackStackSafely() }
                            ) {
                                Text("Back")
                            }
                        }
                    }
                }
                
                composable<LiftrixRoute.Settings> {
                    com.example.liftrix.ui.settings.SettingsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToProfile = {
                            navController.navigateToProfile()
                        },
                        onNavigateToAuth = {
                            // TODO: Navigate to authentication screen
                            navController.clearBackStackAndNavigate(LiftrixRoute.Home)
                        },
                        onNavigateToAnomalyDetection = {
                            navController.navigateToAnomalySettings()
                        },
                        onNavigateToAnomalyDashboard = {
                            navController.navigateToAnomalyDashboard()
                        },
                        onNavigateToWidgetSettings = {
                            navController.navigate(LiftrixRoute.WidgetSettings)
                        }
                    )
                }
                
                composable<LiftrixRoute.WidgetSettings> {
                    com.example.liftrix.ui.settings.WidgetSettingsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.AnomalyDashboard> {
                    com.example.liftrix.ui.anomaly.AnomalyDashboardScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToSettings = {
                            navController.navigateToAnomalySettings()
                        }
                    )
                }
                
                composable<LiftrixRoute.AnomalySettings> {
                    com.example.liftrix.ui.anomaly.AnomalySettingsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.Onboarding> {
                    com.example.liftrix.ui.onboarding.navigation.OnboardingNavigation(
                        userId = "", // TODO: Get from authenticated user
                        onComplete = {
                            navController.clearBackStackAndNavigate(LiftrixRoute.Home)
                        },
                        onSkip = {
                            navController.clearBackStackAndNavigate(LiftrixRoute.Home)
                        }
                    )
                }
                
                // Guest Mode Routes
                composable<LiftrixRoute.GuestModeSelection> {
                    com.example.liftrix.ui.guest.GuestModeSelectionScreen(
                        onContinueAsGuest = {
                            // Handle anonymous sign-in and navigate to home
                            navController.clearBackStackAndNavigate(LiftrixRoute.Home)
                        },
                        onCreateAccount = {
                            navController.navigateToAuthSignUp()
                        },
                        onSignIn = {
                            navController.navigateToAuthSignIn()
                        }
                    )
                }
                
                composable<LiftrixRoute.GuestDashboard> {
                    com.example.liftrix.ui.guest.GuestDashboardScreen(
                        onUpgrade = {
                            navController.navigateToGuestConversion(source = "manual")
                        },
                        onStartWorkout = {
                            navController.navigateToActiveWorkout(isBlankWorkout = true)
                        },
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.GuestConversion> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.GuestConversion>()
                    com.example.liftrix.ui.guest.GuestConversionScreen(
                        source = route.source,
                        onCreateAccount = {
                            navController.navigateAndReplace(LiftrixRoute.AuthSignUp)
                        },
                        onSignIn = {
                            navController.navigateAndReplace(LiftrixRoute.AuthSignIn)
                        },
                        onMaybeLater = {
                            if (route.returnTo != null) {
                                // TODO: Convert returnTo string to LiftrixRoute for type-safe navigation
                                navController.popBackStackSafely()
                            } else {
                                navController.popBackStackSafely()
                            }
                        }
                    )
                }
                
                // Authentication Routes
                composable<LiftrixRoute.AuthSignUp> {
                    // TODO: Implement AuthSignUpScreen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sign Up Screen")
                            Button(
                                onClick = { navController.clearBackStackAndNavigate(LiftrixRoute.Home) }
                            ) {
                                Text("Complete Sign Up")
                            }
                        }
                    }
                }
                
                composable<LiftrixRoute.AuthSignIn> {
                    // TODO: Implement AuthSignInScreen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sign In Screen")
                            Button(
                                onClick = { navController.clearBackStackAndNavigate(LiftrixRoute.Home) }
                            ) {
                                Text("Complete Sign In")
                            }
                        }
                    }
                }
                
                // Workout Editing Routes
                composable<LiftrixRoute.EditWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.EditWorkout>()
                    val editWorkoutViewModel = hiltViewModel<com.example.liftrix.ui.workout.edit.EditWorkoutViewModel>()
                    
                    // Listen for exercise replacement from navigation
                    LaunchedEffect(backStackEntry.savedStateHandle) {
                        backStackEntry.savedStateHandle.getStateFlow<Pair<Int, com.example.liftrix.domain.model.ExerciseLibrary>?>(
                            "replace_exercise", null
                        ).collect { replacementData ->
                            if (replacementData != null) {
                                editWorkoutViewModel.replaceExercise(replacementData.first, replacementData.second)
                                // Clear the saved state to prevent re-triggering
                                backStackEntry.savedStateHandle.remove<Pair<Int, com.example.liftrix.domain.model.ExerciseLibrary>>("replace_exercise")
                            }
                        }
                    }
                    
                    com.example.liftrix.ui.workout.edit.RedesignedEditWorkoutScreen(
                        workoutId = WorkoutId(route.workoutId),
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToExerciseSelection = {
                            navController.navigateToExerciseSelection(isForTemplate = true)
                        },
                        onNavigateToExerciseSelectionWithReplacement = { exerciseIndex ->
                            navController.navigateToExerciseSelection(isForTemplate = true, replaceExerciseIndex = exerciseIndex)
                        },
                        viewModel = editWorkoutViewModel
                    )
                }
                
                composable<LiftrixRoute.EditSession> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.EditSession>()
                    com.example.liftrix.ui.workout.edit.EditSessionScreen(
                        sessionId = WorkoutId(route.sessionId),
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.CreateWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.CreateWorkout>()
                    com.example.liftrix.ui.workout.create.CreateWorkoutScreen(
                        initialFolderId = route.folderId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onStartFromTemplate = {
                            // Navigate to workout templates selection screen
                            navController.navigateToWorkout()
                        },
                        onStartBlankWorkout = {
                            // Navigate to simple template creation with folder support
                            navController.navigate(LiftrixRoute.TemplateCreation())
                        }
                    )
                }
                
                
                // Profile Management Routes
                composable<LiftrixRoute.Profile> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.Profile>()
                    com.example.liftrix.ui.profile.ProfileScreen(
                        onNavigateToEdit = { 
                            navController.navigateToProfileEdit() 
                        },
                        onNavigateToImageCrop = { uri -> 
                            navController.navigateToImageCrop(uri) 
                        },
                        onNavigateToSettings = { 
                            navController.navigateToSettings() 
                        }
                    )
                }

                composable<LiftrixRoute.ProfileEdit> {
                    com.example.liftrix.ui.profile.ProfileEditScreen(
                        onNavigateBack = { 
                            navController.popBackStackSafely() 
                        },
                        onNavigateToImageCrop = { uri -> 
                            navController.navigateToImageCrop(uri) 
                        }
                    )
                }

                composable<LiftrixRoute.ImageCrop> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ImageCrop>()
                    com.example.liftrix.ui.profile.ImageCropScreen(
                        imageUri = android.net.Uri.parse(route.imageUri),
                        onNavigateBack = { 
                            navController.popBackStackSafely() 
                        },
                        onCropConfirmed = { cropRect -> 
                            // Handle cropped image and navigate back
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // Analytics Detail Screen Routes
                composable<LiftrixRoute.VolumeAnalysisDetail> {
                    com.example.liftrix.ui.progress.detail.VolumeAnalysisDetailScreen(
                        navController = navController,
                        groupBy = com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_WEEK,
                        timeRange = com.example.liftrix.domain.model.analytics.TimeRangeType.MONTH
                    )
                }
                
                composable<LiftrixRoute.OneRmDetail> {
                    com.example.liftrix.ui.progress.detail.OneRmProgressionDetailScreen(
                        navController = navController,
                        exerciseIds = null, // Default to all exercises
                        timeRange = com.example.liftrix.domain.model.analytics.TimeRangeType.MONTH
                    )
                }
                
                composable<LiftrixRoute.MuscleGroupDetail> {
                    com.example.liftrix.ui.progress.detail.MuscleGroupDetailScreen(
                        navController = navController,
                        muscleGroup = null, // Default to all muscle groups
                        timeRange = com.example.liftrix.domain.model.analytics.TimeRangeType.MONTH
                    )
                }
                
                composable<LiftrixRoute.ExerciseRankingDetail> {
                    com.example.liftrix.ui.progress.detail.ExerciseRankingDetailScreen(
                        navController = navController,
                        sortBy = com.example.liftrix.domain.model.analytics.RankingMetric.PERFORMANCE_SCORE,
                        limit = 20
                    )
                }
                
                composable<LiftrixRoute.WorkoutFrequencyDetail> {
                    WorkoutFrequencyDetailScreen(
                        navController = navController
                    )
                }
            }
            
            // Persistent live session bar - only show if session exists and not on active workout screen
            if (currentSession?.isLive() == true && 
                currentDestination?.route?.contains("ActiveWorkout") != true) {
                
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
                        .padding(bottom = 80.dp) // Account for bottom navigation
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
        onGuestUpgrade = {
            showWorkoutCreationModal = false
            navController.navigateToGuestConversion(source = "limit_reached")
        }
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
    currentDestination: androidx.navigation.NavDestination?
) {
    val items = listOf(
        BottomNavItem(LiftrixRoute.Home, "Home", Icons.Default.Home),
        BottomNavItem(LiftrixRoute.Workout, "Workout", Icons.Default.FitnessCenter),
        BottomNavItem(LiftrixRoute.Progress, "Progress", Icons.Default.TrendingUp),
        BottomNavItem(LiftrixRoute.Coach, "Coach", Icons.Default.Psychology)
    )
    
    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { 
                it.route?.contains(item.route::class.simpleName.orEmpty()) == true 
            } == true
            
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
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
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
            timber.log.Timber.i("Added exercise to current session: ${exerciseLibrary.name}")
        }
    }
    
    /**
     * Gets the current authenticated user ID securely
     * @return Current user ID or null if not authenticated
     */
    suspend fun getCurrentUserId(): String? {
        return try {
            getCurrentUserIdUseCase()
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
    currentDestination: androidx.navigation.NavDestination?
) {
    val currentRoute = currentDestination?.route
    
    // Define screens that should show back navigation with custom titles
    val routeTitles = mapOf(
        "Settings" to "Settings",
        "WidgetSettings" to "Widget Settings",
        "Friends" to "Friends", 
        "ActiveWorkout" to "Active Workout",
        "TemplateCreation" to "Create Template",
        "EditWorkout" to "Edit Workout",
        "ExerciseSelection" to "Add Exercise",
        "WorkoutDetails" to "Workout Details",
        "ExerciseDetails" to "Exercise Details",
        "AnomalyDashboard" to "Anomaly Detection",
        "AnomalySettings" to "Detection Settings",
        "Profile" to "Profile",
        "ProfileEdit" to "Edit Profile",
        "ImageCrop" to "Crop Image",
        "FolderManagement" to "Manage Folders",
        "CreateFolder" to "Create Folder",
        "EditFolder" to "Edit Folder",
        "FolderSelection" to "Select Folder",
        "VolumeAnalysisDetail" to "Volume Analysis",
        "OneRmDetail" to "1RM Progression", 
        "MuscleGroupDetail" to "Muscle Groups",
        "WorkoutFrequencyDetail" to "Workout Frequency"
    )
    
    // Check if current route is a main tab (should show global top bar)
    val isMainTab = currentRoute?.contains("Home") == true ||
                   currentRoute?.contains("Workout") == true ||
                   currentRoute?.contains("Progress") == true ||
                   currentRoute?.contains("Coach") == true
    
    // Check if current route should show back navigation
    val shouldShowBackNavigation = routeTitles.keys.any { 
        currentRoute?.contains(it) == true 
    }
    
    TopAppBar(
        title = {
            val title = when {
                shouldShowBackNavigation -> {
                    // Find matching title for current route
                    routeTitles.entries.find { (key, _) -> 
                        currentRoute?.contains(key) == true 
                    }?.value ?: "Back"
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
            // Show refresh button specifically for Friends screen
            if (currentRoute?.contains("Friends") == true) {
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
            
            // Show guest mode chip for anonymous users
            com.example.liftrix.ui.guest.GuestModeChip(
                onClick = {
                    navController.navigateToGuestDashboard()
                }
            )
            
            // Show settings button on all screens EXCEPT when already in settings
            if (currentRoute?.contains("Settings") != true) {
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