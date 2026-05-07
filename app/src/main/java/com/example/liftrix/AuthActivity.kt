package com.example.liftrix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.ui.auth.AuthScreen
import com.example.liftrix.ui.auth.AuthViewModel
import com.example.liftrix.ui.auth.StartingScreen
import com.example.liftrix.ui.theme.LiftrixTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class AuthActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Check Google Play Services availability
        checkGooglePlayServices()
        
        setContent {
            val themeManager = com.example.liftrix.ui.theme.ThemeManager.getInstance(this@AuthActivity)
            
            LiftrixTheme(
                themeManager = themeManager
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthFlowContainer(
                        onNavigateToMain = {
                                    navigateToMainActivity()
                                }
                            )
                }
            }
        }
    }
    
    private fun checkGooglePlayServices() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        
        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                Timber.d("Google Play Services is available and up to date")
            }
            ConnectionResult.SERVICE_MISSING -> {
                Timber.w("Google Play Services is missing")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                Timber.w("Google Play Services needs to be updated")
            }
            ConnectionResult.SERVICE_DISABLED -> {
                Timber.w("Google Play Services is disabled")
            }
            else -> {
                Timber.w("Google Play Services error: $resultCode")
            }
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun AuthFlowContainer(
    onNavigateToMain: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    var hasNavigated by remember { mutableStateOf(false) }
    
    // Set explicit auth flow to true when AuthActivity is active
    // This prevents MainViewModel from interfering with auth error states
    LaunchedEffect(Unit) {
        mainViewModel.setExplicitAuthFlow(true)
    }
    
    // Handle navigation side effects
    LaunchedEffect(authState) {
        when (val currentState = authState) {
            is AuthState.Authenticated -> {
                // Only navigate if we haven't already and we're not coming from an error
                if (!hasNavigated) {
                    Timber.d("User authenticated: ${currentState.user.uid}, navigating to main")
                    hasNavigated = true
                    // Clear the explicit auth flow before navigating
                    mainViewModel.setExplicitAuthFlow(false)
                    onNavigateToMain()
                }
            }
            is AuthState.Unauthenticated -> {
                Timber.d("User not authenticated, showing auth screen")
                hasNavigated = false // Reset navigation flag
            }
            is AuthState.Loading -> {
                Timber.d("Checking authentication state...")
            }
            is AuthState.Initial -> {
                Timber.d("Initial auth state, waiting for auth check")
            }
            is AuthState.Error -> {
                Timber.e("Auth error: ${currentState.message}")
                hasNavigated = false // Reset navigation flag on error
            }
        }
    }
    
    when (val currentState = authState) {
        is AuthState.Initial -> {
            LoadingScreen(message = "Initializing...")
        }
        is AuthState.Loading -> {
            LoadingScreen(message = "Authenticating...")
        }
        is AuthState.Unauthenticated, is AuthState.Error -> {
            // Handle both unauthenticated and error states in the same screen
            // This prevents navigation away from the login form
            AuthFlowScreen(
                onAuthSuccess = onNavigateToMain
            )
        }
        is AuthState.Authenticated -> {
            // Show loading while navigating to main
            LoadingScreen(message = "Welcome back!")
        }
    }
}

@Composable
private fun LoadingScreen(message: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}


@Composable
private fun AuthFlowScreen(
    onAuthSuccess: () -> Unit
) {
    var showAuthScreen by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }
    var authenticatedUserId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val themeManager = remember { com.example.liftrix.ui.theme.ThemeManager.getInstance(context) }
    val isDarkTheme = themeManager.getEffectiveThemeState(androidx.compose.foundation.isSystemInDarkTheme())
    
    when {
        showOnboarding -> {
            com.example.liftrix.ui.onboarding.navigation.OnboardingNavigation(
                userId = authenticatedUserId ?: "guest-user", // Use authenticated ID if available, otherwise guest
                onComplete = {
                    showOnboarding = false
                    isSignUpMode = true
                    showAuthScreen = true
                },
                onSkip = {
                    showOnboarding = false
                    isSignUpMode = true
                    showAuthScreen = true
                }
            )
        }
        showAuthScreen -> {
            AuthScreen(
                onAuthSuccess = onAuthSuccess,
                initialSignUpMode = isSignUpMode,
                googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
                isDarkThemeOverride = isDarkTheme
            )
        }
        else -> {
            StartingScreen(
                onGetStarted = {
                    showOnboarding = true
                },
                onSignIn = {
                    isSignUpMode = false
                    showAuthScreen = true
                },
                isDarkThemeOverride = isDarkTheme
            )
        }
    }
}
