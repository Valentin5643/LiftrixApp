package com.example.liftrix.feature.social.navigation

/**
 * App-shell mediated navigation hooks for social presentation routes.
 *
 * The social feature owns screens and ViewModels, while the app shell owns the
 * concrete route graph and cross-feature destinations.
 */
data class SocialNavigationCallbacks(
    val navigateBack: () -> Unit,
    val navigateToUserSearch: () -> Unit = {},
    val navigateToGymBuddy: () -> Unit = {},
    val navigateToUserProfile: (String) -> Unit = {},
    val navigateToQrScanner: () -> Unit = {},
    val navigateToSharedWorkout: (String) -> Unit = {},
    val navigateToShareInbox: (String) -> Unit = {},
    val navigateToTemplateShare: (String) -> Unit = {}
)
