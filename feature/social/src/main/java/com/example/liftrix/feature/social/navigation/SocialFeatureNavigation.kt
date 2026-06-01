package com.example.liftrix.feature.social.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.liftrix.ui.QRScannerScreen
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.share.ShareWorkoutScreen
import com.example.liftrix.ui.share.ShareWorkoutViewModel
import com.example.liftrix.ui.sharing.TemplateBuddyShareScreen
import com.example.liftrix.ui.sharing.WorkoutShareInboxScreen
import com.example.liftrix.ui.sharing.WorkoutSharedWithYouScreen
import com.example.liftrix.ui.social.FriendsScreen
import com.example.liftrix.ui.social.UserSearchScreen
import com.example.liftrix.ui.social.comments.PostCommentsScreen
import com.example.liftrix.ui.social.gymbuddy.GymBuddyScreen
import com.example.liftrix.ui.social.onboarding.SocialOnboardingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FriendsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToGymBuddy: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit
) {
    FriendsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToUserSearch = onNavigateToUserSearch,
        onNavigateToGymBuddy = onNavigateToGymBuddy,
        onNavigateToUserProfile = onNavigateToUserProfile
    )
}

@Composable
fun UserSearchRoute(onNavigateToProfile: (String) -> Unit) {
    UserSearchScreen(onNavigateToProfile = onNavigateToProfile)
}

@Composable
fun SocialOnboardingRoute(
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit
) {
    SocialOnboardingScreen(
        onNavigateBack = onNavigateBack,
        onComplete = onComplete
    )
}

@Composable
fun ShareWorkoutRoute(
    workoutId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ShareWorkoutViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    ShareWorkoutScreen(
        state = uiState,
        effects = viewModel.effects,
        onNavigateBack = onNavigateBack,
        onTemplateSelected = viewModel::selectTemplate,
        onActionSelected = viewModel::performAction,
        modifier = modifier,
        showTopBar = false
    )
}

@Composable
fun GymBuddyRoute(onNavigateToQrScanner: () -> Unit) {
    GymBuddyScreen(onNavigateToQrScanner = onNavigateToQrScanner)
}

@Composable
fun TemplateBuddyShareRoute(
    templateId: String,
    onNavigateBack: () -> Unit,
    onOpenQrShareMode: () -> Unit
) {
    TemplateBuddyShareScreen(
        templateId = templateId,
        onNavigateBack = onNavigateBack,
        onOpenQrShareMode = onOpenQrShareMode
    )
}

@Composable
fun WorkoutSharedWithYouRoute(
    shareId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    WorkoutSharedWithYouScreen(
        shareId = shareId,
        onNavigateBack = onNavigateBack,
        onSaved = onSaved
    )
}

@Composable
fun WorkoutShareInboxRoute(
    senderId: String,
    onNavigateBack: () -> Unit,
    onOpenShare: (String) -> Unit
) {
    WorkoutShareInboxScreen(
        senderId = senderId,
        onNavigateBack = onNavigateBack,
        onOpenShare = onOpenShare
    )
}

@Composable
fun PostCommentsRoute(
    postId: String,
    onNavigateBack: () -> Unit
) {
    PostCommentsScreen(
        postId = postId,
        onNavigateBack = onNavigateBack
    )
}

fun NavGraphBuilder.socialGraph(navController: NavHostController) {
    composable<LiftrixRoute.Friends> {
        FriendsRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToUserSearch = {
                navController.navigate(LiftrixRoute.UserSearch)
            },
            onNavigateToGymBuddy = {
                navController.navigate(LiftrixRoute.GymBuddy)
            },
            onNavigateToUserProfile = { userId ->
                navController.navigate(LiftrixRoute.PublicProfile(userId))
            }
        )
    }

    composable<LiftrixRoute.UserSearch> {
        UserSearchRoute(
            onNavigateToProfile = { userId ->
                navController.navigate(LiftrixRoute.PublicProfile(userId))
            }
        )
    }

    composable<LiftrixRoute.ShareWorkout> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.ShareWorkout>()
        ShareWorkoutRoute(
            workoutId = route.workoutId,
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.SocialOnboarding> {
        SocialOnboardingRoute(
            onNavigateBack = { navController.popBackStackSafely() },
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
        QRScannerScreen(
            onQrCodeScanned = {
                coroutineScope.launch {
                    delay(900)
                    navController.popBackStackSafely()
                }
            },
            onNavigateBack = { navController.popBackStackSafely() },
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

    composable<LiftrixRoute.PostComments> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.PostComments>()
        PostCommentsRoute(
            postId = route.postId,
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }
}

private fun NavController.popBackStackSafely(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

private fun NavController.clearBackStackAndNavigate(route: LiftrixRoute) {
    navigate(route) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
