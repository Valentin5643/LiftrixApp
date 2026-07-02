package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Folder Dialog Components
 * 
 * Contains dialog containers and entry point components for folder operations.
 * Focuses on dialog structure and basic user input collection.
 */

/**
 * Create Folder Dialog Component
 * 
 * Modal dialog for creating new folders with name validation.
 * Provides accessible form input with proper error handling.
 */
@Composable
fun CreateFolderDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (show) {
        var folderName by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        val hapticFeedback = LocalHapticFeedback.current
        val canCreate = folderName.trim().length in 3..30
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Create New Folder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics {
                                contentDescription = "Close create folder dialog"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Folder Name Input
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { newValue ->
                            folderName = newValue
                            isError = newValue.trim().length !in 3..30
                        },
                        label = { Text("Folder Name") },
                        placeholder = { Text("Enter folder name") },
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    text = "Name must be 3-30 characters",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Enter folder name, must be 3 to 30 characters"
                            }
                    )
                    
                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = {
                                val trimmedName = folderName.trim()
                                if (trimmedName.length in 3..30) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCreateFolder(trimmedName)
                                    onDismiss()
                                } else {
                                    isError = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            enabled = canCreate,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                            )
                        ) {
                            val createContentColor = if (canCreate) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                            }
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = createContentColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create",
                                color = createContentColor,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick Create Folder Button Component
 * 
 * Floating action button for quick folder creation access.
 * Provides consistent entry point to folder creation flow.
 */
@Composable
fun QuickCreateFolderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Create new workout folder"
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New folder",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        text = {
            Text(
                text = "New Folder",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

/**
 * Folder Edit Dialog Component
 * 
 * Main dialog container for folder editing operations.
 * Provides navigation structure for different edit modes.
 */
@Composable
fun FolderEditDialog(
    show: Boolean,
    folderId: String?,
    folderName: String,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAddWorkout: (String) -> Unit,
    onReorderWorkouts: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (show && folderId != null) {
        var currentEditMode by remember { mutableStateOf(FolderEditMode.MainMenu) }
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Edit Folder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics {
                                contentDescription = "Close edit folder dialog"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(LiftrixSpacing.cardPadding))
                    
                    // Dynamic Content Based on Edit Mode
                    when (currentEditMode) {
                        FolderEditMode.MainMenu -> {
                            FolderEditMainMenu(
                                onRenameClick = { currentEditMode = FolderEditMode.Rename },
                                onAddWorkoutClick = { currentEditMode = FolderEditMode.AddWorkout },
                                onReorderClick = { currentEditMode = FolderEditMode.Reorder },
                                onDeleteClick = { currentEditMode = FolderEditMode.Delete }
                            )
                        }
                        FolderEditMode.Rename -> {
                            FolderRenameForm(
                                currentName = folderName,
                                onSave = { newName -> 
                                    onRename(folderId, newName)
                                    onDismiss()
                                },
                                onCancel = { currentEditMode = FolderEditMode.MainMenu }
                            )
                        }
                        FolderEditMode.AddWorkout -> {
                            FolderAddWorkoutForm(
                                onAddWorkout = { 
                                    onAddWorkout(folderId)
                                    onDismiss()
                                },
                                onCancel = { currentEditMode = FolderEditMode.MainMenu }
                            )
                        }
                        FolderEditMode.Reorder -> {
                            FolderReorderForm(
                                onReorder = { 
                                    onReorderWorkouts(folderId)
                                    onDismiss()
                                },
                                onCancel = { currentEditMode = FolderEditMode.MainMenu }
                            )
                        }
                        FolderEditMode.Delete -> {
                            FolderDeleteConfirmation(
                                folderName = folderName,
                                onConfirm = { 
                                    onDelete(folderId)
                                    onDismiss()
                                },
                                onCancel = { currentEditMode = FolderEditMode.MainMenu }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Folder Edit Mode Enumeration
 * 
 * Defines the different edit modes available in the folder edit dialog.
 */
private enum class FolderEditMode {
    MainMenu,
    Rename,
    AddWorkout,
    Reorder,
    Delete
}
