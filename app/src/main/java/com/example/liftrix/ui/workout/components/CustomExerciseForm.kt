package com.example.liftrix.ui.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.CustomExerciseCreationEvent
import com.example.liftrix.ui.workout.creation.CustomExerciseCreationUiState
import kotlin.math.roundToInt

/**
 * Custom exercise creation form with Material 3 design and validation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseForm(
    uiState: CustomExerciseCreationUiState,
    onEvent: (CustomExerciseCreationEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    // Show success/error messages
    LaunchedEffect(uiState.showSuccessMessage, uiState.errorMessage) {
        when {
            uiState.showSuccessMessage -> {
                snackbarHostState.showSnackbar("Exercise created successfully!")
                onEvent(CustomExerciseCreationEvent.ClearMessages)
            }
            uiState.errorMessage != null -> {
                snackbarHostState.showSnackbar(uiState.errorMessage)
                onEvent(CustomExerciseCreationEvent.ClearMessages)
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Exercise Name Field
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { onEvent(CustomExerciseCreationEvent.UpdateName(it)) },
            label = { Text("Exercise Name") },
            placeholder = { Text("Enter exercise name") },
            supportingText = {
                Text(
                    text = "${uiState.name.length}/100 characters",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingIcon = {
                when {
                    uiState.nameError == null && uiState.name.isNotBlank() -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Valid name",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    uiState.nameError != null -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Invalid name",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            ),
            isError = uiState.nameError != null,
            enabled = !uiState.isLoading,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Exercise name input field" }
        )
        
        // Name error message
        AnimatedVisibility(
            visible = uiState.nameError != null,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            uiState.nameError?.let { error ->
                ValidationErrorText(message = error)
            }
        }
        
        // Primary Muscle Group Section
        Text(
            text = "Primary Muscle Group",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { contentDescription = "Primary muscle group selection" }
        )
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ExerciseCategory.values().forEach { category ->
                FilterChip(
                    onClick = { 
                        onEvent(CustomExerciseCreationEvent.UpdatePrimaryMuscle(category))
                    },
                    label = { Text(category.displayName) },
                    selected = uiState.primaryMuscle == category,
                    enabled = !uiState.isLoading,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.semantics { 
                        contentDescription = "${category.displayName} muscle group chip"
                    }
                )
            }
        }
        
        // Primary muscle error message
        AnimatedVisibility(
            visible = uiState.primaryMuscleError != null,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            uiState.primaryMuscleError?.let { error ->
                ValidationErrorText(message = error)
            }
        }
        
        // Equipment Section
        Text(
            text = "Equipment",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { contentDescription = "Equipment selection" }
        )
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Equipment.values().forEach { equipment ->
                FilterChip(
                    onClick = { 
                        onEvent(CustomExerciseCreationEvent.UpdateEquipment(equipment))
                    },
                    label = { Text(equipment.displayName) },
                    selected = uiState.equipment == equipment,
                    enabled = !uiState.isLoading,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.semantics { 
                        contentDescription = "${equipment.displayName} equipment chip"
                    }
                )
            }
        }
        
        // Secondary Muscle Groups (Optional)
        if (uiState.primaryMuscle != ExerciseCategory.CARDIO) {
            Text(
                text = "Secondary Muscle Groups (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { contentDescription = "Secondary muscle groups selection" }
            )
            
            Text(
                text = "Select up to 3 additional muscle groups",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExerciseCategory.values()
                    .filter { it != uiState.primaryMuscle && it != ExerciseCategory.CARDIO }
                    .forEach { category ->
                        FilterChip(
                            onClick = { 
                                val updatedMuscles = if (uiState.secondaryMuscles.contains(category)) {
                                    uiState.secondaryMuscles - category
                                } else {
                                    uiState.secondaryMuscles + category
                                }
                                onEvent(CustomExerciseCreationEvent.UpdateSecondaryMuscles(updatedMuscles))
                            },
                            label = { Text(category.displayName) },
                            selected = uiState.secondaryMuscles.contains(category),
                            enabled = !uiState.isLoading && (uiState.secondaryMuscles.size < 3 || uiState.secondaryMuscles.contains(category)),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier.semantics { 
                                contentDescription = "${category.displayName} secondary muscle group chip"
                            }
                        )
                    }
            }
        }
        
        // Difficulty Level (Optional)
        Text(
            text = "Difficulty Level (Optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { contentDescription = "Difficulty level selection" }
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Difficulty:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (uiState.difficulty) {
                            null -> "Not set"
                            in 1..3 -> "${uiState.difficulty} (Beginner)"
                            in 4..6 -> "${uiState.difficulty} (Intermediate)"
                            in 7..8 -> "${uiState.difficulty} (Advanced)"
                            in 9..10 -> "${uiState.difficulty} (Expert)"
                            else -> "${uiState.difficulty}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = uiState.difficulty?.toFloat() ?: 5f,
                        onValueChange = { value ->
                            onEvent(CustomExerciseCreationEvent.UpdateDifficulty(value.roundToInt()))
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .semantics { contentDescription = "Difficulty level slider from 1 to 10" }
                    )
                    Text("10", style = MaterialTheme.typography.bodySmall)
                }
                
                // Show star rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    repeat(10) { index ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (index < (uiState.difficulty ?: 0)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // Notes Field (Optional)
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = { onEvent(CustomExerciseCreationEvent.UpdateNotes(it)) },
            label = { Text("Notes (Optional)") },
            placeholder = { Text("Add instructions, tips, or movement variations...") },
            supportingText = {
                Text(
                    text = "${uiState.notes.length}/500 characters",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            isError = uiState.notesError != null,
            enabled = !uiState.isLoading,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Exercise notes input field" }
        )
        
        // Notes error message
        AnimatedVisibility(
            visible = uiState.notesError != null,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            uiState.notesError?.let { error ->
                ValidationErrorText(message = error)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Snackbar host for messages
    SnackbarHost(hostState = snackbarHostState)
}

/**
 * Validation error text component with consistent styling
 */
@Composable
private fun ValidationErrorText(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Validation error: $message" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

// Preview
@Preview(showBackground = true)
@Composable
private fun CustomExerciseFormPreview() {
    LiftrixTheme {
        CustomExerciseForm(
            uiState = CustomExerciseCreationUiState(
                name = "Custom Push-up",
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.BODYWEIGHT_ONLY,
                secondaryMuscles = setOf(ExerciseCategory.ARMS, ExerciseCategory.SHOULDERS),
                difficulty = 3,
                notes = "Focus on form and controlled movement"
            ),
            onEvent = {}
        )
    }
} 