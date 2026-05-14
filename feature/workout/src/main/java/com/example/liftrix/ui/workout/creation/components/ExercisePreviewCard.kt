package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.feature.workout.R
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.CardSpacing
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Visual exercise preview component for professional training interface
 * Shows exercise details with athletic styling and professional layout
 */
@Composable
fun ExercisePreviewCard(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDetails: Boolean = true,
    isSelected: Boolean = false
) {
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Exercise: ${exercise.libraryExercise.name}, ${exercise.libraryExercise.primaryMuscleGroup}, ${exercise.libraryExercise.primaryMuscleGroup}"
            },
        onClick = onClick,
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CardSpacing.M)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
        ) {
            // Header with exercise name and type icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.libraryExercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = exercise.libraryExercise.primaryMuscleGroup.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Icon(
                    imageVector = getExerciseIcon(exercise.libraryExercise.primaryMuscleGroup),
                    contentDescription = exercise.libraryExercise.primaryMuscleGroup.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            if (showDetails) {
                Spacer(modifier = Modifier.height(CardSpacing.XS))
                
                // Exercise details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Exercise type
                    ExerciseDetailChip(
                        icon = getExerciseIcon(exercise.libraryExercise.primaryMuscleGroup),
                        text = exercise.libraryExercise.primaryMuscleGroup.displayName,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(CardSpacing.XS))
                    
                    // Equipment needed
                    exercise.libraryExercise.equipment?.let { equipment ->
                        ExerciseDetailChip(
                            icon = Icons.Default.FitnessCenter,
                            text = equipment.displayName,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Secondary muscle groups
                if (exercise.libraryExercise.secondaryMuscleGroups.isNotEmpty()) {
                    Text(
                        text = "Also targets: ${exercise.libraryExercise.secondaryMuscleGroups.joinToString(", ") { it.displayName }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Instructions preview
                exercise.libraryExercise.instructions?.let { instructions ->
                    if (instructions.isNotBlank()) {
                        Text(
                            text = instructions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact exercise preview for smaller displays
 */
@Composable
fun CompactExercisePreviewCard(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false
) {
    LiftrixCard(
        modifier = modifier
            .semantics {
                contentDescription = "Exercise: ${exercise.libraryExercise.name}, ${exercise.libraryExercise.primaryMuscleGroup.name}"
            },
        onClick = onClick,
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CardSpacing.S)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.libraryExercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = exercise.libraryExercise.primaryMuscleGroup.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = getExerciseIcon(exercise.libraryExercise.primaryMuscleGroup),
                contentDescription = exercise.libraryExercise.primaryMuscleGroup.displayName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Exercise detail chip component
 */
@Composable
private fun ExerciseDetailChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Get appropriate icon for exercise based on muscle group
 */
private fun getExerciseIcon(category: ExerciseCategory): ImageVector {
    return when (category) {
        ExerciseCategory.CARDIO -> Icons.Default.Timer
        else -> Icons.Default.FitnessCenter
    }
}

@Composable
private fun ExerciseMuscleIcon(
    category: ExerciseCategory,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    val iconResId = muscleGroupIconResId(category)
    if (iconResId != null) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = muscleGroupIconContentDescription(category),
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = "Exercise category",
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun muscleGroupIconResId(muscleGroup: ExerciseCategory): Int? {
    return when (muscleGroup) {
        ExerciseCategory.SHOULDERS -> R.drawable.muscle_shoulders
        ExerciseCategory.CHEST -> R.drawable.muscle_chest
        ExerciseCategory.BACK -> R.drawable.muscle_back
        ExerciseCategory.BICEPS -> R.drawable.muscle_biceps
        ExerciseCategory.TRICEPS -> R.drawable.muscle_triceps
        ExerciseCategory.LEGS,
        ExerciseCategory.QUADRICEPS -> R.drawable.muscle_legs
        ExerciseCategory.GLUTES -> R.drawable.muscle_glutes
        ExerciseCategory.ABS,
        ExerciseCategory.CORE -> R.drawable.muscle_core
        ExerciseCategory.CALVES -> R.drawable.muscle_calves
        else -> null
    }
}

private fun muscleGroupIconContentDescription(muscleGroup: ExerciseCategory): String {
    return when (muscleGroup) {
        ExerciseCategory.SHOULDERS -> "Shoulders muscle icon"
        ExerciseCategory.CHEST -> "Chest muscle icon"
        ExerciseCategory.BACK -> "Back muscle icon"
        ExerciseCategory.BICEPS -> "Biceps muscle icon"
        ExerciseCategory.TRICEPS -> "Triceps muscle icon"
        ExerciseCategory.LEGS,
        ExerciseCategory.QUADRICEPS -> "Legs muscle icon"
        ExerciseCategory.GLUTES -> "Glutes muscle icon"
        ExerciseCategory.ABS,
        ExerciseCategory.CORE -> "Core muscle icon"
        ExerciseCategory.CALVES -> "Calves muscle icon"
        else -> "Exercise category"
    }
}

/**
 * Overloaded ExercisePreviewCard for SearchableExercise
 */
@Composable
fun ExercisePreviewCard(
    exercise: SearchableExercise,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDetails: Boolean = true,
    isSelected: Boolean = false
) {
    // 🔥 CRITICAL DEBUG: Log when ExercisePreviewCard is being rendered
    timber.log.Timber.d("🔥 CARD-DEBUG: Rendering ExercisePreviewCard for: ${exercise.name}")
    timber.log.Timber.d("🔥 CARD-DEBUG: onClick callback is null? ${onClick == null}")
    
    when (exercise) {
        is SearchableExercise.LibraryExercise -> {
            LiftrixCard(
                modifier = modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Exercise: ${exercise.name}, ${exercise.exercise.equipment}"
                    },
                onClick = if (onClick != null) {
                    {
                        timber.log.Timber.d("🔥 CLICK-DEBUG: ExercisePreviewCard clicked for LibraryExercise: ${exercise.name}")
                        timber.log.Timber.d("🔥 CLICK-DEBUG: Exercise type: ${exercise::class.simpleName}")
                        try {
                            onClick.invoke()
                            timber.log.Timber.d("🔥 CLICK-DEBUG: LibraryExercise onClick.invoke() completed successfully")
                        } catch (e: Exception) {
                            timber.log.Timber.e(e, "🔥 CLICK-DEBUG: Error in LibraryExercise onClick.invoke()")
                        }
                    }
                } else {
                    timber.log.Timber.w("🔥 CLICK-DEBUG: onClick is null for LibraryExercise ${exercise.name}")
                    null
                },
                colors = if (isSelected) {
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(CardSpacing.M)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CardSpacing.S)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CardSpacing.S),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = exercise.exercise.equipment.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        ExerciseMuscleIcon(
                            category = exercise.exercise.primaryMuscleGroup,
                            fallbackIcon = Icons.Default.FitnessCenter,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        is SearchableExercise.CustomExercise -> {
            LiftrixCard(
                modifier = modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Custom Exercise: ${exercise.name}"
                    },
                onClick = if (onClick != null) {
                    {
                        timber.log.Timber.d("🔥 CLICK-DEBUG: ExercisePreviewCard clicked for CustomExercise: ${exercise.name}")
                        try {
                            onClick.invoke()
                            timber.log.Timber.d("🔥 CLICK-DEBUG: CustomExercise onClick.invoke() completed successfully")
                        } catch (e: Exception) {
                            timber.log.Timber.e(e, "🔥 CLICK-DEBUG: Error in CustomExercise onClick.invoke()")
                        }
                    }
                } else {
                    timber.log.Timber.w("🔥 CLICK-DEBUG: onClick is null for CustomExercise ${exercise.name}")
                    null
                },
                colors = if (isSelected) {
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(CardSpacing.M)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CardSpacing.S)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CardSpacing.S),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = "Custom Exercise",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        ExerciseMuscleIcon(
                            category = exercise.exercise.primaryMuscle,
                            fallbackIcon = Icons.Default.Person,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisePreviewCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Full preview
            ExercisePreviewCard(
                exercise = Exercise(
                    id = com.example.liftrix.domain.model.ExerciseId("1"),
                    workoutId = com.example.liftrix.domain.model.WorkoutId("workout-1"),
                    libraryExercise = ExerciseLibrary(
                        id = "lib-1",
                        name = "Barbell Bench Press",
                        primaryMuscleGroup = ExerciseCategory.CHEST,
                        equipment = com.example.liftrix.domain.model.Equipment.BARBELL,
                        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
                        movementPattern = "Push",
                        difficultyLevel = 5,
                        instructions = "Lie flat on a bench with your feet planted firmly on the ground.",
                        isCompound = true,
                        searchableTerms = listOf("bench", "press", "chest")
                    ),
                    orderIndex = 0,
                    createdAt = java.time.Instant.now()
                ),
                showDetails = true
            )
            
            // Compact preview
            CompactExercisePreviewCard(
                exercise = Exercise(
                    id = com.example.liftrix.domain.model.ExerciseId("2"),
                    workoutId = com.example.liftrix.domain.model.WorkoutId("workout-1"),
                    libraryExercise = ExerciseLibrary(
                        id = "lib-2",
                        name = "Dumbbell Shoulder Press",
                        primaryMuscleGroup = ExerciseCategory.SHOULDERS,
                        equipment = com.example.liftrix.domain.model.Equipment.DUMBBELLS,
                        secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
                        movementPattern = "Press",
                        difficultyLevel = 4,
                        instructions = "Press dumbbells overhead.",
                        isCompound = false,
                        searchableTerms = listOf("shoulder", "press", "dumbbell")
                    ),
                    orderIndex = 1,
                    createdAt = java.time.Instant.now()
                ),
                isSelected = true
            )
        }
    }
} 
