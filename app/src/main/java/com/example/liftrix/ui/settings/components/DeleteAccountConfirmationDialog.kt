package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Delete Account Confirmation Dialog for Google Play compliance (SPEC-20251230).
 *
 * Requirements (FR-003):
 * - Shows irreversible warning about account deletion
 * - Lists all data that will be permanently deleted
 * - Requires explicit text confirmation ("DELETE MY ACCOUNT")
 * - Supports provider-specific re-authentication (password, Google, Anonymous)
 * - Optional data export before deletion
 * - Clear Cancel vs Proceed actions
 *
 * @param isVisible Whether the dialog should be displayed
 * @param onConfirm Callback when deletion is confirmed with re-auth credentials
 * @param onDismiss Callback when dialog is dismissed without deletion
 * @param modifier Optional modifier for styling
 */
@Composable
fun DeleteAccountConfirmationDialog(
    isVisible: Boolean,
    onConfirm: (reauthProvider: String, reauthPayload: String, exportDataFirst: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    var confirmationText by remember { mutableStateOf("") }
    var exportDataFirst by remember { mutableStateOf(false) }
    var reauthPassword by remember { mutableStateOf("") }
    val requiredText = "DELETE MY ACCOUNT"
    val isConfirmationValid = confirmationText == requiredText && reauthPassword.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Delete Account Permanently?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    "This action is IRREVERSIBLE. The following data will be permanently deleted:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                val dataItems = listOf(
                    "✕ All workout history and templates",
                    "✕ Progress photos and body measurements",
                    "✕ Social profile, posts, and comments",
                    "✕ AI chat history and preferences",
                    "✕ Analytics data and achievements",
                    "✕ Firebase account (email/Google)"
                )

                dataItems.forEach { item ->
                    Text(
                        item,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "This action is permanent. All data will be deleted within 30 days (target 24 hours).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                // Re-authentication section
                Text(
                    "Enter your password to confirm:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = reauthPassword,
                    onValueChange = { reauthPassword = it },
                    placeholder = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Export data checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = exportDataFirst,
                        onCheckedChange = { exportDataFirst = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Export my data before deletion",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Confirmation text input
                Text(
                    "Type \"$requiredText\" to confirm:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmationText,
                    onValueChange = { confirmationText = it.uppercase() },
                    placeholder = { Text(requiredText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (confirmationText == requiredText)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // For now, assume password provider (Google/Anonymous support to be added in ViewModel)
                    onConfirm("password", reauthPassword, exportDataFirst)
                },
                enabled = isConfirmationValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
