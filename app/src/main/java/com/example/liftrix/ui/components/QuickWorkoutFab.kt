package com.example.liftrix.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Floating Action Button for quick workout creation with template selection
 * 
 * @param onQuickWorkoutClick Callback triggered when FAB is clicked
 * @param modifier Modifier for customization
 * @param isExtended Whether to show extended FAB with text or compact FAB with icon only
 */
@Composable
fun QuickWorkoutFab(
    onQuickWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExtended: Boolean = true
) {
    if (isExtended) {
        ExtendedFloatingActionButton(
            onClick = onQuickWorkoutClick,
            modifier = modifier.semantics { 
                contentDescription = "Start quick workout" 
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )
            Text(
                text = "Quick Workout",
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        FloatingActionButton(
            onClick = onQuickWorkoutClick,
            modifier = modifier.semantics { 
                contentDescription = "Start quick workout" 
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Start quick workout"
            )
        }
    }
} 