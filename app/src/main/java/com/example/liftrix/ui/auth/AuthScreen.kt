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
    
    // FIXED: Use ThemeManager to respect user's explicit theme preference instead of system theme
    val themeManager = remember { com.example.liftrix.ui.theme.ThemeManager.getInstance(context) }
    val isDarkTheme = themeManager.getEffectiveThemeState(isSystemInDarkTheme())
    
    var isSignUpMode by remember { mutableStateOf(initialSignUpMode) }
    
    // Setup Google Sign-In client outside of onClick - this ensures it's ready when needed
    val googleSignInClient = remember {
        try {
            val clientId = com.example.liftrix.BuildConfig.GOOGLE_CLIENT_ID
            Timber.d("Configuring Google Sign-In client with clientId: $clientId")
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .requestProfile()
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            Timber.d("Google Sign-In client configured successfully")
            client
        } catch (e: Exception) {
            Timber.e(e, "Failed to configure Google Sign-In client")
            null
        }
    }
    
    // Google Sign-In launcher - properly registered at composable level
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Timber.d("Google Sign-In launcher result received: resultCode=${result.resultCode}")
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            Timber.d("Google Sign-In successful: account=${account?.email}, hasToken=${idToken != null}")
            viewModel.handleGoogleSignInResult(idToken)
        } catch (e: ApiException) {
            Timber.e("Google Sign-In ApiException: statusCode=${e.statusCode}, message=${e.message}")
            when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> {
                    Timber.w("Google Sign-In: User needs to sign in to Google account on device")
                    viewModel.handleGoogleSignInResult(null)
                }
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> {
                    Timber.w("Google Sign-In: Network connectivity issue")
                    viewModel.handleGoogleSignInResult(null)
                }
                com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR -> {
                    Timber.e("Google Sign-In: DEVELOPER_ERROR - Check SHA-1 fingerprints and OAuth client configuration in Firebase Console")
                    viewModel.handleGoogleSignInResult(null)
                }
                12501 -> { // SIGN_IN_CANCELLED
                    Timber.w("Google Sign-In: User cancelled sign-in")
                    viewModel.handleGoogleSignInResult(null)
                }
                else -> {
                    Timber.e("Google Sign-In: Unknown error with statusCode=${e.statusCode}")
                    viewModel.handleGoogleSignInResult(null)
                }
            }
        }
    }

    // Track the previous auth state to detect actual successful authentication
    var previousAuthState by remember { mutableStateOf<AuthState?>(null) }
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        when (val currentState = authState) {
            is AuthState.Authenticated -> {
                // Only navigate if we're transitioning from a non-authenticated state
                // This prevents navigation when the state observer briefly shows authenticated
                if (previousAuthState is AuthState.Loading || 
                    previousAuthState is AuthState.Unauthenticated ||
                    previousAuthState is AuthState.Initial) {
                    onAuthSuccess()
                }
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar(currentState.message)
                // Don't clear error immediately - let user see it
            }
            else -> { /* No action needed */ }
        }
        previousAuthState = authState
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
                Spacer(modifier = Modifier.height(24.dp))

                // Auth form section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSignUpMode) {
                        SignUpForm(
                            onSignUp = { email, password, username ->
                                viewModel.handleEvent(
                                    AuthEvent.EmailPasswordSignUp(email, password, username)
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
                            isLoading = authState is AuthState.Loading,
                            errorMessage = (authState as? AuthState.Error)?.message,
                            onClearError = {
                                viewModel.handleEvent(AuthEvent.ClearError)
                            }
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
                                Timber.d("Google Sign-In button clicked")
                                if (googleSignInClient != null) {
                                    Timber.d("Google Sign-In client available, starting sign-in flow")
                                    viewModel.handleEvent(AuthEvent.GoogleSignIn)
                                    try {
                                        // Force account selection by signing out first
                                        // This clears any cached account selection and shows the account picker
                                        googleSignInClient.signOut().addOnCompleteListener { signOutTask ->
                                            Timber.d("Google Sign-In signOut completed: success=${signOutTask.isSuccessful}")
                                            try {
                                                val signInIntent = googleSignInClient.signInIntent
                                                Timber.d("Launching Google Sign-In intent with account selection")
                                                googleSignInLauncher.launch(signInIntent)
                                            } catch (e: Exception) {
                                                Timber.e(e, "Failed to launch Google Sign-In after signOut")
                                                viewModel.handleGoogleSignInResult(null)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to initiate Google Sign-In flow")
                                        viewModel.handleGoogleSignInResult(null)
                                    }
                                } else {
                                    Timber.e("Google Sign-In client not initialized - cannot proceed")
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