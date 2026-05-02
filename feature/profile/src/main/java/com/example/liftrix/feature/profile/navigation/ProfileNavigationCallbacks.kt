package com.example.liftrix.feature.profile.navigation

data class ProfileNavigationCallbacks(
    val onNavigateBack: () -> Unit,
    val onNavigateToImageCrop: (String) -> Unit,
    val onNavigateToFollowersList: (String) -> Unit,
    val onNavigateToFollowingList: (String) -> Unit,
    val onNavigateToWorkoutDetail: (String) -> Unit,
    val onNavigateToPublicProfile: (String) -> Unit
)
