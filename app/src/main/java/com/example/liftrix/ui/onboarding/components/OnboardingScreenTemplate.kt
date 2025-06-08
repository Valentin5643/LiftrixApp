package com.example.liftrix.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.accessibleHeading
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.accessibleNavigation
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.accessibleProgress
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.enhancedAccessibilitySemantics
import com.example.liftrix.ui.onboarding.accessibility.OnboardingStepAnnouncer
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Reusable template for onboarding screens with comprehensive accessibility support.
 * Provides header with navigation and progress, scrollable content area, and footer with actions.
 * Includes TalkBack announcements, focus management, and WCAG 2.1 AA compliance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreenTemplate(
    title: String,
    subtitle: String? = null,
    currentStep: Int,
    totalSteps: Int,
    stepName: String = "Step $currentStep",
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onContinue: () -> Unit,
    continueText: String = "Continue",
    canContinue: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Focus management for accessibility
    val titleFocusRequester = remember { FocusRequester() }
    val accessibleColors = AccessibilityUtils.getAccessibleColors()
    
    // Announce step change to screen readers
    OnboardingStepAnnouncer(
        stepName = stepName,
        stepNumber = currentStep,
        totalSteps = totalSteps
    )
    
    // Focus on title when step changes
    LaunchedEffect(currentStep) {
        try {
            titleFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request failed, continue without focus
        }
    }
    
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .enhancedAccessibilitySemantics(
                contentDescription = "Onboarding screen, $stepName of $totalSteps"
            ),
        topBar = {
            OnboardingHeader(
                currentStep = currentStep,
                totalSteps = totalSteps,
                stepName = stepName,
                onBack = onBack,
                onSkip = onSkip
            )
        },
        bottomBar = {
            OnboardingFooter(
                onContinue = onContinue,
                continueText = continueText,
                canContinue = canContinue,
                isLoading = isLoading,
                currentStep = currentStep,
                totalSteps = totalSteps
            )
        },
        containerColor = accessibleColors.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title and subtitle section with accessibility support
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accessibleColors.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .accessibleHeading(text = title, level = 1)
                )
                
                subtitle?.let { subtitleText ->
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                                                    .enhancedAccessibilitySemantics(
                            contentDescription = "Instructions: $subtitleText"
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content area with semantic container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .enhancedAccessibilitySemantics(
                        contentDescription = "Main content area for $stepName"
                    )
            ) {
                content()
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Header component with enhanced accessibility support, back navigation, progress indicator, and skip option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingHeader(
    currentStep: Int,
    totalSteps: Int,
    stepName: String,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?
) {
    val accessibleColors = AccessibilityUtils.getAccessibleColors()
    
    TopAppBar(
        title = {
            ProgressIndicator(
                currentStep = currentStep,
                totalSteps = totalSteps,
                stepName = stepName
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.accessibleNavigation(
                        description = "Go back to previous step",
                        currentStep = currentStep - 1,
                        totalSteps = totalSteps,
                        canNavigate = true
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = accessibleColors.onSurface
                    )
                }
            }
        },
        actions = {
            if (onSkip != null) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.enhancedAccessibilitySemantics(
                        contentDescription = "Skip onboarding setup. You can set up your profile later in settings.",
                        role = Role.Button,
                        stateDescription = "This will take you directly to account creation"
                    )
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = accessibleColors.surface
        )
    )
}

/**
 * Progress indicator with comprehensive accessibility support and step information.
 */
@Composable
private fun ProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepName: String
) {
    val progressPercentage = ((currentStep.toFloat() / totalSteps.toFloat()) * 100).toInt()
    val accessibleColors = AccessibilityUtils.getAccessibleColors()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enhancedAccessibilitySemantics(
                contentDescription = "Progress indicator: $progressPercentage percent complete",
                role = Role.Button
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step $currentStep of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.enhancedAccessibilitySemantics(
                    contentDescription = "Currently on step $currentStep of $totalSteps total steps"
                )
            )
            
            Text(
                text = "$progressPercentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.enhancedAccessibilitySemantics(
                    contentDescription = "$progressPercentage percent progress completed"
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .accessibleProgress(
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    stepName = stepName
                ),
            color = accessibleColors.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * Footer component with enhanced accessibility support and primary action button.
 */
@Composable
private fun OnboardingFooter(
    onContinue: () -> Unit,
    continueText: String,
    canContinue: Boolean,
    isLoading: Boolean,
    currentStep: Int,
    totalSteps: Int
) {
    val accessibleColors = AccessibilityUtils.getAccessibleColors()
    val buttonText = if (isLoading) "Loading..." else continueText
    val isLastStep = currentStep == totalSteps
    
    val stateDescription = when {
        isLoading -> "Please wait, processing your information"
        !canContinue -> "Complete the current step to continue"
        isLastStep -> "Complete onboarding and create your account"
        else -> "Continue to step ${currentStep + 1} of $totalSteps"
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .enhancedAccessibilitySemantics(
                contentDescription = "Navigation footer with continue button",

            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onContinue,
            enabled = canContinue && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // WCAG 2.1 AA minimum 44dp touch target
                .enhancedAccessibilitySemantics(
                    contentDescription = buttonText,
                    role = Role.Button,
                    stateDescription = stateDescription
                )
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge,
                color = if (canContinue && !isLoading) {
                    accessibleColors.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

/**
 * Preview for OnboardingScreenTemplate with sample content and accessibility features.
 */
@Preview(showBackground = true)
@Composable
private fun OnboardingScreenTemplatePreview() {
    LiftrixTheme {
        OnboardingScreenTemplate(
            title = "What's your age?",
            subtitle = "This helps us recommend safe and effective workouts",
            currentStep = 2,
            totalSteps = 4,
            stepName = "Age Input",
            onBack = { },
            onSkip = { },
            onContinue = { },
            canContinue = true
        ) {
            Text(
                text = "Sample content area with accessibility support",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .enhancedAccessibilitySemantics(
                        contentDescription = "Sample input area for age entry"
                    )
            )
        }
    }
}

/**
 * Preview for loading state with accessibility announcements.
 */
@Preview(showBackground = true)
@Composable
private fun OnboardingScreenTemplateLoadingPreview() {
    LiftrixTheme {
        OnboardingScreenTemplate(
            title = "Saving your profile",
            subtitle = "Please wait while we save your information",
            currentStep = 4,
            totalSteps = 4,
            stepName = "Profile Completion",
            onBack = null,
            onSkip = null,
            onContinue = { },
            continueText = "Save Profile",
            canContinue = false,
            isLoading = true
        ) {
            Text(
                text = "Your profile is being saved...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .enhancedAccessibilitySemantics(
                        contentDescription = "Saving profile information, please wait"
                    )
            )
        }
    }
} 