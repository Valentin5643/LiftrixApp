package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Dialog shown when user tries to start a new workout while having an active workout session.
 * 
 * Provides options to:
 * - Continue the existing workout
 * - Discard and start a new workout
 * - Cancel the action
 *
 * @param workoutName Name of the current active workout
 * @param onContinueWorkout Callback when user chooses to continue the existing workout
 * @param onDiscardAndStartNew Callback when user chooses to discard current and start new
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun ActiveWorkoutDialog(
    workoutName: String,
    onContinueWorkout: () -> Unit,
    onDiscardAndStartNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Active Workout Detected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "You have an active workout in progress:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                
                // Show the active workout name in a card for emphasis
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = workoutName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LiftrixSpacing.medium),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                
                Text(
                    text = "What would you like to do?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Primary action - Continue workout
                TextButton(
                    onClick = onContinueWorkout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Continue Workout",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cancel button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Discard and start new button
                    TextButton(
                        onClick = onDiscardAndStartNew,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Start New",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    )
}

/**
 * Simple confirmation dialog for active workout with minimal options.
 * Used in contexts where we only need continue/discard options.
 *
 * @param workoutName Name of the current active workout
 * @param onContinue Callback when user chooses to continue
 * @param onDiscard Callback when user chooses to discard
 */
@Composable
fun SimpleActiveWorkoutDialog(
    workoutName: String,
    onContinue: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinue, // Default to continuing on dismiss
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Continue Active Workout?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "You have \"$workoutName\" in progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                Text(
                    text = "Would you like to continue this workout or start a new one?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard
            ) {
                Text(
                    text = "Start New",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onContinue
            ) {
                Text(
                    text = "Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}