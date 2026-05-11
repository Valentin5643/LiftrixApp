package com.example.liftrix.ui.onboarding.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.feature.auth.R
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import com.example.liftrix.ui.theme.LiftrixColorsV2

/**
 * Equipment selection screen for onboarding flow.
 * Provides multi-select equipment grid and other equipment input.
 */
@Composable
fun EquipmentSelectionScreen(
    selectedEquipment: Set<Equipment>,
    otherEquipment: String,
    onEquipmentToggle: (Equipment) -> Unit,
    onOtherEquipmentChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Validation state
    var equipmentValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
    var otherValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
    val validateProfileInputUseCase = remember { ValidateProfileInputUseCase() }
    
    // Validate equipment selection
    LaunchedEffect(selectedEquipment) {
        equipmentValidationResult = validateProfileInputUseCase.validateEquipmentSelection(
            selectedEquipment.toList()
        )
    }
    
    // Validate other equipment description
    LaunchedEffect(otherEquipment) {
        otherValidationResult = if (otherEquipment.isNotBlank()) {
            validateProfileInputUseCase.validateOtherEquipment(otherEquipment)
        } else {
            null
        }
    }
    
    val isEquipmentValid = equipmentValidationResult is ValidationResult.Valid
    val isOtherValid = otherEquipment.isBlank() || otherValidationResult is ValidationResult.Valid
    val canContinue = isEquipmentValid && isOtherValid
    
    OnboardingScreenTemplate(
        title = OnboardingStep.EQUIPMENT.title,
        subtitle = OnboardingStep.EQUIPMENT.description,
        currentStep = OnboardingStep.EQUIPMENT.stepNumber + 1,
        totalSteps = OnboardingStep.getContentSteps().size + 1,
        onBack = onBack,
        onSkip = onSkip,
        onContinue = onContinue,
        continueText = "Continue",
        canContinue = canContinue,
        isLoading = false,
        modifier = modifier
    ) {
        EquipmentSelectionContent(
            selectedEquipment = selectedEquipment,
            otherEquipment = otherEquipment,
            onEquipmentToggle = onEquipmentToggle,
            onOtherEquipmentChange = onOtherEquipmentChange,
            equipmentValidationResult = equipmentValidationResult,
            otherValidationResult = otherValidationResult
        )
    }
}

/**
 * Main content area for equipment selection.
 */
@Composable
private fun EquipmentSelectionContent(
    selectedEquipment: Set<Equipment>,
    otherEquipment: String,
    onEquipmentToggle: (Equipment) -> Unit,
    onOtherEquipmentChange: (String) -> Unit,
    equipmentValidationResult: ValidationResult?,
    otherValidationResult: ValidationResult?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        EquipmentSelectionHelperText()
        EquipmentGrid(
            selectedEquipment = selectedEquipment,
            onEquipmentToggle = onEquipmentToggle
        )
        SelectedEquipmentDisplay(selectedEquipment = selectedEquipment)
        OtherEquipmentInput(
            otherEquipment = otherEquipment,
            onOtherEquipmentChange = onOtherEquipmentChange,
            validationResult = otherValidationResult
        )
        EquipmentValidationMessage(validationResult = equipmentValidationResult)
    }
}

/**
 * Helper text explaining equipment selection.
 */
@Composable
private fun EquipmentSelectionHelperText() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_equipment),
            contentDescription = "Equipment onboarding illustration",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics {
                    contentDescription = "Equipment onboarding illustration"
                }
        )
        
        Text(
            text = "Select all equipment you have access to. This helps us recommend workouts that match your setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Grid layout for equipment selection using FilterChips.
 */
@Composable
private fun EquipmentGrid(
    selectedEquipment: Set<Equipment>,
    onEquipmentToggle: (Equipment) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(300.dp) // Fixed height to prevent layout issues
    ) {
        items(Equipment.values().toList()) { equipment ->
            EquipmentChip(
                equipment = equipment,
                isSelected = selectedEquipment.contains(equipment),
                onToggle = { onEquipmentToggle(equipment) }
            )
        }
    }
}

