package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationState

/**
 * Reusable header form component for workout name and description input
 * following Material 3 design guidelines and accessibility standards
 */
@Composable
fun WorkoutHeaderForm(
    workoutName: String,
    onWorkoutNameChange: (String) -> Unit,
    workoutDescription: String,
    onWorkoutDescriptionChange: (String) -> Unit,
    workoutNameError: String? = null,
    workoutDescriptionError: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Workout Name Field
        OutlinedTextField(
            value = workoutName,
            onValueChange = { newValue ->
                val trimmedValue = newValue.take(WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH)
                onWorkoutNameChange(trimmedValue)
            },
            label = { Text("Workout Name") },
            placeholder = { Text("Enter workout name") },
            supportingText = {
                when {
                    workoutNameError != null -> {
                        Text(
                            text = workoutNameError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> {
                        Text(
                            text = "${workoutName.length}/${WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            isError = workoutNameError != null,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Workout name input field"
                }
        )
        
        // Workout Description Field
        OutlinedTextField(
            value = workoutDescription,
            onValueChange = { newValue ->
                val trimmedValue = newValue.take(WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH)
                onWorkoutDescriptionChange(trimmedValue)
            },
            label = { Text("Description (Optional)") },
            placeholder = { Text("Add workout description...") },
            supportingText = {
                when {
                    workoutDescriptionError != null -> {
                        Text(
                            text = workoutDescriptionError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> {
                        Text(
                            text = "${workoutDescription.length}/${WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            isError = workoutDescriptionError != null,
            enabled = enabled,
            singleLine = false,
            maxLines = 3,
            minLines = 1,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Workout description input field, optional"
                }
        )
    }
}

/**
 * Validates workout name input following business rules
 */
internal fun validateWorkoutName(name: String): String? {
    return when {
        name.isBlank() -> "Workout name is required"
        name.length > WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH -> 
            "Workout name cannot exceed ${WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH} characters"
        else -> null
    }
}

/**
 * Validates workout description input following business rules
 */
internal fun validateWorkoutDescription(description: String): String? {
    return when {
        description.length > WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH -> 
            "Description cannot exceed ${WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH} characters"
        else -> null
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutHeaderFormPreview() {
    LiftrixTheme {
        WorkoutHeaderForm(
            workoutName = "Push Day",
            onWorkoutNameChange = {},
            workoutDescription = "Chest, shoulders, and triceps workout",
            onWorkoutDescriptionChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutHeaderFormWithErrorsPreview() {
    LiftrixTheme {
        WorkoutHeaderForm(
            workoutName = "",
            onWorkoutNameChange = {},
            workoutDescription = "This is a very long description that exceeds the maximum character limit for workout descriptions and should show an error message to the user indicating that they need to shorten their description to fit within the allowed limits.",
            onWorkoutDescriptionChange = {},
            workoutNameError = "Workout name is required",
            workoutDescriptionError = "Description cannot exceed 500 characters",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutHeaderFormDisabledPreview() {
    LiftrixTheme {
        WorkoutHeaderForm(
            workoutName = "Push Day",
            onWorkoutNameChange = {},
            workoutDescription = "Chest, shoulders, and triceps workout",
            onWorkoutDescriptionChange = {},
            enabled = false,
            modifier = Modifier.padding(16.dp)
        )
    }
} 