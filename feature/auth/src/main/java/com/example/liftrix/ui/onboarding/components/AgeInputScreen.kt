package com.example.liftrix.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.accessibleTextInput
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.enhancedAccessibilitySemantics
import com.example.liftrix.ui.onboarding.accessibility.AccessibilityUtils.announceForAccessibility
import com.example.liftrix.ui.onboarding.animation.OnboardingAnimations
import com.example.liftrix.ui.onboarding.animation.OnboardingAnimationComponents
import com.example.liftrix.ui.onboarding.animation.AnimationPerformanceUtils.rememberAnimationState
import com.example.liftrix.ui.onboarding.model.OnboardingStep

/**
 * Age input screen for onboarding flow with comprehensive accessibility support.
 * Provides real-time validation with number keyboard, error feedback, and TalkBack announcements.
 */
@Composable
fun AgeInputScreen(
    currentAge: String,
    onAgeChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Validation state
    var validationResult by remember { mutableStateOf<ValidationResult?>(null) }
    val validateProfileInputUseCase = remember { ValidateProfileInputUseCase() }
    
    // Validate age on input change with accessibility announcements
    LaunchedEffect(currentAge) {
        validationResult = if (currentAge.isNotBlank()) {
            validateProfileInputUseCase.validateAge(currentAge)
        } else {
            null
        }
    }
    
    // Announce validation changes to screen readers
    val context = LocalContext.current
    LaunchedEffect(validationResult) {
        when (validationResult) {
            is ValidationResult.Valid -> {
                if (currentAge.isNotBlank()) {
                    AccessibilityUtils.announceToScreenReader(context, "Age validated successfully")
                }
            }
            is ValidationResult.Invalid -> {
                val errorMessage = (validationResult as ValidationResult.Invalid).message
                AccessibilityUtils.announceToScreenReader(context, "Age validation error: $errorMessage")
            }
            else -> { /* No announcement needed */ }
        }
    }
    
    val isValid = validationResult is ValidationResult.Valid
    val canContinue = currentAge.isNotBlank() && isValid
    
    OnboardingScreenTemplate(
        title = OnboardingStep.AGE.title,
        subtitle = OnboardingStep.AGE.description,
        currentStep = OnboardingStep.AGE.stepNumber + 1,
        totalSteps = OnboardingStep.getContentSteps().size + 1,
        stepName = "Age Input",
        onBack = onBack,
        onSkip = onSkip,
        onContinue = onContinue,
        continueText = "Continue",
        canContinue = canContinue,
        isLoading = false,
        modifier = modifier
    ) {
        AgeInputContent(
            age = currentAge,
            onAgeChange = onAgeChange,
            validationResult = validationResult
        )
    }
}

/**
 * Main content area for age input with comprehensive accessibility support and validation feedback.
 */
@Composable
private fun AgeInputContent(
    age: String,
    onAgeChange: (String) -> Unit,
    validationResult: ValidationResult?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enhancedAccessibilitySemantics(
                contentDescription = "Age input form section"
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AgeInputHelperText()
        AgeInputField(
            age = age,
            onAgeChange = onAgeChange,
            validationResult = validationResult
        )
        AgeValidationMessage(validationResult = validationResult)
    }
}

/**
 * Helper text explaining why age is needed with accessibility support.
 */
@Composable
private fun AgeInputHelperText() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enhancedAccessibilitySemantics(
                contentDescription = "Information about why age is needed for workout recommendations"
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🎂",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .enhancedAccessibilitySemantics(
                    contentDescription = "Birthday cake emoji"
                )
        )
        
        Text(
            text = "Age helps us recommend safe and effective workouts tailored to your fitness level and physical development.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .enhancedAccessibilitySemantics(
                    contentDescription = "Explanation: Age helps us recommend safe and effective workouts tailored to your fitness level and physical development"
                )
        )
    }
}

/**
 * Performance-optimized age input field with <100ms validation feedback animations.
 */
@Composable
private fun AgeInputField(
    age: String,
    onAgeChange: (String) -> Unit,
    validationResult: ValidationResult?
) {
    val isError = validationResult is ValidationResult.Invalid
    val isValid = validationResult is ValidationResult.Valid
    val focusRequester = remember { FocusRequester() }
    val accessibleColors = AccessibilityUtils.getAccessibleColors()
    
    // Stable state for animation triggers
    val showError = rememberAnimationState(isError) { if (isError) 1f else 0f }
    val showSuccess = rememberAnimationState(isValid) { if (isValid) 1f else 0f }
    
    // Auto-focus on field when screen loads
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    OutlinedTextField(
        value = age,
        onValueChange = { newValue ->
            // Filter to allow only numbers and limit to reasonable length
            if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                onAgeChange(newValue)
            }
        },
        label = {
            Text(
                text = "Age",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        placeholder = {
            Text(
                text = "Enter your age",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingText = {
            Text(
                text = "Ages 13-100 are supported",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            when {
                isValid -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Valid age entered",
                        tint = accessibleColors.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .then(OnboardingAnimations.successPulseAnimation(isValid))
                    )
                }
                isError -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Invalid age entered",
                        tint = accessibleColors.error,
                        modifier = Modifier
                            .size(20.dp)
                            .then(OnboardingAnimations.shakeAnimation(isError))
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        singleLine = true,
        isError = isError,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .accessibleTextInput(
                label = "Age",
                value = age,
                isError = isError,
                isRequired = true,
                helperText = "Ages 13-100 are supported. Required for workout recommendations."
            )
    )
}

/**
 * Performance-optimized validation message with <100ms animated feedback and live region support.
 */
@Composable
private fun AgeValidationMessage(
    validationResult: ValidationResult?
) {
    val accessibleColors = AccessibilityUtils.getAccessibleColors()
    
    OnboardingAnimationComponents.AnimatedValidationFeedback(
        isVisible = validationResult != null
    ) {
        when (validationResult) {
            is ValidationResult.Loading -> {
                // Show loading state - no message displayed during validation
            }
            is ValidationResult.Invalid -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .enhancedAccessibilitySemantics(
                            contentDescription = "Age validation error: ${validationResult.message}",
                            liveRegion = LiveRegionMode.Assertive
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error indicator",
                        tint = accessibleColors.error,
                        modifier = Modifier
                            .size(16.dp)
                            .then(OnboardingAnimations.shakeAnimation(true))
                    )
                    
                    Text(
                        text = validationResult.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleColors.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is ValidationResult.Valid -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .enhancedAccessibilitySemantics(
                            contentDescription = "Age validation success: Great! This age is supported",
                            liveRegion = LiveRegionMode.Polite
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success indicator",
                        tint = accessibleColors.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .then(OnboardingAnimations.successPulseAnimation(true))
                    )
                    
                    Text(
                        text = "Great! This age is supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleColors.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            null -> {
                // No validation message when field is empty
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AgeInputScreenPreview() {
    MaterialTheme {
        AgeInputScreen(
            currentAge = "25",
            onAgeChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun AgeInputScreenEmptyPreview() {
    MaterialTheme {
        AgeInputScreen(
            currentAge = "",
            onAgeChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Error State")
@Composable
private fun AgeInputScreenErrorPreview() {
    MaterialTheme {
        AgeInputScreen(
            currentAge = "10",
            onAgeChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun AgeInputScreenDarkPreview() {
    MaterialTheme {
        AgeInputScreen(
            currentAge = "25",
            onAgeChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}
