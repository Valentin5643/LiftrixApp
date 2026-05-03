package com.example.liftrix.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

enum class AIReportReason(val displayText: String, val description: String) {
    HARMFUL_MEDICAL("Dangerous Medical Advice", "AI suggested ignoring injuries or unsafe practices"),
    MISINFORMATION("Misinformation", "Factually incorrect fitness or health information"),
    INAPPROPRIATE("Inappropriate Content", "Offensive or inappropriate language"),
    OTHER("Other", "Other safety concerns")
}

/**
 * Dialog for reporting AI-generated content that may be harmful or violate safety guidelines.
 *
 * Features:
 * - Reason selection with descriptions
 * - Optional notes field for additional context
 * - Clear warning about abuse prevention
 *
 * @param messageId ID of the AI message being reported
 * @param messageContent Content of the AI message being reported
 * @param onDismiss Callback when dialog is dismissed without reporting
 * @param onReport Callback when report is submitted with reason and optional notes
 */
@Composable
fun AIMessageReportDialog(
    messageId: String,
    messageContent: String,
    onDismiss: () -> Unit,
    onReport: (reason: AIReportReason, notes: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReason by remember { mutableStateOf<AIReportReason?>(null) }
    var additionalNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Report AI output",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                "Report AI Response",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
            ) {
                // Warning text
                Text(
                    "Help us improve AI safety by reporting harmful or inaccurate responses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider()

                // Reason selection
                Text(
                    "Select a reason:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AIReportReason.entries.forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    reason.displayText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    reason.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Optional notes
                OutlinedTextField(
                    value = additionalNotes,
                    onValueChange = { additionalNotes = it },
                    label = { Text("Additional details (optional)") },
                    placeholder = { Text("Describe the issue...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Abuse warning
                Text(
                    "False reports may result in restricted access to AI features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedReason?.let { reason ->
                        onReport(reason, additionalNotes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = selectedReason != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColorsV2.primary
                )
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
