package com.example.liftrix.ui.onboarding.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Reusable text field component for onboarding forms with consistent styling,
 * validation support, and comprehensive accessibility features.
 * 
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param label Field label text
 * @param modifier Optional modifier for layout customization
 * @param placeholder Optional placeholder text
 * @param supportingText Optional supporting/helper text
 * @param validationResult Current validation state for styling and feedback
 * @param enabled Whether the field is enabled for input
 * @param readOnly Whether the field is read-only
 * @param singleLine Whether to restrict input to single line
 * @param maxLines Maximum number of lines (when singleLine is false)
 * @param minLines Minimum number of lines
 * @param keyboardType Type of keyboard to show
 * @param imeAction IME action for keyboard
 * @param keyboardActions Actions to perform on IME events
 * @param visualTransformation Visual transformation for text (e.g., password masking)
 * @param inputFilter Optional function to filter input characters
 * @param contentDescriptionText Custom content description for accessibility
 */
@Composable
fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    validationResult: ValidationResult? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    inputFilter: ((String) -> Boolean)? = null,
    contentDescriptionText: String? = null
) {
    val isError = validationResult is ValidationResult.Invalid
    val isValid = validationResult is ValidationResult.Valid
    
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (inputFilter?.invoke(newValue) != false) {
                onValueChange(newValue)
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        placeholder = placeholder?.let { placeholderText ->
            {
                Text(
                    text = placeholderText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingText = supportingText?.let { supportText ->
            {
                Text(
                    text = supportText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            when {
                isValid -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Valid input",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isError -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Invalid input",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        isError = isError,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .applyAccessibilitySemantics(
                contentDescription = contentDescriptionText ?: "$label input field"
            )
    )
}

/**
 * Reusable filter chip component for selection interfaces with consistent styling
 * and accessibility support.
 * 
 * @param selected Whether the chip is currently selected
 * @param onClick Callback when chip is clicked
 * @param label Text to display on the chip
 * @param modifier Optional modifier for layout customization
 * @param enabled Whether the chip is enabled for interaction
 * @param leadingIcon Optional leading icon content
 * @param contentDescriptionText Custom content description for accessibility
 */
@Composable
fun OnboardingFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    contentDescriptionText: String? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        leadingIcon = leadingIcon ?: if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .applyAccessibilitySemantics(
                contentDescription = contentDescriptionText ?: "$label selector",
                role = Role.Button
            )
    )
}

/**
 * Reusable primary action button for onboarding flows with consistent styling,
 * loading states, and accessibility support.
 * 
 * @param onClick Callback when button is clicked
 * @param text Button text content
 * @param modifier Optional modifier for layout customization
 * @param enabled Whether the button is enabled for interaction
 * @param isLoading Whether to show loading state
 * @param contentDescriptionText Custom content description for accessibility
 */
@Composable
fun PrimaryActionButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentDescriptionText: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // Minimum 44dp touch target + padding
            .applyAccessibilitySemantics(
                contentDescription = contentDescriptionText ?: if (enabled) {
                    "Continue to next step"
                } else {
                    "Complete current step to continue"
                },
                role = Role.Button
            )
    ) {
        Text(
            text = if (isLoading) "Loading..." else text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Utility function to apply consistent accessibility semantics to components.
 * Ensures proper content descriptions, roles, and touch target accessibility.
 * 
 * @param contentDescription Descriptive text for screen readers
 * @param role Semantic role of the component
 * @param testTag Optional test tag for UI testing
 */
fun Modifier.applyAccessibilitySemantics(
    contentDescription: String,
    role: Role? = null,
    testTag: String? = null
): Modifier = this.semantics {
    this.contentDescription = contentDescription
    role?.let { this.role = it }
    testTag?.let { 
        // Test tag would be applied here if needed for UI testing
        // Currently not implemented as it's not in the core semantics API
    }
}

/**
 * Input validation helper for common input patterns used in onboarding.
 */
object InputValidationFilters {
    
    /**
     * Filter for numeric input with optional length limit.
     */
    fun numericOnly(maxLength: Int = Int.MAX_VALUE): (String) -> Boolean = { input ->
        input.all { it.isDigit() } && input.length <= maxLength
    }
    
    /**
     * Filter for decimal numeric input with optional length limit.
     */
    fun decimalNumeric(maxLength: Int = Int.MAX_VALUE): (String) -> Boolean = { input ->
        input.isEmpty() || (input.matches(Regex("^\\d*\\.?\\d*$")) && input.length <= maxLength)
    }
    
    /**
     * Filter for text input with length limit.
     */
    fun textWithLength(maxLength: Int): (String) -> Boolean = { input ->
        input.length <= maxLength
    }
    
    /**
     * Filter for alphabetic characters only.
     */
    fun alphabeticOnly(): (String) -> Boolean = { input ->
        input.all { it.isLetter() || it.isWhitespace() }
    }
}

// Preview components for design verification
@Preview(showBackground = true, name = "OnboardingTextField")
@Composable
private fun OnboardingTextFieldPreview() {
    LiftrixTheme {
        OnboardingTextField(
            value = "25",
            onValueChange = {},
            label = "Age",
            placeholder = "Enter your age",
            supportingText = "Ages 13-100 are supported",
            validationResult = ValidationResult.Valid,
            keyboardType = KeyboardType.Number
        )
    }
}

@Preview(showBackground = true, name = "OnboardingTextField Error")
@Composable
private fun OnboardingTextFieldErrorPreview() {
    LiftrixTheme {
        OnboardingTextField(
            value = "12",
            onValueChange = {},
            label = "Age",
            placeholder = "Enter your age",
            supportingText = "Ages 13-100 are supported",
            validationResult = ValidationResult.Invalid("Age must be at least 13 years old"),
            keyboardType = KeyboardType.Number
        )
    }
}

@Preview(showBackground = true, name = "OnboardingFilterChip")
@Composable
private fun OnboardingFilterChipPreview() {
    LiftrixTheme {
        OnboardingFilterChip(
            selected = true,
            onClick = {},
            label = "Dumbbells"
        )
    }
}

@Preview(showBackground = true, name = "PrimaryActionButton")
@Composable
private fun PrimaryActionButtonPreview() {
    LiftrixTheme {
        PrimaryActionButton(
            onClick = {},
            text = "Continue",
            enabled = true
        )
    }
}

@Preview(showBackground = true, name = "PrimaryActionButton Loading")
@Composable
private fun PrimaryActionButtonLoadingPreview() {
    LiftrixTheme {
        PrimaryActionButton(
            onClick = {},
            text = "Continue",
            enabled = true,
            isLoading = true
        )
    }
} 
