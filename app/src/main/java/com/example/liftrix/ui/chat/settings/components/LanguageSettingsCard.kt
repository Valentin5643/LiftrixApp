package com.example.liftrix.ui.chat.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard

/**
 * Card component for language preference settings.
 * Provides language selection and auto-detection controls.
 */
@Composable
fun LanguageSettingsCard(
    preferredLanguage: String,
    autoDetectLanguage: Boolean,
    onLanguageChange: (String) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = when (preferredLanguage) {
            "ro" -> "Setări limbă"
            else -> "Language Settings"
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
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = LiftrixColorsV2.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = when (preferredLanguage) {
                        "ro" -> "Setări Limbă"
                        else -> "Language Settings"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LiftrixColorsV2.onSurface
                )
            }
            
            Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
            
            // Language Selection
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Text(
                    text = when (preferredLanguage) {
                        "ro" -> "Limba preferată:"
                        else -> "Preferred Language:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.onSurface
                )
                
                // English Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = preferredLanguage == "en",
                            onClick = { onLanguageChange("en") },
                            role = Role.RadioButton
                        )
                        .padding(vertical = LiftrixSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferredLanguage == "en",
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = LiftrixColorsV2.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                    Column {
                        Text(
                            text = "English",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LiftrixColorsV2.onSurface
                        )
                        Text(
                            text = when (preferredLanguage) {
                                "ro" -> "Engleză - limba implicită"
                                else -> "English - default language"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = LiftrixColorsV2.onSurfaceVariant
                        )
                    }
                }
                
                // Romanian Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = preferredLanguage == "ro",
                            onClick = { onLanguageChange("ro") },
                            role = Role.RadioButton
                        )
                        .padding(vertical = LiftrixSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferredLanguage == "ro",
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = LiftrixColorsV2.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                    Column {
                        Text(
                            text = "Română",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LiftrixColorsV2.onSurface
                        )
                        Text(
                            text = when (preferredLanguage) {
                                "ro" -> "Română - pentru utilizatori români"
                                else -> "Romanian - for Romanian users"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = LiftrixColorsV2.onSurfaceVariant
                        )
                    }
                }
            }
            
            Divider(color = LiftrixColorsV2.outline.copy(alpha = 0.3f))
            
            // Auto-detect Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when (preferredLanguage) {
                            "ro" -> "Detectare automată"
                            else -> "Auto-detect Language"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = LiftrixColorsV2.onSurface
                    )
                    Text(
                        text = when (preferredLanguage) {
                            "ro" -> "Detectează automat limba din mesaj"
                            else -> "Automatically detect language from message"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColorsV2.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoDetectLanguage,
                    onCheckedChange = onAutoDetectChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LiftrixColorsV2.primary,
                        checkedTrackColor = LiftrixColorsV2.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = LiftrixColorsV2.outline,
                        uncheckedTrackColor = LiftrixColorsV2.surfaceVariant
                    )
                )
            }
            
            // Auto-detect explanation
            if (autoDetectLanguage) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LiftrixColorsV2.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (preferredLanguage) {
                            "ro" -> "💡 AI-ul va detecta automat dacă scrii în română și va răspunde în consecință, indiferent de setarea de limbă."
                            else -> "💡 The AI will automatically detect if you write in Romanian and respond accordingly, regardless of the language setting."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColorsV2.onSurface,
                        modifier = Modifier.padding(LiftrixSpacing.small)
                    )
                }
            }
        }
    }
}