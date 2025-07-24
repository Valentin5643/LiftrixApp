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
                // Persian Green for action text (primary)
                actionColor = MaterialTheme.colorScheme.primary,
                // Snackbar background uses surface colors
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // Dismiss icon uses surface variant
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
} 