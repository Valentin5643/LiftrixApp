package com.example.liftrix.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.auth.AuthScreen
import com.example.liftrix.ui.onboarding.navigation.OnboardingNavigation

@Composable
fun AuthRoute(
    initialSignUpMode: Boolean,
    googleClientId: String,
    onAuthSuccess: () -> Unit
) {
    AuthScreen(
        onAuthSuccess = onAuthSuccess,
        initialSignUpMode = initialSignUpMode,
        googleClientId = googleClientId
    )
}

@Composable
fun OnboardingRoute(
    userId: String,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    OnboardingNavigation(
        userId = userId,
        onComplete = onComplete,
        onSkip = onSkip
    )
}

fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    googleClientId: String
) {
    composable<LiftrixRoute.Onboarding> {
        OnboardingRoute(
            userId = "",
            onComplete = {
                navController.clearBackStackAndNavigate(LiftrixRoute.Home)
            },
            onSkip = {
                navController.clearBackStackAndNavigate(LiftrixRoute.Home)
            }
        )
    }

    composable<LiftrixRoute.AuthSignUp> {
        AuthRoute(
            initialSignUpMode = true,
            googleClientId = googleClientId,
            onAuthSuccess = {
                navController.clearBackStackAndNavigate(LiftrixRoute.Home)
            }
        )
    }

    composable<LiftrixRoute.AuthSignIn> {
        AuthRoute(
            initialSignUpMode = false,
            googleClientId = googleClientId,
            onAuthSuccess = {
                navController.clearBackStackAndNavigate(LiftrixRoute.Home)
            }
        )
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
