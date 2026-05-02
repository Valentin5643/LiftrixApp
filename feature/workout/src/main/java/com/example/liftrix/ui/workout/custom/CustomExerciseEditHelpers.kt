package com.example.liftrix.ui.workout.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import com.example.liftrix.ui.workout.components.SecondaryActionButton

/**
 * Basic Information Section for Edit Screen
 */
@Composable
internal fun BasicInfoSection(
    formState: CustomExerciseEditFormState,
    onEvent: (CustomExerciseEditEvent) -> Unit,
    focusManager: FocusManager
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Exercise Name
        OutlinedTextField(
            value = formState.name,
            onValueChange = { onEvent(CustomExerciseEditEvent.UpdateName(it)) },
            label = { Text("Exercise Name") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.nameError != null,
            supportingText = formState.nameError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )
        
        // Exercise Description
        OutlinedTextField(
            value = formState.description,
            onValueChange = { onEvent(CustomExerciseEditEvent.UpdateDescription(it)) },
            label = { Text("Description (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.descriptionError != null,
            supportingText = formState.descriptionError?.let { { Text(it) } },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )
        
        // Exercise Type Selection
        ExerciseTypeSelectionEdit(
            selectedType = formState.exerciseType,
            onTypeSelected = { onEvent(CustomExerciseEditEvent.UpdateExerciseType(it)) }
        )
    }
}

/**
 * Exercise Details Section for Edit Screen
 */
@Composable
internal fun ExerciseDetailsSection(
    formState: CustomExerciseEditFormState,
    onEvent: (CustomExerciseEditEvent) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Primary Muscle Selection
        MuscleGroupSelectionEdit(
            title = "Primary Muscle Group *",
            selectedMuscle = formState.primaryMuscle,
            onMuscleSelected = { onEvent(CustomExerciseEditEvent.UpdatePrimaryMuscle(it)) }
        )
        
        // Secondary Muscles
        SecondaryMuscleSelectionEdit(
            selectedMuscles = formState.secondaryMuscles,
            primaryMuscle = formState.primaryMuscle,
            onAddMuscle = { onEvent(CustomExerciseEditEvent.AddSecondaryMuscle(it)) },
            onRemoveMuscle = { onEvent(CustomExerciseEditEvent.RemoveSecondaryMuscle(it)) }
        )
        
        // Equipment Selection
        EquipmentSelectionEdit(
            selectedEquipment = formState.equipment,
            onEquipmentSelected = { onEvent(CustomExerciseEditEvent.UpdateEquipment(it)) }
        )
        
        // Difficulty Selection
        DifficultySelectionEdit(
            selectedDifficulty = formState.difficulty,
            onDifficultySelected = { onEvent(CustomExerciseEditEvent.UpdateDifficulty(it)) }
        )
    }
}

/**
 * Media Section for Edit Screen
 */
@Composable
internal fun MediaSection(
    formState: CustomExerciseEditFormState,
    onEvent: (CustomExerciseEditEvent) -> Unit,
    onAddImages: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Image Gallery
        if (formState.mainImageUri != null || formState.additionalImageUris.isNotEmpty()) {
            ExerciseImageGalleryEdit(
                mainImageUri = formState.mainImageUri,
                additionalImageUris = formState.additionalImageUris,
                onSetMainImage = { onEvent(CustomExerciseEditEvent.SetMainImage(it)) },
                onRemoveImage = { onEvent(CustomExerciseEditEvent.RemoveImage(it)) }
            )
        }
        
        // Add Images Button
        SecondaryActionButton(
            text = if (formState.mainImageUri == null && formState.additionalImageUris.isEmpty()) 
                "Add Images" else "Add More Images",
            onClick = onAddImages,
            leadingIcon = Icons.Default.Add
        )
        
        // Video URL
        OutlinedTextField(
            value = formState.videoUrl,
            onValueChange = { onEvent(CustomExerciseEditEvent.UpdateVideoUrl(it)) },
            label = { Text("Video URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.videoUrlError != null,
            supportingText = formState.videoUrlError?.let { { Text(it) } },
            placeholder = { Text("YouTube, Vimeo, or direct video link") }
        )
    }
}


// Helper components - these would need to be adapted from the creation versions
// For now, I'll create stubs that delegate to the creation versions

@Composable
private fun ExerciseTypeSelectionEdit(
    selectedType: ExerciseType,
    onTypeSelected: (ExerciseType) -> Unit
) {
    // For now, delegate to creation version - would need proper Edit version
    com.example.liftrix.ui.workout.custom.components.ExerciseTypeSelector(
        selectedType = selectedType,
        onTypeSelected = onTypeSelected
    )
}

@Composable
private fun MuscleGroupSelectionEdit(
    title: String,
    selectedMuscle: ExerciseCategory?,
    onMuscleSelected: (ExerciseCategory) -> Unit
) {
    // Similar dropdown as creation version but adapted for edit
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedTextField(
            value = selectedMuscle?.name ?: "",
            onValueChange = { },
            label = { Text(title) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, "Select muscle group")
                }
            }
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ExerciseCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onMuscleSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SecondaryMuscleSelectionEdit(
    selectedMuscles: Set<ExerciseCategory>,
    primaryMuscle: ExerciseCategory?,
    onAddMuscle: (ExerciseCategory) -> Unit,
    onRemoveMuscle: (ExerciseCategory) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Secondary Muscle Groups",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        
        // Selected muscles
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedMuscles.size) { index ->
                val muscle = selectedMuscles.elementAt(index)
                FilterChip(
                    onClick = { onRemoveMuscle(muscle) },
                    label = { Text(muscle.name) },
                    selected = true,
                    trailingIcon = {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        
        // Add muscle dropdown
        var expanded by remember { mutableStateOf(false) }
        
        Box {
            SecondaryActionButton(
                text = "Add Muscle Group",
                onClick = { expanded = true },
                leadingIcon = Icons.Default.Add
            )
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ExerciseCategory.entries.forEach { category ->
                    if (category != primaryMuscle && !selectedMuscles.contains(category)) {
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onAddMuscle(category)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentSelectionEdit(
    selectedEquipment: Equipment?,
    onEquipmentSelected: (Equipment) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedTextField(
            value = selectedEquipment?.name ?: "",
            onValueChange = { },
            label = { Text("Equipment *") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, "Select equipment")
                }
            }
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Equipment.entries.forEach { equipment ->
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

@Composable
private fun DifficultySelectionEdit(
    selectedDifficulty: Int?,
    onDifficultySelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val difficultyText = when (selectedDifficulty) {
        1 -> "Beginner"
        2 -> "Intermediate" 
        3 -> "Advanced"
        4 -> "Expert"
        5 -> "Professional"
        else -> "Not Set"
    }
    
    Box {
        OutlinedTextField(
            value = difficultyText,
            onValueChange = { },
            label = { Text("Difficulty Level") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, "Select difficulty")
                }
            }
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(
                null to "Not Set",
                1 to "Beginner",
                2 to "Intermediate", 
                3 to "Advanced",
                4 to "Expert",
                5 to "Professional"
            ).forEach { (level, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onDifficultySelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExerciseImageGalleryEdit(
    mainImageUri: android.net.Uri?,
    additionalImageUris: List<android.net.Uri>,
    onSetMainImage: (android.net.Uri) -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit
) {
    // Convert separate main and additional images to a combined list
    val allImageUris = listOfNotNull(mainImageUri) + additionalImageUris
    val mainImageIndex = if (mainImageUri != null) 0 else -1
    
    // Use the proper ExerciseImageGallery component with correct parameters
    com.example.liftrix.ui.workout.custom.components.ExerciseImageGallery(
        imageUris = allImageUris,
        mainImageIndex = mainImageIndex,
        onAddImage = { /* No add functionality needed in edit mode */ },
        onRemoveImage = { index ->
            // Convert index back to URI for the callback
            if (index < allImageUris.size) {
                onRemoveImage(allImageUris[index])
            }
        },
        onSetMainImage = { index ->
            // Convert index back to URI for the callback
            if (index < allImageUris.size) {
                onSetMainImage(allImageUris[index])
            }
        }
    )
}