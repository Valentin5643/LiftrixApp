package com.example.liftrix.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.ui.onboarding.OnboardingState
import com.example.liftrix.ui.onboarding.WeightUnit
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import androidx.compose.foundation.isSystemInDarkTheme
import kotlin.math.round

/**
 * Weight input screen for onboarding flow.
 * Provides weight input with unit conversion, validation, and skip option.
 */
@Composable
fun WeightInputScreen(
    currentWeight: String,
    currentUnit: WeightUnit,
    onWeightChange: (String) -> Unit,
    onUnitChange: (WeightUnit) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Validation state
    var validationResult by remember { mutableStateOf<ValidationResult?>(null) }
    val validateProfileInputUseCase = remember { ValidateProfileInputUseCase() }
    
    // Validate weight on input change
    LaunchedEffect(currentWeight, currentUnit) {
        validationResult = if (currentWeight.isNotBlank()) {
            validateProfileInputUseCase.validateWeight(currentWeight, currentUnit.symbol)
        } else {
            null
        }
    }
    
    val isValid = validationResult is ValidationResult.Valid
    val canContinue = currentWeight.isNotBlank() && isValid
    
    OnboardingScreenTemplate(
        title = OnboardingStep.WEIGHT.title,
        subtitle = OnboardingStep.WEIGHT.description,
        currentStep = OnboardingStep.WEIGHT.stepNumber + 1,
        totalSteps = OnboardingStep.getContentSteps().size + 1,
        onBack = onBack,
        onSkip = onSkip,
        onContinue = onContinue,
        continueText = "Continue",
        canContinue = canContinue,
        isLoading = false,
        modifier = modifier
    ) {
        WeightInputContent(
            weight = currentWeight,
            unit = currentUnit,
            onWeightChange = onWeightChange,
            onUnitChange = onUnitChange,
            validationResult = validationResult,
            onSkip = onSkip
        )
    }
}

/**
 * Main content area for weight input with unit selection and conversion.
 */
@Composable
private fun WeightInputContent(
    weight: String,
    unit: WeightUnit,
    onWeightChange: (String) -> Unit,
    onUnitChange: (WeightUnit) -> Unit,
    validationResult: ValidationResult?,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        WeightInputHelperText()
        WeightInputField(
            weight = weight,
            onWeightChange = onWeightChange,
            validationResult = validationResult
        )
        UnitSelector(
            selectedUnit = unit,
            onUnitChange = onUnitChange
        )
        ConversionDisplay(
            weight = weight,
            currentUnit = unit
        )
        WeightValidationMessage(validationResult = validationResult)
        SkipOption(onSkip = onSkip)
    }
}

/**
 * Helper text explaining why weight is collected.
 */
@Composable
private fun WeightInputHelperText() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "⚖️",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "Weight helps us track your progress over time and provide more accurate workout recommendations.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Weight input field with decimal number keyboard.
 */
@Composable
private fun WeightInputField(
    weight: String,
    onWeightChange: (String) -> Unit,
    validationResult: ValidationResult?
) {
    val isError = validationResult is ValidationResult.Invalid
    val isValid = validationResult is ValidationResult.Valid
    
    OutlinedTextField(
        value = weight,
        onValueChange = { newValue ->
            // Allow numbers and one decimal point
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                onWeightChange(newValue)
            }
        },
        label = {
            Text(
                text = "Weight",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        placeholder = {
            Text(
                text = "Enter your weight",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
            )
        },
        supportingText = {
            Text(
                text = "This information is optional",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
            )
        },
        trailingIcon = {
            when {
                isValid -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Valid weight",
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isError -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Invalid weight",
                        tint = LiftrixColorsV2.DataViz.Series1,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        singleLine = true,
        isError = isError,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Weight input field"
            }
    )
}

/**
 * Unit selector using FilterChip toggle.
 */
@Composable
private fun UnitSelector(
    selectedUnit: WeightUnit,
    onUnitChange: (WeightUnit) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Unit",
            style = MaterialTheme.typography.titleMedium,
            color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeightUnit.entries.forEach { unit ->
                FilterChip(
                    selected = selectedUnit == unit,
                    onClick = { onUnitChange(unit) },
                    label = {
                        Text(
                            text = unit.displayName,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Text(
                            text = unit.symbol,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiftrixColorsV2.TealSurface,
                        selectedLabelColor = LiftrixColorsV2.TealDark
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "${unit.displayName} unit selector"
                    }
                )
            }
        }
    }
}

/**
 * Live conversion display showing equivalent in other unit.
 */
@Composable
private fun ConversionDisplay(
    weight: String,
    currentUnit: WeightUnit
) {
    val weightValue = weight.toDoubleOrNull()
    
    if (weightValue != null && weightValue > 0) {
        val convertedValue = when (currentUnit) {
            WeightUnit.KILOGRAMS -> {
                val pounds = weightValue / 0.453592
                "${round(pounds * 10) / 10} ${WeightUnit.POUNDS.symbol}"
            }
            WeightUnit.POUNDS -> {
                val kilograms = weightValue * 0.453592
                "${round(kilograms * 10) / 10} ${WeightUnit.KILOGRAMS.symbol}"
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Weight conversion: $weight ${currentUnit.symbol} equals $convertedValue"
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$weight ${currentUnit.symbol}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "=",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = convertedValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LiftrixColorsV2.Teal,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Validation message display with appropriate styling.
 */
@Composable
private fun WeightValidationMessage(
    validationResult: ValidationResult?
) {
    when (validationResult) {
        is ValidationResult.Loading -> {
            // Show loading state - no message displayed during validation
        }
        is ValidationResult.Invalid -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Weight validation error: ${validationResult.message}"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = LiftrixColorsV2.DataViz.Series1,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = validationResult.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.DataViz.Series1,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        is ValidationResult.Valid -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Weight is valid"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LiftrixColorsV2.Teal,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "Weight looks good!",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.Teal,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        null -> {
            // No validation message when field is empty
        }
    }
}

/**
 * Skip option for users who prefer not to share weight.
 */
@Composable
private fun SkipOption(
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Prefer not to say?",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSystemInDarkTheme()) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                fontWeight = FontWeight.Medium
            )
            
            TextButton(
                onClick = onSkip,
                modifier = Modifier.semantics {
                    contentDescription = "Skip weight input"
                }
            ) {
                Text(
                    text = "Skip this step",
                    style = MaterialTheme.typography.labelLarge,
                    color = LiftrixColorsV2.Teal
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WeightInputScreenPreview() {
    LiftrixTheme {
        WeightInputScreen(
            currentWeight = "70.5",
            currentUnit = WeightUnit.KILOGRAMS,
            onWeightChange = {},
            onUnitChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Pounds Unit")
@Composable
private fun WeightInputScreenPoundsPreview() {
    LiftrixTheme {
        WeightInputScreen(
            currentWeight = "155",
            currentUnit = WeightUnit.POUNDS,
            onWeightChange = {},
            onUnitChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun WeightInputScreenEmptyPreview() {
    LiftrixTheme {
        WeightInputScreen(
            currentWeight = "",
            currentUnit = WeightUnit.KILOGRAMS,
            onWeightChange = {},
            onUnitChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun WeightInputScreenDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        WeightInputScreen(
            currentWeight = "70.5",
            currentUnit = WeightUnit.KILOGRAMS,
            onWeightChange = {},
            onUnitChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}
