package com.example.liftrix.feature.profile.navigation

import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.liftrix.ui.navigation.LiftrixRoute
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

fun NavGraphBuilder.profileGraph(navController: NavHostController) {
    composable<LiftrixRoute.PublicProfile> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.PublicProfile>()
        UserProfileRoute(
            userId = route.userId,
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToFollowersList = { userId ->
                navController.navigate(
                    LiftrixRoute.FollowersList(userId = userId, listType = "FOLLOWERS")
                )
            },
            onNavigateToFollowingList = { userId ->
                navController.navigate(
                    LiftrixRoute.FollowingList(userId = userId, listType = "FOLLOWING")
                )
            },
            onNavigateToWorkoutDetail = { workoutId ->
                navController.navigate(LiftrixRoute.WorkoutDetails(workoutId, route.userId))
            }
        )
    }

    composable<LiftrixRoute.Profile> {
        ProfileEditRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToImageCrop = { uri ->
                navController.navigate(LiftrixRoute.ImageCrop(uri))
            }
        )
    }

    composable<LiftrixRoute.ProfileEdit> {
        ProfileEditRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToImageCrop = { uri ->
                navController.navigate(LiftrixRoute.ImageCrop(uri))
            }
        )
    }

    composable<LiftrixRoute.ImageCrop> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.ImageCrop>()
        ImageCropRoute(
            imageUri = Uri.parse(route.imageUri),
            onNavigateBack = { navController.popBackStackSafely() },
            onCropConfirmed = { _ -> navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.FollowersList> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.FollowersList>()
        FollowerListRoute(
            userId = route.userId,
            rawListType = route.listType,
            fallbackListType = "FOLLOWERS",
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToProfile = { userId ->
                navController.navigate(LiftrixRoute.PublicProfile(userId))
            }
        )
    }

    composable<LiftrixRoute.FollowingList> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.FollowingList>()
        FollowerListRoute(
            userId = route.userId,
            rawListType = route.listType,
            fallbackListType = "FOLLOWING",
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToProfile = { userId ->
                navController.navigate(LiftrixRoute.PublicProfile(userId))
            }
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
