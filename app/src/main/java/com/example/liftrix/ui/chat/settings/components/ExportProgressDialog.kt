package com.example.liftrix.ui.chat.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
// Removed import com.example.liftrix.ui.theme.LiftrixColorsV2 - using MaterialTheme.colorScheme instead
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.domain.usecase.chat.ExportFormat

/**
 * Dialog for handling chat history export with progress indication.
 * Shows export progress and provides options to copy or download the result.
 */
@Composable
fun ExportProgressDialog(
    isExporting: Boolean,
    exportedData: String?,
    language: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var hasCopied by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.large),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isExporting -> {
                        // Export in progress
                        ExportingState(language)
                    }
                    
                    exportedData != null -> {
                        // Export completed
                        ExportCompletedState(
                            exportedData = exportedData,
                            language = language,
                            hasCopied = hasCopied,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(exportedData))
                                hasCopied = true
                            },
                            onDismiss = onDismiss
                        )
                    }
                    
                    else -> {
                        // Export options
                        ExportOptionsState(language, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportingState(
    language: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = when (language) {
                "ro" -> "Exportare în curs..."
                else -> "Exporting..."
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = when (language) {
                "ro" -> "Se pregătesc datele pentru export..."
                else -> "Preparing data for export..."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExportCompletedState(
    exportedData: String,
    language: String,
    hasCopied: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = when (language) {
                "ro" -> "Export finalizat!"
                else -> "Export Complete!"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Export Statistics
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Detalii export:"
                        else -> "Export Details:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                val sizeKB = (exportedData.length / 1024.0).let { size ->
                    if (size < 1) "< 1" else "%.1f".format(size)
                }
                
                Text(
                    text = when (language) {
                        "ro" -> "• Format: JSON\n• Mărime: $sizeKB KB\n• Include: mesaje, timestamp-uri, context"
                        else -> "• Format: JSON\n• Size: $sizeKB KB\n• Includes: messages, timestamps, context"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        
        // Export Actions
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            Text(
                text = when (language) {
                    "ro" -> "Opțiuni export:"
                    else -> "Export Options:"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Copy to Clipboard Button
            Button(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                    contentColor = if (hasCopied) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (hasCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                Text(
                    when {
                        hasCopied && language == "ro" -> "Copiat!"
                        hasCopied -> "Copied!"
                        language == "ro" -> "Copiază în clipboard"
                        else -> "Copy to Clipboard"
                    }
                )
            }
            
            if (hasCopied) {
                Text(
                    text = when (language) {
                        "ro" -> "✅ Datele au fost copiate. Poți lipi conținutul într-un fișier text."
                        else -> "✅ Data copied. You can paste the content into a text file."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Download instruction
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "💡 Pentru a salva ca fișier .json, copiază conținutul și lipește-l într-un editor de text, apoi salvează cu extensia .json"
                        else -> "💡 To save as a .json file, copy the content and paste it into a text editor, then save with .json extension"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(LiftrixSpacing.small)
                )
            }
        }
        
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        
        // Close Button
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                when (language) {
                    "ro" -> "Închide"
                    else -> "Close"
                }
            )
        }
    }
}

@Composable
private fun ExportOptionsState(
    language: String,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = when (language) {
                "ro" -> "Exportă istoric chat"
                else -> "Export Chat History"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = when (language) {
                "ro" -> "Exportă toate conversațiile în format JSON pentru backup sau analiză."
                else -> "Export all conversations in JSON format for backup or analysis."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        // Format options (for future expansion)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when (language) {
                    "ro" -> "📄 Format: JSON\n🔐 Include: toate mesajele, context, timestamp-uri"
                    else -> "📄 Format: JSON\n🔐 Includes: all messages, context, timestamps"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(LiftrixSpacing.small)
            )
        }
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    when (language) {
                        "ro" -> "Anulează"
                        else -> "Cancel"
                    }
                )
            }
            
            Button(
                onClick = { /* This should trigger the export - handled by parent */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    when (language) {
                        "ro" -> "Exportă"
                        else -> "Export"
                    }
                )
            }
        }
    }
}