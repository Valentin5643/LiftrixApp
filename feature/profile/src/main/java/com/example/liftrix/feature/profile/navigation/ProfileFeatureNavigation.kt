package com.example.liftrix.feature.profile.navigation

import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.liftrix.ui.profile.FollowerListScreen
import com.example.liftrix.ui.profile.FollowerListType
import com.example.liftrix.ui.profile.ImageCropScreen
import com.example.liftrix.ui.profile.ProfileEditScreenRedesigned
import com.example.liftrix.ui.profile.UserProfileScreen

@Composable
fun UserProfileRoute(
    userId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFollowersList: (String) -> Unit,
    onNavigateToFollowingList: (String) -> Unit,
    onNavigateToWorkoutDetail: (String) -> Unit
) {
    UserProfileScreen(
        userId = userId,
        onNavigateBack = onNavigateBack,
        onNavigateToFollowersList = onNavigateToFollowersList,
        onNavigateToFollowingList = onNavigateToFollowingList,
        onNavigateToWorkoutDetail = onNavigateToWorkoutDetail
    )
}

@Composable
fun ProfileEditRoute(
    onNavigateBack: () -> Unit,
    onNavigateToImageCrop: (String) -> Unit
) {
    ProfileEditScreenRedesigned(
        onNavigateBack = onNavigateBack,
        onNavigateToImageCrop = onNavigateToImageCrop
    )
}

@Composable
fun ImageCropRoute(
    imageUri: Uri,
    onCropConfirmed: (Rect) -> Unit,
    onNavigateBack: () -> Unit
) {
    ImageCropScreen(
        imageUri = imageUri,
        onCropConfirmed = onCropConfirmed,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun FollowerListRoute(
    userId: String,
    listType: FollowerListType,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    FollowerListScreen(
        userId = userId,
        listType = listType,
        onNavigateBack = onNavigateBack,
        onNavigateToProfile = onNavigateToProfile
    )
}

@Composable
fun FollowerListRoute(
    userId: String,
    rawListType: String,
    fallbackListType: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    FollowerListRoute(
        userId = userId,
        listType = followerListTypeFromRoute(rawListType, fallbackListType),
        onNavigateBack = onNavigateBack,
        onNavigateToProfile = onNavigateToProfile
    )
}

private fun followerListTypeFromRoute(
    rawType: String,
    fallbackType: String
): FollowerListType = when (rawType) {
    "FOLLOWERS" -> FollowerListType.FOLLOWERS
    "FOLLOWING" -> FollowerListType.FOLLOWING
    "PENDING_REQUESTS" -> FollowerListType.PENDING_REQUESTS
    else -> when (fallbackType) {
        "FOLLOWING" -> FollowerListType.FOLLOWING
        "PENDING_REQUESTS" -> FollowerListType.PENDING_REQUESTS
        else -> FollowerListType.FOLLOWERS
    }
}
