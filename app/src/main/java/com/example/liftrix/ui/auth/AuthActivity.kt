package com.example.liftrix.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.MainActivity
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.ui.theme.LiftrixTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class AuthActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check Google Play Services availability
        checkGooglePlayServices()
        
        setContent {
            LiftrixTheme {
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
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    
    // Handle navigation side effects
    LaunchedEffect(authState) {
        when (val currentState = authState) {
            is AuthState.Authenticated -> {
                Timber.d("User authenticated: ${currentState.user.uid}, navigating to main")
                onNavigateToMain()
            }
            is AuthState.Unauthenticated -> {
                Timber.d("User not authenticated, showing auth screen")
            }
            is AuthState.Loading -> {
                Timber.d("Checking authentication state...")
            }
            is AuthState.Initial -> {
                Timber.d("Initial auth state, waiting for auth check")
            }
            is AuthState.Error -> {
                Timber.e("Auth error: ${currentState.message}")
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
        is AuthState.Unauthenticated -> {
            AuthFlowScreen(
                onAuthSuccess = onNavigateToMain
            )
        }
        is AuthState.Authenticated -> {
            // Show loading while navigating to main
            LoadingScreen(message = "Welcome back!")
        }
        is AuthState.Error -> {
            ErrorScreen(
                error = currentState.message,
                onRetry = {
                    // Clear error and let auth observer handle state
                    viewModel.handleEvent(AuthEvent.ClearError)
                }
            )
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
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Authentication Error",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Button(
                onClick = onRetry
            ) {
                Text("Retry")
            }
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
    
    when {
        showOnboarding -> {
            com.example.liftrix.ui.onboarding.navigation.OnboardingNavigation(
                userId = "temp-user-id", // TODO: Get actual user ID after auth
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
                initialSignUpMode = isSignUpMode
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
                }
            )
        }
    }
} 