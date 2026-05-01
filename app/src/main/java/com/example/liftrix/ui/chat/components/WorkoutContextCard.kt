package com.example.liftrix.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.chat.Language

/**
 * Simplified workout context card component for displaying workout information in chat.
 */
@Composable
fun WorkoutContextCard(
    context: WorkoutContext,
    currentLanguage: Language,
    onExpandDetails: () -> Unit = {},
    onQuickAction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentLanguage == Language.ROMANIAN) "Context Antrenament" else "Workout Context",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
                
                IconButton(onClick = onExpandDetails) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Expand details",
                        tint = LiftrixColorsV2.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            // Exercise info if available
            val exerciseName = context.exerciseName
            if (exerciseName != null) {
                ExerciseInfo(
                    exerciseName = exerciseName,
                    exerciseCategory = context.exerciseCategory,
                    currentLanguage = currentLanguage
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            }
            
            // Quick stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
            ) {
                if (context.previousWeight != null) {
                    StatItem(
                        label = if (currentLanguage == Language.ROMANIAN) "Greutate" else "Weight",
                        value = "${context.previousWeight}kg"
                    )
                }
                
                if (context.previousReps != null) {
                    StatItem(
                        label = if (currentLanguage == Language.ROMANIAN) "Repetări" else "Reps",
                        value = context.previousReps.toString()
                    )
                }
                
                if (context.workoutDuration != null) {
                    StatItem(
                        label = if (currentLanguage == Language.ROMANIAN) "Durată" else "Duration",
                        value = "${context.workoutDuration}min"
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseInfo(
    exerciseName: String,
    exerciseCategory: String?,
    currentLanguage: Language
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = LiftrixColorsV2.onSurface
        )
        
        exerciseCategory?.let { category ->
            Text(
                text = category,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColorsV2.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LiftrixColorsV2.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = LiftrixColorsV2.onSurface
        )
    }
}
