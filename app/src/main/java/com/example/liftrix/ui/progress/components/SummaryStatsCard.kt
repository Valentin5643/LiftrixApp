package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.repository.ProgressSummary

/**
 * Summary statistics card component displaying key progress metrics.
 * 
 * Shows key workout statistics including total workouts, average duration, 
 * total volume, and workout frequency. Uses Material 3 design with proper
 * accessibility support and loading states.
 * 
 * @param summaryData Progress summary data to display
 * @param isLoading Whether the summary data is currently loading
 * @param modifier Modifier for styling the card container
 */
@Composable
fun SummaryStatsCard(
    summaryData: ProgressSummary,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Progress Summary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (isLoading) {
                LoadingState()
            } else {
                SummaryStatsContent(summaryData = summaryData)
            }
        }
    }
}

/**
 * Loading state for summary stats
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Loading summary statistics...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Main summary stats content
 */
@Composable
private fun SummaryStatsContent(summaryData: ProgressSummary) {
    val stats = listOf(
        StatItem(
            icon = Icons.Default.FitnessCenter,
            label = "Total Workouts",
            value = summaryData.totalWorkouts.toString(),
            contentDescription = "Total workouts completed: ${summaryData.totalWorkouts}"
        ),
        StatItem(
            icon = Icons.Default.AccessTime,
            label = "Avg Duration",
            value = "${summaryData.averageDuration}min",
            contentDescription = "Average workout duration: ${summaryData.averageDuration} minutes"
        ),
        StatItem(
            icon = Icons.Default.LocalFireDepartment,
            label = "Total Volume",
            value = "${(summaryData.totalVolume / 1000).toInt()}k kg",
            contentDescription = "Total volume lifted: ${(summaryData.totalVolume / 1000).toInt()} thousand kilograms"
        ),
        StatItem(
            icon = Icons.Default.TrendingUp,
            label = "Frequency",
            value = "${summaryData.averageWorkoutsPerWeek.toInt()}/week",
            contentDescription = "Workout frequency: ${summaryData.averageWorkoutsPerWeek.toInt()} workouts per week"
        )
    )
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(stats) { stat ->
            StatCard(stat = stat)
        }
    }
}

/**
 * Individual stat card
 */
@Composable
private fun StatCard(stat: StatItem) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .width(120.dp)
            .semantics {
                contentDescription = stat.contentDescription
            }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = stat.icon,
                contentDescription = stat.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = stat.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Data class representing a stat item
 */
private data class StatItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val contentDescription: String
) 
