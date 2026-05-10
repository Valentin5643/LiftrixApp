package com.example.liftrix.feature.auth.navigation

import androidx.compose.runtime.Composable
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
