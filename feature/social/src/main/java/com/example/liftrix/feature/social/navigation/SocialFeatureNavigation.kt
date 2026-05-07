package com.example.liftrix.feature.social.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
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

    ShareWorkoutScreen(
        workoutContent = shareableContent,
        shareUrl = shareUrl,
        onNavigateBack = onNavigateBack,
        onShareToPlatform = { platform, message ->
            viewModel.shareWorkout(platform, message ?: "", shareUrl)
        },
        onGenerateQRCode = {
            viewModel.generateQRCode(shareUrl)
        },
        modifier = modifier
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
