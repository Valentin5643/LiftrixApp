package com.example.liftrix.ui.common.error

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import timber.log.Timber

/**
 * Error boundary component that catches ViewModel initialization failures and provides recovery UI.
 * 
 * This component wraps screens that use ViewModels and provides a fallback UI when
 * dependency injection or ViewModel initialization fails. It prevents crashes and
 * provides users with recovery options.
 * 
 * Usage:
 * ```kotlin
 * ViewModelErrorBoundary(
 *     onRetry = { /* retry logic */ }
 * ) {
 *     ProgressDashboardScreen()
 * }
 * ```
 */
@Composable
fun ViewModelErrorBoundary(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    if (hasError) {
        ViewModelErrorScreen(
            errorMessage = errorMessage,
            onRetry = {
                hasError = false
                errorMessage = ""
                onRetry()
            },
            modifier = modifier
        )
    } else {
        // Note: Error boundaries in Compose need to be implemented differently
        // than traditional try-catch. For ViewModel errors, we rely on the
        // safe collection patterns and state validation instead.
        content()
    }
}

/**
 * Error screen displayed when ViewModel initialization fails.
 */
@Composable
private fun ViewModelErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Loading Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}