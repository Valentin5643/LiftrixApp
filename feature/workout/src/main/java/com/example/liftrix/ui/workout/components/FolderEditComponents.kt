package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Folder Edit Components
 * 
 * Contains UI components for folder editing operations and menu navigation.
 * Provides structured interaction patterns for folder management.
 */

/**
 * Folder Edit Main Menu Component
 * 
 * Primary menu for folder edit operations with accessible action buttons.
 * Organizes edit functions in a clear hierarchical structure.
 */
@Composable
fun FolderEditMainMenu(
    onRenameClick: () -> Unit,
    onAddWorkoutClick: () -> Unit,
    onReorderClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.listItemSpacing)
    ) {
        Text(
            text = "Edit Options",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = LiftrixSpacing.elementSpacing)
        )
        
        // Rename Folder Action
        FolderEditActionButton(
            icon = Icons.Filled.Edit,
            title = "Rename Folder",
            description = "Change the folder name",
            onClick = onRenameClick,
            buttonColor = ButtonDefaults.outlinedButtonColors()
        )
        
        // Add Workout Action
        FolderEditActionButton(
            icon = Icons.Filled.Add,
            title = "Add Workout",
            description = "Create or move workouts to this folder",
            onClick = onAddWorkoutClick,
            buttonColor = ButtonDefaults.outlinedButtonColors()
        )
        
        // Reorder Workouts Action
        FolderEditActionButton(
            icon = Icons.Filled.Reorder,
            title = "Reorder Workouts",
            description = "Change the order of workouts in this folder",
            onClick = onReorderClick,
            buttonColor = ButtonDefaults.outlinedButtonColors()
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
        
        // Delete Folder Action - Separate section for destructive action
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
            FolderEditActionButton(
                icon = Icons.Filled.Delete,
                title = "Delete Folder",
                description = "Permanently remove this folder",
                onClick = onDeleteClick,
                buttonColor = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.padding(LiftrixSpacing.elementSpacing)
            )
        }
    }
}

/**
 * Folder Edit Action Button Component
 * 
 * Reusable action button for folder edit operations with consistent styling.
 * Provides accessible interaction with proper visual hierarchy.
 */
@Composable
fun FolderEditActionButton(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonColor: ButtonColors = ButtonDefaults.outlinedButtonColors()
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    OutlinedButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics {
                contentDescription = "$title: $description"
            },
        colors = buttonColor,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(LiftrixSpacing.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.cardPadding))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = buttonColor.contentColor
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = buttonColor.contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
