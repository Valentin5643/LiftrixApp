package com.example.liftrix.ui.progress.components

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.repository.ProgressSummary

/**
 * Progress summary cards component displaying key workout statistics in a grid layout.
 * 
 * Shows essential workout metrics including total workouts, streak information, 
 * and average duration. Uses Material 3 design with LazyVerticalGrid layout
 * for optimal display of summary statistics with proper accessibility support.
 * 
 * @param summaryData Progress summary data to display
 * @param isLoading Whether the summary data is currently loading
 * @param modifier Modifier for styling the component container
 */
@Composable
fun ProgressSummaryCards(
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
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isLoading) {
                LoadingState()
            } else {
                ProgressSummaryContent(summaryData = summaryData)
            }
        }
    }
}

/**
 * Loading state for progress summary cards
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
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
                text = "Loading progress summary...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Main progress summary content with grid layout
 */
@Composable
private fun ProgressSummaryContent(summaryData: ProgressSummary) {
    val summaryStats = listOf(
        SummaryStatItem(
            icon = Icons.Default.FitnessCenter,
            label = "Total Workouts",
            value = summaryData.totalWorkouts.toString(),
            contentDescription = "Total workouts completed: ${summaryData.totalWorkouts}"
        ),
        SummaryStatItem(
            icon = Icons.Default.LocalFireDepartment,
            label = "Current Streak",
            value = "${summaryData.currentStreak} days",
            contentDescription = "Current workout streak: ${summaryData.currentStreak} days"
        ),
        SummaryStatItem(
            icon = Icons.Default.EmojiEvents,
            label = "Longest Streak",
            value = "${summaryData.longestStreak} days",
            contentDescription = "Longest workout streak: ${summaryData.longestStreak} days"
        ),
        SummaryStatItem(
            icon = Icons.Default.AccessTime,
            label = "Avg Duration",
            value = "${summaryData.averageDuration}min",
            contentDescription = "Average workout duration: ${summaryData.averageDuration} minutes"
        )
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Fixed height to prevent infinite constraints
    ) {
        items(summaryStats) { stat ->
            SummaryCard(stat = stat)
        }
    }
}

/**
 * Individual summary card for displaying a single statistic
 */
@Composable
private fun SummaryCard(stat: SummaryStatItem) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = stat.contentDescription
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = stat.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stat.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Data class representing a summary statistic item
 */
private data class SummaryStatItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val contentDescription: String
) 