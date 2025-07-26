package com.example.liftrix.ui.common.error

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.liftrix.core.error.ErrorMapper
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Utility functions and composables for handling errors in UI components.
 * 
 * Provides integration between LiftrixError system and Compose UI components,
 * making it easy to display errors consistently throughout the application.
 * 
 * Updated for 5-color system with preserved red error colors (only exception to 5-color rule),
 * Persian Green for success states, and Tiffany Blue for warning/info states.
 */

/**
 * Feedback color system for Liftrix - preserves error red colors as exception to 5-color rule
 */
object FeedbackColors {
    // Error states (exception to 5-color rule)
    val Error = Color(0xFFFF4444)                    // Preserved red
    val ErrorContainer = Color(0xFFFFDAD6)           // Light error background
    val ErrorContainerDark = Color(0xFF93000A)       // Dark error background
    val OnError = Color.White                        // Text on error
    val OnErrorContainer = Color(0xFF410002)         // Text on error container
    
    // Success states using Persian Green
    val Success = LiftrixColors.PersianGreen         // #339989
    val SuccessContainer = LiftrixColors.PersianGreen.copy(alpha = 0.1f)
    val OnSuccess = Color.White
    val OnSuccessContainer = LiftrixColors.Night
    
    // Warning/Info states using Tiffany Blue
    val Warning = LiftrixColors.TiffanyBlue          // #7DE2D1
    val WarningContainer = LiftrixColors.TiffanyBlue.copy(alpha = 0.1f)
    val OnWarning = LiftrixColors.Night
    val OnWarningContainer = LiftrixColors.Night
}

/**
 * Handles error states automatically by displaying appropriate error UI components.
 * 
 * This composable provides a declarative way to handle errors in UI screens.
 * It automatically shows the appropriate error display based on the error type
 * and provides retry functionality when applicable.
 * 
 * Usage:
 * ```kotlin
 * val uiState by viewModel.uiState.collectAsState()
 * 
 * ErrorHandler(
 *     uiState = uiState,
 *     onRetry = { viewModel.retryLastAction() }
 * ) { data ->
 *     // Success content using data
 *     SuccessContent(data)
 * }
 * ```
 * 
 * @param uiState The current UI state containing potential error information
 * @param onRetry Optional callback for retry actions
 * @param onErrorDismiss Optional callback for dismissing error displays
 * @param showAsDialog Whether to show errors as dialogs instead of inline (default: false)
 * @param showAsSnackbar Whether to show errors as snackbars instead of inline (default: false)
 * @param content The content to display when the state is successful
 */
@Composable
fun <T> ErrorHandler(
    uiState: UiState<T>,
    onRetry: (() -> Unit)? = null,
    onErrorDismiss: (() -> Unit)? = null,
    showAsDialog: Boolean = false,
    showAsSnackbar: Boolean = false,
    content: @Composable (T) -> Unit
) {
    when (uiState) {
        is UiState.Success -> {
            content(uiState.data)
        }
        
        is UiState.Error -> {
            when {
                showAsDialog -> {
                    ErrorDialog(
                        error = uiState.error,
                        onDismiss = onErrorDismiss ?: {},
                        onRetry = onRetry
                    )
                }
                
                showAsSnackbar -> {
                    // Snackbar handling is done through SnackbarManager
                    // Content is still shown if previousData exists
                    if (uiState.previousData != null) {
                        content(uiState.previousData)
                    } else {
                        ErrorEmptyState(
                            error = uiState.error,
                            onRetry = onRetry
                        )
                    }
                }
                
                else -> {
                    if (uiState.previousData != null) {
                        // Show content with inline error
                        content(uiState.previousData)
                        ErrorDisplay(
                            error = uiState.error,
                            onRetry = onRetry,
                            onDismiss = onErrorDismiss,
                            compact = true
                        )
                    } else {
                        // Show error as empty state
                        ErrorEmptyState(
                            error = uiState.error,
                            onRetry = onRetry
                        )
                    }
                }
            }
        }
        
        UiState.Loading -> {
            // Loading state - content should handle this separately
            // This handler only deals with error states
        }
        
        is UiState.Empty -> {
            // Empty state - content should handle this separately
            // This handler only deals with error states
        }
        
        else -> {
            // Exhaustive catch-all for any other states
        }
    }
}

