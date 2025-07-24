package com.example.liftrix.ui.validation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.validation.ValidationResult
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard

/**
 * ValidationFeedback - Visual components for displaying validation states and errors
 * 
 * Provides consistent validation feedback UI components using Liftrix design system
 * with coral accent color for errors and proper accessibility compliance.
 */

/**
 * Inline validation feedback for individual form fields
 * Shows success/error states with appropriate colors and icons
 */
@Composable
fun ValidationFeedback(
    validation: ValidationResult,
    modifier: Modifier = Modifier,
    showSuccessState: Boolean = false
) {
    when (validation) {
        is ValidationResult.Success -> {
            if (showSuccessState) {
                SuccessValidationFeedback(modifier = modifier)
            }
        }
        is ValidationResult.Error -> {
            ErrorValidationFeedback(
                message = validation.message,
                modifier = modifier
            )
        }
    }
}

/**
 * Success validation feedback with check icon
 */
@Composable
private fun SuccessValidationFeedback(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(start = 16.dp, top = 4.dp)
            .semantics {
                contentDescription = "Input is valid"
                role = Role.Image
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = LiftrixColors.Primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Looks good",
            color = LiftrixColors.Primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Error validation feedback with error icon and coral accent color
 */
@Composable
private fun ErrorValidationFeedback(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(start = 16.dp, top = 4.dp)
            .semantics {
                contentDescription = "Error: $message"
                role = Role.Image
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = LiftrixColors.TiffanyBlue, // Coral accent for errors
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = message,
            color = LiftrixColors.TiffanyBlue, // Coral accent for errors
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Form validation summary card
 * Shows all validation errors in a unified card format
 */
@Composable
fun ValidationSummaryCard(
    validations: List<ValidationResult>,
    modifier: Modifier = Modifier,
    title: String = "Please fix the following issues:"
) {
    val errors = validations.filterIsInstance<ValidationResult.Error>()
    
    if (errors.isNotEmpty()) {
        UnifiedWorkoutCard(
            title = title,
            subtitle = "${errors.size} ${if (errors.size == 1) "issue" else "issues"} found",
            modifier = modifier,
            leadingIcon = Icons.Default.Warning
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                errors.forEach { error ->
                    ValidationErrorItem(
                        message = error.message,
                        field = error.field
                    )
                }
            }
        }
    }
}

/**
 * Individual validation error item for summary cards
 */
@Composable
private fun ValidationErrorItem(
    message: String,
    field: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (field != null) {
                    "Error in $field: $message"
                } else {
                    "Error: $message"
                }
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = LiftrixColors.TiffanyBlue, // Coral accent for errors
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp) // Align with text baseline
        )
        
        Column(modifier = Modifier.weight(1f)) {
            if (field != null) {
                Text(
                    text = field.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Comprehensive form validation status indicator
 * Shows overall form status with progress indication
 */
@Composable
fun FormValidationStatus(
    validations: List<ValidationResult>,
    modifier: Modifier = Modifier
) {
    val errors = validations.filterIsInstance<ValidationResult.Error>()
    val totalFields = validations.size
    val validFields = totalFields - errors.size
    val validationPercentage = if (totalFields > 0) {
        (validFields.toFloat() / totalFields.toFloat())
    } else {
        1f
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (errors.isEmpty()) {
                LiftrixColors.PrimaryContainer.copy(alpha = 0.1f)
            } else {
                LiftrixColors.TertiaryContainer.copy(alpha = 0.1f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (errors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (errors.isEmpty()) LiftrixColors.Primary else LiftrixColors.TiffanyBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = if (errors.isEmpty()) "Form is valid" else "Form has errors",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Text(
                    text = "$validFields/$totalFields fields",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Progress indicator
            if (totalFields > 0) {
                LinearProgressIndicator(
                    progress = validationPercentage,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (errors.isEmpty()) LiftrixColors.Primary else LiftrixColors.TiffanyBlue,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }
            
            // Error summary
            if (errors.isNotEmpty()) {
                Text(
                    text = "${errors.size} ${if (errors.size == 1) "error" else "errors"} need to be fixed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Real-time validation indicator for text fields
 * Shows validation state as user types
 */
@Composable
fun RealtimeValidationIndicator(
    validation: ValidationResult,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val (icon, color) = when (validation) {
        is ValidationResult.Success -> Icons.Default.CheckCircle to LiftrixColors.Primary
        is ValidationResult.Error -> Icons.Default.Error to LiftrixColors.TiffanyBlue
    }
    
    Icon(
        imageVector = icon,
        contentDescription = when (validation) {
            is ValidationResult.Success -> "Valid input"
            is ValidationResult.Error -> "Invalid input: ${validation.message}"
        },
        tint = color,
        modifier = modifier.size(16.dp)
    )
}

/**
 * Validation tooltip for detailed error explanations
 */
@Composable
fun ValidationTooltip(
    validation: ValidationResult.Error,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = LiftrixColors.TiffanyBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Validation Error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = validation.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}