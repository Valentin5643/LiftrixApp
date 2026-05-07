package com.example.liftrix.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit

/**
 * Weight input field with anomaly detection for unusually high weights
 */
@Composable
fun WeightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onAnomalyDetected: (Double) -> Unit = {},
    label: String = "Weight",
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    unit: WeightUnit = WeightUnit.KILOGRAMS
) {
    var showAnomalyWarning by remember { mutableStateOf(false) }
    
    // Check for anomaly when value changes and is a complete number
    LaunchedEffect(value) {
        if (value.isNotEmpty() && value.toDoubleOrNull() != null) {
            val inputValue = value.toDouble()
            val kgValue = unit.convertToKilograms(inputValue)
            
            if (Weight.isAnomalousWeight(kgValue)) {
                showAnomalyWarning = true
                onAnomalyDetected(kgValue)
            } else {
                showAnomalyWarning = false
            }
        } else {
            showAnomalyWarning = false
        }
    }
    
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Filter to only allow numeric input with decimal
                val filtered = newValue.filter { it.isDigit() || it == '.' }
                    .let { filtered ->
                        // Prevent multiple decimal points
                        if (filtered.count { it == '.' } <= 1) filtered else value
                    }
                onValueChange(filtered)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            enabled = enabled,
            isError = showAnomalyWarning,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = if (showAnomalyWarning) {
                {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Unusually high weight detected",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else null,
            supportingText = if (showAnomalyWarning) {
                {
                    Text(
                        text = "This weight seems unusually high. Please double-check.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else null,
            modifier = modifier
        )
    }
}