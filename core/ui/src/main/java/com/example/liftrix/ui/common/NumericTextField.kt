package com.example.liftrix.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * Simple numeric text field component for Liftrix app
 */
@Composable
fun NumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Filter to only allow numeric input
            val filtered = newValue.filter { it.isDigit() || it == '.' }
            onValueChange(filtered)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
} 