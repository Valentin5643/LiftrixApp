package com.example.liftrix.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.ui.auth.components.SignInForm
import com.example.liftrix.ui.auth.components.SignUpForm
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import timber.log.Timber
import com.example.liftrix.R
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    initialSignUpMode: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    
    var isSignUpMode by remember { mutableStateOf(initialSignUpMode) }
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            viewModel.handleGoogleSignInResult(idToken)
        } catch (e: ApiException) {
            when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> {
                    // User needs to sign in to Google account on device
                    viewModel.handleGoogleSignInResult(null)
                }
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> {
                    // Network connectivity issue
                    viewModel.handleGoogleSignInResult(null)
                }
                else -> {
                    // Other errors (including DEVELOPER_ERROR for config issues)
                    viewModel.handleGoogleSignInResult(null)
                }
            }
        }
    }

    // Handle auth state changes
    LaunchedEffect(authState) {
        when (val currentState = authState) {
            is AuthState.Authenticated -> {
                onAuthSuccess()
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar(currentState.message)
                viewModel.handleEvent(AuthEvent.ClearError)
            }
            else -> { /* No action needed */ }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                
                // Top Section: Tagline and Hero Image
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your fitness journey",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal,
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "starts here",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal,
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Auth form section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSignUpMode) {
                        SignUpForm(
                            onSignUp = { email, password, displayName ->
                                viewModel.handleEvent(
                                    AuthEvent.EmailPasswordSignUp(email, password, displayName)
                                )
                            },
                            isLoading = authState is AuthState.Loading
                        )
                    } else {
                        SignInForm(
                            onSignIn = { email, password ->
                                viewModel.handleEvent(
                                    AuthEvent.EmailPasswordSignIn(email, password)
                                )
                            },
                            onForgotPassword = { email ->
                                viewModel.handleEvent(AuthEvent.ForgotPassword(email))
                            },
                            isLoading = authState is AuthState.Loading
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Alternative sign-in options
                    Text(
                        text = "or",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Google Sign-In
                        OutlinedButton(
                            onClick = {
                                viewModel.handleEvent(AuthEvent.GoogleSignIn)
                                try {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken(com.example.liftrix.BuildConfig.GOOGLE_CLIENT_ID)
                                        .requestEmail()
                                        .requestProfile()
                                        .build()
                                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                    
                                    // Sign out any existing account first to force account picker
                                    googleSignInClient.signOut().addOnCompleteListener {
                                        val signInIntent = googleSignInClient.signInIntent
                                        googleSignInLauncher.launch(signInIntent)
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to configure Google Sign-In")
                                    viewModel.handleGoogleSignInResult(null)
                                }
                            },
                            enabled = authState !is AuthState.Loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = "Google",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Google", color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary)
                            }
                        }
                        
                        // Guest Sign-In
                        TextButton(
                            onClick = {
                                viewModel.handleEvent(AuthEvent.AnonymousSignIn)
                            },
                            enabled = authState !is AuthState.Loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Continue as Guest",
                                color = LiftrixColorsV2.Light.TextSecondary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Toggle between sign in and sign up - moved below Google/Guest
                    TextButton(
                        onClick = { isSignUpMode = !isSignUpMode },
                        enabled = authState !is AuthState.Loading
                    ) {
                        Text(
                            text = if (isSignUpMode) {
                                "Already have an account?"
                            } else {
                                "Don't have an account?"
                            },
                            color = LiftrixColorsV2.Light.TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
            
            // Loading overlay
            if (authState is AuthState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundTertiary
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = LiftrixColorsV2.Teal
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Authenticating...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                            )
                        }
                    }
                }
            }
            
            // Snackbar host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
} 