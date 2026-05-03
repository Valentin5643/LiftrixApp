package com.example.liftrix.ui.workout.edit.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.theme.LiftrixSpacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * HistoricalDataCard - Component for displaying and editing historical workout data
 * 
 * This component provides a specialized card interface for editing historical records with:
 * - Clear visual indicators showing original vs modified values
 * - Inline editing capabilities with proper validation
 * - Historical context preservation with modification timestamps
 * - Consistent styling with unified component system
 * 
 * Key Features:
 * - Shows "Original" vs "Current" values with visual differentiation
 * - Modification indicators with timestamp tracking  
 * - Input validation with user-friendly error messages
 * - Accessibility support for screen readers
 * - Integration with UnifiedWorkoutCard for consistent theming
 * 
 * @param title Display title for the data field being edited
 * @param originalValue Original value from historical record (nullable if unknown)
 * @param currentValue Current edited value
 * @param onValueChange Callback when user modifies the current value
 * @param modifier Optional modifier for customizing layout and behavior
 * @param placeholder Optional placeholder text for empty values
 * @param isRequired Whether this field is required (affects validation)
 * @param maxLines Maximum lines for text input (default: 1 for single line)
 * @param supportingText Optional supporting text or validation message
 * @param isError Whether current input state is in error
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalDataCard(
    title: String,
    originalValue: String?,
    currentValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isRequired: Boolean = false,
    maxLines: Int = 1,
    supportingText: String? = null,
    isError: Boolean = false
) {
    UnifiedWorkoutCard(
        title = title,
        subtitle = originalValue?.let { "Original: $it" },
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            // Current value input field
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { 
                    Text(if (isRequired) "$title *" else title)
                },
                placeholder = placeholder?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                maxLines = maxLines,
                isError = isError,
                supportingText = supportingText?.let { { Text(it) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    focusedLabelColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            )
            
            // Show modification indicator if value has changed
            if (originalValue != null && originalValue != currentValue) {
                ModificationIndicator(
                    originalValue = originalValue,
                    hasModification = true
                )
            } else if (originalValue != null) {
                ModificationIndicator(
                    originalValue = originalValue,
                    hasModification = false
                )
            }
        }
    }
}

/**
 * ModificationIndicator - Shows visual indicator for data modifications
 */
@Composable
private fun ModificationIndicator(
    originalValue: String,
    hasModification: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasModification) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (hasModification) Icons.Default.Edit else Icons.Default.History,
                contentDescription = if (hasModification) "Modified from original" else "Original value",
                tint = if (hasModification) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasModification) "Modified from original" else "Original value",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasModification) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (hasModification) FontWeight.Medium else FontWeight.Normal
                )
                
                Text(
                    text = "\"$originalValue\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Enhanced HistoricalDataCard for complex data types like Duration or Weight
 */
@Composable
fun HistoricalDataCardWithCustomInput(
    title: String,
    originalValue: String?,
    currentDisplayValue: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    UnifiedWorkoutCard(
        title = title,
        subtitle = originalValue?.let { "Original: $it" },
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            // Custom content for specialized input controls
            content()
            
            // Show modification indicator if value has changed
            if (originalValue != null && originalValue != currentDisplayValue) {
                ModificationIndicator(
                    originalValue = originalValue,
                    hasModification = true
                )
            } else if (originalValue != null) {
                ModificationIndicator(
                    originalValue = originalValue,
                    hasModification = false
                )
            }
        }
    }
}

/**
 * Helper function to format historical timestamps for display
 */
fun formatHistoricalTimestamp(instant: Instant): String {
    return try {
        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        localDate.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    } catch (e: Exception) {
        "Unknown time"
    }
}