package com.example.liftrix.ui.workout.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Basic Information Section
 */
@Composable
internal fun BasicInfoSection(
    formState: CustomExerciseFormState,
    onEvent: (CustomExerciseCreationEvent) -> Unit,
    focusManager: FocusManager
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Exercise Name
        OutlinedTextField(
            value = formState.name,
            onValueChange = { onEvent(CustomExerciseCreationEvent.UpdateName(it)) },
            label = { Text("Exercise Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            isError = formState.nameError != null,
            supportingText = formState.nameError?.let { { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiftrixColorsV2.Teal,
                focusedLabelColor = LiftrixColorsV2.Teal
            )
        )
        
        // Description
        OutlinedTextField(
            value = formState.description,
            onValueChange = { onEvent(CustomExerciseCreationEvent.UpdateDescription(it)) },
            label = { Text("Description") },
            placeholder = { Text("Brief description of the exercise...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            isError = formState.descriptionError != null,
            supportingText = formState.descriptionError?.let { { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiftrixColorsV2.Teal,
                focusedLabelColor = LiftrixColorsV2.Teal
            )
        )
    }
}

/**
 * Exercise Details Section
 */
@Composable
internal fun ExerciseDetailsSection(
    formState: CustomExerciseFormState,
    onEvent: (CustomExerciseCreationEvent) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Primary Muscle Group
        ExerciseCategoryDropdown(
            label = "Primary Muscle Group *",
            selectedCategory = formState.primaryMuscle,
            onCategorySelected = { onEvent(CustomExerciseCreationEvent.UpdatePrimaryMuscle(it)) },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Equipment
        EquipmentDropdown(
            selectedEquipment = formState.equipment,
            onEquipmentSelected = { onEvent(CustomExerciseCreationEvent.UpdateEquipment(it)) },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Secondary Muscle Groups (Multi-select chips)
        Text(
            text = "Secondary Muscle Groups",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ExerciseCategory.values().size) { index ->
                val category = ExerciseCategory.values()[index]
                if (category != formState.primaryMuscle) {
                    FilterChip(
                        onClick = { 
                            if (formState.secondaryMuscles.contains(category)) {
                                onEvent(CustomExerciseCreationEvent.RemoveSecondaryMuscle(category))
                            } else {
                                onEvent(CustomExerciseCreationEvent.AddSecondaryMuscle(category))
                            }
                        },
                        label = { Text(category.name) },
                        selected = formState.secondaryMuscles.contains(category),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LiftrixColorsV2.Teal.copy(alpha = 0.2f),
                            selectedLabelColor = LiftrixColorsV2.Teal
                        )
                    )
                }
            }
        }
        
        // Difficulty Slider
        Column {
            Text(
                text = "Difficulty: ${formState.difficulty ?: "Not set"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Slider(
                value = formState.difficulty?.toFloat() ?: 0f,
                onValueChange = { 
                    val value = it.toInt()
                    onEvent(CustomExerciseCreationEvent.UpdateDifficulty(if (value > 0) value else null))
                },
                valueRange = 0f..10f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = LiftrixColorsV2.Teal,
                    activeTrackColor = LiftrixColorsV2.Teal
                )
            )
        }
    }
}

/**
 * Media Section
 */
@Composable
internal fun MediaSection(
    formState: CustomExerciseFormState,
    onEvent: (CustomExerciseCreationEvent) -> Unit,
    onAddImages: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Video URL
        OutlinedTextField(
            value = formState.videoUrl,
            onValueChange = { onEvent(CustomExerciseCreationEvent.UpdateVideoUrl(it)) },
            label = { Text("Video URL") },
            placeholder = { Text("YouTube, Vimeo, or direct video link...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FitnessCenter, // Will be Movie icon when imported
                    contentDescription = "Video link",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            isError = formState.videoUrlError != null,
            supportingText = formState.videoUrlError?.let { { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiftrixColorsV2.Teal,
                focusedLabelColor = LiftrixColorsV2.Teal
            )
        )
    }
}


/**
 * Exercise Category Dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCategoryDropdown(
    label: String,
    selectedCategory: ExerciseCategory?,
    onCategorySelected: (ExerciseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiftrixColorsV2.Teal,
                focusedLabelColor = LiftrixColorsV2.Teal
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ExerciseCategory.values().forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Equipment Dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentDropdown(
    selectedEquipment: Equipment?,
    onEquipmentSelected: (Equipment) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedEquipment?.name ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text("Equipment *") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiftrixColorsV2.Teal,
                focusedLabelColor = LiftrixColorsV2.Teal
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Equipment.values().forEach { equipment ->
                DropdownMenuItem(
                    text = { Text(equipment.name) },
                    onClick = {
                        onEquipmentSelected(equipment)
                        expanded = false
                    }
                )
            }
        }
    }
}
