package com.example.liftrix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.MainViewModel
import com.example.liftrix.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import com.example.liftrix.ui.auth.AuthActivity
import com.example.liftrix.ui.navigation.UnifiedNavigationContainer
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeManager = ThemeManager.getInstance(this)
            val themeState = themeManager.getCurrentThemeState()
            
            LiftrixTheme(
                themeVersion = themeState.themeVersion,
                themeManager = themeManager
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent(
                        onNavigateToAuth = {
                            navigateToAuthActivity()
                        }
                    )
                }
            }
        }
    }
    
    private fun navigateToAuthActivity() {
        val intent = Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
@Composable
fun MainContent(
    onNavigateToAuth: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    
    // Set explicit auth flow to false when MainActivity is active
    // This allows MainViewModel to handle auth state normally
    LaunchedEffect(Unit) {
        viewModel.setExplicitAuthFlow(false)
    }
    
    when (val currentState = authState) {
        is MainViewModel.AuthenticationState.Loading -> {
            LoadingScreen()
        }
        is MainViewModel.AuthenticationState.Unauthenticated -> {
            LaunchedEffect(Unit) {
                onNavigateToAuth()
            }
        }
        is MainViewModel.AuthenticationState.Authenticated -> {
            AuthenticatedContent(
                user = currentState.user,
                onNavigateToAuth = onNavigateToAuth
            )
        }
    }
}

@Composable
fun LoadingScreen() {
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
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AuthenticatedContent(
    user: com.example.liftrix.domain.model.User,
    onNavigateToAuth: () -> Unit
) {
    // Trigger immediate sync when user is authenticated
    LaunchedEffect(user.uid) {
        try {
            // This would require SyncCoordinator to be injected here
            // For now, we'll rely on the LiftrixApp to handle the sync setup
            // The auth state listener in LiftrixApp will catch this authentication
            Timber.d("User authenticated in MainActivity: ${user.uid}")
        } catch (e: Exception) {
            Timber.e(e, "Error during authentication sync setup")
        }
    }
    
    // Main navigation with type-safe navigation system
    UnifiedNavigationContainer()
}

