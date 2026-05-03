package com.example.liftrix.ui.chat.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
// Removed import com.example.liftrix.ui.theme.LiftrixColorsV2 - using MaterialTheme.colorScheme instead
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.components.actions.UnifiedWorkoutCard

/**
 * Card component for AI usage limits settings.
 * Provides controls for daily message limits, monthly token limits, and notification thresholds.
 */
@Composable
fun UsageLimitsCard(
    maxMessagesPerDay: Int,
    maxTokensPerMonth: Int,
    usageNotificationsThreshold: Int,
    language: String,
    onMaxMessagesChange: (Int) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onThresholdChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var messagesText by remember(maxMessagesPerDay) { 
        mutableStateOf(maxMessagesPerDay.toString()) 
    }
    var tokensText by remember(maxTokensPerMonth) { 
        mutableStateOf(maxTokensPerMonth.toString()) 
    }
    var thresholdValue by remember(usageNotificationsThreshold) { 
        mutableStateOf(usageNotificationsThreshold.toFloat()) 
    }
    
    UnifiedWorkoutCard(
        title = when (language) {
            "ro" -> "Limite utilizare"
            else -> "Usage Limits"
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
                    Icons.Default.Analytics,
                    contentDescription = "Usage limits",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = when (language) {
                        "ro" -> "Limite Utilizare"
                        else -> "Usage Limits"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Daily Messages Limit
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Mesaje maxime pe zi:"
                        else -> "Max Messages per Day:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                OutlinedTextField(
                    value = messagesText,
                    onValueChange = { newValue ->
                        messagesText = newValue
                        newValue.toIntOrNull()?.let { value ->
                            if (value in 1..1000) {
                                onMaxMessagesChange(value)
                            }
                        }
                    },
                    label = {
                        Text(when (language) {
                            "ro" -> "Mesaje zilnice"
                            else -> "Daily messages"
                        })
                    },
                    supportingText = {
                        Text(
                            when (language) {
                                "ro" -> "Între 1 și 1000 mesaje"
                                else -> "Between 1 and 1000 messages"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = messagesText.toIntOrNull()?.let { it !in 1..1000 } ?: messagesText.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                Text(
                    text = when (language) {
                        "ro" -> "💡 Setează o limită zilnică pentru a controla costurile și utilizarea AI."
                        else -> "💡 Set a daily limit to control AI costs and usage."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Monthly Token Limit
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Tokeni maximi pe lună:"
                        else -> "Max Tokens per Month:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                OutlinedTextField(
                    value = tokensText,
                    onValueChange = { newValue ->
                        tokensText = newValue
                        newValue.toIntOrNull()?.let { value ->
                            if (value in 100..100000) {
                                onMaxTokensChange(value)
                            }
                        }
                    },
                    label = {
                        Text(when (language) {
                            "ro" -> "Tokeni lunari"
                            else -> "Monthly tokens"
                        })
                    },
                    supportingText = {
                        Text(
                            when (language) {
                                "ro" -> "Între 100 și 100,000 tokeni"
                                else -> "Between 100 and 100,000 tokens"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = tokensText.toIntOrNull()?.let { it !in 100..100000 } ?: tokensText.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                Text(
                    text = when (language) {
                        "ro" -> "💡 Tokenul = ~1 cuvânt. Conversațiile lungi folosesc mai mulți tokeni."
                        else -> "💡 Token ≈ 1 word. Longer conversations use more tokens."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            // Usage Notification Threshold
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Prag alertă utilizare:"
                        else -> "Usage Alert Threshold:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "${thresholdValue.toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Slider(
                    value = thresholdValue,
                    onValueChange = { newValue ->
                        thresholdValue = newValue
                    },
                    onValueChangeFinished = {
                        onThresholdChange(thresholdValue.toInt())
                    },
                    valueRange = 50f..95f,
                    steps = 8, // 50, 55, 60, 65, 70, 75, 80, 85, 90, 95
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "50%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "95%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = when (language) {
                        "ro" -> "💡 Vei primi o alertă când utilizarea depășește acest procent din limitele tale."
                        else -> "💡 You'll receive an alert when usage exceeds this percentage of your limits."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Current Usage Preview
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
                ) {
                    Text(
                        text = when (language) {
                            "ro" -> "Limitele vor fi aplicate la următoarele mesaje"
                            else -> "Limits will be applied to future messages"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (language) {
                            "ro" -> "• Mesaje zilnice: max $maxMessagesPerDay\n• Tokeni lunari: max $maxTokensPerMonth\n• Alertă la ${usageNotificationsThreshold}%"
                            else -> "• Daily messages: max $maxMessagesPerDay\n• Monthly tokens: max $maxTokensPerMonth\n• Alert at ${usageNotificationsThreshold}%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
