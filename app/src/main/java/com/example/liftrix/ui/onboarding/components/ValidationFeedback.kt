package com.example.liftrix.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Reusable validation error text component with consistent styling and animations.
 * 
 * @param message The error message to display
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun ValidationErrorText(
    message: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message.isNotBlank(),
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Validation error: $message"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Validation error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Reusable validation success indicator with consistent styling and animations.
 * 
 * @param message The success message to display
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun ValidationSuccessIndicator(
    message: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message.isNotBlank(),
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Validation success: $message"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Validation success",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Reusable validation loading indicator for async validation operations.
 * 
 * @param message The loading message to display
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun ValidationLoadingIndicator(
    message: String = "Validating...",
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message.isNotBlank(),
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Validation in progress: $message"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * High-level input field error component that automatically displays the appropriate
 * validation state (error, success, loading, or hidden) based on ValidationResult.
 * 
 * This is the recommended component for most use cases as it handles state transitions
 * automatically and provides consistent messaging.
 * 
 * @param validationResult The current validation state
 * @param successMessage Custom success message (optional)
 * @param loadingMessage Custom loading message (optional)
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun InputFieldError(
    validationResult: ValidationResult?,
    successMessage: String? = null,
    loadingMessage: String? = null,
    modifier: Modifier = Modifier
) {
    when (validationResult) {
        is ValidationResult.Invalid -> {
            ValidationErrorText(
                message = validationResult.message,
                modifier = modifier
            )
        }
        is ValidationResult.Valid -> {
            successMessage?.let { message ->
                ValidationSuccessIndicator(
                    message = message,
                    modifier = modifier
                )
            }
        }
        is ValidationResult.Loading -> {
            ValidationLoadingIndicator(
                message = loadingMessage ?: "Validating...",
                modifier = modifier
            )
        }
        null -> {
            // No validation feedback when result is null
        }
    }
}

// Preview components for design verification
@Preview(showBackground = true)
@Composable
private fun ValidationErrorTextPreview() {
    LiftrixTheme {
        ValidationErrorText(
            message = "Age must be at least 13 years old"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ValidationSuccessIndicatorPreview() {
    LiftrixTheme {
        ValidationSuccessIndicator(
            message = "Great! This age is supported"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ValidationLoadingIndicatorPreview() {
    LiftrixTheme {
        ValidationLoadingIndicator(
            message = "Checking weight validity..."
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InputFieldErrorPreview() {
    LiftrixTheme {
        InputFieldError(
            validationResult = ValidationResult.Invalid("Weight must be a positive value"),
            successMessage = "Weight looks good!"
        )
    }
} 
