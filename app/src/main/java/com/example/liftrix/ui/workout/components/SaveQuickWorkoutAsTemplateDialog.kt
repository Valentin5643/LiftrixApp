package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog component for asking users if they want to save their Quick workout as a template.
 * 
 * This dialog appears after completing a Quick workout and allows users to:
 * - Save the workout as a template with a custom name
 * - Skip saving and just complete the workout
 * 
 * The template will be saved to the "Uncategorized" folder by default.
 */
@Composable
fun SaveQuickWorkoutAsTemplateDialog(
    show: Boolean,
    defaultTemplateName: String = "My Workout",
    onSaveAsTemplate: (templateName: String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!show) return

    var templateName by remember(show) { mutableStateOf(defaultTemplateName) }
    var isValidName by remember(templateName) { 
        mutableStateOf(templateName.isNotBlank() && templateName.length <= 50) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Save as Template?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Would you like to save this workout as a template for future use?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = templateName,
                    onValueChange = { newName ->
                        if (newName.length <= 50) {
                            templateName = newName
                            isValidName = newName.isNotBlank()
                        }
                    },
                    label = { Text("Template Name") },
                    placeholder = { Text("Enter template name") },
                    singleLine = true,
                    isError = !isValidName && templateName.isNotEmpty(),
                    supportingText = {
                        if (!isValidName && templateName.isNotEmpty()) {
                            Text(
                                text = "Name cannot be empty and must be under 50 characters",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = "${templateName.length}/50 characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Template will be saved to \"Uncategorized\" folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Skip")
                }

                TextButton(
                    onClick = {
                        if (isValidName) {
                            onSaveAsTemplate(templateName.trim())
                        }
                    },
                    enabled = isValidName,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = if (isValidName) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Text(
                        text = "Save Template",
                        color = if (isValidName) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        },
        modifier = modifier
    )
}