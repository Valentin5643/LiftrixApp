package com.example.liftrix.ui.progress.detail

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
    val uiState by viewModel.uiState.collectAsState()
    val selectedTimeRange by viewModel.timeRange.collectAsState()
    val consistencyScore by viewModel.consistencyScore.collectAsState()

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                } else {
                    EmptyState(
                        message = "Start working out to see frequency patterns",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        // Frequency patterns and analysis
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Frequency Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                FrequencyAnalysisContent(data = data)
            }
        }

        // Weekly patterns
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Weekly Patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                WeeklyPatternsContent(data = data)
            }
        }

        // Rest day analysis
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Rest Day Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                RestDayAnalysisContent(data = data)
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
            contentDescription = null,
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
private fun FrequencyAnalysisContent(data: WorkoutFrequencyDetailViewModel.WorkoutFrequencyData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Current Streak",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${data.currentStreak} days",
                style = MaterialTheme.typography.bodySmall,
                color = if (data.currentStreak >= 7) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Best Streak",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${data.bestStreak} days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Days Since Last Workout",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${data.daysSinceLastWorkout} days",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    data.daysSinceLastWorkout == 0 -> MaterialTheme.colorScheme.tertiary
                    data.daysSinceLastWorkout <= 2 -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Rest Days Average",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = String.format("%.1f", data.averageRestDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeeklyPatternsContent(data: WorkoutFrequencyDetailViewModel.WorkoutFrequencyData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Most active days:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        data.weeklyPattern.sortedByDescending { it.second }.take(3).forEach { (dayOfWeek, workoutCount) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dayOfWeek,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "$workoutCount workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Preferred workout times:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Morning: ${data.morningWorkouts}% • Afternoon: ${data.afternoonWorkouts}% • Evening: ${data.eveningWorkouts}%",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RestDayAnalysisContent(data: WorkoutFrequencyDetailViewModel.WorkoutFrequencyData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Total Rest Days",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${data.totalRestDays}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Optimal Rest Days",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${data.optimalRestDays}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (data.restDayRecommendation.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "💡 ${data.restDayRecommendation}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}