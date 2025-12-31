package com.example.liftrix.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.common.LiveSessionBar
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.domain.model.ProgressComparison
import com.example.liftrix.domain.model.ProgressPhoto
import com.example.liftrix.domain.model.BodyPart
import com.example.liftrix.domain.model.PhotoType
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.ui.settings.sync.SyncSettingsViewModel
import com.example.liftrix.ui.share.ShareWorkoutViewModel
import com.example.liftrix.ui.progress.ProgressComparisonViewModel
import com.example.liftrix.ui.workout.WorkoutScreen
import com.example.liftrix.ui.workout.active.RedesignedActiveWorkoutScreen
import com.example.liftrix.ui.workout.create.RedesignedCreateTemplateScreen
import com.example.liftrix.ui.workout.edit.RedesignedEditWorkoutScreen
import com.example.liftrix.ui.workout.custom.CustomExerciseCreationScreen
import com.example.liftrix.ui.workout.custom.CustomExerciseEditScreen
import com.example.liftrix.ui.workout.custom.CustomExerciseListScreen
import com.example.liftrix.ui.progress.ProgressDashboardScreen
import com.example.liftrix.ui.progress.detail.WorkoutFrequencyDetailScreen
import com.example.liftrix.ui.coach.CoachScreen
import com.example.liftrix.ui.settings.account.EmailChangeScreen
import com.example.liftrix.ui.settings.account.PasswordChangeScreen
import com.example.liftrix.ui.settings.account.UsernameChangeScreen
import com.example.liftrix.ui.settings.account.AccountDeletionFlow
import com.example.liftrix.ui.help.HelpScreen
import com.example.liftrix.ui.help.HelpArticleScreen
import com.example.liftrix.ui.support.ContactSupportScreen
import com.example.liftrix.ui.support.SupportTicketScreen
import com.example.liftrix.ui.settings.about.AboutScreen
import com.example.liftrix.ui.settings.legal.PrivacyPolicyScreen
import com.example.liftrix.ui.settings.legal.TermsOfServiceScreen
import com.example.liftrix.ui.settings.legal.CommunityGuidelinesScreen
import com.example.liftrix.ui.chat.ChatbotScreen
import com.example.liftrix.ui.chat.settings.AIChatSettingsScreen
import com.example.liftrix.ui.social.SocialViewModel
import com.example.liftrix.ui.social.SocialEvent
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.ui.components.ConditionalWorkoutFab
import com.example.liftrix.ui.components.WorkoutCreationModal
import com.example.liftrix.ui.navigation.navigateToEditWorkout
import com.example.liftrix.ui.navigation.navigateToWorkout
import com.example.liftrix.ui.navigation.navigateToProfile
import com.example.liftrix.ui.navigation.navigateToProfileEdit
import com.example.liftrix.ui.navigation.navigateToSettings
import com.example.liftrix.ui.navigation.navigateToAnomalySettings
import com.example.liftrix.ui.navigation.navigateToAnomalyDashboard
import com.example.liftrix.ui.navigation.navigateToActiveWorkout
import com.example.liftrix.ui.navigation.navigateToExerciseSelection
import com.example.liftrix.ui.navigation.navigateToImageCrop
import com.example.liftrix.ui.navigation.navigateToGuestDashboard
import com.example.liftrix.ui.navigation.navigateToGuestConversion
import com.example.liftrix.ui.navigation.navigateToAuthSignUp
import com.example.liftrix.ui.navigation.navigateToAuthSignIn
import com.example.liftrix.ui.navigation.navigateToPublicProfile
import com.example.liftrix.ui.navigation.navigateToQRCodeDisplay
import com.example.liftrix.ui.navigation.navigateToFriends
import com.example.liftrix.ui.navigation.navigateAndReplace
import com.example.liftrix.ui.navigation.clearBackStackAndNavigate
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
                            navController = navController,
                            onNavigateToWorkout = {
                                navController.navigateToWorkout()
                            },
                            onNavigateToFriends = {
                                navController.navigateToFriends()
                            },
                            onNavigateToMyWorkouts = {
                                navController.navigate(LiftrixRoute.UserWorkouts)
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
                        },
                        onNavigateToDashboardCustomization = {
                            navController.navigate(LiftrixRoute.DashboardCustomization)
                        }
                    )
                }
                
                composable<LiftrixRoute.Coach> {
                    CoachScreen(navController = navController)
                }
                
                composable<LiftrixRoute.Friends> {
                    com.example.liftrix.ui.social.FriendsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToUserSearch = {
                            navController.navigate(LiftrixRoute.UserSearch)
                        },
                        onNavigateToQRCode = {
                            navController.navigateToQRCodeDisplay()
                        },
                        onNavigateToUserProfile = { userId ->
                            navController.navigateToPublicProfile(userId)
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
                    com.example.liftrix.ui.profile.UserProfileScreen(
                        userId = route.userId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToFollowersList = { userId ->
                            navController.navigate(LiftrixRoute.FollowersList(userId = userId, listType = "FOLLOWERS"))
                        },
                        onNavigateToFollowingList = { userId ->
                            navController.navigate(LiftrixRoute.FollowingList(userId = userId, listType = "FOLLOWING"))
                        },
                        onNavigateToWorkoutDetail = { workoutId ->
                            navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
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
                    // WorkoutDetailsScreen implementation placeholder
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
                            navController.navigate(LiftrixRoute.CustomExerciseCreation)
                        },
                        onManageCustomExercises = {
                            navController.navigate(LiftrixRoute.CustomExerciseList(selectionMode = false))
                        }
                    )
                }
                
                // Custom Exercise Management Routes
                composable<LiftrixRoute.CustomExerciseCreation> {
                    com.example.liftrix.ui.workout.custom.CustomExerciseCreationScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onExerciseCreated = { exerciseId ->
                            // Navigate back to previous screen (likely exercise list) 
                            // The list will refresh to show the newly created exercise
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.CustomExerciseEdit> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.CustomExerciseEdit>()
                    com.example.liftrix.ui.workout.custom.CustomExerciseEditScreen(
                        exerciseId = route.exerciseId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onExerciseUpdated = { exerciseId ->
                            // Stay on the screen or navigate back based on UX needs
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.CustomExerciseList> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.CustomExerciseList>()
                    com.example.liftrix.ui.workout.custom.CustomExerciseListScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onCreateExercise = {
                            navController.navigate(LiftrixRoute.CustomExerciseCreation)
                        },
                        onEditExercise = { exerciseId ->
                            navController.navigate(LiftrixRoute.CustomExerciseEdit(exerciseId))
                        },
                        onExerciseSelected = if (route.selectionMode) { exerciseId ->
                            // Pass selected custom exercise back to previous screen
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                "selected_custom_exercise", 
                                exerciseId
                            )
                            navController.popBackStackSafely()
                        } else null
                    )
                }
                
                composable<LiftrixRoute.ActiveWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ActiveWorkout>()
                    com.example.liftrix.ui.workout.active.RedesignedActiveWorkoutScreen(
                        navController = navController,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToExerciseLibrary = {
                            navController.navigateToExerciseSelection()
                        },
                        onNavigateToPostCreation = { workoutId ->
                            navController.navigate(LiftrixRoute.PostCreation(workoutId))
                        },
                        onNavigateToPostWorkoutSummary = { workoutId ->
                            navController.navigate(LiftrixRoute.PostWorkoutSummary(workoutId))
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
                        navBackStackEntry = backStackEntry,
                        initialFolderId = route.folderId
                    )
                }
                
                composable<LiftrixRoute.ExerciseDetails> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ExerciseDetails>()
                    // ExerciseDetailsScreen implementation placeholder
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
                            navController.navigateToProfileEdit()
                        },
                        onNavigateToAuth = {
                            // Authentication navigation placeholder
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
                        },
                        onNavigateToNotifications = {
                            navController.navigate(LiftrixRoute.NotificationSettings)
                        },
                        onNavigateToEmailChange = {
                            navController.navigate(LiftrixRoute.EmailChange)
                        },
                        onNavigateToPasswordChange = {
                            navController.navigate(LiftrixRoute.PasswordChange)
                        },
                        onNavigateToUsernameChange = {
                            navController.navigate(LiftrixRoute.UsernameChange)
                        },
                        onNavigateToAccountDeletion = {
                            navController.navigate(LiftrixRoute.AccountDeletion)
                        },
                        onNavigateToHelpCenter = {
                            navController.navigate(LiftrixRoute.HelpCenter)
                        },
                        onNavigateToContactSupport = {
                            navController.navigate(LiftrixRoute.ContactSupport)
                        },
                        onNavigateToAbout = {
                            navController.navigate(LiftrixRoute.About)
                        },
                        onNavigateToPrivacyPolicy = {
                            navController.navigate(LiftrixRoute.PrivacyPolicy)
                        },
                        onNavigateToTermsOfService = {
                            navController.navigate(LiftrixRoute.TermsOfService)
                        },
                        onNavigateToAIDisclaimer = {
                            navController.navigate(LiftrixRoute.AIDisclaimer)
                        },
                        onNavigateToCommunityGuidelines = {
                            navController.navigate(LiftrixRoute.CommunityGuidelines)
                        },
                        onNavigateToContentModerationPolicy = {
                            navController.navigate(LiftrixRoute.ContentModerationPolicy)
                        },
                        onNavigateToRefundSubscriptionPolicy = {
                            navController.navigate(LiftrixRoute.RefundSubscriptionPolicy)
                        },
                        onNavigateToDataPortability = {
                            navController.navigate(LiftrixRoute.DataPortability)
                        },
                        onNavigateToAIChatSettings = {
                            navController.navigate(LiftrixRoute.AIChatSettings)
                        },
                        onNavigateToAdminBanManagement = {
                            navController.navigate(LiftrixRoute.AdminBanManagement)
                        },
                        onNavigateToUpgradeToPremium = {
                            navController.navigate(LiftrixRoute.UpgradeToPremium)
                        }
                    )
                }
                
                composable<LiftrixRoute.SyncSettings> {
                    val authQueryUseCase: AuthQueryUseCase = hiltViewModel<SyncSettingsViewModel>().authQueryUseCase
                    var currentUserId by remember { mutableStateOf<String?>(null) }
                    var isLoading by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        val result = authQueryUseCase(waitForAuth = false)
                        currentUserId = result.fold(
                            onSuccess = { it.value },
                            onFailure = { null }
                        )
                        isLoading = false
                    }
                    
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        currentUserId != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                TopAppBar(
                                    title = { Text("Sync Settings") },
                                    navigationIcon = {
                                        IconButton(onClick = { navController.popBackStackSafely() }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                        }
                                    }
                                )
                                
                                com.example.liftrix.ui.common.sync.SettingsSyncIntegration(
                                    userId = currentUserId!!
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Authentication required")
                            }
                        }
                    }
                }
                
                composable<LiftrixRoute.WidgetSettings> {
                    com.example.liftrix.ui.settings.WidgetSettingsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // Account Management Routes (Added for SPEC-20250116-account-management)
                composable<LiftrixRoute.EmailChange> {
                    EmailChangeScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.PasswordChange> {
                    PasswordChangeScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.UsernameChange> {
                    UsernameChangeScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.AccountDeletion> {
                    AccountDeletionFlow(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onDeletionCompleted = {
                            // Navigate to auth/onboarding after successful account deletion
                            navController.clearBackStackAndNavigate(LiftrixRoute.AuthSignIn)
                        }
                    )
                }
                
                // Help and Support System Routes (Added for SPEC-20250116-app-information)
                composable<LiftrixRoute.HelpCenter> {
                    com.example.liftrix.ui.help.HelpScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToArticle = { articleId ->
                            navController.navigate(LiftrixRoute.HelpArticle(articleId))
                        },
                        onNavigateToSupport = {
                            navController.navigate(LiftrixRoute.ContactSupport)
                        }
                    )
                }
                
                composable<LiftrixRoute.HelpArticle> { backStackEntry ->
                    val articleRoute = backStackEntry.toRoute<LiftrixRoute.HelpArticle>()
                    com.example.liftrix.ui.help.HelpArticleScreen(
                        articleId = articleRoute.articleId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.ContactSupport> {
                    com.example.liftrix.ui.support.ContactSupportScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToTicket = { ticketId ->
                            navController.navigate(LiftrixRoute.SupportTicket(ticketId))
                        }
                    )
                }
                
                composable<LiftrixRoute.SupportTicket> { backStackEntry ->
                    val ticketRoute = backStackEntry.toRoute<LiftrixRoute.SupportTicket>()
                    com.example.liftrix.ui.support.SupportTicketScreen(
                        ticketId = ticketRoute.ticketId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.About> {
                    com.example.liftrix.ui.settings.about.AboutScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToPrivacy = {
                            navController.navigate(LiftrixRoute.PrivacyPolicy)
                        },
                        onNavigateToTerms = {
                            navController.navigate(LiftrixRoute.TermsOfService)
                        },
                        onNavigateToLicenses = {
                            // Since there's no specific licenses route, navigate to Help Center
                            // or implement a simple dialog/bottom sheet for licenses
                            navController.navigate(LiftrixRoute.HelpCenter)
                        }
                    )
                }
                
                composable<LiftrixRoute.PrivacyPolicy> {
                    com.example.liftrix.ui.settings.legal.PrivacyPolicyScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.TermsOfService> {
                    com.example.liftrix.ui.settings.legal.TermsOfServiceScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.AIDisclaimer> {
                    com.example.liftrix.ui.settings.legal.AIDisclaimerScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.CommunityGuidelines> {
                    CommunityGuidelinesScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.ContentModerationPolicy> {
                    com.example.liftrix.ui.settings.legal.ContentModerationPolicyScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.RefundSubscriptionPolicy> {
                    com.example.liftrix.ui.settings.legal.RefundSubscriptionPolicyScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.DataPortability> {
                    com.example.liftrix.ui.settings.data.DataPortabilityScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.AIChatbot> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.AIChatbot>()
                    ChatbotScreen(
                        conversationId = route.conversationId,
                        initialWorkoutContext = route.workoutContext,
                        onNavigateBack = { navController.popBackStackSafely() }
                    )
                }
                
                composable<LiftrixRoute.AIChatSettings> {
                    AIChatSettingsScreen(
                        onNavigateBack = { navController.popBackStackSafely() }
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
                        userId = "", // Authentication user ID placeholder
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
                                // Type-safe navigation conversion placeholder
                                navController.popBackStackSafely()
                            } else {
                                navController.popBackStackSafely()
                            }
                        }
                    )
                }
                
                // Authentication Routes
                composable<LiftrixRoute.AuthSignUp> {
                    // AuthSignUpScreen implementation placeholder
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
                    // AuthSignInScreen implementation placeholder
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
                    
                    // Listen for new exercise addition from navigation
                    LaunchedEffect(backStackEntry.savedStateHandle) {
                        backStackEntry.savedStateHandle.getStateFlow<com.example.liftrix.domain.model.ExerciseLibrary?>(
                            "selected_exercise", null
                        ).collect { selectedExercise ->
                            if (selectedExercise != null) {
                                editWorkoutViewModel.handleEvent(com.example.liftrix.ui.workout.edit.EditWorkoutEvent.AddExercise(selectedExercise.id))
                                // Clear the saved state to prevent re-triggering
                                backStackEntry.savedStateHandle.remove<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
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
                        onNavigateToPostCreation = { workoutId ->
                            navController.navigate(LiftrixRoute.PostCreation(workoutId)) {
                                // Pop the edit screen from the back stack
                                popUpTo(LiftrixRoute.EditWorkout(workoutId)) {
                                    inclusive = true
                                }
                            }
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
                
                // Social System Routes (Added for social system completion)
                
                // Post-workout summary screen
                composable<LiftrixRoute.PostWorkoutSummary> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.PostWorkoutSummary>()
                    com.example.liftrix.ui.workout.completion.PostWorkoutSummaryScreen(
                        workoutId = route.workoutId,
                        navController = navController
                    )
                }
                
                // Workout details screen  
                composable<LiftrixRoute.WorkoutDetails> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.WorkoutDetails>()
                    com.example.liftrix.ui.workout.details.WorkoutDetailsScreen(
                        workoutId = route.workoutId,
                        navController = navController
                    )
                }
                
                composable<LiftrixRoute.ShareWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ShareWorkout>()
                    ShareWorkoutContainer(
                        workoutId = route.workoutId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.ProgressComparison> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ProgressComparison>()
                    ProgressComparisonContainer(
                        comparisonId = route.comparisonId,
                        shareMode = route.shareMode,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // SocialFeed route removed - feed is now integrated into Home screen
                // If you need to navigate to the feed, use LiftrixRoute.Home instead
                
                composable<LiftrixRoute.NotificationSettings> {
                    com.example.liftrix.ui.settings.NotificationSettingsScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.GymBuddy> {
                    com.example.liftrix.ui.social.gymbuddy.GymBuddyScreen(
                        onNavigateToQrScanner = {
                            // QR Scanner navigation placeholder
                        }
                    )
                }
                
                composable<LiftrixRoute.UserWorkouts> {
                    com.example.liftrix.ui.workouts.UserWorkoutsScreen(
                        navController = navController
                    )
                }
                
                composable<LiftrixRoute.PostCreation> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.PostCreation>()
                    com.example.liftrix.ui.workout.completion.PostCreationScreen(
                        workoutId = route.workoutId,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onPostCreated = { postId ->
                            // Navigate to home screen where the social feed is now displayed
                            navController.navigate(LiftrixRoute.Home) {
                                // Clear back to home to avoid complex back stack
                                popUpTo(LiftrixRoute.Home) {
                                    inclusive = true
                                }
                            }
                        }
                    )
                }
                
                composable<LiftrixRoute.PostComments> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.PostComments>()
                    com.example.liftrix.ui.social.comments.PostCommentsScreen(
                        postId = route.postId,
                        onNavigateBack = { navController.popBackStackSafely() }
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
                
                // Social Follow System Routes
                composable<LiftrixRoute.FollowersList> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.FollowersList>()
                    val listType = when (route.listType) {
                        "FOLLOWERS" -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWERS
                        "FOLLOWING" -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWING
                        "PENDING_REQUESTS" -> com.example.liftrix.ui.profile.FollowerListType.PENDING_REQUESTS
                        else -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWERS
                    }

                    com.example.liftrix.ui.profile.FollowerListScreen(
                        userId = route.userId,
                        listType = listType,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToProfile = { userId ->
                            navController.navigateToPublicProfile(userId)
                        }
                    )
                }

                composable<LiftrixRoute.FollowingList> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.FollowingList>()
                    val listType = when (route.listType) {
                        "FOLLOWERS" -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWERS
                        "FOLLOWING" -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWING
                        "PENDING_REQUESTS" -> com.example.liftrix.ui.profile.FollowerListType.PENDING_REQUESTS
                        else -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWING
                    }

                    com.example.liftrix.ui.profile.FollowerListScreen(
                        userId = route.userId,
                        listType = listType,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToProfile = { userId ->
                            navController.navigateToPublicProfile(userId)
                        }
                    )
                }
                
                // Admin System Routes (Admin-only access)
                composable<LiftrixRoute.AdminBanManagement> {
                    com.example.liftrix.ui.admin.AdminBanManagementScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // Subscription Management Routes
                composable<LiftrixRoute.UpgradeToPremium> {
                    com.example.liftrix.ui.settings.upgrade.UpgradeToPremiumScreen(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onContactSupport = {
                            navController.navigate(LiftrixRoute.ContactSupport)
                        }
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
        BottomNavItem(LiftrixRoute.Progress, "Progress", Icons.Default.TrendingUp)
        // Temporarily hidden: BottomNavItem(LiftrixRoute.Coach, "Coach", Icons.Default.Psychology)
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
        "CustomExerciseCreation" to "Create Exercise",
        "CustomExerciseEdit" to "Edit Exercise", 
        "CustomExerciseList" to "My Exercises",
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
        "WorkoutFrequencyDetail" to "Workout Frequency",
        // Social System Routes (Added for social system completion)
        "ShareWorkout" to "Share Workout",
        "ProgressComparison" to "Progress Comparison",
        "SocialFeed" to "Social Feed",
        "NotificationSettings" to "Notification Settings",
        "GymBuddy" to "Gym Buddy",
        "PostCreation" to "Create Post",
        "PostComments" to "Comments",
        "FollowersList" to "Followers",
        "FollowingList" to "Following",
        "PublicProfile" to "Profile"
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
            // Show actions specific to PublicProfile screen
            // Note: Profile-specific actions are handled within the screen itself
            // to maintain proper ViewModel scoping
            
            // Show search button specifically for Friends screen
            if (currentRoute?.contains("Friends") == true) {
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
            
            // Show guest mode chip for anonymous users
            com.example.liftrix.ui.guest.GuestModeChip(
                onClick = {
                    navController.navigateToGuestDashboard()
                }
            )
            
            // Show social button on all screens EXCEPT when already in social
            if (currentRoute?.contains("Friends") != true) {
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

/**
 * Container composable for ShareWorkout screen that bridges the gap between
 * navigation parameters (workoutId) and the actual screen requirements (ShareableContent).
 */
@Composable
private fun ShareWorkoutContainer(
    workoutId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Load workout data and convert to shareable content
    val viewModel: ShareWorkoutViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }
    
    val shareableContent = uiState.shareableContent ?: ShareableContent(
        id = workoutId,
        type = ShareableContentType.WORKOUT,
        title = "Loading workout...",
        subtitle = "",
        stats = emptyMap(),
        imageUrl = null,
        userAvatar = null,
        metadata = emptyMap()
    )
    
    val shareUrl = "https://liftrix.app/share/workout/$workoutId"
    
    com.example.liftrix.ui.share.ShareWorkoutScreen(
        workoutContent = shareableContent,
        shareUrl = shareUrl,
        onNavigateBack = onNavigateBack,
        onShareToPlatform = { platform, message ->
            // Implement platform-specific sharing
            viewModel.shareWorkout(platform, message ?: "", shareUrl)
        },
        onGenerateQRCode = {
            // Generate QR code for workout sharing
            viewModel.generateQRCode(shareUrl)
        },
        modifier = modifier
    )
}

/**
 * Container composable for ProgressComparison screen that bridges the gap between
 * navigation parameters and the actual screen requirements.
 */
@Composable  
private fun ProgressComparisonContainer(
    comparisonId: String,
    shareMode: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Load progress comparison data
    val viewModel: ProgressComparisonViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(comparisonId) {
        viewModel.loadComparison(comparisonId, shareMode)
    }
    
    val comparison = uiState.comparison ?: run {
        // Fallback placeholder while loading
        val currentTime = System.currentTimeMillis()
        val placeholderBeforePhoto = ProgressPhoto(
            id = "before_photo_$comparisonId",
            userId = "",
            mediaId = "placeholder_media_before",
            bodyPart = BodyPart.FULL_BODY,
            photoType = PhotoType.FRONT,
            isPrivate = !shareMode,
            takenAt = currentTime - (4 * 7 * 24 * 60 * 60 * 1000), // 4 weeks ago
            createdAt = currentTime
        )
        val placeholderAfterPhoto = ProgressPhoto(
            id = "after_photo_$comparisonId",
            userId = "",
            mediaId = "placeholder_media_after",
            bodyPart = BodyPart.FULL_BODY,
            photoType = PhotoType.FRONT,
            isPrivate = !shareMode,
            takenAt = currentTime,
            createdAt = currentTime
        )
        ProgressComparison(
            id = comparisonId,
            userId = "",
            name = "Loading...",
            bodyPart = BodyPart.FULL_BODY,
            beforePhoto = placeholderBeforePhoto,
            afterPhoto = placeholderAfterPhoto,
            timeDifferenceWeeks = 4,
            createdAt = currentTime
        )
    }
    
    com.example.liftrix.ui.progress.ProgressComparisonView(
        comparison = comparison,
        modifier = modifier,
        onImageTap = { photo ->
            // Handle image tap - open in full screen viewer
            // For now, just log the action since the methods don't exist yet
            timber.log.Timber.d("Image tap requested for photo: ${photo.id}")
        },
        onComparisonModeToggle = {
            // Handle comparison mode toggle (side-by-side vs overlay)
            // For now, just log the action since the methods don't exist yet
            timber.log.Timber.d("Comparison mode toggle requested")
        }
    )
}
