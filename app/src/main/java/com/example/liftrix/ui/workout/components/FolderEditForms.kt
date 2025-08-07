package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Folder Edit Forms
 * 
 * Contains specialized form components for folder editing operations.
 * Handles user input validation and submission for specific edit actions.
 */

/**
 * Folder Rename Form Component
 * 
 * Form for renaming folder with validation and error handling.
 * Provides immediate feedback on name validity.
 */
@Composable
fun FolderRenameForm(
    currentName: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newName by remember { mutableStateOf(currentName) }
    var isError by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardPadding)
    ) {
        Text(
            text = "Rename Folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Folder Name Input Field
        OutlinedTextField(
            value = newName,
            onValueChange = { value ->
                newName = value
                isError = value.trim().length !in 3..30 || value.trim() == currentName.trim()
            },
            label = { Text("Folder Name") },
            placeholder = { Text("Enter new folder name") },
            isError = isError,
            supportingText = {
                when {
                    newName.trim().length !in 3..30 -> {
                        Text(
                            text = "Name must be 3-30 characters",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    newName.trim() == currentName.trim() -> {
                        Text(
                            text = "Name must be different from current name",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = "Choose a unique, descriptive name",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Enter new folder name, must be 3 to 30 characters and different from current name"
                }
        )
        
        // Form Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing, Alignment.End)
        ) {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
            
            PrimaryActionButton(
                text = "Save",
                onClick = {
                    val trimmedName = newName.trim()
                    if (trimmedName.length in 3..30 && trimmedName != currentName.trim()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(trimmedName)
                    } else {
                        isError = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                enabled = newName.trim().length in 3..30 && newName.trim() != currentName.trim()
            )
        }
    }
}

/**
 * Folder Add Workout Form Component
 * 
 * Form for adding workouts to folder with action guidance.
 * Provides navigation to workout creation or move operations.
 */
@Composable
fun FolderAddWorkoutForm(
    onAddWorkout: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardPadding)
    ) {
        Text(
            text = "Add Workout to Folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Information Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LiftrixSpacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(LiftrixSpacing.elementSpacing))
                
                Column {
                    Text(
                        text = "Create New Workout",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "This will open the workout creation flow and automatically assign the new workout to this folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Form Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing, Alignment.End)
        ) {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
            
            PrimaryActionButton(
                text = "Create Workout",
                onClick = onAddWorkout
            )
        }
    }
}

/**
 * Folder Reorder Form Component
 * 
 * Form for reordering workouts within folder.
 * Provides guidance on drag-and-drop reordering functionality.
 */
@Composable
fun FolderReorderForm(
    onReorder: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardPadding)
    ) {
        Text(
            text = "Reorder Workouts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Information Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LiftrixSpacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Reorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(LiftrixSpacing.elementSpacing))
                
                Column {
                    Text(
                        text = "Drag and Drop Reordering",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "This will enable drag-and-drop mode for reordering workouts. Hold and drag any workout to change its position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Form Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing, Alignment.End)
        ) {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
            
            PrimaryActionButton(
                text = "Enable Reordering",
                onClick = onReorder
            )
        }
    }
}

/**
 * Folder Delete Confirmation Component
 * 
 * Destructive action confirmation form with clear warning messaging.
 * Provides safety guard against accidental folder deletion.
 */
@Composable
fun FolderDeleteConfirmation(
    folderName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardPadding)
    ) {
        Text(
            text = "Delete Folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error
        )
        
        // Warning Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LiftrixSpacing.cardPadding),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(LiftrixSpacing.elementSpacing))
                
                Column {
                    Text(
                        text = "Permanent Action",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "This will permanently delete the folder \"$folderName\". All workouts in this folder will be moved to your main workout list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Form Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing, Alignment.End)
        ) {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
            
            Button(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Confirm deletion of folder $folderName. This action cannot be undone."
                }
            ) {
                Text(
                    text = "Delete Folder",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}