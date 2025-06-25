package com.example.liftrix.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.liftrix.MainActivity
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
                                onAuthSuccess = {
                                    navigateToMainActivity()
                                },
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