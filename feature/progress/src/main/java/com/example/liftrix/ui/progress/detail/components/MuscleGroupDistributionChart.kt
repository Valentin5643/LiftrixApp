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
import com.example.liftrix.ui.progress.components.charts.MuscleGroupPieChart
import com.example.liftrix.ui.progress.components.charts.MuscleGroup as ChartMuscleGroup

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
            // Convert data to the format expected by MuscleGroupPieChart
            val pieChartData = data.associate { distribution ->
                mapDomainToChartMuscleGroup(distribution.muscleGroup) to distribution.percentage
            }
            
            // Use the actual MuscleGroupPieChart component
            MuscleGroupPieChart(
                data = pieChartData,
                onSliceClick = { chartMuscleGroup ->
                    val domainMuscleGroup = mapChartToDomainMuscleGroup(chartMuscleGroup)
                    onSliceClick?.invoke(domainMuscleGroup)
                },
                showLegend = showLegend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        } else {
            EmptyChartState()
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

/**
 * Map domain MuscleGroup to chart MuscleGroup
 */
private fun mapDomainToChartMuscleGroup(domainMuscleGroup: MuscleGroup): ChartMuscleGroup {
    return when (domainMuscleGroup) {
        MuscleGroup.CHEST -> ChartMuscleGroup.CHEST
        MuscleGroup.BACK -> ChartMuscleGroup.BACK
        MuscleGroup.SHOULDERS -> ChartMuscleGroup.SHOULDERS
        MuscleGroup.TRICEPS, MuscleGroup.BICEPS, MuscleGroup.FOREARMS -> ChartMuscleGroup.ARMS
        MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES -> ChartMuscleGroup.LEGS
        MuscleGroup.CORE, MuscleGroup.LOWER_BACK -> ChartMuscleGroup.CORE
        MuscleGroup.CARDIO -> ChartMuscleGroup.CARDIO
        else -> ChartMuscleGroup.OTHER
    }
}

/**
 * Map chart MuscleGroup back to domain MuscleGroup (for primary mapping)
 */
private fun mapChartToDomainMuscleGroup(chartMuscleGroup: ChartMuscleGroup): MuscleGroup {
    return when (chartMuscleGroup) {
        ChartMuscleGroup.CHEST -> MuscleGroup.CHEST
        ChartMuscleGroup.BACK -> MuscleGroup.BACK
        ChartMuscleGroup.SHOULDERS -> MuscleGroup.SHOULDERS
        ChartMuscleGroup.ARMS -> MuscleGroup.BICEPS // Use biceps as primary arm muscle
        ChartMuscleGroup.LEGS -> MuscleGroup.QUADRICEPS // Use quads as primary leg muscle
        ChartMuscleGroup.CORE -> MuscleGroup.CORE // Use core as primary core muscle
        ChartMuscleGroup.CARDIO -> MuscleGroup.CARDIO
        ChartMuscleGroup.OTHER -> MuscleGroup.FULL_BODY // Use full body as fallback
    }
}