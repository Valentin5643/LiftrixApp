package com.example.liftrix.ui.common.error

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.core.error.ErrorMapper
import com.example.liftrix.core.error.ErrorSeverity
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.components.buttons.ButtonVariant
import com.example.liftrix.ui.components.buttons.LiftrixButton
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixTokens

/**
 * Main error display component for showing errors in a consistent, user-friendly way.
 * 
 * This component provides a complete error display with icon, message, recovery suggestions,
 * and optional retry functionality. It follows the Liftrix design system and accessibility
 * guidelines.
 * 
 * @param error The LiftrixError to display
 * @param onRetry Optional callback for retry action (shown only for recoverable errors)
 * @param onDismiss Optional callback for dismissing the error display
 * @param modifier Modifier for styling the component
 * @param showIcon Whether to show the error type icon (default: true)
 * @param showSuggestions Whether to show recovery suggestions (default: true)
 * @param compact Whether to use compact layout for limited space (default: false)
 */
@Composable
fun ErrorDisplay(
    error: LiftrixError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    showSuggestions: Boolean = true,
    compact: Boolean = false
) {
    val severity = ErrorMapper.getErrorSeverity(error)
    val shouldShowRetry = onRetry != null && ErrorMapper.shouldShowRetryButton(error)
    val suggestions = if (showSuggestions) ErrorMapper.getRecoverySuggestion(error) else null
    
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_display")
            .semantics {
                contentDescription = "Error: ${ErrorMapper.mapToUserMessage(error)}"
            },
        colors = CardDefaults.cardColors(
            containerColor = getErrorBackgroundColor(severity)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                if (compact) LiftrixTokens.Spacing.Medium else LiftrixTokens.Spacing.Large
            ),
            verticalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Small)
        ) {
            // Error header with icon and message
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Medium),
                verticalAlignment = Alignment.Top
            ) {
                if (showIcon) {
                    Icon(
                        imageVector = getErrorIcon(error),
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                        tint = getErrorContentColor(severity)
                    )
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(
                        if (compact) LiftrixTokens.Spacing.ExtraSmall else LiftrixTokens.Spacing.Small
                    )
                ) {
                    Text(
                        text = ErrorMapper.mapToUserMessage(error),
                        style = if (compact) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = getErrorContentColor(severity),
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Recovery suggestion
                    if (suggestions != null && !compact) {
                        Text(
                            text = suggestions,
                            style = MaterialTheme.typography.bodySmall,
                            color = getErrorContentColor(severity).copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Dismiss button
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics {
                            contentDescription = "Dismiss error"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff, // Using CloudOff as close icon substitute
                            contentDescription = null,
                            tint = getErrorContentColor(severity).copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            // Retry button
            if (shouldShowRetry && !compact) {
                Spacer(modifier = Modifier.height(LiftrixTokens.Spacing.Small))
                
                LiftrixButton(
                    onClick = onRetry!!,
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.testTag("error_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(LiftrixTokens.Spacing.Small))
                    Text(text = "Try Again")
                }
            }
        }
    }
}

/**
 * Error snackbar component for non-blocking error notifications.
 * 
 * Displays errors as snackbars at the bottom of the screen without interrupting
 * the user's workflow. Suitable for non-critical errors that don't require
 * immediate attention.
 * 
 * @param error The LiftrixError to display
 * @param onDismiss Callback for dismissing the snackbar
 * @param onRetry Optional callback for retry action
 * @param modifier Modifier for styling the component
 */
@Composable
fun ErrorSnackbar(
    error: LiftrixError,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val severity = ErrorMapper.getErrorSeverity(error)
    val shouldShowRetry = onRetry != null && ErrorMapper.shouldShowRetryButton(error)
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(LiftrixTokens.Spacing.Medium)
            .testTag("error_snackbar"),
        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium),
        color = getErrorBackgroundColor(severity),
        shadowElevation = LiftrixTokens.Elevation.Level2
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixTokens.Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getErrorIcon(error),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = getErrorContentColor(severity)
            )
            
            Text(
                text = ErrorMapper.mapToUserMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = getErrorContentColor(severity),
                modifier = Modifier.weight(1f)
            )
            
            if (shouldShowRetry) {
                TextButton(
                    onClick = onRetry!!,
                    modifier = Modifier.semantics {
                        contentDescription = "Retry action"
                    }
                ) {
                    Text(
                        text = "Retry",
                        color = getErrorContentColor(severity)
                    )
                }
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = "Dismiss error notification"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff, // Using CloudOff as close substitute
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = getErrorContentColor(severity).copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Error dialog component for critical errors that require user attention.
 * 
 * Displays errors in a modal dialog that blocks user interaction until
 * acknowledged. Suitable for critical errors that prevent the user from
 * continuing their current task.
 * 
 * @param error The LiftrixError to display
 * @param onDismiss Callback for dismissing the dialog
 * @param onRetry Optional callback for retry action
 * @param showSuggestions Whether to show recovery suggestions (default: true)
 */
@Composable
fun ErrorDialog(
    error: LiftrixError,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    showSuggestions: Boolean = true
) {
    val severity = ErrorMapper.getErrorSeverity(error)
    val shouldShowRetry = onRetry != null && ErrorMapper.shouldShowRetryButton(error)
    val suggestions = if (showSuggestions) ErrorMapper.getRecoverySuggestion(error) else null
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = getErrorIcon(error),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = getErrorContentColor(severity)
            )
        },
        title = {
            Text(
                text = ErrorMapper.mapToErrorTitle(error),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Medium)
            ) {
                Text(
                    text = ErrorMapper.mapToUserMessage(error),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                if (suggestions != null) {
                    Text(
                        text = suggestions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (shouldShowRetry) {
                LiftrixButton(
                    onClick = onRetry!!,
                    variant = ButtonVariant.Primary
                ) {
                    Text(text = "Try Again")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (shouldShowRetry) "Cancel" else "OK")
            }
        },
        modifier = Modifier.testTag("error_dialog")
    )
}

/**
 * Inline error message component for form validation and field-level errors.
 * 
 * Displays validation errors directly below form fields or input components.
 * Provides immediate feedback for user input validation without blocking
 * the interface.
 * 
 * @param error The LiftrixError to display (usually ValidationError)
 * @param visible Whether the error should be visible (for animations)
 * @param modifier Modifier for styling the component
 */
@Composable
fun InlineErrorMessage(
    error: LiftrixError?,
    visible: Boolean = error != null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && error != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LiftrixTokens.Spacing.ExtraSmall)
                    .testTag("inline_error_message"),
                horizontalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.ExtraSmall),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LiftrixTokens.SemanticColors.Error
                )
                
                Text(
                    text = ErrorMapper.mapToUserMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixTokens.SemanticColors.Error,
                    modifier = Modifier.semantics {
                        contentDescription = "Error: ${ErrorMapper.mapToUserMessage(error)}"
                    }
                )
            }
        }
    }
}

