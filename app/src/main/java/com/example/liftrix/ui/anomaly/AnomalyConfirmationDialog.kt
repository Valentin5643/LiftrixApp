package com.example.liftrix.ui.anomaly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.AnomalyType
import com.example.liftrix.domain.model.AnomalyValue
import com.example.liftrix.domain.model.UserAnomalyAction
import com.example.liftrix.domain.model.WorkoutAnomaly

/**
 * Dialog for confirming or correcting detected workout anomalies
 */
@Composable
fun AnomalyConfirmationDialog(
    anomaly: WorkoutAnomaly,
    onAnomalyResolved: (UserAnomalyAction, AnomalyValue?) -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCorrectionInput by remember { mutableStateOf(false) }
    var correctionValue by remember { mutableStateOf("") }
    var correctionError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissed,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unusual Value Detected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                // Exercise name
                Text(
                    text = "Exercise: ${anomaly.exerciseName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Anomaly description
                Text(
                    text = anomaly.getConfirmationPrompt(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // Current value display
                Text(
                    text = "Current value: ${formatAnomalyValue(anomaly.currentValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Previous value display (if available)
                anomaly.previousValue?.let { previousValue ->
                    Text(
                        text = "Previous value: ${formatAnomalyValue(previousValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Correction input (shown when user chooses to edit)
                if (showCorrectionInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Enter correct value:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    OutlinedTextField(
                        value = correctionValue,
                        onValueChange = { 
                            correctionValue = it
                            correctionError = null
                        },
                        label = { Text(getInputLabel(anomaly.anomalyType)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (anomaly.anomalyType) {
                                AnomalyType.WEIGHT_SPIKE -> KeyboardType.Decimal
                                AnomalyType.REPS_SPIKE -> KeyboardType.Number
                                AnomalyType.DURATION_SPIKE -> KeyboardType.Number
                                AnomalyType.IMPOSSIBLE_VALUE -> KeyboardType.Decimal
                            }
                        ),
                        isError = correctionError != null,
                        supportingText = correctionError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (showCorrectionInput) {
                    // Save correction button
                    TextButton(
                        onClick = {
                            val correctedValue = parseCorrectionValue(
                                correctionValue,
                                anomaly.anomalyType,
                                anomaly.currentValue
                            )
                            if (correctedValue != null) {
                                onAnomalyResolved(UserAnomalyAction.CORRECTED, correctedValue)
                            } else {
                                correctionError = "Invalid value format"
                            }
                        }
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Cancel correction button
                    TextButton(
                        onClick = { showCorrectionInput = false }
                    ) {
                        Text("Cancel")
                    }
                } else {
                    // Yes, it's correct button
                    TextButton(
                        onClick = { 
                            onAnomalyResolved(UserAnomalyAction.CONFIRMED, null)
                        }
                    ) {
                        Text(
                            text = "Yes, Correct",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Edit button
                    TextButton(
                        onClick = { 
                            showCorrectionInput = true
                            correctionValue = getCurrentValueString(anomaly.currentValue)
                        }
                    ) {
                        Text("Edit Value")
                    }
                }
            }
        },
        dismissButton = {
            if (!showCorrectionInput) {
                TextButton(
                    onClick = { 
                        onAnomalyResolved(UserAnomalyAction.DISMISSED, null)
                    }
                ) {
                    Text("Dismiss")
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Formats an anomaly value for display
 */
private fun formatAnomalyValue(value: AnomalyValue): String {
    return when (value) {
        is AnomalyValue.WeightValue -> "${value.value} ${value.unit}"
        is AnomalyValue.RepsValue -> "${value.value} reps"
        is AnomalyValue.DurationValue -> {
            val minutes = value.seconds / 60
            val seconds = value.seconds % 60
            "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
    }
}

/**
 * Gets the current value as a string for editing
 */
private fun getCurrentValueString(value: AnomalyValue): String {
    return when (value) {
        is AnomalyValue.WeightValue -> value.value.toString()
        is AnomalyValue.RepsValue -> value.value.toString()
        is AnomalyValue.DurationValue -> value.seconds.toString()
    }
}

/**
 * Gets the input label for the correction field
 */
private fun getInputLabel(anomalyType: AnomalyType): String {
    return when (anomalyType) {
        AnomalyType.WEIGHT_SPIKE -> "Weight"
        AnomalyType.REPS_SPIKE -> "Reps"
        AnomalyType.DURATION_SPIKE -> "Duration (seconds)"
        AnomalyType.IMPOSSIBLE_VALUE -> "Value"
    }
}

/**
 * Parses the correction value based on anomaly type
 */
private fun parseCorrectionValue(
    input: String,
    anomalyType: AnomalyType,
    originalValue: AnomalyValue
): AnomalyValue? {
    return try {
        when (anomalyType) {
            AnomalyType.WEIGHT_SPIKE -> {
                val weight = input.toDouble()
                if (weight >= 0) {
                    val unit = (originalValue as? AnomalyValue.WeightValue)?.unit ?: "lbs"
                    AnomalyValue.WeightValue(weight, unit)
                } else null
            }
            AnomalyType.REPS_SPIKE -> {
                val reps = input.toInt()
                if (reps >= 0) {
                    AnomalyValue.RepsValue(reps)
                } else null
            }
            AnomalyType.DURATION_SPIKE -> {
                val duration = input.toLong()
                if (duration >= 0) {
                    AnomalyValue.DurationValue(duration)
                } else null
            }
            AnomalyType.IMPOSSIBLE_VALUE -> {
                // Try to parse as the same type as original
                when (originalValue) {
                    is AnomalyValue.WeightValue -> {
                        val weight = input.toDouble()
                        if (weight >= 0) AnomalyValue.WeightValue(weight, originalValue.unit) else null
                    }
                    is AnomalyValue.RepsValue -> {
                        val reps = input.toInt()
                        if (reps >= 0) AnomalyValue.RepsValue(reps) else null
                    }
                    is AnomalyValue.DurationValue -> {
                        val duration = input.toLong()
                        if (duration >= 0) AnomalyValue.DurationValue(duration) else null
                    }
                }
            }
        }
    } catch (e: NumberFormatException) {
        null
    }
}