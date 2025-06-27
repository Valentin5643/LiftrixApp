package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SetInput

/**
 * Compact input row for exercise set data (reps, RPE, weight)
 * following Material 3 design guidelines and accessibility standards
 */
@Composable
fun SetInputRow(
    setInput: SetInput,
    setNumber: Int,
    onSetChange: (SetInput) -> Unit,
    onRemoveSet: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showRemoveButton: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set Number Indicator
        Text(
            text = "$setNumber",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(24.dp)
                .semantics {
                    contentDescription = "Set number $setNumber"
                },
            textAlign = TextAlign.Center
        )
        
        // Reps Field
        RepsField(
            value = setInput.reps,
            onValueChange = { newReps ->
                val validatedReps = newReps.filter { it.isDigit() }.take(3)
                val repsError = setInput.validateReps(validatedReps)
                onSetChange(setInput.copy(reps = validatedReps, repsError = repsError))
            },
            isError = setInput.repsError != null,
            enabled = enabled,
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            modifier = Modifier.weight(1f)
        )
        
        // RPE Field
        RpeField(
            value = setInput.rpe,
            onValueChange = { newRpe ->
                val validatedRpe = newRpe.filter { it.isDigit() }.take(2)
                val rpeError = setInput.validateRpe(validatedRpe)
                onSetChange(setInput.copy(rpe = validatedRpe, rpeError = rpeError))
            },
            isError = setInput.rpeError != null,
            enabled = enabled,
            onNext = { 
                if (setInput.isWeightSupported) {
                    focusManager.moveFocus(FocusDirection.Next)
                } else {
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.weight(1f)
        )
        
        // Weight Field (conditional)
        if (setInput.isWeightSupported) {
            WeightField(
                value = setInput.weight,
                onValueChange = { newWeight ->
                    val validatedWeight = newWeight.filter { it.isDigit() || it == '.' }.take(6)
                    val weightError = setInput.validateWeight(validatedWeight)
                    onSetChange(setInput.copy(weight = validatedWeight, weightError = weightError))
                },
                isError = setInput.weightError != null,
                enabled = enabled,
                onDone = { focusManager.clearFocus() },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Remove Set Button
        if (showRemoveButton) {
            IconButton(
                onClick = onRemoveSet,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Remove set $setNumber"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        } else {
            // Spacer to maintain consistent layout when remove button is hidden
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * Reps input field component
 */
@Composable
private fun RepsField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Reps") },
        placeholder = { Text("10") },
        isError = isError,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() }
        ),
        modifier = modifier.semantics {
            contentDescription = "Repetitions input field"
        }
    )
}

/**
 * RPE input field component
 */
@Composable
private fun RpeField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("RPE") },
        placeholder = { Text("8") },
        isError = isError,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() }
        ),
        modifier = modifier.semantics {
            contentDescription = "Rate of perceived exertion input field, optional"
        }
    )
}

/**
 * Weight input field component
 */
@Composable
private fun WeightField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Weight") },
        placeholder = { Text("20.0") },
        isError = isError,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        modifier = modifier.semantics {
            contentDescription = "Weight in kilograms input field"
        }
    )
}

/**
 * Validates complete set input and returns validation state
 */
internal fun validateSetInput(setInput: SetInput): SetInput {
    val repsError = setInput.validateReps(setInput.reps)
    val rpeError = setInput.validateRpe(setInput.rpe)
    val weightError = setInput.validateWeight(setInput.weight)
    
    return setInput.copy(
        repsError = repsError,
        rpeError = rpeError,
        weightError = weightError
    )
}

/**
 * Creates a new SetInput with weight memory pre-populated
 */
fun SetInput.withWeightMemory(lastUsedWeight: Float?): SetInput {
    return if (lastUsedWeight != null && isWeightSupported && weight.isBlank()) {
        copy(weight = String.format("%.1f", lastUsedWeight))
    } else {
        this
    }
}

@Preview(showBackground = true)
@Composable
private fun SetInputRowPreview() {
    LiftrixTheme {
        SetInputRow(
            setInput = SetInput(
                reps = "10",
                rpe = "8",
                weight = "20.0",
                isWeightSupported = true
            ),
            setNumber = 1,
            onSetChange = {},
            onRemoveSet = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetInputRowBodyweightPreview() {
    LiftrixTheme {
        SetInputRow(
            setInput = SetInput(
                reps = "15",
                rpe = "7",
                isWeightSupported = false
            ),
            setNumber = 2,
            onSetChange = {},
            onRemoveSet = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetInputRowWithErrorsPreview() {
    LiftrixTheme {
        SetInputRow(
            setInput = SetInput(
                reps = "",
                repsError = "Reps are required",
                rpe = "15",
                rpeError = "RPE cannot exceed 10",
                weight = "-5",
                weightError = "Weight cannot be negative",
                isWeightSupported = true
            ),
            setNumber = 1,
            onSetChange = {},
            onRemoveSet = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetInputRowDisabledPreview() {
    LiftrixTheme {
        SetInputRow(
            setInput = SetInput(
                reps = "10",
                rpe = "8",
                weight = "20.0",
                isWeightSupported = true
            ),
            setNumber = 1,
            onSetChange = {},
            onRemoveSet = {},
            enabled = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetInputRowNoRemoveButtonPreview() {
    LiftrixTheme {
        SetInputRow(
            setInput = SetInput(
                reps = "10",
                rpe = "8",
                weight = "20.0",
                isWeightSupported = true
            ),
            setNumber = 1,
            onSetChange = {},
            onRemoveSet = {},
            showRemoveButton = false,
            modifier = Modifier.padding(16.dp)
        )
    }
} 