/**
 * Empty state with error context for when data loading fails.
 * 
 * Displays an empty state with error information when data cannot be loaded.
 * Provides context about why content is unavailable and offers recovery actions.
 * 
 * @param error The LiftrixError explaining why content is unavailable
 * @param onRetry Optional callback for retry action
 * @param emptyStateMessage Custom message for the empty state
 * @param modifier Modifier for styling the component
 */
@Composable
fun ErrorEmptyState(
    error: LiftrixError,
    onRetry: (() -> Unit)? = null,
    emptyStateMessage: String = "Unable to load content",
    modifier: Modifier = Modifier
) {
    val severity = ErrorMapper.getErrorSeverity(error)
    val shouldShowRetry = onRetry != null && ErrorMapper.shouldShowRetryButton(error)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(LiftrixTokens.Spacing.ExtraLarge)
            .testTag("error_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Large)
        ) {
            // Large error icon
            Icon(
                imageVector = getErrorIcon(error),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = getErrorContentColor(severity).copy(alpha = 0.6f)
            )
            
            // Empty state content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Medium)
            ) {
                Text(
                    text = emptyStateMessage,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = ErrorMapper.mapToUserMessage(error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            // Retry button
            if (shouldShowRetry) {
                LiftrixButton(
                    onClick = onRetry!!,
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.testTag("error_empty_state_retry")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(LiftrixTokens.Spacing.Small))
                    Text(text = "Try Again")
                }
            }
        }
    }
}

/**
 * Gets the appropriate icon for the error type.
 */
private fun getErrorIcon(error: LiftrixError): ImageVector {
    return when (error) {
        is LiftrixError.NetworkError -> Icons.Default.CloudOff
        is LiftrixError.ValidationError -> Icons.Default.ErrorOutline
        is LiftrixError.AuthenticationError -> Icons.Default.Lock
        is LiftrixError.DatabaseError -> Icons.Default.Storage
        is LiftrixError.BusinessLogicError -> Icons.Default.Rule
        is LiftrixError.UnknownError -> Icons.Default.HelpOutline
        is LiftrixError.CalculationError -> Icons.Default.Calculate
        is LiftrixError.ExportError -> Icons.Default.FileDownload
        is LiftrixError.FileSystemError -> Icons.Default.FolderOff
        is LiftrixError.NotFoundError -> Icons.Default.ErrorOutline
        is LiftrixError.ConfigurationError -> Icons.Default.ErrorOutline
        is LiftrixError.DataRetrievalError -> Icons.Default.CloudOff
    }
}

/**
 * Gets the background color for the error severity level.
 */
@Composable
private fun getErrorBackgroundColor(severity: ErrorSeverity): Color {
    return when (severity) {
        ErrorSeverity.INFO -> LiftrixTokens.ColorRoles.SecondaryContainer
        ErrorSeverity.WARNING -> LiftrixTokens.SemanticColors.Warning.copy(alpha = 0.1f)
        ErrorSeverity.ERROR -> LiftrixTokens.ColorRoles.ErrorContainer
    }
}

/**
 * Gets the content color for the error severity level.
 */
@Composable
private fun getErrorContentColor(severity: ErrorSeverity): Color {
    return when (severity) {
        ErrorSeverity.INFO -> LiftrixTokens.ColorRoles.OnSecondaryContainer
        ErrorSeverity.WARNING -> LiftrixTokens.SemanticColors.Warning
        ErrorSeverity.ERROR -> LiftrixTokens.ColorRoles.OnErrorContainer
    }
}