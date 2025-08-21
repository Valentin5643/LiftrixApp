package com.example.liftrix.ui.chat.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard

/**
 * Card component for AI personalization settings.
 * Provides controls for AI response style, user context, and behavior preferences.
 */
@Composable
fun PersonalizationCard(
    aiResponseStyle: String,
    userContextPrompt: String?,
    includeWorkoutHistory: Boolean,
    includeExerciseFormTips: Boolean,
    language: String,
    onResponseStyleChange: (String) -> Unit,
    onUserContextChange: (String?) -> Unit,
    onWorkoutHistoryChange: (Boolean) -> Unit,
    onExerciseFormTipsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var contextText by remember(userContextPrompt) { 
        mutableStateOf(userContextPrompt ?: "") 
    }
    
    UnifiedWorkoutCard(
        title = when (language) {
            "ro" -> "Personalizare"
            else -> "Personalization"
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
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = LiftrixColorsV2.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = when (language) {
                        "ro" -> "Personalizare AI"
                        else -> "AI Personalization"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LiftrixColorsV2.onSurface
                )
            }
            
            Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
            
            // Response Style Selection
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Stil răspuns AI:"
                        else -> "AI Response Style:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.onSurface
                )
                
                // Concise option
                ResponseStyleOption(
                    style = "concise",
                    isSelected = aiResponseStyle == "concise",
                    title = when (language) {
                        "ro" -> "Concis"
                        else -> "Concise"
                    },
                    description = when (language) {
                        "ro" -> "Răspunsuri scurte și directe"
                        else -> "Short and direct responses"
                    },
                    onSelect = onResponseStyleChange
                )
                
                // Balanced option
                ResponseStyleOption(
                    style = "balanced",
                    isSelected = aiResponseStyle == "balanced",
                    title = when (language) {
                        "ro" -> "Echilibrat"
                        else -> "Balanced"
                    },
                    description = when (language) {
                        "ro" -> "Călea de mijloc între detaliat și concis"
                        else -> "Middle ground between detailed and concise"
                    },
                    onSelect = onResponseStyleChange
                )
                
                // Detailed option
                ResponseStyleOption(
                    style = "detailed",
                    isSelected = aiResponseStyle == "detailed",
                    title = when (language) {
                        "ro" -> "Detaliat"
                        else -> "Detailed"
                    },
                    description = when (language) {
                        "ro" -> "Explicații complete cu exemple"
                        else -> "Comprehensive explanations with examples"
                    },
                    onSelect = onResponseStyleChange
                )
            }
            
            Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
            
            // User Context Prompt
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Context personal:"
                        else -> "Personal Context:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.onSurface
                )
                
                OutlinedTextField(
                    value = contextText,
                    onValueChange = { newValue ->
                        if (newValue.length <= 500) {
                            contextText = newValue
                            onUserContextChange(newValue.takeIf { it.isNotBlank() })
                        }
                    },
                    placeholder = {
                        Text(
                            when (language) {
                                "ro" -> "Ex: Sunt începător, prefer antrenamente de dimineață, am probleme cu genunchiul..."
                                else -> "e.g., I'm a beginner, prefer morning workouts, have knee issues..."
                            }
                        )
                    },
                    supportingText = {
                        Text(
                            "${contextText.length}/500",
                            color = if (contextText.length > 450) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiftrixColorsV2.primary,
                        unfocusedBorderColor = LiftrixColorsV2.outline
                    )
                )
                
                Text(
                    text = when (language) {
                        "ro" -> "💡 Acest text va fi inclus în toate conversațiile pentru a personaliza răspunsurile AI."
                        else -> "💡 This text will be included in all conversations to personalize AI responses."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
            }
            
            Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
            
            // Context Inclusion Settings
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
            ) {
                Text(
                    text = when (language) {
                        "ro" -> "Include în context:"
                        else -> "Include in Context:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.onSurface
                )
                
                // Workout History Toggle
                SettingToggle(
                    title = when (language) {
                        "ro" -> "Istoric antrenamente"
                        else -> "Workout History"
                    },
                    description = when (language) {
                        "ro" -> "Include antrenamentele recente în contextul AI"
                        else -> "Include recent workouts in AI context"
                    },
                    checked = includeWorkoutHistory,
                    onCheckedChange = onWorkoutHistoryChange
                )
                
                // Exercise Form Tips Toggle
                SettingToggle(
                    title = when (language) {
                        "ro" -> "Sfaturi tehnica exercițiilor"
                        else -> "Exercise Form Tips"
                    },
                    description = when (language) {
                        "ro" -> "Include sfaturi pentru tehnica corectă"
                        else -> "Include tips for proper exercise form"
                    },
                    checked = includeExerciseFormTips,
                    onCheckedChange = onExerciseFormTipsChange
                )
            }
        }
    }
}

@Composable
private fun ResponseStyleOption(
    style: String,
    isSelected: Boolean,
    title: String,
    description: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = { onSelect(style) },
                role = Role.RadioButton
            )
            .padding(vertical = LiftrixSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = LiftrixColorsV2.primary
            )
        )
        Spacer(modifier = Modifier.width(LiftrixSpacing.small))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColorsV2.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColorsV2.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LiftrixColorsV2.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColorsV2.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LiftrixColorsV2.primary,
                checkedTrackColor = LiftrixColorsV2.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = LiftrixColorsV2.outline,
                uncheckedTrackColor = LiftrixColorsV2.surfaceVariant
            )
        )
    }
}