/**
 * Manages error snackbars with automatic display and retry functionality.
 * 
 * This class provides a centralized way to show error messages as snackbars
 * throughout the application. It integrates with the SnackbarHostState and
 * provides retry functionality for recoverable errors.
 * 
 * Usage:
 * ```kotlin
 * val snackbarManager = remember { ErrorSnackbarManager(snackbarHostState) }
 * 
 * LaunchedEffect(error) {
 *     if (error != null) {
 *         snackbarManager.showError(error) {
 *             // Retry action
 *             viewModel.retryLastAction()
 *         }
 *     }
 * }
 * ```
 */
class ErrorSnackbarManager(
    private val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope
) {
    
    /**
     * Shows an error as a snackbar with optional retry functionality.
     * 
     * @param error The LiftrixError to display
     * @param onRetry Optional callback for retry action
     * @param duration Duration for the snackbar display
     * @return SnackbarResult indicating user action (dismissed, action performed, etc.)
     */
    suspend fun showError(
        error: LiftrixError,
        onRetry: (() -> Unit)? = null,
        duration: SnackbarDuration = SnackbarDuration.Long
    ): SnackbarResult {
        val message = ErrorMapper.mapToUserMessage(error)
        val actionLabel = if (onRetry != null && ErrorMapper.shouldShowRetryButton(error)) {
            "Retry"
        } else null
        
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = duration
        )
        
        if (result == SnackbarResult.ActionPerformed && onRetry != null) {
            onRetry()
        }
        
        return result
    }
    
    /**
     * Shows a validation error with field-specific messaging.
     * 
     * @param validationError The ValidationError to display
     * @param duration Duration for the snackbar display
     */
    suspend fun showValidationError(
        validationError: LiftrixError.ValidationError,
        duration: SnackbarDuration = SnackbarDuration.Short
    ): SnackbarResult {
        return showError(validationError, onRetry = null, duration = duration)
    }
    
    /**
     * Shows a network error with automatic retry functionality.
     * 
     * @param networkError The NetworkError to display
     * @param onRetry Callback for retry action
     * @param duration Duration for the snackbar display
     */
    suspend fun showNetworkError(
        networkError: LiftrixError.NetworkError,
        onRetry: () -> Unit,
        duration: SnackbarDuration = SnackbarDuration.Indefinite
    ): SnackbarResult {
        return showError(networkError, onRetry = onRetry, duration = duration)
    }
}

/**
 * Creates and remembers an ErrorSnackbarManager instance.
 * 
 * @param snackbarHostState The SnackbarHostState to use for displaying snackbars
 * @return ErrorSnackbarManager instance tied to the current composition
 */
@Composable
fun rememberErrorSnackbarManager(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
): ErrorSnackbarManager {
    val coroutineScope = rememberCoroutineScope()
    return remember(snackbarHostState, coroutineScope) {
        ErrorSnackbarManager(snackbarHostState, coroutineScope)
    }
}

/**
 * Effect that automatically shows errors as snackbars when they occur.
 * 
 * This effect monitors a UI state and automatically displays errors as snackbars
 * when they occur. It's useful for screens where you want non-blocking error notifications.
 * 
 * @param uiState The UI state to monitor for errors
 * @param snackbarManager The ErrorSnackbarManager to use for displaying errors
 * @param onRetry Optional callback for retry actions
 * @param showForAllErrors Whether to show snackbars for all errors or only certain types
 */
