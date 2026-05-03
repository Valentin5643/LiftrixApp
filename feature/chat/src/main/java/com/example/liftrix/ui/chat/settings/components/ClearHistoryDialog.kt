package com.example.liftrix.ui.chat.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Dialog for confirming chat history deletion with safety measures.
 * Requires exact confirmation text to prevent accidental deletion.
 */
@Composable
fun ClearHistoryDialog(
    totalMessages: Int,
    requiredConfirmationText: String,
    language: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmationInput by remember { mutableStateOf(TextFieldValue()) }
    val isConfirmationValid = confirmationInput.text == requiredConfirmationText
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = LiftrixColorsV2.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.large),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
            ) {
                // Warning Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Title
                Text(
                    text = when (language) {
                        "ro" -> "Șterge tot istoricul?"
                        else -> "Clear All History?"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LiftrixColorsV2.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Warning Message
                Text(
                    text = when (language) {
                        "ro" -> "Această acțiune va șterge permanent toate cele $totalMessages mesaje din istoricul tău de chat. Această operațiune nu poate fi anulată."
                        else -> "This action will permanently delete all $totalMessages messages from your chat history. This operation cannot be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColorsV2.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Impact Warning
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
                    ) {
                        Text(
                            text = when (language) {
                                "ro" -> "⚠️ Ce va fi pierdut:"
                                else -> "⚠️ What will be lost:"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Text(
                            text = when (language) {
                                "ro" -> "• Toate conversațiile cu AI-ul\n• Contextul conversațiilor anterioare\n• Sfaturile și recomandările personalizate\n• Istoricul antrenamentelor discutat"
                                else -> "• All conversations with the AI\n• Previous conversation context\n• Personalized advice and recommendations\n• Workout history discussions"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
                
                Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
                
                // Confirmation Input
                Column(
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
                ) {
                    Text(
                        text = when (language) {
                            "ro" -> "Pentru a confirma, tastează exact:"
                            else -> "To confirm, type exactly:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = LiftrixColorsV2.onSurface
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = requiredConfirmationText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LiftrixSpacing.cardPadding)
                        )
                    }
                    
                    OutlinedTextField(
                        value = confirmationInput,
                        onValueChange = { confirmationInput = it },
                        placeholder = {
                            Text(
                                when (language) {
                                    "ro" -> "Tastează textul de confirmarea aici..."
                                    else -> "Type confirmation text here..."
                                }
                            )
                        },
                        isError = confirmationInput.text.isNotEmpty() && !isConfirmationValid,
                        supportingText = if (confirmationInput.text.isNotEmpty() && !isConfirmationValid) {
                            {
                                Text(
                                    when (language) {
                                        "ro" -> "Textul nu se potrivește"
                                        else -> "Text doesn't match"
                                    },
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isConfirmationValid) LiftrixColorsV2.primary else LiftrixColorsV2.outline,
                            unfocusedBorderColor = LiftrixColorsV2.outline
                        )
                    )
                }
                
                Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LiftrixColorsV2.onSurface
                        )
                    ) {
                        Text(
                            when (language) {
                                "ro" -> "Anulează"
                                else -> "Cancel"
                            }
                        )
                    }
                    
                    // Confirm Button
                    Button(
                        onClick = { onConfirm(confirmationInput.text) },
                        enabled = isConfirmationValid,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = LiftrixColorsV2.surfaceVariant,
                            disabledContentColor = LiftrixColorsV2.onSurfaceVariant
                        )
                    ) {
                        Text(
                            when (language) {
                                "ro" -> "Șterge tot"
                                else -> "Delete All"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Additional Warning
                Text(
                    text = when (language) {
                        "ro" -> "💡 Poți exporta istoricul înainte de ștergere pentru a face o copie de siguranță."
                        else -> "💡 You can export your history before clearing to create a backup."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
