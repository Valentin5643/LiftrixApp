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
import com.example.liftrix.ui.auth.AuthActivity
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiftrixTheme {
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
            AuthenticatedContent(user = currentState.user)
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
fun AuthenticatedContent(user: com.example.liftrix.domain.model.User) {
    // Single entry point navigation through WorkoutNavigation
    com.example.liftrix.ui.navigation.WorkoutNavigation(
        onNavigateBack = { /* Handle back navigation */ },
        onWorkoutComplete = { /* Handle workout completion */ }
    )
}

