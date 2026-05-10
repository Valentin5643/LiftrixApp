package com.example.liftrix.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.feature.auth.R
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.domain.model.ConsentChoices
import com.example.liftrix.ui.auth.components.ConsentDialog
import com.example.liftrix.ui.auth.components.SignInForm
import com.example.liftrix.ui.auth.components.SignUpForm
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import timber.log.Timber

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    initialSignUpMode: Boolean = false,
    googleClientId: String = "",
    isDarkThemeOverride: Boolean? = null,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val isDarkTheme = isDarkThemeOverride ?: isSystemInDarkTheme()
    val dividerColor = if (isDarkTheme) LiftrixColorsV2.Dark.Divider else LiftrixColorsV2.Light.Divider
    val textSecondary = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
    val textTertiary = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary

    var isSignUpMode by remember { mutableStateOf(initialSignUpMode) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingSignUpData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val bottomPadding = if (isSignUpMode) 88.dp else 32.dp

    // Setup Google Sign-In client outside of onClick - this ensures it's ready when needed
    val googleSignInClient = remember {
        try {
            if (googleClientId.isBlank()) {
                Timber.e("Google Sign-In client id is missing")
                return@remember null
            }
            Timber.d("Configuring Google Sign-In client")
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(googleClientId)
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
            when (e.statusCode) {
                12501 -> { // SIGN_IN_CANCELLED - User cancelled the sign-in flow
                    Timber.d("Google Sign-In: User cancelled sign-in (silent return)")
                    // Do NOT call handleGoogleSignInResult - just return to auth screen silently
                    // No error message needed for user-initiated cancellation
                }
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
                else -> {
                    Timber.e("Google Sign-In: ApiException with statusCode=${e.statusCode}, message=${e.message}")
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
        color = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundTertiary
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = bottomPadding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                LiftrixBrandHeader(
                    logoWidth = 132.dp,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Auth form section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSignUpMode) {
                        SignUpForm(
                            onSignUp = { email, password, username ->
                                // Store signup data and show consent dialog
                                pendingSignUpData = Triple(email, password, username)
                                showConsentDialog = true
                            },
                            isLoading = authState is AuthState.Loading,
                            isDarkThemeOverride = isDarkTheme
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
                            },
                            isDarkThemeOverride = isDarkTheme
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Alternative sign-in options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = dividerColor
                        )
                        Text(
                            text = "or",
                            style = MaterialTheme.typography.bodySmall,
                            color = textTertiary,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = dividerColor
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDarkTheme) LiftrixColorsV2.Dark.Outline else LiftrixColorsV2.Light.Outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                        )
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continue with Google")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle between sign in and sign up
                    if (!isSignUpMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Don't have an account?",
                                color = textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = { isSignUpMode = true },
                                enabled = authState !is AuthState.Loading,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "Create account",
                                    color = LiftrixColorsV2.Teal
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }

            if (isSignUpMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 48.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account?",
                        color = textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = { isSignUpMode = false },
                        enabled = authState !is AuthState.Loading,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Sign in",
                            color = LiftrixColorsV2.Teal
                        )
                    }
                }
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

        // Consent Dialog (shown after signup form submission)
        if (showConsentDialog && pendingSignUpData != null) {
            ConsentDialog(
                onConsentProvided = { consentData ->
                    val (email, password, username) = pendingSignUpData!!
                    // Process signup with consent data
                    viewModel.handleEvent(
                        AuthEvent.EmailPasswordSignUpWithConsent(
                            email = email,
                            password = password,
                            username = username,
                            consents = ConsentChoices(
                                privacyPolicy = consentData.privacyPolicy,
                                healthData = consentData.healthData,
                                aiChat = consentData.aiChat,
                                analytics = consentData.analytics
                            )
                        )
                    )
                    showConsentDialog = false
                    pendingSignUpData = null
                },
                onDismiss = {
                    // User cancelled consent - clear pending data
                    showConsentDialog = false
                    pendingSignUpData = null
                },
                onPrivacyPolicyClick = {
                    // TODO: Navigate to Privacy Policy screen
                    Timber.d("Privacy Policy link clicked")
                }
            )
        }
    }
} 
