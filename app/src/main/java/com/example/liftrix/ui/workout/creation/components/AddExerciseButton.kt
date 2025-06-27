package com.example.liftrix.ui.workout.creation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Floating action button for adding exercises to the workout
 * following Material 3 design guidelines and accessibility standards
 */
@Composable
fun AddExerciseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ExtendedFloatingActionButton(
        text = {
            Text(
                text = "Add Exercise",
                style = MaterialTheme.typography.labelLarge
            )
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )
        },
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Add exercise to workout"
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}

@Preview(showBackground = true)
@Composable
private fun AddExerciseButtonPreview() {
    LiftrixTheme {
        AddExerciseButton(
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddExerciseButtonDisabledPreview() {
    LiftrixTheme {
        AddExerciseButton(
            onClick = {},
            enabled = false
        )
    }
} 