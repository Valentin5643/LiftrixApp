package com.example.liftrix.ui.progress.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.progress.detail.MuscleGroupDetailViewModel

/**
 * Muscle Group Exercises List
 * 
 * Displays a list of exercises for a selected muscle group with performance metrics,
 * trends, and detailed statistics. Provides drill-down functionality for individual
 * exercise analysis.
 */
@Composable
fun MuscleGroupExercisesList(
    exercises: List<MuscleGroupDetailViewModel.MuscleGroupExercise>,
    modifier: Modifier = Modifier,
    onExerciseClick: ((String) -> Unit)? = null
) {
    Column(
        modifier = modifier
    ) {
        // Header
        Text(
            text = "Exercises (${exercises.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Exercises list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(exercises) { exercise ->
                ExerciseListItem(
                    exercise = exercise,
                    onClick = onExerciseClick?.let { { it(exercise.id) } }
                )
            }
        }
    }
}

/**
 * Individual exercise list item
 */
@Composable
private fun ExerciseListItem(
    exercise: MuscleGroupDetailViewModel.MuscleGroupExercise,
    onClick: (() -> Unit)? = null
) {
    LiftrixCard(
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Exercise name and trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                TrendIndicator(trend = exercise.trend)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem(
                    label = "Volume",
                    value = "${exercise.volume.toInt()}",
                    color = MaterialTheme.colorScheme.primary
                )
                
                MetricItem(
                    label = "Share",
                    value = "${exercise.percentage.toInt()}%",
                    color = MaterialTheme.colorScheme.secondary
                )
                
                MetricItem(
                    label = "Sessions",
                    value = "${exercise.sessionCount}",
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                exercise.latestOneRm?.let { oneRm ->
                    MetricItem(
                        label = "1RM",
                        value = "${oneRm.toInt()}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Trend indicator component
 */
@Composable
private fun TrendIndicator(
    trend: MuscleGroupDetailViewModel.Trend
) {
    val (icon, color, text) = when (trend) {
        MuscleGroupDetailViewModel.Trend.IMPROVING -> Triple(
            Icons.Default.TrendingUp,
            MaterialTheme.colorScheme.primary,
            "Improving"
        )
        MuscleGroupDetailViewModel.Trend.STABLE -> Triple(
            Icons.Default.TrendingFlat,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Stable"
        )
        MuscleGroupDetailViewModel.Trend.DECLINING -> Triple(
            Icons.Default.TrendingDown,
            MaterialTheme.colorScheme.error,
            "Declining"
        )
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Individual metric item
 */
@Composable
private fun MetricItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}