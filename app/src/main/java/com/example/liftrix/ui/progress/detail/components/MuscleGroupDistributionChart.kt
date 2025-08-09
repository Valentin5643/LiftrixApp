package com.example.liftrix.ui.progress.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.ui.progress.detail.MuscleGroupDetailViewModel

/**
 * Muscle Group Distribution Chart
 * 
 * Interactive pie chart specifically designed for the muscle group detail screen.
 * Integrates with the MuscleGroupDetailViewModel data structure and provides
 * drill-down functionality for muscle group analysis.
 */
@Composable
fun MuscleGroupDistributionChart(
    data: List<MuscleGroupDetailViewModel.MuscleGroupDistribution>,
    selectedMuscleGroup: MuscleGroup?,
    onSliceClick: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
    showPercentages: Boolean = true,
    showLegend: Boolean = true
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Chart title
        Text(
            text = if (selectedMuscleGroup != null) {
                "${selectedMuscleGroup.displayName} Focus"
            } else {
                "Muscle Group Distribution"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (data.isNotEmpty()) {
            // Convert to the format expected by the pie chart
            val chartData = data.associate { distribution ->
                distribution.muscleGroup to distribution.totalVolume
            }
            
            // Use the existing pie chart component
            // TODO: This is a placeholder - need to create or adapt the actual pie chart component
            // to work with the MuscleGroupDetailViewModel data structure
            MuscleGroupPieChartPlaceholder(
                data = data,
                selectedMuscleGroup = selectedMuscleGroup,
                onSliceClick = onSliceClick,
                showPercentages = showPercentages,
                showLegend = showLegend
            )
        } else {
            EmptyChartState()
        }
    }
}

/**
 * Placeholder for the actual pie chart implementation
 * TODO: Replace with actual interactive pie chart component
 */
@Composable
private fun MuscleGroupPieChartPlaceholder(
    data: List<MuscleGroupDetailViewModel.MuscleGroupDistribution>,
    selectedMuscleGroup: MuscleGroup?,
    onSliceClick: (MuscleGroup) -> Unit,
    showPercentages: Boolean,
    showLegend: Boolean
) {
    Column {
        // Pie chart placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🥧 Interactive Pie Chart",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Legend
        if (showLegend) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Muscle Groups",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                data.sortedByDescending { it.percentage }.forEach { distribution ->
                    MuscleGroupLegendItem(
                        distribution = distribution,
                        isSelected = distribution.muscleGroup == selectedMuscleGroup,
                        onClick = { onSliceClick(distribution.muscleGroup) },
                        showPercentages = showPercentages
                    )
                }
            }
        }
    }
}

/**
 * Individual legend item for muscle group distribution
 */
@Composable
private fun MuscleGroupLegendItem(
    distribution: MuscleGroupDetailViewModel.MuscleGroupDistribution,
    isSelected: Boolean,
    onClick: () -> Unit,
    showPercentages: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(2.dp)
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawCircle(color = distribution.color)
                }
            }
            
            Text(
                text = distribution.muscleGroup.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        
        if (showPercentages) {
            Text(
                text = "${distribution.percentage.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Empty state when no chart data is available
 */
@Composable
private fun EmptyChartState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📊",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "No muscle group data",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Complete workouts to see distribution",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}