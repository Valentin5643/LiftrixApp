package com.example.liftrix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.validation.ValidationResult
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.validation.ValidationFeedback
import com.example.liftrix.ui.validation.RealtimeValidationIndicator

/**
 * ValidatedTextField - Enhanced OutlinedTextField with real-time validation feedback
 * 
 * Wraps Material 3 OutlinedTextField with integrated validation state management,
 * user-friendly error display, and accessibility compliance following Liftrix design patterns.
 * 
 * Features:
 * - Real-time validation with visual feedback
 * - Coral accent color for error states  
 * - Accessibility-compliant error announcements
 * - Success state indicators (optional)
 * - Integration with Liftrix theme system
 */
@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validationResult: ValidationResult,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isRequired: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardCapitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    showSuccessState: Boolean = false,
    showRealtimeIndicator: Boolean = true,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var hasUserInteracted by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    // Show validation feedback only after user has interacted or if there's an error
    val shouldShowValidation = hasUserInteracted || validationResult is ValidationResult.Error
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                hasUserInteracted = true
                onValueChange(newValue)
            },
            label = {
                Text(
                    text = if (isRequired) "$label *" else label,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        validationResult is ValidationResult.Error -> LiftrixColors.TiffanyBlue
                        isFocused -> LiftrixColors.Primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon,
            trailingIcon = if (showRealtimeIndicator && shouldShowValidation) {
                {
                    RealtimeValidationIndicator(
                        validation = validationResult,
                        isActive = true
                    )
                }
            } else null,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = keyboardCapitalization,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onNext?.invoke() },
                onDone = {
                    keyboardController?.hide()
                    onDone?.invoke()
                }
            ),
            visualTransformation = visualTransformation,
            isError = validationResult is ValidationResult.Error,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        hasUserInteracted = true
                    }
                }
                .semantics {
                    contentDescription = buildContentDescription(
                        label = label,
                        isRequired = isRequired,
                        validationResult = validationResult,
                        hasError = validationResult is ValidationResult.Error
                    )
                    
                    // Add error state for accessibility
                    if (validationResult is ValidationResult.Error) {
                        error(validationResult.message)
                    }
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = when (validationResult) {
                    is ValidationResult.Error -> LiftrixColors.TiffanyBlue
                    is ValidationResult.Success -> if (showSuccessState && value.isNotEmpty()) {
                        LiftrixColors.Primary
                    } else {
                        LiftrixColors.Primary
                    }
                },
                unfocusedBorderColor = when (validationResult) {
                    is ValidationResult.Error -> LiftrixColors.TiffanyBlue.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.outline
                },
                errorBorderColor = LiftrixColors.TiffanyBlue,
                errorLabelColor = LiftrixColors.TiffanyBlue,
                errorLeadingIconColor = LiftrixColors.TiffanyBlue,
                errorTrailingIconColor = LiftrixColors.TiffanyBlue,
                focusedLabelColor = when (validationResult) {
                    is ValidationResult.Error -> LiftrixColors.TiffanyBlue
                    else -> LiftrixColors.Primary
                },
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = when (validationResult) {
                    is ValidationResult.Error -> LiftrixColors.TiffanyBlue
                    else -> LiftrixColors.Primary
                }
            )
        )
        
        // Validation feedback
        if (shouldShowValidation) {
            ValidationFeedback(
                validation = validationResult,
                showSuccessState = showSuccessState && value.isNotEmpty()
            )
        }
    }
}

/**
 * ValidatedTextField variant for multiline input (descriptions, notes)
 */
@Composable
fun ValidatedMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validationResult: ValidationResult,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isRequired: Boolean = false,
    minLines: Int = 3,
    maxLines: Int = 6,
    showSuccessState: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    ValidatedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        validationResult = validationResult,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        isRequired = isRequired,
        keyboardType = KeyboardType.Text,
        keyboardCapitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done,
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines,
        showSuccessState = showSuccessState,
        showRealtimeIndicator = false, // Disable for multiline to avoid layout issues
        onDone = onDone
    )
}

/**
 * ValidatedTextField variant for numeric input (weights, reps)
 */
@Composable
fun ValidatedNumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validationResult: ValidationResult,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    enabled: Boolean = true,
    isRequired: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Number,
    showSuccessState: Boolean = true,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    ValidatedTextField(
        value = value,
        onValueChange = { newValue ->
            // Filter out non-numeric characters (except decimal point for weights)
            val filteredValue = if (keyboardType == KeyboardType.Number) {
                newValue.filter { it.isDigit() || it == '.' }
            } else {
                newValue.filter { it.isDigit() }
            }
            onValueChange(filteredValue)
        },
        label = label,
        validationResult = validationResult,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        isRequired = isRequired,
        keyboardType = keyboardType,
        keyboardCapitalization = KeyboardCapitalization.None,
        imeAction = if (onNext != null) ImeAction.Next else ImeAction.Done,
        showSuccessState = showSuccessState,
        onNext = onNext,
        onDone = onDone
    )
}

/**
 * ValidatedTextField variant for required text input
 */
@Composable
fun ValidatedRequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validationResult: ValidationResult,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    showSuccessState: Boolean = true,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    ValidatedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        validationResult = validationResult,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        enabled = enabled,
        isRequired = true,
        keyboardType = keyboardType,
        imeAction = imeAction,
        showSuccessState = showSuccessState,
        onNext = onNext,
        onDone = onDone
    )
}

/**
 * Build accessibility content description for text field
 */
private fun buildContentDescription(
    label: String,
    isRequired: Boolean,
    validationResult: ValidationResult,
    hasError: Boolean
): String {
    return buildString {
        append(label)
        if (isRequired) {
            append(", required field")
        }
        when (validationResult) {
            is ValidationResult.Success -> {
                if (hasError) {
                    // Previously had error but now valid
                    append(", input is now valid")
                }
            }
            is ValidationResult.Error -> {
                append(", has error: ${validationResult.message}")
            }
        }
    }
}