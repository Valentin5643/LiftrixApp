package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.ui.common.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Unified Exercise Card Component
 * 
 * Reusable card component for displaying exercise information with sets, reps, and weights.
 * Works across creation, active workout, and editing modes with consistent visual design.
 * 
 * Built on top of UnifiedWorkoutCard to maintain design consistency while providing
 * exercise-specific functionality including set tracking, completion status, and input handling.
 * 
 * Features:
 * - Exercise details display (name, category, target metrics)
 * - Set-by-set breakdown with completion indicators
 * - Support for different exercise types (weight-based, time-based, distance-based)
 * - Consistent styling with other workout components
 * - Interactive mode support for active workouts
 * - Accessibility compliance with proper semantics
 * 
 * @param exercise The exercise domain model to display
 * @param sets List of exercise sets to show (can be targets or actual performance data)
 * @param isActive Whether this is displayed during an active workout session
 * @param onSetUpdate Callback for when a set is updated (for active mode)
 * @param onExerciseClick Callback for when the exercise card is clicked
 * @param modifier Modifier for customizing the card's layout and behavior
 */
@Composable
fun UnifiedExerciseCard(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    isActive: Boolean = false,
    onSetUpdate: (ExerciseSet) -> Unit = {},
    onExerciseClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = exercise.libraryExercise.name,
        subtitle = buildExerciseSubtitle(exercise, sets),
        modifier = modifier,
        onClick = onExerciseClick,
        actions = {
            if (isActive) {
                ExerciseStatusIndicator(
                    completedSets = sets.count { it.isCompleted },
                    totalSets = sets.size,
                    isCompleted = exercise.isCompleted()
                )
            }
        }
    ) {
        ExerciseDetailsSection(exercise = exercise)
        
        if (sets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
            ExerciseSetsSection(
                sets = sets,
                exercise = exercise,
                isActive = isActive,
                onSetUpdate = onSetUpdate
            )
        }
        
        // Volume and progress summary for completed exercises
        if (exercise.isWeightBased && sets.any { it.isCompleted }) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
            ExerciseSummarySection(exercise = exercise, sets = sets)
        }
    }
}

/**
 * Displays basic exercise information and capabilities
 */
@Composable
private fun ExerciseDetailsSection(
    exercise: Exercise,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Exercise category and type information
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            // Category chip
            AssistChip(
                onClick = { /* Category chip is informational only */ },
                label = {
                    Text(
                        text = exercise.libraryExercise.primaryMuscleGroup.displayName,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.accessibilitySemantics(
                    description = "Exercise category: ${exercise.libraryExercise.primaryMuscleGroup.displayName}"
                )
            )
            
            // Exercise type indicators
            if (exercise.isWeightBased) {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = {
                        Text(
                            text = "Weight",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.accessibilitySemantics(
                        description = "Weight-based exercise"
                    )
                )
            }
            
            if (exercise.isTimeBased) {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = {
                        Text(
                            text = "Timed",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.accessibilitySemantics(
                        description = "Time-based exercise"
                    )
                )
            }
        }
        
        // Target information if available
        exercise.targetSets?.let { targetSets ->
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
            Text(
                text = buildTargetText(exercise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.accessibilitySemantics(
                    description = "Target: ${buildTargetText(exercise)}"
                )
            )
        }
        
        // Notes if available
        exercise.notes?.let { notes ->
            if (notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.accessibilitySemantics(
                        description = "Exercise notes: $notes"
                    )
                )
            }
        }
    }
}

/**
 * Displays the list of exercise sets with completion status
 */
@Composable
private fun ExerciseSetsSection(
    sets: List<ExerciseSet>,
    exercise: Exercise,
    isActive: Boolean,
    onSetUpdate: (ExerciseSet) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Sets",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
        
        // Sets list - using LazyColumn for performance with many sets
        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp), // Limit height to prevent excessive scrolling
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
        ) {
            itemsIndexed(sets) { index, set ->
                ExerciseSetRow(
                    setNumber = set.setNumber,
                    set = set,
                    exercise = exercise,
                    isActive = isActive,
                    onSetUpdate = onSetUpdate
                )
            }
        }
    }
}

/**
 * Individual set row showing reps, weight, and completion status
 */
@Composable
private fun ExerciseSetRow(
    setNumber: Int,
    set: ExerciseSet,
    exercise: Exercise,
    isActive: Boolean,
    onSetUpdate: (ExerciseSet) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (set.isCompleted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.elementPaddingLarge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set number and completion indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                Icon(
                    imageVector = if (set.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (set.isCompleted) "Completed set" else "Incomplete set",
                    tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Set metrics (reps, weight, time, distance)
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reps display
                set.reps?.let { reps ->
                    Text(
                        text = "${reps.count} reps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Weight display
                set.weight?.let { weight ->
                    Text(
                        text = "${weight.kilograms}kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Time display
                set.time?.let { time ->
                    Text(
                        text = formatDuration(time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Distance display
                set.distance?.let { distance ->
                    Text(
                        text = "${distance.meters}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // RPE display if available
                set.rpe?.let { rpe ->
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = "RPE ${rpe.value}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Shows completion status and progress indicator
 */
@Composable
private fun ExerciseStatusIndicator(
    completedSets: Int,
    totalSets: Int,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$completedSets/$totalSets",
            style = MaterialTheme.typography.labelMedium,
            color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Exercise completed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Summary section showing total volume and other aggregate metrics
 */
@Composable
private fun ExerciseSummarySection(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.elementPaddingLarge)
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Total volume
                exercise.getTotalVolume()?.let { volume ->
                    Column {
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${volume.kilograms.toInt()}kg",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Total reps
                val totalReps = exercise.getTotalRepsCompleted()
                if (totalReps.count > 0) {
                    Column {
                        Text(
                            text = "Total Reps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${totalReps.count}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Max weight
                exercise.getMaxWeight()?.let { maxWeight ->
                    Column {
                        Text(
                            text = "Max Weight",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${maxWeight.kilograms}kg",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to build exercise subtitle based on sets and completion
 */
private fun buildExerciseSubtitle(exercise: Exercise, sets: List<ExerciseSet>): String {
    val completedSets = sets.count { it.isCompleted }
    val totalSets = sets.size
    
    return when {
        totalSets == 0 -> "No sets configured"
        completedSets == 0 -> "$totalSets sets planned"
        completedSets == totalSets -> "$totalSets sets completed"
        else -> "$completedSets/$totalSets sets completed"
    }
}

/**
 * Helper function to build target text from exercise targets
 */
private fun buildTargetText(exercise: Exercise): String {
    val parts = mutableListOf<String>()
    
    exercise.targetSets?.let { sets ->
        parts.add("$sets sets")
    }
    
    exercise.targetReps?.let { reps ->
        parts.add("$reps reps")
    }
    
    exercise.targetWeight?.let { weight ->
        parts.add("${weight.kilograms}kg")
    }
    
    exercise.targetTime?.let { time ->
        parts.add(formatDuration(time))
    }
    
    exercise.targetDistance?.let { distance ->
        parts.add("${distance.meters}m")
    }
    
    return "Target: ${parts.joinToString(", ")}"
}

/**
 * Helper function to format duration for display
 */
private fun formatDuration(duration: java.time.Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    
    return when {
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}