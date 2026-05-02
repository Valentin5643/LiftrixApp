package com.example.liftrix.ui.workout.active.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.format.DateTimeFormatter

/**
 * Dialog for saving a completed workout with two options:
 * 1. Save as One-Time Workout (for history tracking only)
 * 2. Save as Template (reusable structure)
 * 
 * Key features:
 * - Clear explanation of the difference between options
 * - Template naming and description when saving as template
 * - Data filtering information for templates (removes session-specific data)
 * - Modern Material 3 design with clear visual hierarchy
 * 
 * @param session The active workout session to save
 * @param isVisible Whether the dialog is visible
 * @param onSaveAsWorkout Callback to save as one-time workout
 * @param onSaveAsTemplate Callback to save as template with name and description
 * @param onDismiss Callback when dialog is dismissed
 * @param modifier Modifier for styling
 */
@Composable
fun SaveWorkoutDialog(
    session: UnifiedWorkoutSession?,
    isVisible: Boolean,
    onSaveAsWorkout: () -> Unit,
    onSaveAsTemplate: (name: String, description: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible || session == null) return
    
    var selectedOption by remember { mutableStateOf(SaveOption.ONE_TIME) }
    var templateName by remember { mutableStateOf(session.name) }
    var templateDescription by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = "Save workout",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Workout",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Workout summary
                WorkoutSummaryCard(session = session)
                
                // Save options
                Text(
                    text = "How would you like to save this workout?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                // Option 1: One-time workout
                SaveOptionCard(
                    option = SaveOption.ONE_TIME,
                    isSelected = selectedOption == SaveOption.ONE_TIME,
                    onSelect = { selectedOption = SaveOption.ONE_TIME },
                    icon = Icons.Default.History,
                    title = "Save as One-Time Workout",
                    description = "Saves this workout to your history for tracking progress. Cannot be reused.",
                    details = "• Added to workout history\n• Includes all session data (times, notes)\n• Perfect for tracking progress"
                )
                
                // Option 2: Template
                SaveOptionCard(
                    option = SaveOption.TEMPLATE,
                    isSelected = selectedOption == SaveOption.TEMPLATE,
                    onSelect = { selectedOption = SaveOption.TEMPLATE },
                    icon = Icons.Default.Assignment,
                    title = "Save as Template",
                    description = "Creates a reusable workout template that you can start again later.",
                    details = "• Reusable workout structure\n• Removes session data (times, notes)\n• Great for regular routines"
                )
                
                // Template details (only shown when template option is selected)
                if (selectedOption == SaveOption.TEMPLATE) {
                    TemplateDetailsForm(
                        templateName = templateName,
                        onTemplateNameChange = { templateName = it },
                        templateDescription = templateDescription,
                        onTemplateDescriptionChange = { templateDescription = it }
                    )
                }
                
                // Information card about data filtering for templates
                if (selectedOption == SaveOption.TEMPLATE) {
                    InfoCard(
                        text = "When saved as a template, session-specific data like timer duration and notes will be removed to create a clean, reusable workout structure."
                    )
                }
            }
        },
        confirmButton = {
            when (selectedOption) {
                SaveOption.ONE_TIME -> {
                    Button(
                        onClick = {
                            onSaveAsWorkout()
                            onDismiss()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Save as one-time workout"
                        }
                    ) {
                        Text("Save to History")
                    }
                }
                SaveOption.TEMPLATE -> {
                    Button(
                        onClick = {
                            if (templateName.isNotBlank()) {
                                onSaveAsTemplate(
                                    templateName.trim(),
                                    templateDescription.trim().takeIf { it.isNotBlank() }
                                )
                                onDismiss()
                            }
                        },
                        enabled = templateName.isNotBlank(),
                        modifier = Modifier.semantics {
                            contentDescription = "Save as template"
                        }
                    ) {
                        Text("Create Template")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

/**
 * Card displaying workout summary information
 */
@Composable
private fun WorkoutSummaryCard(
    session: UnifiedWorkoutSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${session.exercises.size} exercises",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${session.getCompletedSetsCount()}/${session.getTotalSetsCount()} sets completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatSessionDuration(session.getTotalDurationSeconds()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Card for a save option with selection state
 */
@Composable
private fun SaveOptionCard(
    option: SaveOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    details: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title option. $description"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else {
            CardDefaults.outlinedCardBorder()
        },
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    }
                )
            }
        }
    }
}

/**
 * Form for template name and description
 */
@Composable
private fun TemplateDetailsForm(
    templateName: String,
    onTemplateNameChange: (String) -> Unit,
    templateDescription: String,
    onTemplateDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Template Details",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        OutlinedTextField(
            value = templateName,
            onValueChange = onTemplateNameChange,
            label = { Text("Template Name") },
            placeholder = { Text("e.g., Push Day, Full Body Workout") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = templateName.isBlank()
        )
        
        OutlinedTextField(
            value = templateDescription,
            onValueChange = onTemplateDescriptionChange,
            label = { Text("Description (Optional)") },
            placeholder = { Text("Brief description of this workout template") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
    }
}

/**
 * Information card with icon and text
 */
@Composable
private fun InfoCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Save options enum
 */
private enum class SaveOption {
    ONE_TIME,
    TEMPLATE
}

/**
 * Formats session duration in seconds to readable format
 */
private fun formatSessionDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

@Preview(showBackground = true)
@Composable
private fun SaveWorkoutDialogPreview() {
    LiftrixTheme {
        // Mock session for preview
        val mockSession = UnifiedWorkoutSession.createBlank(
            userId = "user123",
            name = "Push Day Workout"
        ).copy(
            exercises = emptyList() // Simplified for preview
        )
        
        SaveWorkoutDialog(
            session = mockSession,
            isVisible = true,
            onSaveAsWorkout = {},
            onSaveAsTemplate = { _, _ -> },
            onDismiss = {}
        )
    }
}
