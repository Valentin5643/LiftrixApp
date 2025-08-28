package com.example.liftrix.ui.workout.custom.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Exercise type selector component for choosing the primary exercise type.
 * 
 * This component helps users select how their custom exercise should be tracked:
 * - WEIGHT_BASED: Tracks weight, reps, and sets (barbell, dumbbells, etc.)
 * - TIME_BASED: Tracks duration and intensity (planks, wall sits, etc.)
 * - DISTANCE_BASED: Tracks distance and time (running, cycling, etc.)
 * - BODYWEIGHT: Tracks reps and sets without weight (push-ups, pull-ups, etc.)
 * - CARDIO: Flexible tracking combining time, distance, and intensity
 * - HYBRID: Supports multiple tracking methods for versatile exercises
 * 
 * Features:
 * - Visual icons for each exercise type for quick recognition
 * - Descriptive text explaining what each type tracks
 * - Material 3 design with proper selection states
 * - Accessibility support with semantic descriptions
 * - Responsive layout that adapts to screen size
 * - Single-selection behavior with clear visual feedback
 * - Helpful examples for each exercise type
 * 
 * @param selectedType Currently selected exercise type
 * @param onTypeSelected Callback when user selects a new type
 * @param modifier Modifier for styling the component
 * @param showDescriptions Whether to show detailed descriptions (default: true)
 */
@Composable
fun ExerciseTypeSelector(
    selectedType: ExerciseType,
    onTypeSelected: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier,
    showDescriptions: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header section
        Text(
            text = "Exercise Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (showDescriptions) {
            Text(
                text = "Choose how this exercise should be tracked in your workouts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Exercise type options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Primary exercise types (most commonly used)
                ExerciseTypeOption(
                    type = ExerciseType.WEIGHT_BASED,
                    icon = Icons.Default.FitnessCenter,
                    title = "Weight-Based",
                    description = "Tracks weight, reps, and sets",
                    examples = "Barbell squats, dumbbell press, bench press",
                    isSelected = selectedType == ExerciseType.WEIGHT_BASED,
                    onSelected = onTypeSelected,
                    showDescription = showDescriptions
                )
                
                ExerciseTypeOption(
                    type = ExerciseType.BODYWEIGHT,
                    icon = Icons.Default.DirectionsRun,
                    title = "Bodyweight",
                    description = "Tracks reps and sets without added weight",
                    examples = "Push-ups, pull-ups, bodyweight squats",
                    isSelected = selectedType == ExerciseType.BODYWEIGHT,
                    onSelected = onTypeSelected,
                    showDescription = showDescriptions
                )
                
                ExerciseTypeOption(
                    type = ExerciseType.TIME_BASED,
                    icon = Icons.Default.Timer,
                    title = "Time-Based",
                    description = "Tracks duration and holds",
                    examples = "Planks, wall sits, dead hangs",
                    isSelected = selectedType == ExerciseType.TIME_BASED,
                    onSelected = onTypeSelected,
                    showDescription = showDescriptions
                )
                
                ExerciseTypeOption(
                    type = ExerciseType.DISTANCE_BASED,
                    icon = Icons.Default.Straighten,
                    title = "Distance-Based",
                    description = "Tracks distance and pace",
                    examples = "Running, cycling, swimming",
                    isSelected = selectedType == ExerciseType.DISTANCE_BASED,
                    onSelected = onTypeSelected,
                    showDescription = showDescriptions
                )
                
                ExerciseTypeOption(
                    type = ExerciseType.CARDIO,
                    icon = Icons.Default.Favorite,
                    title = "Cardio",
                    description = "Flexible cardio tracking",
                    examples = "HIIT, circuit training, dance",
                    isSelected = selectedType == ExerciseType.CARDIO,
                    onSelected = onTypeSelected,
                    showDescription = showDescriptions
                )
                
                ExerciseTypeOption(
                    type = ExerciseType.HYBRID,
                    icon = Icons.Default.Tune,
                    title = "Hybrid",
                    description = "Supports multiple tracking methods",
                    examples = "Burpees, mountain climbers, functional movements",
                    isSelected = selectedType == ExerciseType.HYBRID,
                    onSelected = onTypeSelected,
                    showDescription = showDescriptions
                )
            }
        }
    }
}

/**
 * Individual exercise type option component.
 */
