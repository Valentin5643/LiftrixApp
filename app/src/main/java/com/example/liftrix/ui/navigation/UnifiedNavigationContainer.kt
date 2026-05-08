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
import androidx.compose.material.icons.filled.Psychology
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
import com.example.liftrix.BuildConfig
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.feature.auth.navigation.AuthRoute
import com.example.liftrix.feature.auth.navigation.GuestConversionRoute
import com.example.liftrix.feature.auth.navigation.GuestDashboardRoute
import com.example.liftrix.feature.auth.navigation.GuestModeChipRoute
import com.example.liftrix.feature.auth.navigation.GuestModeSelectionRoute
import com.example.liftrix.feature.auth.navigation.GuestSessionIndicatorRoute
import com.example.liftrix.feature.auth.navigation.OnboardingRoute
import com.example.liftrix.feature.chat.navigation.AIChatSettingsRoute
import com.example.liftrix.feature.chat.navigation.ChatbotRoute
import com.example.liftrix.feature.chat.navigation.CoachRoute
import com.example.liftrix.feature.home.navigation.HomeRoute
import com.example.liftrix.feature.profile.navigation.FollowerListRoute
import com.example.liftrix.feature.profile.navigation.ImageCropRoute
import com.example.liftrix.feature.profile.navigation.ProfileEditRoute
import com.example.liftrix.feature.profile.navigation.UserProfileRoute
import com.example.liftrix.feature.social.navigation.FriendsRoute
import com.example.liftrix.feature.social.navigation.GymBuddyRoute
import com.example.liftrix.feature.social.navigation.PostCommentsRoute
import com.example.liftrix.feature.social.navigation.ShareWorkoutRoute
import com.example.liftrix.feature.social.navigation.SocialOnboardingRoute
import com.example.liftrix.feature.social.navigation.TemplateBuddyShareRoute
import com.example.liftrix.feature.social.navigation.UserSearchRoute
import com.example.liftrix.feature.social.navigation.WorkoutShareInboxRoute
import com.example.liftrix.feature.social.navigation.WorkoutSharedWithYouRoute
import com.example.liftrix.feature.workout.navigation.ActiveWorkoutRoute
import com.example.liftrix.feature.workout.navigation.CreateWorkoutRoute
import com.example.liftrix.feature.workout.navigation.CustomExerciseCreationRoute
import com.example.liftrix.feature.workout.navigation.CustomExerciseEditRoute
import com.example.liftrix.feature.workout.navigation.CustomExerciseListRoute
import com.example.liftrix.feature.workout.navigation.EditSessionRoute
import com.example.liftrix.feature.workout.navigation.EditWorkoutRoute
import com.example.liftrix.feature.workout.navigation.ExerciseSelectionRoute
import com.example.liftrix.feature.workout.navigation.PostCreationRoute
import com.example.liftrix.feature.workout.navigation.PostWorkoutSummaryRoute
import com.example.liftrix.feature.workout.navigation.TemplateCreationRoute
import com.example.liftrix.feature.workout.navigation.UserWorkoutsRoute
import com.example.liftrix.feature.workout.navigation.WorkoutDetailsRoute
import com.example.liftrix.feature.workout.navigation.WorkoutRoute
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.ui.settings.sync.SyncSettingsViewModel
import com.example.liftrix.feature.progress.navigation.AnomalyDashboardRoute
import com.example.liftrix.feature.progress.navigation.AnomalySettingsRoute
import com.example.liftrix.feature.progress.navigation.ExerciseRankingDetailRoute
import com.example.liftrix.feature.progress.navigation.MuscleGroupDetailRoute
import com.example.liftrix.feature.progress.navigation.OneRmDetailRoute
import com.example.liftrix.feature.progress.navigation.ProgressComparisonRoute
import com.example.liftrix.feature.progress.navigation.ProgressDashboardRoute
import com.example.liftrix.feature.progress.navigation.VolumeAnalysisDetailRoute
import com.example.liftrix.feature.progress.navigation.WorkoutFrequencyDetailRoute
import com.example.liftrix.feature.settings.navigation.AIDisclaimerRoute
import com.example.liftrix.feature.settings.navigation.AboutRoute
import com.example.liftrix.feature.settings.navigation.AccountDeletionRoute
import com.example.liftrix.feature.settings.navigation.AdminBanManagementRoute
import com.example.liftrix.feature.settings.navigation.CommunityGuidelinesRoute
import com.example.liftrix.feature.settings.navigation.ContactSupportRoute
import com.example.liftrix.feature.settings.navigation.ContentModerationPolicyRoute
import com.example.liftrix.feature.settings.navigation.DashboardCustomizationRoute
import com.example.liftrix.feature.settings.navigation.DataPortabilityRoute
import com.example.liftrix.feature.settings.navigation.EmailChangeRoute
import com.example.liftrix.feature.settings.navigation.HelpArticleRoute
import com.example.liftrix.feature.settings.navigation.HelpCenterRoute
import com.example.liftrix.feature.settings.navigation.NotificationSettingsRoute
import com.example.liftrix.feature.settings.navigation.PasswordChangeRoute
import com.example.liftrix.feature.settings.navigation.PrivacySettingsRoute
import com.example.liftrix.feature.settings.navigation.PrivacyPolicyRoute
import com.example.liftrix.feature.settings.navigation.RefundSubscriptionPolicyRoute
import com.example.liftrix.feature.settings.navigation.SettingsRoute
import com.example.liftrix.feature.settings.navigation.SupportTicketRoute
import com.example.liftrix.feature.settings.navigation.TermsOfServiceRoute
import com.example.liftrix.feature.settings.navigation.UpgradeToPremiumRoute
import com.example.liftrix.feature.settings.navigation.UsernameChangeRoute
import com.example.liftrix.feature.settings.navigation.WidgetSettingsRoute
import com.example.liftrix.service.UnifiedWorkoutSessionManager
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
                        GuestSessionIndicatorRoute(
                            onClick = {
                                navController.navigateToGuestConversion(source = "nudge")
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        HomeRoute(
                            onNavigateToWorkout = { _ ->
                                navController.navigateToWorkout()
                            },
                            onNavigateToFriends = {
                                navController.navigateToFriends()
                            },
                            onNavigateToMyWorkouts = {
                                navController.navigate(LiftrixRoute.UserWorkouts)
                            },
                            onNavigateToPublicProfile = { userId ->
                                navController.navigateToPublicProfile(userId)
                            },
                            onNavigateToUserSearch = {
                                navController.navigate(LiftrixRoute.UserSearch)
                            },
                            onNavigateToPostComments = { postId ->
                                navController.navigate(LiftrixRoute.PostComments(postId))
                            },
                            onNavigateToWorkoutDetails = { workoutId ->
                                navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
                            },
                            onNavigateToEditWorkout = { workoutId ->
                                navController.navigateToEditWorkout(workoutId)
                            },
                            syncStatusContent = {
                                val syncStatusViewModel = hiltViewModel<com.example.liftrix.ui.common.sync.SyncStatusViewModel>()
                                val syncStatus by syncStatusViewModel.syncStatus.collectAsState(
                                    initial = com.example.liftrix.domain.service.SyncStatus.Idle
                                )

                                com.example.liftrix.ui.common.sync.SyncStatusIndicator(
                                    syncStatus = syncStatus,
                                    showText = true,
                                    autoHideSuccess = true,
                                    contentDescription = "Firebase sync status"
                                )
                            }
                        )
                    }
                }
                
                composable<LiftrixRoute.Workout> {
                    WorkoutRoute(
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
                        },
                        onNavigateToTemplateBuddyShare = { templateId ->
                            navController.navigate(LiftrixRoute.TemplateBuddyShare(templateId))
                        }
                    )
                }
                
                composable<LiftrixRoute.Progress> {
                    ProgressDashboardRoute(
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
                    CoachRoute(navController = navController)
                }
                
                composable<LiftrixRoute.Friends> {
                    FriendsRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToUserSearch = {
                            navController.navigate(LiftrixRoute.UserSearch)
                        },
                        onNavigateToGymBuddy = {
                            navController.navigate(LiftrixRoute.GymBuddy)
                        },
                        onNavigateToUserProfile = { userId ->
                            navController.navigateToPublicProfile(userId)
                        }
                    )
                }
                
                // Social Discovery Routes
                composable<LiftrixRoute.UserSearch> {
                    UserSearchRoute(
                        onNavigateToProfile = { userId ->
                            navController.navigateToPublicProfile(userId)
                        }
                    )
                }
                
                composable<LiftrixRoute.PublicProfile> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.PublicProfile>()
                    UserProfileRoute(
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
                
                composable<LiftrixRoute.ExerciseSelection> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ExerciseSelection>()
                    ExerciseSelectionRoute(
                        isForTemplate = route.isForTemplate,
                        replaceExerciseIndex = route.replaceExerciseIndex,
                        backStackEntry = navController.previousBackStackEntry ?: backStackEntry,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onSessionExerciseSelected = { exerciseLibrary ->
                            viewModel.addExerciseToCurrentSession(exerciseLibrary)
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
                    CustomExerciseCreationRoute(
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
                    CustomExerciseEditRoute(
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
                    CustomExerciseListRoute(
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
                    ActiveWorkoutRoute(
                        navController = navController,
                        isBlankWorkout = route.isBlankWorkout,
                        templateId = route.templateId,
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
                        }
                    )
                }
                
                composable<LiftrixRoute.TemplateCreation> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.TemplateCreation>()
                    
                    TemplateCreationRoute(
                        initialFolderId = route.folderId,
                        navBackStackEntry = backStackEntry,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToExerciseSelection = {
                            navController.navigateToExerciseSelection(isForTemplate = true)
                        }
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
                    SettingsRoute(
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
                    WidgetSettingsRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // Account Management Routes (Added for SPEC-20250116-account-management)
                composable<LiftrixRoute.EmailChange> {
                    EmailChangeRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.PasswordChange> {
                    PasswordChangeRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.UsernameChange> {
                    UsernameChangeRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.AccountDeletion> {
                    AccountDeletionRoute(
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
                    HelpCenterRoute(
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
                    HelpArticleRoute(
                        articleId = articleRoute.articleId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.ContactSupport> {
                    ContactSupportRoute(
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
                    SupportTicketRoute(
                        ticketId = ticketRoute.ticketId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.About> {
                    AboutRoute(
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
                    PrivacyPolicyRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.TermsOfService> {
                    TermsOfServiceRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.AIDisclaimer> {
                    AIDisclaimerRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.CommunityGuidelines> {
                    CommunityGuidelinesRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.ContentModerationPolicy> {
                    ContentModerationPolicyRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.RefundSubscriptionPolicy> {
                    RefundSubscriptionPolicyRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.DataPortability> {
                    DataPortabilityRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.AIChatbot> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.AIChatbot>()
                    ChatbotRoute(
                        conversationId = route.conversationId,
                        initialWorkoutContext = route.workoutContext,
                        onNavigateBack = { navController.popBackStackSafely() }
                    )
                }
                
                composable<LiftrixRoute.AIChatSettings> {
                    AIChatSettingsRoute(
                        onNavigateBack = { navController.popBackStackSafely() }
                    )
                }
                
                composable<LiftrixRoute.AnomalyDashboard> {
                    AnomalyDashboardRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToSettings = {
                            navController.navigateToAnomalySettings()
                        }
                    )
                }
                
                composable<LiftrixRoute.AnomalySettings> {
                    AnomalySettingsRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.Onboarding> {
                    OnboardingRoute(
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
                    GuestModeSelectionRoute(
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
                    GuestDashboardRoute(
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
                    GuestConversionRoute(
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
                    AuthRoute(
                        initialSignUpMode = true,
                        googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
                        onAuthSuccess = {
                            navController.clearBackStackAndNavigate(LiftrixRoute.Home)
                        }
                    )
                }
                
                composable<LiftrixRoute.AuthSignIn> {
                    AuthRoute(
                        initialSignUpMode = false,
                        googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
                        onAuthSuccess = {
                            navController.clearBackStackAndNavigate(LiftrixRoute.Home)
                        }
                    )
                }
                
                // Workout Editing Routes
                composable<LiftrixRoute.EditWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.EditWorkout>()
                    EditWorkoutRoute(
                        workoutId = route.workoutId,
                        backStackEntry = backStackEntry,
                        onNavigateBack = { navController.popBackStackSafely() },
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
                    )
                }
                
                composable<LiftrixRoute.EditSession> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.EditSession>()
                    EditSessionRoute(
                        sessionId = route.sessionId,
                        onNavigateBack = { navController.popBackStackSafely() }
                    )
                }
                
                composable<LiftrixRoute.CreateWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.CreateWorkout>()
                    CreateWorkoutRoute(
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
                    ProfileEditRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onNavigateToImageCrop = { uri ->
                            navController.navigateToImageCrop(uri)
                        }
                    )
                }

                composable<LiftrixRoute.ProfileEdit> {
                    ProfileEditRoute(
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
                    ImageCropRoute(
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
                    PostWorkoutSummaryRoute(
                        workoutId = route.workoutId,
                        navController = navController,
                        onNavigateToWorkoutDetails = { workoutId ->
                            navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
                        },
                        onNavigateToPostCreation = { workoutId ->
                            navController.navigate(LiftrixRoute.PostCreation(workoutId))
                        },
                        onNavigateHome = {
                            navController.navigate(LiftrixRoute.Home) {
                                popUpTo(LiftrixRoute.Home) {
                                    inclusive = false
                                }
                            }
                        }
                    )
                }
                
                // Workout details screen  
                composable<LiftrixRoute.WorkoutDetails> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.WorkoutDetails>()
                    WorkoutDetailsRoute(
                        workoutId = route.workoutId,
                        navController = navController,
                        onNavigateToEditWorkout = { workoutId ->
                            navController.navigate(LiftrixRoute.EditWorkout(workoutId))
                        },
                        onNavigateToShareWorkout = { workoutId ->
                            navController.navigate(LiftrixRoute.ShareWorkout(workoutId))
                        }
                    )
                }
                
                composable<LiftrixRoute.ShareWorkout> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ShareWorkout>()
                    ShareWorkoutRoute(
                        workoutId = route.workoutId,
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                composable<LiftrixRoute.ProgressComparison> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.ProgressComparison>()
                    ProgressComparisonRoute(
                        comparisonId = route.comparisonId,
                        shareMode = route.shareMode
                    )
                }
                
                composable<LiftrixRoute.NotificationSettings> {
                    NotificationSettingsRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.DashboardCustomization> {
                    DashboardCustomizationRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.PrivacySettings> {
                    PrivacySettingsRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }

                composable<LiftrixRoute.SocialOnboarding> {
                    SocialOnboardingRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onComplete = {
                            navController.clearBackStackAndNavigate(LiftrixRoute.Friends)
                        }
                    )
                }
                
                composable<LiftrixRoute.GymBuddy> {
                    GymBuddyRoute(
                        onNavigateToQrScanner = {
                            navController.navigate(LiftrixRoute.QRScanner)
                        }
                    )
                }

                composable<LiftrixRoute.QRScanner> {
                    val coroutineScope = rememberCoroutineScope()
                    com.example.liftrix.ui.QRScannerScreen(
                        onQrCodeScanned = {
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(900)
                                navController.popBackStackSafely()
                            }
                        },
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        },
                        onTemplateShareFound = { shareId ->
                            navController.navigate(LiftrixRoute.WorkoutSharedWithYou(shareId))
                        },
                        onMultipleTemplateSharesFound = { senderId ->
                            navController.navigate(LiftrixRoute.WorkoutShareInbox(senderId))
                        }
                    )
                }

                composable<LiftrixRoute.TemplateBuddyShare> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.TemplateBuddyShare>()
                    TemplateBuddyShareRoute(
                        templateId = route.templateId,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onOpenQrShareMode = { navController.navigate(LiftrixRoute.GymBuddy) }
                    )
                }

                composable<LiftrixRoute.WorkoutSharedWithYou> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.WorkoutSharedWithYou>()
                    WorkoutSharedWithYouRoute(
                        shareId = route.shareId,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onSaved = {
                            navController.popBackStack(LiftrixRoute.Workout, inclusive = false)
                        }
                    )
                }

                composable<LiftrixRoute.WorkoutShareInbox> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.WorkoutShareInbox>()
                    WorkoutShareInboxRoute(
                        senderId = route.senderId,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onOpenShare = { shareId ->
                            navController.navigate(LiftrixRoute.WorkoutSharedWithYou(shareId))
                        }
                    )
                }
                
                composable<LiftrixRoute.UserWorkouts> {
                    UserWorkoutsRoute(
                        onNavigateBack = { navController.popBackStackSafely() },
                        onNavigateToWorkoutDetails = { workoutId ->
                            navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
                        },
                        onNavigateToPostComments = { postId ->
                            navController.navigate(LiftrixRoute.PostComments(postId))
                        },
                        onNavigateToProfile = {
                            navController.navigate(LiftrixRoute.Profile())
                        },
                        onNavigateToCreateWorkout = {
                            navController.navigate(LiftrixRoute.CreateWorkout(folderId = null))
                        },
                        onNavigateToEditWorkout = { workoutId ->
                            navController.navigate(LiftrixRoute.EditWorkout(workoutId))
                        }
                    )
                }
                
                composable<LiftrixRoute.PostCreation> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.PostCreation>()
                    PostCreationRoute(
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
                    PostCommentsRoute(
                        postId = route.postId,
                        onNavigateBack = { navController.popBackStackSafely() }
                    )
                }
                
                // Analytics Detail Screen Routes
                composable<LiftrixRoute.VolumeAnalysisDetail> {
                    VolumeAnalysisDetailRoute(navController = navController)
                }
                
                composable<LiftrixRoute.OneRmDetail> {
                    OneRmDetailRoute(navController = navController)
                }
                
                composable<LiftrixRoute.MuscleGroupDetail> {
                    MuscleGroupDetailRoute(navController = navController)
                }
                
                composable<LiftrixRoute.ExerciseRankingDetail> {
                    ExerciseRankingDetailRoute(navController = navController)
                }
                
                composable<LiftrixRoute.WorkoutFrequencyDetail> {
                    WorkoutFrequencyDetailRoute(navController = navController)
                }
                
                // Social Follow System Routes
                composable<LiftrixRoute.FollowersList> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiftrixRoute.FollowersList>()
                    FollowerListRoute(
                        userId = route.userId,
                        rawListType = route.listType,
                        fallbackListType = "FOLLOWERS",
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
                    FollowerListRoute(
                        userId = route.userId,
                        rawListType = route.listType,
                        fallbackListType = "FOLLOWING",
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
                    AdminBanManagementRoute(
                        onNavigateBack = {
                            navController.popBackStackSafely()
                        }
                    )
                }
                
                // Subscription Management Routes
                composable<LiftrixRoute.UpgradeToPremium> {
                    UpgradeToPremiumRoute(
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
    val items = buildList {
        add(BottomNavItem(LiftrixRoute.Home, "Home", Icons.Default.Home))
        add(BottomNavItem(LiftrixRoute.Workout, "Workout", Icons.Default.FitnessCenter))
        add(BottomNavItem(LiftrixRoute.Progress, "Progress", Icons.Default.TrendingUp))
        add(BottomNavItem(LiftrixRoute.AIChatbot(), "AI", Icons.Default.Psychology))
    }
    
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
            GuestModeChipRoute(
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
