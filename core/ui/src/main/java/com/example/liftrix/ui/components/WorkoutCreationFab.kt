package com.example.liftrix.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Floating Action Button for workout creation with modal selection.
 * 
 * This FAB is designed to trigger the workout creation modal that provides
 * users with different workout creation options (templates, custom workouts, etc.).
 * 
 * Features:
 * - Material3 design with proper theming
 * - Accessibility support with content descriptions
 * - Consistent styling with app theme
 * - Integration with workout creation modal system
 * 
 * @param onWorkoutCreationClick Callback triggered when FAB is clicked to open workout creation modal
 * @param modifier Modifier for styling and positioning
 */
@Composable
fun WorkoutCreationFab(
    onWorkoutCreationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onWorkoutCreationClick,
        modifier = modifier.semantics { 
            contentDescription = "Create new workout" 
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Create new workout"
        )
    }
} 
