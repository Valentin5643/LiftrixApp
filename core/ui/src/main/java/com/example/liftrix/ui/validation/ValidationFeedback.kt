package com.example.liftrix.ui.validation

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

@Composable
fun RealtimeValidationIndicator(
    validation: ValidationResult,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val (icon, tint) = when (validation) {
        is ValidationResult.Success -> Icons.Filled.CheckCircle to LiftrixColors.Primary
        is ValidationResult.Error -> Icons.Filled.Error to LiftrixColors.TiffanyBlue
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(20.dp)
    )
}

@Composable
fun ValidationFeedback(
    validation: ValidationResult,
    showSuccessState: Boolean,
    modifier: Modifier = Modifier
) {
    val message = when (validation) {
        is ValidationResult.Error -> validation.message
        is ValidationResult.Success -> if (showSuccessState) "Looks good" else null
    } ?: return

    val tint = when (validation) {
        is ValidationResult.Error -> LiftrixColors.TiffanyBlue
        is ValidationResult.Success -> LiftrixColors.Primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 4.dp)
    ) {
        RealtimeValidationIndicator(
            validation = validation,
            isActive = true
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message,
            color = tint,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
