package com.example.liftrix.ui.common.error

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.liftrix.core.error.ErrorMapper
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.state.UiState

/**
 * Utility functions and composables for handling errors in UI components.
 * 
 * Provides integration between LiftrixError system and Compose UI components,
 * making it easy to display errors consistently throughout the application.
 */

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
        
        is UiState.Loading -> {
            // Loading state - content should handle this separately
            // This handler only deals with error states
        }
        
        is UiState.Empty -> {
            // Empty state - content should handle this separately
            // This handler only deals with error states
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
                is LiftrixError.UnknownError -> false
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
        is LiftrixError.BusinessLogicError -> false
        is LiftrixError.CalculationError -> true
        is LiftrixError.ExportError -> true
        is LiftrixError.FileSystemError -> true
        is LiftrixError.UnknownError -> false
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
        
        is UiState.Loading,
        is UiState.Empty -> {
            // These states should be handled by the calling composable
            // This handler focuses on error states only
        }
    }
}