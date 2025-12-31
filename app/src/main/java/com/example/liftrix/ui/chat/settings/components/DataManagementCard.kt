package com.example.liftrix.ui.chat.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.liftrix.ui.theme.LiftrixColorsV2
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton

/**
 * Card component for chat data management settings.
 * Provides controls for history management, export, and storage statistics.
 */
@Composable
fun DataManagementCard(
    totalMessages: Int,
    totalTokens: Int,
    conversationSaveEnabled: Boolean,
    autoClearDays: Int,
    language: String,
    isLoading: Boolean,
    onClearHistory: () -> Unit,
    onExportHistory: () -> Unit,
    onConversationSaveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = when (language) {
            "ro" -> "Gestionare Date"
            else -> "Data Management"
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = "Data management",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = when (language) {
                        "ro" -> "Gestionare Date"
                        else -> "Data Management"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Storage Statistics
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
                            "ro" -> "Statistici stocare:"
                            else -> "Storage Statistics:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = totalMessages.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (language) {
                                    "ro" -> "mesaje"
                                    else -> "messages"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column {
                            Text(
                                text = formatTokenCount(totalTokens, language),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = when (language) {
                                    "ro" -> "tokeni"
                                    else -> "tokens"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column {
                            Text(
                                text = estimateStorageSize(totalMessages, language),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = when (language) {
                                    "ro" -> "stocare"
                                    else -> "storage"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Conversation Save Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when (language) {
                            "ro" -> "Salvare conversații"
                            else -> "Save Conversations"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (language) {
                            "ro" -> "Păstrează conversațiile pentru referințe viitoare"
                            else -> "Keep conversations for future reference"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = conversationSaveEnabled,
                    onCheckedChange = onConversationSaveChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = LiftrixColorsV2.outline,
                        uncheckedTrackColor = LiftrixColorsV2.surfaceVariant
                    )
                )
            }
            
            // Auto-clear info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = LiftrixColorsV2.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "🕒 Conversațiile vechi se șterg automat după $autoClearDays zile"
                        else -> "🕒 Old conversations are automatically deleted after $autoClearDays days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(LiftrixSpacing.small)
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Acțiuni date:"
                        else -> "Data Actions:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Export History Button
                SecondaryActionButton(
                    text = when (language) {
                        "ro" -> "Exportă istoric"
                        else -> "Export History"
                    },
                    onClick = onExportHistory,
                    enabled = !isLoading && totalMessages > 0,
                    leadingIcon = Icons.Default.CloudDownload,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (totalMessages > 0) {
                    Text(
                        text = when (language) {
                            "ro" -> "💾 Descarcă toate conversațiile în format JSON"
                            else -> "💾 Download all conversations in JSON format"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Clear History Button
                TertiaryActionButton(
                    text = when (language) {
                        "ro" -> "Șterge tot istoricul"
                        else -> "Clear All History"
                    },
                    onClick = onClearHistory,
                    enabled = !isLoading && totalMessages > 0,
                    leadingIcon = Icons.Default.DeleteForever,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (totalMessages > 0) {
                    Text(
                        text = when (language) {
                            "ro" -> "⚠️ Această acțiune nu poate fi anulată"
                            else -> "⚠️ This action cannot be undone"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = when (language) {
                            "ro" -> "📭 Nu există istoric de șters"
                            else -> "📭 No history to clear"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Formats token count with appropriate units.
 */
private fun formatTokenCount(tokens: Int, language: String): String {
    return when {
        tokens >= 1000000 -> "${(tokens / 1000000.0).format(1)}M"
        tokens >= 1000 -> "${(tokens / 1000.0).format(1)}K"
        else -> tokens.toString()
    }
}

/**
 * Estimates storage size based on message count.
 */
private fun estimateStorageSize(messageCount: Int, language: String): String {
    val sizeKB = maxOf(1, messageCount / 10) // Rough estimate: ~10 messages per KB
    return when {
        sizeKB >= 1024 -> "${(sizeKB / 1024.0).format(1)} MB"
        else -> "$sizeKB KB"
    }
}

/**
 * Extension function to format doubles to specified decimal places.
 */
private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
