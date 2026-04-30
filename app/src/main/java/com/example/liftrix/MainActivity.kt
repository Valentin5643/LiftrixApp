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
import androidx.compose.foundation.layout.padding
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
import com.example.liftrix.ui.theme.ProvideWeightUnitManager
import com.example.liftrix.domain.service.WeightUnitManager
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import com.example.liftrix.ui.common.LocalFirebaseStorageUrlResolver
import com.example.liftrix.ui.common.LocalProfileImageCache
import com.example.liftrix.ui.common.components.LiftrixLoadingAnimation
import com.example.liftrix.service.ProfileImageCache
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var weightUnitManager: WeightUnitManager

    @Inject
    lateinit var firebaseStorageUrlResolver: FirebaseStorageUrlResolver

    @Inject
    lateinit var profileImageCache: ProfileImageCache
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Initialize WeightUnitManager
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                weightUnitManager.initialize()
                Timber.d("WeightUnitManager initialized successfully in MainActivity")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize WeightUnitManager in MainActivity")
            }
        }
        
        setContent {
            val themeManager = remember { ThemeManager.getInstance(this@MainActivity) }
            
            LiftrixTheme(
                themeManager = themeManager
            ) {
                ProvideWeightUnitManager(weightUnitManager = weightUnitManager) {
                    CompositionLocalProvider(
                        LocalFirebaseStorageUrlResolver provides firebaseStorageUrlResolver,
                        LocalProfileImageCache provides profileImageCache
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
            LiftrixLoadingAnimation(
                message = "Loading Liftrix...",
                size = 144.dp
            )
        }
    }
}

@Composable
fun AuthenticatedContent(
    user: com.example.liftrix.domain.model.User,
    onNavigateToAuth: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var profileCheckComplete by remember { mutableStateOf(false) }

    // Verify profile exists before showing main UI
    LaunchedEffect(user.uid) {
        try {
            Timber.d("User authenticated in MainActivity: ${user.uid}")

            // Check if profile exists, wait up to 5 seconds for it to be created
            var attempts = 0
            val maxAttempts = 10 // 5 seconds total (10 * 500ms)

            while (attempts < maxAttempts && !profileCheckComplete) {
                val hasProfile = viewModel.checkProfileExists(user.uid)

                if (hasProfile) {
                    Timber.i("Profile verified for user: ${user.uid}")
                    profileCheckComplete = true
                    break
                } else {
                    Timber.d("Profile not found yet for user: ${user.uid}, attempt ${attempts + 1}/$maxAttempts")
                    kotlinx.coroutines.delay(500) // Wait 500ms between checks
                    attempts++
                }
            }

            if (!profileCheckComplete) {
                Timber.w("Profile verification timeout for user: ${user.uid}, proceeding anyway")
                profileCheckComplete = true // Proceed anyway after timeout
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during profile verification")
            profileCheckComplete = true // Proceed on error
        }
    }

    // Show loading while verifying profile existence
    if (!profileCheckComplete) {
        LoadingScreen()
    } else {
        // Main navigation with type-safe navigation system
        UnifiedNavigationContainer()
    }
}