@Composable
fun <T> AutoErrorSnackbar(
    uiState: UiState<T>,
    snackbarManager: ErrorSnackbarManager,
    onRetry: (() -> Unit)? = null,
    showForAllErrors: Boolean = false
) {
    LaunchedEffect(uiState) {
        if (uiState is UiState.Error) {
            val shouldShow = showForAllErrors || when (uiState.error) {
                is LiftrixError.NetworkError -> true
                is LiftrixError.ValidationError -> true
                is LiftrixError.DatabaseError -> uiState.previousData != null // Only if we have fallback content
                is LiftrixError.AuthenticationError -> false
                is LiftrixError.BusinessLogicError -> false
                is LiftrixError.CalculationError -> true
                is LiftrixError.ExportError -> true
                is LiftrixError.FileSystemError -> true
                is LiftrixError.NotFoundError -> true
                is LiftrixError.UnknownError -> false
                is LiftrixError.ConfigurationError -> true
                is LiftrixError.DataRetrievalError -> true
                is LiftrixError.CacheError -> true
                is LiftrixError.PermissionError -> false
            }
            
            if (shouldShow) {
                snackbarManager.showError(uiState.error, onRetry)
            }
        }
    }
}

/**
 * Maps UI state errors to user messages for quick display.
 * 
 * @param uiState The UI state containing potential error information
 * @return User-friendly error message string, or null if no error
 */
fun <T> UiState<T>.getErrorMessage(): String? {
    return if (this is UiState.Error) {
        ErrorMapper.mapToUserMessage(this.error)
    } else null
}

/**
 * Checks if the UI state has a recoverable error.
 * 
 * @param uiState The UI state to check
 * @return true if the state contains a recoverable error, false otherwise
 */
fun <T> UiState<T>.hasRecoverableError(): Boolean {
    return this is UiState.Error && this.error.isRecoverable
}

/**
 * Gets the error title for dialog display.
 * 
 * @param uiState The UI state containing potential error information
 * @return Error title string, or null if no error
 */
fun <T> UiState<T>.getErrorTitle(): String? {
    return if (this is UiState.Error) {
        ErrorMapper.mapToErrorTitle(this.error)
    } else null
}

/**
 * Checks if an error should be shown as a blocking dialog.
 * 
 * @param error The LiftrixError to evaluate
 * @return true if the error should be shown as a dialog, false otherwise
 */
fun shouldShowAsDialog(error: LiftrixError): Boolean {
    return when (error) {
        is LiftrixError.AuthenticationError -> true
        is LiftrixError.BusinessLogicError -> {
            // Show critical business logic errors as dialogs
            error.code in listOf(
                "PREMIUM_FEATURE_REQUIRED",
                "MAX_WORKOUTS_REACHED",
                "ACCOUNT_SUSPENDED",
                "INVALID_USER_STATE"
            )
        }
        is LiftrixError.UnknownError -> true
        is LiftrixError.NetworkError -> false
        is LiftrixError.ValidationError -> false
        is LiftrixError.DatabaseError -> false
        is LiftrixError.CalculationError -> false
        is LiftrixError.ExportError -> false
        is LiftrixError.FileSystemError -> false
        is LiftrixError.NotFoundError -> true
        is LiftrixError.ConfigurationError -> true
        is LiftrixError.DataRetrievalError -> false
        is LiftrixError.CacheError -> false
        is LiftrixError.PermissionError -> true
    }
}

/**
 * Checks if an error should be shown as a non-blocking snackbar.
 * 
 * @param error The LiftrixError to evaluate
 * @return true if the error should be shown as a snackbar, false otherwise
 */
fun shouldShowAsSnackbar(error: LiftrixError): Boolean {
    return when (error) {
        is LiftrixError.NetworkError -> true
        is LiftrixError.ValidationError -> true
        is LiftrixError.DatabaseError -> true
        is LiftrixError.AuthenticationError -> false
        is LiftrixError.CacheError -> true
        is LiftrixError.PermissionError -> false
        is LiftrixError.BusinessLogicError -> false
        is LiftrixError.CalculationError -> true
        is LiftrixError.ExportError -> true
        is LiftrixError.FileSystemError -> true
        is LiftrixError.NotFoundError -> true
        is LiftrixError.UnknownError -> false
        is LiftrixError.ConfigurationError -> false
        is LiftrixError.DataRetrievalError -> true
    }
}

