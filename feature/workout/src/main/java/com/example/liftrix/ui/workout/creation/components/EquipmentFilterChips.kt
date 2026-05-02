package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Equipment filter chips component for multi-select filtering
 * following Material 3 design guidelines and accessibility standards
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentFilterChips(
    selectedEquipment: Set<Equipment>,
    onSelectionChange: (Set<Equipment>) -> Unit,
    modifier: Modifier = Modifier,
    availableEquipment: List<Equipment> = Equipment.entries
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        availableEquipment.forEach { equipment ->
            val isSelected = selectedEquipment.contains(equipment)
            
            FilterChip(
                onClick = {
                    val newSelection = if (isSelected) {
                        selectedEquipment - equipment
                    } else {
                        selectedEquipment + equipment
                    }
                    onSelectionChange(newSelection)
                },
                label = {
                    Text(
                        text = equipment.displayName,
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
                        "Remove ${equipment.displayName} filter"
                    } else {
                        "Add ${equipment.displayName} filter"
                    }
                }
            )
        }
    }
}

/**
 * Muscle group filter chips component for multi-select filtering
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleGroupFilterChips(
    selectedMuscleGroups: Set<com.example.liftrix.domain.model.ExerciseCategory>,
    onSelectionChange: (Set<com.example.liftrix.domain.model.ExerciseCategory>) -> Unit,
    modifier: Modifier = Modifier,
    availableMuscleGroups: List<com.example.liftrix.domain.model.ExerciseCategory> = com.example.liftrix.domain.model.ExerciseCategory.entries
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        availableMuscleGroups.forEach { muscleGroup ->
            val isSelected = selectedMuscleGroups.contains(muscleGroup)
            
            FilterChip(
                onClick = {
                    val newSelection = if (isSelected) {
                        selectedMuscleGroups - muscleGroup
                    } else {
                        selectedMuscleGroups + muscleGroup
                    }
                    onSelectionChange(newSelection)
                },
                label = {
                    Text(
                        text = muscleGroup.displayName,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                selected = isSelected,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (isSelected) {
                        "Remove ${muscleGroup.displayName} filter"
                    } else {
                        "Add ${muscleGroup.displayName} filter"
                    }
                }
            )
        }
    }
}

/**
 * Unified horizontal scrollable filter chips for both equipment and muscle groups
 * Provides a compact single-row layout to maximize exercise list visibility
 */
@Composable
fun UnifiedFilterChips(
    selectedEquipment: Set<Equipment>,
    onEquipmentSelectionChange: (Set<Equipment>) -> Unit,
    selectedMuscleGroups: Set<com.example.liftrix.domain.model.ExerciseCategory>,
    onMuscleGroupSelectionChange: (Set<com.example.liftrix.domain.model.ExerciseCategory>) -> Unit,
    modifier: Modifier = Modifier,
    availableEquipment: List<Equipment> = Equipment.entries,
    availableMuscleGroups: List<com.example.liftrix.domain.model.ExerciseCategory> = com.example.liftrix.domain.model.ExerciseCategory.entries
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Equipment chips with primary color scheme
        availableEquipment.forEach { equipment ->
            val isSelected = selectedEquipment.contains(equipment)
            
            FilterChip(
                onClick = {
                    val newSelection = if (isSelected) {
                        selectedEquipment - equipment
                    } else {
                        selectedEquipment + equipment
                    }
                    onEquipmentSelectionChange(newSelection)
                },
                label = {
                    Text(
                        text = equipment.displayName,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = isSelected,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .semantics {
                        contentDescription = if (isSelected) {
                            "Remove ${equipment.displayName} equipment filter"
                        } else {
                            "Add ${equipment.displayName} equipment filter"
                        }
                    }
            )
        }
        
        // Muscle group chips with secondary color scheme for visual distinction
        availableMuscleGroups.forEach { muscleGroup ->
            val isSelected = selectedMuscleGroups.contains(muscleGroup)
            
            FilterChip(
                onClick = {
                    val newSelection = if (isSelected) {
                        selectedMuscleGroups - muscleGroup
                    } else {
                        selectedMuscleGroups + muscleGroup
                    }
                    onMuscleGroupSelectionChange(newSelection)
                },
                label = {
                    Text(
                        text = muscleGroup.displayName,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = isSelected,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .semantics {
                        contentDescription = if (isSelected) {
                            "Remove ${muscleGroup.displayName} muscle group filter"
                        } else {
                            "Add ${muscleGroup.displayName} muscle group filter"
                        }
                    }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EquipmentFilterChipsPreview() {
    LiftrixTheme {
        EquipmentFilterChips(
            selectedEquipment = setOf(Equipment.DUMBBELLS, Equipment.BARBELL),
            onSelectionChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MuscleGroupFilterChipsPreview() {
    LiftrixTheme {
        MuscleGroupFilterChips(
            selectedMuscleGroups = setOf(
                com.example.liftrix.domain.model.ExerciseCategory.CHEST,
                com.example.liftrix.domain.model.ExerciseCategory.SHOULDERS
            ),
            onSelectionChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UnifiedFilterChipsPreview() {
    LiftrixTheme {
        UnifiedFilterChips(
            selectedEquipment = setOf(Equipment.DUMBBELLS, Equipment.BARBELL),
            onEquipmentSelectionChange = {},
            selectedMuscleGroups = setOf(
                com.example.liftrix.domain.model.ExerciseCategory.CHEST,
                com.example.liftrix.domain.model.ExerciseCategory.SHOULDERS
            ),
            onMuscleGroupSelectionChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}