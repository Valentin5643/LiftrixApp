package com.example.liftrix.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.ui.social.FriendsScreen

/**
 * DEPRECATED: Legacy Home Navigation Graph
 * 
 * This file is maintained for backward compatibility during the migration to
 * type-safe navigation with LiftrixRoute sealed classes. All navigation is now
 * handled centrally in UnifiedNavigationContainer.kt.
 * 
 * @deprecated Use UnifiedNavigationContainer with LiftrixRoute sealed classes instead
 * 
 * Migration Notes:
 * - HomeRoutes.HOME_MAIN → LiftrixRoute.Home
 * - HomeRoutes.FRIENDS → LiftrixRoute.Friends
 * - All string-based routes replaced with type-safe LiftrixRoute variants
 * - Deep linking now handled through kotlinx.serialization in LiftrixRoute
 * 
 * This file can be removed once all consumers are migrated to the unified navigation system.
 */
@Deprecated(
    message = "Use UnifiedNavigationContainer with LiftrixRoute sealed classes instead",
    replaceWith = ReplaceWith("UnifiedNavigationContainer", "com.example.liftrix.ui.navigation.UnifiedNavigationContainer")
)
fun NavGraphBuilder.homeGraph(
    onNavigateToAuth: () -> Unit,
    onNavigateToWorkout: () -> Unit = {},
    navController: NavHostController
) {
    // This function is deprecated and should not be used in new code
    // All navigation is now handled by UnifiedNavigationContainer with type-safe routes
    
    navigation(
        startDestination = HomeRoutes.HOME_MAIN,
        route = MainNavigationItem.HOME.route
    ) {
        composable(HomeRoutes.HOME_MAIN) {
            HomeScreen(
                navController = navController,
                onNavigateToWorkout = { workoutId ->
                    // Legacy navigation - replaced by type-safe LiftrixRoute.WorkoutDetails
                    onNavigateToWorkout()
                },
                onNavigateToFriends = {
                    // Legacy navigation - replaced by LiftrixRoute.Friends
                    navController.navigate(HomeRoutes.FRIENDS)
                },
                onNavigateToMyWorkouts = {
                    // Legacy navigation - functionality moved to Progress screen
                    navController.navigate(HomeRoutes.MY_WORKOUTS)
                }
            )
        }
        
        // Friends screen with deep link support
        composable(
            route = HomeRoutes.FRIENDS,
            deepLinks = listOf(
                navDeepLink { 
                    uriPattern = "liftrix://friends/{requestId?}"
                }
            )
        ) {
            FriendsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToGymBuddy = {
                    navController.navigate(HomeRoutes.GYM_BUDDY)
                }
            )
        }

        composable(HomeRoutes.GYM_BUDDY) {
            com.example.liftrix.ui.social.gymbuddy.GymBuddyScreen(
                onNavigateToQrScanner = {
                    navController.navigate(HomeRoutes.QR_SCANNER)
                }
            )
        }

        composable(HomeRoutes.QR_SCANNER) {
            com.example.liftrix.ui.QRScannerScreen(
                onQrCodeScanned = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // My Workouts screen - functionality moved to Progress dashboard
        composable(HomeRoutes.MY_WORKOUTS) {
            // Redirect to progress screen which now handles workout history
            navController.popBackStack()
        }
    }
}

/**
 * DEPRECATED: Legacy Route definitions for the Home navigation graph.
 * 
 * @deprecated Use LiftrixRoute sealed classes instead
 */
@Deprecated(
    message = "Use LiftrixRoute sealed classes instead", 
    replaceWith = ReplaceWith("LiftrixRoute", "com.example.liftrix.ui.navigation.LiftrixRoute")
)
object HomeRoutes {
    const val HOME_MAIN = "home/main"
    const val FRIENDS = "home/friends"
    const val SETTINGS = "home/settings"
    const val WORKOUT_DETAIL = "home/workout_detail/{workoutId}"
    const val RECENT_WORKOUTS = "home/recent_workouts"
    const val MY_WORKOUTS = "home/my_workouts"
    const val GYM_BUDDY = "home/gym_buddy"
    const val QR_SCANNER = "home/qr_scanner"
}

 
