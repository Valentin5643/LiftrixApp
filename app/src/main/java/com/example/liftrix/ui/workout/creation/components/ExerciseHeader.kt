package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput

/**
 * Compact exercise header component with thumbnail, clickable exercise name, 
 * and expand toggle button following Material 3 design guidelines
 */
@Composable
fun ExerciseHeader(
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
        ExerciseThumbnail(
            exercise = exercise.libraryExercise,
            size = 40.dp
        )
        
        // Exercise details with clickable name
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Clickable exercise name with touch feedback
            ClickableExerciseName(
                name = exercise.libraryExercise.name,
                onClick = onNameClick
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
        
        // Expand/collapse toggle button with 44x44dp touch target
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

/**
 * Exercise thumbnail component with 40x40dp size and placeholder support
 */
@Composable
fun ExerciseThumbnail(
    exercise: ExerciseLibrary,
    size: Dp = 40.dp
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
 * Clickable exercise name component with proper touch feedback
 */
@Composable
fun ClickableExerciseName(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clickable { onClick() }
            .semantics {
                contentDescription = "Exercise name: $name, tap for details"
            }
    )
}

@Preview(showBackground = true, name = "Exercise Header")
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
        sets = listOf(
            SetInput(reps = "10", rpe = "8", weight = "25.0", isWeightSupported = true),
            SetInput(reps = "8", rpe = "9", weight = "25.0", isWeightSupported = true)
        ),
        orderIndex = 0
    )
    
    LiftrixTheme {
        ExerciseHeader(
            exercise = selectedExercise,
            isExpanded = false,
            onToggleExpand = {},
            onNameClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "Exercise Header - Expanded")
@Composable
private fun ExerciseHeaderExpandedPreview() {
    val sampleExercise = ExerciseLibrary(
        id = "2",
        name = "Barbell Bench Press with Long Name",
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
            SetInput(reps = "8", rpe = "7", weight = "80.0", isWeightSupported = true)
        ),
        orderIndex = 0
    )
    
    LiftrixTheme {
        ExerciseHeader(
            exercise = selectedExercise,
            isExpanded = true,
            onToggleExpand = {},
            onNameClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
} 