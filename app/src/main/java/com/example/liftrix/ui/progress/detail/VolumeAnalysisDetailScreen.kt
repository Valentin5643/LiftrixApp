package com.example.liftrix.ui.progress.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import timber.log.Timber

/**
 * Volume Analysis Detail Screen
 * 
 * Full-screen detailed view of volume analysis data with interactive features:
 * - Volume progression charts with trend analysis
 * - Multiple grouping options (total, by exercise, by muscle group, by time periods)
 * - Time range selection (1M, 3M, 6M, 1Y, All)
 * - Volume projections and trend forecasting
 * - Comparative analysis across different dimensions
 * - Export functionality
 * - Loading, error, and empty states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeAnalysisDetailScreen(
    navController: NavController,
    groupBy: VolumeGrouping,
    timeRange: TimeRangeType,
    modifier: Modifier = Modifier,
    viewModel: VolumeAnalysisDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentGroupBy by viewModel.groupBy.collectAsState()
    val currentTimeRange by viewModel.timeRange.collectAsState()
    val showProjections by viewModel.showProjections.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    // Initialize with passed parameters
    LaunchedEffect(groupBy, timeRange) {
        if (currentGroupBy != groupBy) {
            viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.UpdateGroupBy(groupBy))
        }
        if (currentTimeRange != timeRange) {
            viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.UpdateTimeRange(timeRange))
        }
        Timber.d("VolumeAnalysisDetailScreen initialized: groupBy=$groupBy, timeRange=$timeRange")
    }

    AnalyticsDetailScreen(
        title = "Volume Analysis",
        onBackClick = { navController.popBackStack() },
        topBarActions = {
            // Volume grouping selector
            var showGroupingMenu by remember { mutableStateOf(false) }
            
            IconButton(onClick = { showGroupingMenu = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Grouping Options"
                )
            }
            
            DropdownMenu(
                expanded = showGroupingMenu,
                onDismissRequest = { showGroupingMenu = false }
            ) {
                VolumeGrouping.values().forEach { grouping ->
                    DropdownMenuItem(
                        text = { Text(grouping.displayName) },
                        onClick = {
                            viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.UpdateGroupBy(grouping))
                            showGroupingMenu = false
                        },
                        leadingIcon = if (currentGroupBy == grouping) {
                            { Icon(Icons.Default.BarChart, null) }
                        } else null
                    )
                }
            }

            // Export button
            IconButton(
                onClick = { viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.ExportData) },
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export Data"
                    )
                }
            }
        }
    ) {
        when (uiState) {
            is VolumeAnalysisDetailViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            is VolumeAnalysisDetailViewModel.UiState.Success -> {
                VolumeAnalysisContent(
                    data = (uiState as VolumeAnalysisDetailViewModel.UiState.Success).data,
                    groupBy = currentGroupBy,
                    timeRange = currentTimeRange,
                    showProjections = showProjections,
                    onTimeRangeChange = { newTimeRange ->
                        viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.UpdateTimeRange(newTimeRange))
                    },
                    onToggleProjections = {
                        viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.ToggleProjections)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is VolumeAnalysisDetailViewModel.UiState.Error -> {
                ErrorDisplay(
                    error = (uiState as VolumeAnalysisDetailViewModel.UiState.Error).error,
                    onRetry = { 
                        viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.RefreshData)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is VolumeAnalysisDetailViewModel.UiState.Empty -> {
                EmptyState(
                    message = (uiState as VolumeAnalysisDetailViewModel.UiState.Empty).message,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun VolumeAnalysisContent(
    data: VolumeAnalysisDetailViewModel.VolumeAnalysisData,
    groupBy: VolumeGrouping,
    timeRange: TimeRangeType,
    showProjections: Boolean,
    onTimeRangeChange: (TimeRangeType) -> Unit,
    onToggleProjections: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time range selector
        GlobalTimeRangeSelector(
            selectedTimeRange = timeRange,
            onTimeRangeChange = onTimeRangeChange,
            modifier = Modifier.fillMaxWidth()
        )

        // Summary statistics card
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VolumeStatItem(
                        label = "Total Volume",
                        value = "${(data.totalVolume / 1000).toInt()}K lbs",
                        icon = Icons.Default.BarChart
                    )
                    VolumeStatItem(
                        label = "Growth Rate",
                        value = "${String.format("%.1f", data.volumeGrowth)}%",
                        icon = Icons.Default.TrendingUp,
                        isPositive = data.volumeGrowth > 0
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VolumeStatItem(
                        label = "Average/Week",
                        value = "${(data.averageVolume / 1000).toInt()}K lbs",
                        icon = Icons.Default.BarChart
                    )
                    VolumeStatItem(
                        label = "Grouping",
                        value = groupBy.displayName,
                        icon = Icons.Default.Settings
                    )
                }
            }
        }

        // Volume progression chart card
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume Progression",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = showProjections,
                            onCheckedChange = { onToggleProjections() }
                        )
                        Text(
                            text = "Projections",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Placeholder for volume progression chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Volume Progression Chart\n(${data.volumeData.size} data points)\nGrouped by: ${groupBy.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Volume breakdown by grouping
        LiftrixCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Volume Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = groupBy.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Volume breakdown content based on grouping type
                when (groupBy) {
                    VolumeGrouping.TOTAL -> TotalVolumeBreakdown(data)
                    VolumeGrouping.BY_EXERCISE -> ExerciseVolumeBreakdown(data)
                    VolumeGrouping.BY_MUSCLE_GROUP -> MuscleGroupVolumeBreakdown(data)
                    VolumeGrouping.BY_SESSION -> SessionVolumeBreakdown(data)
                    VolumeGrouping.BY_WEEK -> WeeklyVolumeBreakdown(data)
                    VolumeGrouping.BY_MONTH -> MonthlyVolumeBreakdown(data)
                }
            }
        }
    }
}

@Composable
private fun VolumeStatItem(
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
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = when (isPositive) {
                true -> MaterialTheme.colorScheme.tertiary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

// Placeholder breakdown components
@Composable
private fun TotalVolumeBreakdown(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    Text("Total volume across all exercises and sessions")
}

@Composable
private fun ExerciseVolumeBreakdown(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    Text("Volume breakdown by individual exercises")
}

@Composable
private fun MuscleGroupVolumeBreakdown(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    Text("Volume breakdown by muscle groups")
}

@Composable
private fun SessionVolumeBreakdown(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    Text("Volume breakdown by workout sessions")
}

@Composable
private fun WeeklyVolumeBreakdown(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    Text("Volume breakdown by weekly periods")
}

@Composable
private fun MonthlyVolumeBreakdown(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    Text("Volume breakdown by monthly periods")
}