@Composable
private fun ExerciseTypeOption(
    type: ExerciseType,
    icon: ImageVector,
    title: String,
    description: String,
    examples: String,
    isSelected: Boolean,
    onSelected: (ExerciseType) -> Unit,
    showDescription: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = if (isSelected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = { onSelected(type) },
                role = Role.RadioButton
            )
            .semantics {
                contentDescription = if (isSelected) {
                    "$title exercise type selected. $description. Examples: $examples"
                } else {
                    "Select $title exercise type. $description. Examples: $examples"
                }
            },
        colors = colors,
        elevation = if (isSelected) {
            CardDefaults.cardElevation(defaultElevation = 4.dp)
        } else {
            CardDefaults.cardElevation(defaultElevation = 1.dp)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with selection indicator
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                // Description
                if (showDescription) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    // Examples
                    Text(
                        text = examples,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            // Selection indicator
            RadioButton(
                selected = isSelected,
                onClick = null, // Handled by card selection
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Compact exercise type selector for smaller spaces.
 * Uses chips instead of cards for a more compact layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactExerciseTypeSelector(
    selectedType: ExerciseType,
    onTypeSelected: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier,
    availableTypes: List<ExerciseType> = listOf(
        ExerciseType.WEIGHT_BASED,
        ExerciseType.BODYWEIGHT,
        ExerciseType.TIME_BASED,
        ExerciseType.DISTANCE_BASED,
        ExerciseType.CARDIO,
        ExerciseType.HYBRID
    )
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Exercise Type",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableTypes.forEach { type ->
                val isSelected = selectedType == type
                val displayName = when (type) {
                    ExerciseType.WEIGHT_BASED -> "Weight"
                    ExerciseType.BODYWEIGHT -> "Bodyweight"
                    ExerciseType.TIME_BASED -> "Time"
                    ExerciseType.DISTANCE_BASED -> "Distance"
                    ExerciseType.CARDIO -> "Cardio"
                    ExerciseType.HYBRID -> "Hybrid"
                    else -> type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                }
                
                FilterChip(
                    onClick = { onTypeSelected(type) },
                    label = {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    selected = isSelected,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = if (isSelected) {
                            "$displayName exercise type selected"
                        } else {
                            "Select $displayName exercise type"
                        }
                    }
                )
            }
        }
    }
}

/**
 * Gets the tracking fields that should be shown for each exercise type.
 */
fun getTrackingFieldsForType(type: ExerciseType): Set<TrackingField> {
    return when (type) {
        ExerciseType.WEIGHT_BASED -> setOf(
            TrackingField.WEIGHT,
            TrackingField.REPS,
            TrackingField.SETS
        )
        ExerciseType.BODYWEIGHT -> setOf(
            TrackingField.REPS,
            TrackingField.SETS
        )
        ExerciseType.TIME_BASED -> setOf(
            TrackingField.DURATION,
            TrackingField.SETS
        )
        ExerciseType.DISTANCE_BASED -> setOf(
            TrackingField.DISTANCE,
            TrackingField.DURATION
        )
        ExerciseType.CARDIO -> setOf(
            TrackingField.DURATION,
            TrackingField.DISTANCE,
            TrackingField.HEART_RATE
        )
        ExerciseType.HYBRID -> setOf(
            TrackingField.WEIGHT,
            TrackingField.REPS,
            TrackingField.SETS,
            TrackingField.DURATION,
            TrackingField.DISTANCE
        )
        else -> setOf(
            TrackingField.WEIGHT,
            TrackingField.REPS,
            TrackingField.SETS
        )
    }
}

/**
 * Enum representing the different tracking fields available for exercises.
 */
enum class TrackingField {
    WEIGHT,
    REPS,
    SETS,
    DURATION,
    DISTANCE,
    HEART_RATE,
    CALORIES
}

@Preview(showBackground = true)
@Composable
private fun ExerciseTypeSelectorPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Exercise Type Selector Variants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Full selector
            Text("Full Selector:", style = MaterialTheme.typography.titleSmall)
            ExerciseTypeSelector(
                selectedType = ExerciseType.WEIGHT_BASED,
                onTypeSelected = { },
                showDescriptions = true
            )
            
            // Compact selector
            Text("Compact Selector:", style = MaterialTheme.typography.titleSmall)
            CompactExerciseTypeSelector(
                selectedType = ExerciseType.BODYWEIGHT,
                onTypeSelected = { }
            )
        }
    }
}