/**
 * Composable that provides automatic error handling based on error type.
 * 
 * This composable automatically determines the best way to display an error
 * based on its type and characteristics. It provides a high-level, declarative
 * API for error handling.
 * 
 * @param uiState The UI state containing potential error information
 * @param onRetry Optional callback for retry actions
 * @param onErrorDismiss Optional callback for dismissing errors
 * @param snackbarManager Optional ErrorSnackbarManager for snackbar display
 * @param content The content to display when the state is successful
 */
@Composable
fun <T> AutoErrorHandler(
    uiState: UiState<T>,
    onRetry: (() -> Unit)? = null,
    onErrorDismiss: (() -> Unit)? = null,
    snackbarManager: ErrorSnackbarManager? = null,
    content: @Composable (T) -> Unit
) {
    when (uiState) {
        is UiState.Success -> {
            content(uiState.data)
        }
        
        is UiState.Error -> {
            val error = uiState.error
            
            when {
                shouldShowAsDialog(error) -> {
                    ErrorDialog(
                        error = error,
                        onDismiss = onErrorDismiss ?: {},
                        onRetry = onRetry
                    )
                    
                    // Still show content if available
                    if (uiState.previousData != null) {
                        content(uiState.previousData)
                    }
                }
                
                shouldShowAsSnackbar(error) && snackbarManager != null -> {
                    // Show snackbar and content
                    if (uiState.previousData != null) {
                        content(uiState.previousData)
                    } else {
                        ErrorEmptyState(
                            error = error,
                            onRetry = onRetry
                        )
                    }
                    
                    AutoErrorSnackbar(
                        uiState = uiState,
                        snackbarManager = snackbarManager,
                        onRetry = onRetry
                    )
                }
                
                else -> {
                    // Show inline error display
                    if (uiState.previousData != null) {
                        content(uiState.previousData)
                        ErrorDisplay(
                            error = error,
                            onRetry = onRetry,
                            onDismiss = onErrorDismiss,
                            compact = true
                        )
                    } else {
                        ErrorEmptyState(
                            error = error,
                            onRetry = onRetry
                        )
                    }
                }
            }
        }
        
        UiState.Loading -> {
            // Loading state should be handled by the calling composable
            // This handler focuses on error states only
        }
        
        is UiState.Empty -> {
            // Empty state should be handled by the calling composable
            // This handler focuses on error states only
        }
        
        else -> {
            // Exhaustive catch-all for any other states
        }
    }
}

/**
 * Error Message Component using preserved red colors (exception to 5-color rule)
 */
@Composable
fun ErrorMessage(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Uses preserved error colors (exception)
            containerColor = FeedbackColors.ErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = FeedbackColors.Error
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = FeedbackColors.OnErrorContainer,
                modifier = Modifier.weight(1f)
            )
            
            onDismiss?.let {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = FeedbackColors.OnErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * Success Message Component using Persian Green
 */
@Composable
fun SuccessMessage(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Uses Persian Green for success
            containerColor = FeedbackColors.SuccessContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = FeedbackColors.Success
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = FeedbackColors.OnSuccessContainer
            )
            
            onDismiss?.let {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = FeedbackColors.OnSuccessContainer
                    )
                }
            }
        }
    }
}

/**
 * Warning Message Component using Tiffany Blue
 */
@Composable
fun WarningMessage(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Uses Tiffany Blue for warnings
            containerColor = FeedbackColors.WarningContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Warning",
                tint = FeedbackColors.Warning
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = FeedbackColors.OnWarningContainer
            )
            
            onDismiss?.let {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = FeedbackColors.OnWarningContainer
                    )
                }
            }
        }
    }
}