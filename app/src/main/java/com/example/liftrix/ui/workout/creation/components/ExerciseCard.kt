package com.example.liftrix.ui.workout.creation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput

/**
 * Enhanced card component that displays selected exercise with expandable set management
 * and weight memory integration following Material 3 design guidelines
 */
@Composable
fun ExerciseCard(
    selectedExercise: SelectedExercise,
    onSetUpdate: (Int, SetInput) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemoveExercise: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    lastUsedWeight: Float? = null
) {
    var expanded by remember { mutableStateOf(isExpanded) }
    
    LaunchedEffect(isExpanded) {
        expanded = isExpanded
    }
    
    LaunchedEffect(expanded) {
        onExpandedChange(expanded)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .semantics {
                contentDescription = "${selectedExercise.libraryExercise.name} exercise card with ${selectedExercise.sets.size} sets"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column {
            // Exercise Header
            ExerciseHeader(
                exercise = selectedExercise.libraryExercise,
                setsCount = selectedExercise.sets.size,
                isExpanded = expanded,
                onExpandClick = { expanded = !expanded },
                onRemoveExercise = onRemoveExercise,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Sets Section (expandable)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    
                    SetsSection(
                        selectedExercise = selectedExercise,
                        onSetUpdate = onSetUpdate,
                        onAddSet = onAddSet,
                        onRemoveSet = onRemoveSet,
                        enabled = enabled,
                        lastUsedWeight = lastUsedWeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseHeader(
    exercise: ExerciseLibrary,
    setsCount: Int,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onRemoveExercise: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Exercise thumbnail
        ExerciseThumbnail(
            equipment = exercise.equipment,
            modifier = Modifier.size(48.dp)
        )
        
        // Exercise details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.primaryMuscleGroup.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "$setsCount ${if (setsCount == 1) "set" else "sets"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Action buttons
        Row {
            // Remove exercise button
            IconButton(
                onClick = onRemoveExercise,
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = "Remove ${exercise.name} exercise"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            
            // Expand/collapse button
            IconButton(
                onClick = onExpandClick,
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = if (isExpanded) "Collapse sets" else "Expand sets"
                }
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
private fun SetsSection(
    selectedExercise: SelectedExercise,
    onSetUpdate: (Int, SetInput) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    enabled: Boolean,
    lastUsedWeight: Float?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sets header with weight memory indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            // Last used weight indicator
            lastUsedWeight?.let { weight ->
                if (selectedExercise.libraryExercise.equipment != Equipment.BODYWEIGHT_ONLY) {
                    Text(
                        text = "Last: ${String.format("%.1f", weight)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Set input rows
        selectedExercise.sets.forEachIndexed { index, setInput ->
            SetInputRow(
                setInput = if (index == 0 && lastUsedWeight != null && setInput.isWeightSupported && setInput.weight.isBlank()) {
                    // Pre-populate first set with last used weight
                    setInput.copy(weight = String.format("%.1f", lastUsedWeight))
                } else {
                    setInput
                },
                setNumber = index + 1,
                onSetChange = { updatedSet ->
                    onSetUpdate(index, updatedSet)
                },
                onRemoveSet = {
                    onRemoveSet(index)
                },
                enabled = enabled,
                showRemoveButton = selectedExercise.sets.size > 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Add Set Button
        OutlinedButton(
            onClick = {
                onAddSet()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Add set to ${selectedExercise.libraryExercise.name}"
                }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Set")
        }
    }
}

@Composable
private fun ExerciseThumbnail(
    equipment: Equipment,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(CircleShape),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseCardCollapsedPreview() {
    val sampleExercise = ExerciseLibrary(
        id = "1",
        name = "Push-ups",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BODYWEIGHT_ONLY,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 3,
        instructions = "Classic push-up exercise",
        isCompound = true,
        searchableTerms = listOf("push", "chest", "bodyweight")
    )
    
    val selectedExercise = SelectedExercise(
        libraryExercise = sampleExercise,
        sets = listOf(
            SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false)
        ),
        orderIndex = 0
    )
    
    LiftrixTheme {
        ExerciseCard(
            selectedExercise = selectedExercise,
            onSetUpdate = { _, _ -> },
            onAddSet = {},
            onRemoveSet = {},
            onRemoveExercise = {},
            isExpanded = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseCardExpandedPreview() {
    val sampleExercise = ExerciseLibrary(
        id = "2",
        name = "Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 5,
        instructions = "Barbell bench press",
        isCompound = true,
        searchableTerms = listOf("bench", "press", "chest", "barbell")
    )
    
    val selectedExercise = SelectedExercise(
        libraryExercise = sampleExercise,
        sets = listOf(
            SetInput(reps = "10", rpe = "7", weight = "", isWeightSupported = true),
            SetInput(reps = "8", rpe = "8", weight = "65", isWeightSupported = true),
            SetInput(reps = "6", rpe = "9", weight = "70", isWeightSupported = true)
        ),
        orderIndex = 0
    )
    
    LiftrixTheme {
        ExerciseCard(
            selectedExercise = selectedExercise,
            onSetUpdate = { _, _ -> },
            onAddSet = {},
            onRemoveSet = {},
            onRemoveExercise = {},
            isExpanded = true,
            lastUsedWeight = 60.0f,
            modifier = Modifier.padding(16.dp)
        )
    }
} 