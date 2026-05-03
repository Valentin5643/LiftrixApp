package com.example.liftrix.ui.social.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.components.actions.UnifiedWorkoutCard

/**
 * Social onboarding screen providing multi-step flow for enabling social features.
 * 
 * This screen guides users through:
 * 1. Privacy explanation and education
 * 2. Social feature benefits showcase
 * 3. Profile creation with username and display name
 * 4. Privacy settings configuration
 * 5. Completion confirmation
 * 
 * Features privacy-first design with clear explanations and user control.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialOnboardingScreen(
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialOnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentStep = uiState.currentStep
    val totalSteps = SocialOnboardingStep.entries.size
    
    // Handle back navigation
    BackHandler {
        if (currentStep == SocialOnboardingStep.PRIVACY_INTRO) {
            onNavigateBack()
        } else {
            viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
        }
    }
    
    // Handle completion
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Enable Social Features",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep == SocialOnboardingStep.PRIVACY_INTRO) {
                                onNavigateBack()
                            } else {
                                viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                actions = {
                    if (currentStep != SocialOnboardingStep.COMPLETION) {
                        TextButton(
                            onClick = { onNavigateBack() }
                        ) {
                            Text("Skip")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep.ordinal + 1).toFloat() / totalSteps },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            // Step indicator
            Text(
                text = "Step ${currentStep.ordinal + 1} of $totalSteps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Animated content for different steps
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (targetState.ordinal > initialState.ordinal) fullWidth else -fullWidth },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (targetState.ordinal > initialState.ordinal) -fullWidth else fullWidth },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { step ->
                when (step) {
                    SocialOnboardingStep.PRIVACY_INTRO -> PrivacyIntroStep(
                        onContinue = { viewModel.handleEvent(SocialOnboardingEvent.NavigateNext) }
                    )
                    SocialOnboardingStep.BENEFITS -> BenefitsStep(
                        onContinue = { viewModel.handleEvent(SocialOnboardingEvent.NavigateNext) }
                    )
                    SocialOnboardingStep.PROFILE_CREATION -> ProfileCreationStep(
                        uiState = uiState,
                        onEvent = viewModel::handleEvent
                    )
                    SocialOnboardingStep.PRIVACY_SETTINGS -> PrivacySettingsStep(
                        uiState = uiState,
                        onEvent = viewModel::handleEvent
                    )
                    SocialOnboardingStep.COMPLETION -> CompletionStep(
                        onComplete = { viewModel.handleEvent(SocialOnboardingEvent.CompleteOnboarding) }
                    )
                }
            }
        }
    }
    
    // Loading dialog
    if (uiState.isLoading) {
        LoadingDialog(
            message = when (currentStep) {
                SocialOnboardingStep.PROFILE_CREATION -> "Creating your social profile..."
                SocialOnboardingStep.PRIVACY_SETTINGS -> "Saving your privacy preferences..."
                else -> "Please wait..."
            }
        )
    }
}

@Composable
private fun PrivacyIntroStep(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        UnifiedWorkoutCard(
            title = "Your Privacy Comes First",
            subtitle = "Complete control over your data",
            leadingIcon = Icons.Default.Security,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "We believe fitness is personal. That's why we've designed our social features with privacy at the core, giving you complete control over what you share and who sees it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Privacy principles
        PrivacyPrincipleCard(
            icon = Icons.Default.Lock,
            title = "Private by Default",
            description = "Your profile starts private. You choose what to share and when."
        )
        
        PrivacyPrincipleCard(
            icon = Icons.Default.Visibility,
            title = "Granular Control",
            description = "Control visibility for workouts, achievements, and profile information separately."
        )
        
        PrivacyPrincipleCard(
            icon = Icons.Default.Groups,
            title = "Your Community",
            description = "Connect with friends and training partners while maintaining your privacy boundaries."
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun BenefitsStep(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        UnifiedWorkoutCard(
            title = "Connect & Grow Together",
            subtitle = "Benefits of joining the Liftrix community",
            leadingIcon = Icons.Default.Groups,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Join a supportive fitness community where you can share your progress, get motivated, and achieve your goals together.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Benefits list
        BenefitCard(
            title = "Find Training Partners",
            description = "Connect with people who share your fitness goals and workout preferences."
        )
        
        BenefitCard(
            title = "Share Your Progress",
            description = "Celebrate your achievements and inspire others with your fitness journey."
        )
        
        BenefitCard(
            title = "Get Motivated",
            description = "See what your friends are doing and get inspired to push your own limits."
        )
        
        BenefitCard(
            title = "Join Challenges",
            description = "Participate in friendly competitions and group challenges to stay engaged."
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

@Composable
private fun ProfileCreationStep(
    uiState: SocialOnboardingUiState,
    onEvent: (SocialOnboardingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val usernameFocusRequester = remember { FocusRequester() }
    val displayNameFocusRequester = remember { FocusRequester() }
    val bioFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        usernameFocusRequester.requestFocus()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        UnifiedWorkoutCard(
            title = "Create Your Profile",
            subtitle = "How others will find and recognize you",
            leadingIcon = Icons.Default.PersonAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Choose a username and display name for your social profile. You can always change these later in settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Profile form
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Username field
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = { onEvent(SocialOnboardingEvent.UpdateUsername(it)) },
                    label = { Text("Username") },
                    supportingText = {
                        Text(
                            text = if (uiState.usernameError != null) {
                                uiState.usernameError
                            } else {
                                "This is how others will find you. Must be 3-20 characters, letters, numbers, and underscores only."
                            },
                            color = if (uiState.usernameError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    isError = uiState.usernameError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { displayNameFocusRequester.requestFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                )
                
                // Display name field
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { onEvent(SocialOnboardingEvent.UpdateDisplayName(it)) },
                    label = { Text("Display Name") },
                    supportingText = {
                        Text(
                            text = if (uiState.displayNameError != null) {
                                uiState.displayNameError
                            } else {
                                "Your full name or how you'd like to be addressed."
                            },
                            color = if (uiState.displayNameError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    isError = uiState.displayNameError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { bioFocusRequester.requestFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(displayNameFocusRequester)
                )
                
                // Bio field (optional)
                OutlinedTextField(
                    value = uiState.bio,
                    onValueChange = { onEvent(SocialOnboardingEvent.UpdateBio(it)) },
                    label = { Text("Bio (Optional)") },
                    supportingText = {
                        Text(
                            text = "${uiState.bio.length}/500 characters",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(bioFocusRequester)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onEvent(SocialOnboardingEvent.NavigateBack) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            
            Button(
                onClick = { onEvent(SocialOnboardingEvent.CreateProfile) },
                enabled = uiState.canCreateProfile && !uiState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Create Profile")
            }
        }
    }
}

@Composable
private fun PrivacySettingsStep(
    uiState: SocialOnboardingUiState,
    onEvent: (SocialOnboardingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        UnifiedWorkoutCard(
            title = "Configure Your Privacy",
            subtitle = "Choose what you want to share",
            leadingIcon = Icons.Default.Security,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "These settings control what information is visible to other users. You can change these anytime in your privacy settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Privacy settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PrivacySettingItem(
                    title = "Allow Follow Requests",
                    description = "Other users can request to follow you",
                    checked = uiState.allowFollowRequests,
                    onCheckedChange = { onEvent(SocialOnboardingEvent.UpdateAllowFollowRequests(it)) }
                )
                
                PrivacySettingItem(
                    title = "Share Workouts",
                    description = "Your workouts will be visible to followers",
                    checked = uiState.workoutSharingEnabled,
                    onCheckedChange = { onEvent(SocialOnboardingEvent.UpdateWorkoutSharing(it)) }
                )
                
                PrivacySettingItem(
                    title = "Enable Gym Buddies",
                    description = "Connect with training partners at your gym",
                    checked = uiState.gymBuddiesEnabled,
                    onCheckedChange = { onEvent(SocialOnboardingEvent.UpdateGymBuddies(it)) }
                )
                
                PrivacySettingItem(
                    title = "Show Achievements",
                    description = "Display your fitness milestones and personal records",
                    checked = uiState.showAchievements,
                    onCheckedChange = { onEvent(SocialOnboardingEvent.UpdateShowAchievements(it)) }
                )
            }
        }
        
        // Privacy note
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Privacy reminder",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = "Privacy Reminder",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Text(
                    text = "Your profile will be private by default. Only users you accept as followers will see your shared content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onEvent(SocialOnboardingEvent.NavigateBack) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            
            Button(
                onClick = { onEvent(SocialOnboardingEvent.SavePrivacySettings) },
                enabled = !uiState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun CompletionStep(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Success message
            UnifiedWorkoutCard(
                title = "Welcome to the Community!",
                subtitle = "Your social profile is ready",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🎉",
                        style = MaterialTheme.typography.displayMedium
                    )
                    
                    Text(
                        text = "You're all set! You can now connect with other fitness enthusiasts, share your progress, and stay motivated together.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Remember, you can always adjust your privacy settings in the app settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Complete button
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Get Started")
            }
        }
    }
}

@Composable
private fun PrivacyPrincipleCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BenefitCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrivacySettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $description"
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingDialog(
    message: String,
    modifier: Modifier = Modifier
) {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

enum class SocialOnboardingStep {
    PRIVACY_INTRO,
    BENEFITS,
    PROFILE_CREATION,
    PRIVACY_SETTINGS,
    COMPLETION
}

@Preview(showBackground = true)
@Composable
private fun SocialOnboardingScreenPreview() {
    LiftrixTheme {
        SocialOnboardingScreen(
            onNavigateBack = {},
            onComplete = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun SocialOnboardingScreenDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        SocialOnboardingScreen(
            onNavigateBack = {},
            onComplete = {}
        )
    }
}
