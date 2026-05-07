package com.example.liftrix.ui.validation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.validation.ValidationResult
import com.example.liftrix.ui.theme.LiftrixColors

object InputValidation {
    fun validateSessionNotes(notes: String): ValidationResult = when {
        notes.length > MAX_SESSION_NOTES_LENGTH ->
            ValidationResult.Error("Session notes cannot exceed $MAX_SESSION_NOTES_LENGTH characters")
        else -> ValidationResult.Success
    }

    fun validateWeight(weight: String): ValidationResult = when {
        weight.isBlank() -> ValidationResult.Success
        weight.toDoubleOrNull() == null -> ValidationResult.Error("Weight must be a valid number")
        weight.toDouble() < 0.0 -> ValidationResult.Error("Weight cannot be negative")
        weight.toDouble() > MAX_WEIGHT -> ValidationResult.Error("Weight is too high")
        else -> ValidationResult.Success
    }

    fun validateReps(reps: String): ValidationResult = when {
        reps.isBlank() -> ValidationResult.Error("Reps are required")
        reps.toIntOrNull() == null -> ValidationResult.Error("Reps must be a whole number")
        reps.toInt() < 0 -> ValidationResult.Error("Reps cannot be negative")
        reps.toInt() > MAX_REPS -> ValidationResult.Error("Reps are too high")
        else -> ValidationResult.Success
    }

    private const val MAX_SESSION_NOTES_LENGTH = 500
    private const val MAX_WEIGHT = 2000.0
    private const val MAX_REPS = 1000
}

@Composable
fun ValidationSummaryCard(
    validationResults: List<ValidationResult>,
    modifier: Modifier = Modifier
) {
    val errors = validationResults.filterIsInstance<ValidationResult.Error>()
    if (validationResults.isEmpty()) return

    androidx.compose.material3.Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            val isValid = errors.isEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (isValid) LiftrixColors.Primary else LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isValid) "All fields are valid" else "Please fix ${errors.size} issue(s)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            errors.forEach { error ->
                Text(
                    text = error.message,
                    color = LiftrixColors.TiffanyBlue,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                )
            }
        }
    }
}
