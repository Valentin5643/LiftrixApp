package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import com.example.liftrix.ui.progress.components.charts.ModernVolumeChart
import com.example.liftrix.ui.theme.LiftrixColorsV2
import timber.log.Timber

/**
 * Workout Frequency Detail Screen
 * 
 * Full-screen detailed view of workout frequency analysis data with interactive features:
 * - Workout frequency patterns and trends
 * - Time range selection (1M, 3M, 6M, 1Y, All)
 * - Consistency scoring and streaks
 * - Weekly/monthly frequency analysis
 * - Rest day analysis and recommendations
 * - Loading, error, and empty states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutFrequencyDetailScreen(
    timeRange: TimeRangeType = TimeRangeType.MONTH,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WorkoutFrequencyDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTimeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val consistencyScore by viewModel.consistencyScore.collectAsStateWithLifecycle()

    LaunchedEffect(timeRange) {
        if (selectedTimeRange != timeRange) {
            viewModel.updateTimeRange(timeRange)
        }
    }

    AnalyticsDetailScreen(
        title = "Workout Frequency",
        onBackClick = { navController.navigateUp() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time range selector
            GlobalTimeRangeSelector(
                selectedTimeRange = selectedTimeRange,
                onTimeRangeChange = viewModel::updateTimeRange,
                modifier = Modifier.fillMaxWidth()
            )

            when (val state = uiState) {
                is WorkoutFrequencyDetailViewModel.UiState.Loading -> {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
                
                is WorkoutFrequencyDetailViewModel.UiState.Error -> {
                    ErrorDisplay(
                        error = state.error,
                        onRetry = { viewModel.handleEvent(WorkoutFrequencyDetailViewModel.Event.RefreshData) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
                
                is WorkoutFrequencyDetailViewModel.UiState.Empty -> {
                    EmptyState(
                        title = "No frequency data",
                        message = state.message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
                
                is WorkoutFrequencyDetailViewModel.UiState.Success -> {
                    WorkoutFrequencyContent(
                        data = state.data,
                        timeRange = selectedTimeRange,
                        consistencyScore = consistencyScore
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutFrequencyContent(
    data: WorkoutFrequencyDetailViewModel.WorkoutFrequencyData,
    timeRange: TimeRangeType,
    consistencyScore: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Frequency overview stats
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Frequency Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FrequencyStatItem(
                        label = "Total Workouts",
                        value = data.totalWorkouts.toString(),
                        icon = Icons.Default.Assessment,
                        modifier = Modifier.weight(1f)
                    )
                    
                    FrequencyStatItem(
                        label = "Workouts/Week",
                        value = String.format("%.1f", data.averageWorkoutsPerWeek),
                        icon = Icons.Default.CalendarToday,
                        isPositive = data.averageWorkoutsPerWeek >= 3.0f,
                        modifier = Modifier.weight(1f)
                    )
                    
                    FrequencyStatItem(
                        label = "Consistency",
                        value = "${String.format("%.0f", consistencyScore)}%",
                        icon = Icons.Default.TrendingUp,
                        isPositive = consistencyScore >= 75f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Frequency progression chart
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Frequency Progression",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                // Frequency chart - real data implementation
                if (data.frequencyData.isNotEmpty()) {
                    ModernVolumeChart(
                        data = data.frequencyData,
                        timeRange = timeRange,
                        onDataPointSelected = { point ->
                            Timber.d("Frequency data point selected: $point")
                        },
                        showPersonalRecords = false,
                        unit = " workouts",
                        chartTitle = "Workout Frequency",
                        allowPointSelection = timeRange != TimeRangeType.ALL_TIME,
                        useZeroBaseline = true,
                        maxVisiblePoints = if (timeRange == TimeRangeType.ALL_TIME) 0 else 32,
                        fillBrush = Brush.verticalGradient(
                            colors = listOf(
                                LiftrixColorsV2.Teal.copy(alpha = 0.35f),
                                LiftrixColorsV2.Teal.copy(alpha = 0.12f)
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                } else {
                    EmptyState(
                        title = "No frequency patterns",
                        message = "Start working out to see frequency patterns",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        // Consolidated key insights - focused on what matters most
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Workout Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                // Show only the most important frequency metrics
                StreamlinedFrequencyInsights(data = data)
            }
        }
    }
}

@Composable
private fun FrequencyStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = when (isPositive) {
                true -> MaterialTheme.colorScheme.tertiary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = when (isPositive) {
                true -> MaterialTheme.colorScheme.tertiary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun StreamlinedFrequencyInsights(data: WorkoutFrequencyDetailViewModel.WorkoutFrequencyData) {
    // Focus on the most impactful metrics in a cleaner layout
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Current Streak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${data.currentStreak}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = if (data.currentStreak >= 7) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Best Streak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${data.bestStreak}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // Show a single actionable insight based on current state
    if (data.daysSinceLastWorkout > 2) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "âš ï¸ ${data.daysSinceLastWorkout} days since last workout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    } else if (data.currentStreak >= 7) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ðŸ”¥ Great consistency! Keep it up!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

// Note: WeeklyPatternsContent and RestDayAnalysisContent removed 
// Their functionality is now consolidated into StreamlinedFrequencyInsights
// This reduces cognitive load while maintaining essential information

