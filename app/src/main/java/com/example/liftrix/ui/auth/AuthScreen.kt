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
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App branding
                Text(
                    text = "Liftrix",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Your fitness journey starts here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Auth form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Toggle between sign in and sign up
                        TextButton(
                            onClick = { isSignUpMode = !isSignUpMode },
                            enabled = authState !is AuthState.Loading
                        ) {
                            Text(
                                text = if (isSignUpMode) {
                                    "Already have an account? Sign In"
                                } else {
                                    "Don't have an account? Sign Up"
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Alternative sign-in options
                Text(
                    text = "Or continue with",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Google Sign-In
                    ElevatedButton(
                        onClick = {
                            viewModel.handleEvent(AuthEvent.GoogleSignIn)
                            try {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(context.getString(R.string.default_web_client_id))
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
                                painter = painterResource(id = android.R.drawable.ic_menu_search), // Replace with Google icon
                                contentDescription = "Google",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google")
                        }
                    }
                    
                    // Anonymous Sign-In
                    OutlinedButton(
                        onClick = {
                            viewModel.handleEvent(AuthEvent.AnonymousSignIn)
                        },
                        enabled = authState !is AuthState.Loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guest")
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Authenticating...",
                                style = MaterialTheme.typography.bodyMedium
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