package com.example.liftrix.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.liftrix.ui.auth.AuthScreen
import com.example.liftrix.ui.guest.GuestConversionScreen
import com.example.liftrix.ui.guest.GuestDashboardScreen
import com.example.liftrix.ui.guest.GuestModeChip
import com.example.liftrix.ui.guest.GuestModeSelectionScreen
import com.example.liftrix.ui.guest.GuestSessionIndicator
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

@Composable
fun GuestModeSelectionRoute(
    onContinueAsGuest: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit
) {
    GuestModeSelectionScreen(
        onContinueAsGuest = onContinueAsGuest,
        onCreateAccount = onCreateAccount,
        onSignIn = onSignIn
    )
}

@Composable
fun GuestDashboardRoute(
    onUpgrade: () -> Unit,
    onStartWorkout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    GuestDashboardScreen(
        onUpgrade = onUpgrade,
        onStartWorkout = onStartWorkout,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun GuestConversionRoute(
    source: String,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onMaybeLater: () -> Unit
) {
    GuestConversionScreen(
        source = source,
        onCreateAccount = onCreateAccount,
        onSignIn = onSignIn,
        onMaybeLater = onMaybeLater
    )
}

@Composable
fun GuestSessionIndicatorRoute(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GuestSessionIndicator(
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun GuestModeChipRoute(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GuestModeChip(
        onClick = onClick,
        modifier = modifier
    )
}
