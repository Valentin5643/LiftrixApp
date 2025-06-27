package com.example.liftrix.ui.workout.creation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationEvent

/**
 * Compact exercise card component with <80dp collapsed height, exercise thumbnail,
 * clickable name, and inline set management following Material 3 design guidelines
 */
@Composable
fun CompactExerciseCard(
    exercise: SelectedExercise,
    isExpanded: Boolean,
    onEvent: (WorkoutCreationEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Compact exercise header (always visible, <80dp height)
            CompactExerciseHeader(
                exercise = exercise,
                isExpanded = isExpanded,
                onToggleExpand = { /* TODO: Add toggle expansion event */ },
                onNameClick = { /* TODO: Add exercise name click event */ }
            )
            
            // Expandable set management section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    
                    SetManagementSection(
                        sets = exercise.sets,
                        exerciseType = ExerciseType.fromLibraryExercise(exercise.libraryExercise),
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

/**
 * Compact exercise header component with 40x40dp thumbnail, clickable exercise name,
 * and expand toggle button with proper touch targets (44x44dp minimum)
 */
@Composable
private fun CompactExerciseHeader(
    exercise: SelectedExercise,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp), // Ensures <80dp total card height with padding
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Exercise thumbnail 40x40dp with placeholder support
        CompactExerciseThumbnail(
            exercise = exercise.libraryExercise,
            size = 40.dp
        )
        
        // Exercise details with clickable name
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Clickable exercise name with touch feedback
            Text(
                text = exercise.libraryExercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onNameClick() }
                    .semantics {
                        contentDescription = "Exercise name: ${exercise.libraryExercise.name}, tap for details"
                    }
            )
            
            // Exercise metadata
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.libraryExercise.primaryMuscleGroup.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "${exercise.sets.size} ${if (exercise.sets.size == 1) "set" else "sets"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Action buttons with 44x44dp touch targets
        Row {
            // Remove exercise button
            IconButton(
                onClick = { /* TODO: Add remove exercise event */ },
                modifier = Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = "Remove ${exercise.libraryExercise.name} exercise"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Expand/collapse toggle button
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = if (isExpanded) "Collapse sets" else "Expand sets"
                    }
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Exercise thumbnail component with 40x40dp size and placeholder support
 */
@Composable
private fun CompactExerciseThumbnail(
    exercise: ExerciseLibrary,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "Exercise thumbnail for ${exercise.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.6f) // 60% of container size
            )
        }
    }
}

/**
 * Set management section with inline set input and conditional weight fields
 */
@Composable
private fun SetManagementSection(
    sets: List<SetInput>,
    exerciseType: ExerciseType,
    onEvent: (WorkoutCreationEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sets header
        Text(
            text = "Sets",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        
        // Set input rows with conditional weight fields
        sets.forEachIndexed { index, setInput ->
            SetInputRow(
                setInput = setInput.copy(
                    isWeightSupported = exerciseType in listOf(
                        ExerciseType.WEIGHT_BASED,
                        ExerciseType.STRENGTH,
                        ExerciseType.HYBRID
                    )
                ),
                setNumber = index + 1,
                onSetChange = { updatedSet ->
                    // Handle individual field updates
                    // TODO: Handle set updates
                    // if (updatedSet.reps != setInput.reps) {
                    //     onEvent(WorkoutCreationEvent.UpdateSetReps(0, index, updatedSet.reps))
                    // }
                    // if (updatedSet.rpe != setInput.rpe) {
                    //     onEvent(WorkoutCreationEvent.UpdateSetRpe(0, index, updatedSet.rpe))
                    // }
                    // if (updatedSet.weight != setInput.weight) {
                    //     onEvent(WorkoutCreationEvent.UpdateSetWeight(0, index, updatedSet.weight))
                    // }
                },
                onRemoveSet = {
                    // TODO: Add remove set event
                    // onEvent(WorkoutCreationEvent.RemoveSetFromExercise(0, index))
                },
                showRemoveButton = sets.size > 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Add Set Button
        OutlinedButton(
            onClick = {
                // TODO: Add set event
                // onEvent(WorkoutCreationEvent.AddSetToExercise(0))
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Add set"
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

@Preview(showBackground = true, name = "Compact Exercise Card - Collapsed")
@Composable
private fun CompactExerciseCardCollapsedPreview() {
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
            SetInput(reps = "15", rpe = "8", weight = "", isWeightSupported = false),
            SetInput(reps = "12", rpe = "9", weight = "", isWeightSupported = false)
        ),
        orderIndex = 0
    )
    
    LiftrixTheme {
        CompactExerciseCard(
            exercise = selectedExercise,
            isExpanded = false,
            onEvent = {}
        )
    }
}

@Preview(showBackground = true, name = "Compact Exercise Card - Expanded")
@Composable
private fun CompactExerciseCardExpandedPreview() {
    val sampleExercise = ExerciseLibrary(
        id = "2",
        name = "Barbell Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 4,
        instructions = "Classic barbell bench press",
        isCompound = true,
        searchableTerms = listOf("bench", "press", "chest", "barbell")
    )
    
    val selectedExercise = SelectedExercise(
        libraryExercise = sampleExercise,
        sets = listOf(
            SetInput(reps = "8", rpe = "7", weight = "80.0", isWeightSupported = true),
            SetInput(reps = "8", rpe = "8", weight = "80.0", isWeightSupported = true),
            SetInput(reps = "6", rpe = "9", weight = "85.0", isWeightSupported = true)
        ),
        orderIndex = 0
    )
    
    LiftrixTheme {
        CompactExerciseCard(
            exercise = selectedExercise,
            isExpanded = true,
            onEvent = {}
        )
    }
}

@Preview(showBackground = true, name = "Exercise Header Only")
@Composable
private fun ExerciseHeaderPreview() {
    val sampleExercise = ExerciseLibrary(
        id = "1",
        name = "Dumbbell Shoulder Press",
        primaryMuscleGroup = ExerciseCategory.SHOULDERS,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
        movementPattern = "Press",
        difficultyLevel = 3,
        instructions = "Seated dumbbell shoulder press",
        isCompound = true,
        searchableTerms = listOf("shoulder", "press", "dumbbell")
    )
    
    val selectedExercise = SelectedExercise(
        libraryExercise = sampleExercise,
        sets = listOf(SetInput(reps = "10", rpe = "8", weight = "25.0", isWeightSupported = true)),
        orderIndex = 0
    )
    
    LiftrixTheme {
                    CompactExerciseHeader(
            exercise = selectedExercise,
            isExpanded = false,
            onToggleExpand = {},
            onNameClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
} 