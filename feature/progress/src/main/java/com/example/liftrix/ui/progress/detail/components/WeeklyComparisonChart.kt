package com.example.liftrix.ui.progress.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.progress.detail.MuscleGroupDetailViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

/**
 * Weekly Comparison Chart
 * 
 * Displays weekly muscle group distribution comparisons over time.
 * Shows trends in muscle group training patterns and volume changes.
 * Uses a bar chart implementation for visual comparison.
 */
@Composable
fun WeeklyComparisonChart(
    weeklyData: List<MuscleGroupDetailViewModel.WeeklyComparison>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Chart title
        Text(
            text = "Weekly Comparison",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (weeklyData.isNotEmpty()) {
            // Chart placeholder
            WeeklyComparisonChartPlaceholder(
                weeklyData = weeklyData
            )
        } else {
            EmptyWeeklyChartState()
        }
    }
}

/**
 * Placeholder for the weekly comparison chart
 */
@Composable
private fun WeeklyComparisonChartPlaceholder(
    weeklyData: List<MuscleGroupDetailViewModel.WeeklyComparison>
) {
    Column {
        // Chart area placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📊 Weekly Trend Chart",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Weekly data list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 200.dp)
        ) {
            items(weeklyData.takeLast(4)) { weekData -> // Show last 4 weeks
                WeeklyDataItem(weekData = weekData)
            }
        }
    }
}

/**
 * Individual weekly data item
 */
@Composable
private fun WeeklyDataItem(
    weekData: MuscleGroupDetailViewModel.WeeklyComparison
) {
    LiftrixCard {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Week header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatWeekDate(weekData.weekStartDate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${weekData.totalVolume.toInt()} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Top muscle groups for this week
            val topGroups = weekData.distribution
                .sortedByDescending { it.percentage }
                .take(3)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                topGroups.forEach { distribution ->
                    WeeklyMuscleGroupItem(
                        distribution = distribution,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Individual muscle group item within weekly data
 */
@Composable
private fun WeeklyMuscleGroupItem(
    distribution: MuscleGroupDetailViewModel.MuscleGroupDistribution,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${distribution.percentage.toInt()}%",
            style = MaterialTheme.typography.titleSmall,
            color = distribution.color,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = distribution.muscleGroup.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty state for weekly chart
 */
@Composable
private fun EmptyWeeklyChartState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📈",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "No weekly data",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Complete more workouts to see trends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format week start date for display
 */
private fun formatWeekDate(date: LocalDate): String {
    val format = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        dayOfMonth()
    }
    return "Week of ${date.format(format)}"
}