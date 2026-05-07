package com.example.liftrix.ui.migration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Dialog explaining terminology changes to existing users
 * Provides options to accept new terminology or continue with legacy terms
 */
@Composable
fun MigrationExplanationDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onUseLegacyTerminology: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Updated Workout Experience",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                Text(
                    text = "We've updated our language to make workout creation clearer!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "What's changed:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "• 'Templates' are now 'Your Workouts'\n" +
                          "• 'Create Template' is now 'Creating a workout'\n" +
                          "• 'Start from Template' is now 'Start Workout'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your existing workouts and data remain exactly the same!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(LiftrixSpacing.cardPadding)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(text = "Got it!")
            }
        },
        dismissButton = {
            TextButton(onClick = onUseLegacyTerminology) {
                Text(text = "Use old labels")
            }
        },
        modifier = modifier
    )
}

/**
 * State holder for MigrationExplanationDialog
 */
@Composable
fun rememberMigrationDialogState(
    initiallyVisible: Boolean = false
): MigrationDialogState {
    return remember { MigrationDialogState(initiallyVisible) }
}

class MigrationDialogState(
    initiallyVisible: Boolean
) {
    var isVisible by mutableStateOf(initiallyVisible)
        private set
    
    fun show() {
        isVisible = true
    }
    
    fun hide() {
        isVisible = false
    }
}
