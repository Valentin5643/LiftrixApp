package com.example.liftrix.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Custom snackbar host for Liftrix app with new 5-color system styling
 * Uses Persian Green for actions and Snow/Night for content based on theme
 */
@Composable
fun LiftrixSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                // Action color uses inverse primary for proper contrast
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                // Snackbar background uses inverse surface per Material 3 guidelines
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                // Dismiss icon uses inverse colors for consistency
                dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
            )
        }
    )
} 