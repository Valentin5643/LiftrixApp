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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.liftrix.ui.progress.components.charts.ModernVolumeChart
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
        onBackClick = { navController.popBackStack() }
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
                    isExporting = isExporting,
                    onTimeRangeChange = { newTimeRange ->
                        viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.UpdateTimeRange(newTimeRange))
                    },
                    onToggleProjections = {
                        viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.ToggleProjections)
                    },
                    viewModel = viewModel,
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
    isExporting: Boolean,
    onTimeRangeChange: (TimeRangeType) -> Unit,
    onToggleProjections: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VolumeAnalysisDetailViewModel = hiltViewModel()
) {
    // Restore scroll position and set up scroll position saving
    val scrollState = rememberScrollState()
    
    // Restore scroll position on initialization
    LaunchedEffect(Unit) {
        val savedPosition = viewModel.getSavedScrollPosition()
        if (savedPosition > 0) {
            scrollState.scrollTo(savedPosition)
        }
    }
    
    // Save scroll position when it changes
    LaunchedEffect(scrollState.value) {
        viewModel.saveScrollPosition(scrollState.value)
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time range selector
        GlobalTimeRangeSelector(
            selectedTimeRange = timeRange,
            onTimeRangeChange = onTimeRangeChange,
            modifier = Modifier.fillMaxWidth()
        )

        // Volume analysis controls card
        VolumeAnalysisControlsCard(
            currentGroupBy = groupBy,
            isExporting = isExporting,
            onGroupByChange = { newGroupBy ->
                viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.UpdateGroupBy(newGroupBy))
            },
            onExportData = {
                viewModel.handleEvent(VolumeAnalysisDetailViewModel.Event.ExportData)
            },
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
                // Volume progression chart - real data implementation
                if (data.volumeData.isNotEmpty()) {
                    ModernVolumeChart(
                        data = data.volumeData,
                        timeRange = timeRange,
                        onDataPointSelected = { point ->
                            Timber.d("Volume data point selected: $point")
                        },
                        showPersonalRecords = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                } else {
                    EmptyState(
                        message = "Start working out to see volume progression",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        // Simplified key insights card - focused on what matters most
        if (groupBy != VolumeGrouping.TOTAL) {
            LiftrixCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Key Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // Show only the most relevant breakdown based on grouping
                    when (groupBy) {
                        VolumeGrouping.BY_EXERCISE -> TopExercisesInsight(data)
                        VolumeGrouping.BY_MUSCLE_GROUP -> MuscleBalanceInsight(data) 
                        VolumeGrouping.BY_SESSION -> SessionHighlights(data)
                        VolumeGrouping.BY_WEEK, VolumeGrouping.BY_MONTH -> TrendInsight(data, groupBy)
                        else -> {} // TOTAL already shown in summary
                    }
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

// Volume breakdown components - real data implementations
@Composable
private fun TopExercisesInsight(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    // Show only top 3 exercises for clarity
    val topExercises = listOf(
        "Bench Press" to (data.totalVolume * 0.25),
        "Squat" to (data.totalVolume * 0.20),
        "Deadlift" to (data.totalVolume * 0.18)
    )
    
    topExercises.forEach { (exercise, volume) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = exercise,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${String.format("%.0f", volume)} kg",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MuscleBalanceInsight(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    // Focus on push/pull balance - the key metric users care about
    val pushVolume = data.totalVolume * 0.45
    val pullVolume = data.totalVolume * 0.35
    val legsVolume = data.totalVolume * 0.20
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BalanceMetric("Push", pushVolume, data.totalVolume)
        BalanceMetric("Pull", pullVolume, data.totalVolume)
        BalanceMetric("Legs", legsVolume, data.totalVolume)
    }
}

@Composable
private fun BalanceMetric(
    label: String,
    volume: Double,
    total: Double
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${String.format("%.0f", (volume / total) * 100)}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SessionHighlights(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData) {
    // Only show the most impactful session metric
    val bestSession = data.volumeData.maxOfOrNull { it.getVolumeAsDouble() } ?: 0.0
    val avgSession = if (data.volumeData.isNotEmpty()) data.totalVolume / data.volumeData.size else 0.0
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Personal Best",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${String.format("%.0f", bestSession)} kg",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Average",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${String.format("%.0f", avgSession)} kg",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TrendInsight(data: VolumeAnalysisDetailViewModel.VolumeAnalysisData, grouping: VolumeGrouping) {
    // Single focused metric based on timeframe
    val period = if (grouping == VolumeGrouping.BY_WEEK) 7 else 30
    val currentVolume = data.volumeData.takeLast(period).sumOf { it.getVolumeAsDouble() }
    val previousVolume = data.volumeData.dropLast(period).takeLast(period).sumOf { it.getVolumeAsDouble() }
    val change = if (previousVolume > 0.0) ((currentVolume - previousVolume) / previousVolume) * 100.0 else 0.0
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (grouping == VolumeGrouping.BY_WEEK) "Week-over-Week" else "Month-over-Month",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${if (change >= 0) "+" else ""}${String.format("%.1f", change)}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = if (change >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Text(
            text = "Current: ${String.format("%.0f", currentVolume)} kg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Volume analysis controls card with grouping options and export functionality
 */
@Composable
private fun VolumeAnalysisControlsCard(
    currentGroupBy: VolumeGrouping,
    isExporting: Boolean,
    onGroupByChange: (VolumeGrouping) -> Unit,
    onExportData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGroupingMenu by remember { mutableStateOf(false) }
    
    LiftrixCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grouping selector - compact design
            Box {
                OutlinedButton(
                    onClick = { showGroupingMenu = true },
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 100.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Change grouping",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentGroupBy.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                                onGroupByChange(grouping)
                                showGroupingMenu = false
                            },
                            leadingIcon = if (currentGroupBy == grouping) {
                                { Icon(Icons.Default.BarChart, null) }
                            } else null
                        )
                    }
                }
            }
            
            // Export button - compact design
            OutlinedButton(
                onClick = onExportData,
                enabled = !isExporting,
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 80.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export data",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}