/**
 * Individual equipment FilterChip component.
 */
@Composable
private fun EquipmentChip(
    equipment: Equipment,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onToggle,
        label = {
            Text(
                text = equipment.displayName,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LiftrixColorsV2.TealSurface,
            selectedLabelColor = LiftrixColorsV2.TealDark,
            selectedLeadingIconColor = LiftrixColorsV2.TealDark
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${equipment.displayName} equipment selector"
            }
    )
}

/**
 * Display selected equipment count and list.
 */
@Composable
private fun SelectedEquipmentDisplay(
    selectedEquipment: Set<Equipment>
) {
    if (selectedEquipment.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected Equipment",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "${selectedEquipment.size}/10",
                        style = MaterialTheme.typography.labelLarge,
                        color = LiftrixColorsV2.Teal,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = selectedEquipment.joinToString(", ") { it.displayName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Other equipment input field with character counter.
 */
@Composable
private fun OtherEquipmentInput(
    otherEquipment: String,
    onOtherEquipmentChange: (String) -> Unit,
    validationResult: ValidationResult?
) {
    val isError = validationResult is ValidationResult.Invalid
    val isValid = validationResult is ValidationResult.Valid
    val characterCount = otherEquipment.length
    val maxLength = UserProfile.MAX_OTHER_EQUIPMENT_LENGTH
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Other Equipment",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        OutlinedTextField(
            value = otherEquipment,
            onValueChange = { newValue ->
                if (newValue.length <= maxLength) {
                    onOtherEquipmentChange(newValue)
                }
            },
            label = {
                Text(
                    text = "Describe other equipment",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            placeholder = {
                Text(
                    text = "e.g., Medicine ball, Yoga mat, Foam roller...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Optional - describe any additional equipment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "$characterCount/$maxLength",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (characterCount > maxLength * 0.9) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add equipment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                when {
                    isValid -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Valid description",
                            tint = LiftrixColorsV2.Teal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    isError -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Invalid description",
                            tint = LiftrixColorsV2.DataViz.Series1,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            maxLines = 3,
            isError = isError,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Other equipment description input"
                }
        )
        
        // Validation message for other equipment
        when (validationResult) {
            is ValidationResult.Loading -> {
                // Show loading state - no message displayed during validation
            }
            is ValidationResult.Invalid -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Other equipment validation error: ${validationResult.message}"
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Other equipment error",
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
            else -> {
                // No validation message for valid or null states
            }
        }
    }
}

/**
 * Equipment selection validation message.
 */
@Composable
private fun EquipmentValidationMessage(
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
                        contentDescription = "Equipment selection error: ${validationResult.message}"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Equipment error",
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
                        contentDescription = "Equipment selection is valid"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Equipment selection valid",
                    tint = LiftrixColorsV2.Teal,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "Great equipment selection!",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.Teal,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        null -> {
            // No validation message when no equipment selected yet
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EquipmentSelectionScreenPreview() {
    MaterialTheme {
        EquipmentSelectionScreen(
            selectedEquipment = setOf(Equipment.DUMBBELLS, Equipment.BENCH, Equipment.RESISTANCE_BANDS),
            otherEquipment = "Yoga mat, foam roller",
            onEquipmentToggle = {},
            onOtherEquipmentChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun EquipmentSelectionScreenEmptyPreview() {
    MaterialTheme {
        EquipmentSelectionScreen(
            selectedEquipment = emptySet(),
            otherEquipment = "",
            onEquipmentToggle = {},
            onOtherEquipmentChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun EquipmentSelectionScreenDarkPreview() {
    MaterialTheme {
        EquipmentSelectionScreen(
            selectedEquipment = setOf(Equipment.DUMBBELLS, Equipment.BARBELL),
            otherEquipment = "Medicine ball",
            onEquipmentToggle = {},
            onOtherEquipmentChange = {},
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}
