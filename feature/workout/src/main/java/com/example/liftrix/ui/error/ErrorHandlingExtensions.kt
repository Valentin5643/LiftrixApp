package com.example.liftrix.ui.error

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard

/**
 * Comprehensive error handling UI components for consistent error display across the Liftrix app.
 * 
 * Provides user-friendly error messages with recovery options, graceful degradation patterns,
 * and accessibility-compliant error states following Material 3 design principles.
 * 
 * Key Features:
 * - Type-specific error handling with contextual recovery options
 * - Offline mode graceful degradation with data preservation
 * - Accessibility-compliant error announcements
 * - Visual consistency with UnifiedWorkoutCard design system
 * - Haptic feedback for error interactions
 */
object ErrorHandlingExtensions {
    
    /**
     * Main error display component with type-specific handling and recovery options.
     * 
     * Displays appropriate error UI based on the LiftrixError type, providing contextual
     * recovery actions and user-friendly messaging.
     */
    @Composable
    fun LiftrixErrorState(
        error: LiftrixError,
        onRetry: () -> Unit,
        onDismiss: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        
        UnifiedWorkoutCard(
            title = getErrorTitle(error),
            subtitle = getErrorSubtitle(error),
            modifier = modifier.semantics {
                contentDescription = "Error: ${error.message}. ${getErrorAccessibilityMessage(error)}"
                role = Role.Button
            }
        ) {
            when (error) {
                is LiftrixError.NetworkError -> {
                    NetworkErrorContent(
                        error = error,
                        onRetry = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onRetry()
                        },
                        onWorkOffline = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        }
                    )
                }
                is LiftrixError.DatabaseError -> {
                    DataCorruptionErrorContent(
                        error = error,
                        onRestore = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onRetry()
                        },
                        onContinue = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        }
                    )
                }
                is LiftrixError.ValidationError -> {
                    ValidationErrorContent(
                        error = error,
                        onFixInput = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        }
                    )
                }
                is LiftrixError.AuthenticationError -> {
                    AuthenticationErrorContent(
                        error = error,
                        onRetry = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onRetry()
                        },
                        onDismiss = onDismiss
                    )
                }
                else -> {
                    GenericErrorContent(
                        error = error,
                        onRetry = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onRetry()
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
    
    /**
     * Network error content with offline mode graceful degradation.
     */
    @Composable
    private fun NetworkErrorContent(
        error: LiftrixError.NetworkError,
        onRetry: () -> Unit,
        onWorkOffline: () -> Unit
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getNetworkErrorMessage(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "Your changes are saved locally and will sync when connection is restored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                SecondaryActionButton(
                    text = "Try Again",
                    onClick = onRetry,
                    leadingIcon = Icons.Default.Refresh
                )
                PrimaryActionButton(
                    text = "Continue Offline",
                    onClick = onWorkOffline,
                    leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.State.Completed
                )
            }
        }
    }
    
    /**
     * Data corruption error content with backup restoration options.
     */
    @Composable
    private fun DataCorruptionErrorContent(
        error: LiftrixError.DatabaseError,
        onRestore: () -> Unit,
        onContinue: () -> Unit
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Data integrity issue detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "Some workout data appears corrupted. We can restore from your last backup or continue with current data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                SecondaryActionButton(
                    text = "Restore Backup",
                    onClick = onRestore,
                    leadingIcon = Icons.Default.RestoreFromTrash
                )
                PrimaryActionButton(
                    text = "Continue Anyway",
                    onClick = onContinue,
                    leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Navigation.Forward
                )
            }
        }
    }
    
    /**
     * Validation error content with specific field guidance.
     */
    @Composable
    private fun ValidationErrorContent(
        error: LiftrixError.ValidationError,
        onFixInput: () -> Unit
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Please check your input",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Display specific validation violations
            error.violations.forEach { violation ->
                Text(
                    text = "• $violation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
            
            PrimaryActionButton(
                text = "Fix Input",
                onClick = onFixInput,
                leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Workflow.Edit
            )
        }
    }
    
    /**
     * Authentication error content with retry options.
     */
    @Composable
    private fun AuthenticationErrorContent(
        error: LiftrixError.AuthenticationError,
        onRetry: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Authentication error",
                    tint = LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Authentication required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "Please sign in again to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                TertiaryActionButton(
                    text = "Cancel",
                    onClick = onDismiss
                )
                PrimaryActionButton(
                    text = "Sign In",
                    onClick = onRetry,
                    leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Workflow.Profile
                )
            }
        }
    }
    
    /**
     * Generic error content for unspecified error types.
     */
    @Composable
    private fun GenericErrorContent(
        error: LiftrixError,
        onRetry: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "Please try again or contact support if the problem persists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                TertiaryActionButton(
                    text = "Dismiss",
                    onClick = onDismiss
                )
                if (error.isRecoverable) {
                    PrimaryActionButton(
                        text = "Try Again",
                        onClick = onRetry,
                        leadingIcon = Icons.Default.Refresh
                    )
                }
            }
        }
    }
    
    /**
     * Success state component for positive feedback.
     */
    @Composable
    fun SuccessState(
        title: String,
        message: String,
        onContinue: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        UnifiedWorkoutCard(
            title = title,
            subtitle = "Operation completed successfully",
            modifier = modifier.semantics {
                contentDescription = "Success: $message"
                role = Role.Button
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = LiftrixColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            PrimaryActionButton(
                text = "Continue",
                onClick = onContinue,
                leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Navigation.Forward
            )
        }
    }
    
    // Helper functions for error messaging
    private fun getErrorTitle(error: LiftrixError): String = when (error) {
        is LiftrixError.NetworkError -> "Connection Issue"
        is LiftrixError.DatabaseError -> "Data Issue"
        is LiftrixError.ValidationError -> "Input Error"
        is LiftrixError.AuthenticationError -> "Sign In Required"
        is LiftrixError.NotFoundError -> "Not Found"
        else -> "Something Went Wrong"
    }
    
    private fun getErrorSubtitle(error: LiftrixError): String = when (error) {
        is LiftrixError.NetworkError -> "Check your connection"
        is LiftrixError.DatabaseError -> "Data integrity issue detected"
        is LiftrixError.ValidationError -> "Please check your input"
        is LiftrixError.AuthenticationError -> "Authentication required"
        is LiftrixError.NotFoundError -> "Resource not found"
        else -> "Please try again"
    }
    
    private fun getNetworkErrorMessage(error: LiftrixError.NetworkError): String {
        return when {
            error.httpStatusCode != null -> "Server responded with error ${error.httpStatusCode}"
            error.networkType != null -> "Connection failed via ${error.networkType}"
            else -> "Unable to connect to server"
        }
    }
    
    private fun getErrorAccessibilityMessage(error: LiftrixError): String = when (error) {
        is LiftrixError.NetworkError -> "Network connection failed. You can try again or continue working offline."
        is LiftrixError.DatabaseError -> "Data issue detected. You can restore from backup or continue."
        is LiftrixError.ValidationError -> "Input validation failed for ${error.field}. Please fix the errors to continue."
        is LiftrixError.AuthenticationError -> "Authentication required to continue. Please sign in."
        else -> "An error occurred. You can try again or dismiss this message."
    }
}
