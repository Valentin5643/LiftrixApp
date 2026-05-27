package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMetric
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import com.example.liftrix.domain.model.analytics.MuscleHeatmapWidgetData
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen
import com.example.liftrix.ui.progress.detail.components.MuscleGroupDistributionChart
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import com.example.liftrix.ui.progress.components.widgets.heatmap.MuscleHeatmapCanvas

enum class MuscleGroupDetailContentMode {
    DISTRIBUTION,
    HEATMAP
}

/**
 * Muscle Group Distribution Detail Screen
 * 
 * Full-screen detailed view of muscle group analysis with interactive features:
 * - Interactive pie chart with drill-down functionality
 * - Time range selection and filtering with GlobalTimeRangeSelector
 * - Distribution view with summary statistics
 * - Export functionality
 * - Loading, error, and empty states
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MuscleGroupDetailScreen(
    navController: NavController,
    muscleGroup: MuscleGroup?,
    timeRange: TimeRangeType,
    contentMode: MuscleGroupDetailContentMode = MuscleGroupDetailContentMode.DISTRIBUTION,
    modifier: Modifier = Modifier,
    viewModel: MuscleGroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedMuscleGroup by viewModel.selectedMuscleGroup.collectAsStateWithLifecycle()
    val currentTimeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val heatmapData by viewModel.heatmapData.collectAsStateWithLifecycle()
    val heatmapGender by viewModel.heatmapGender.collectAsStateWithLifecycle()
    val heatmapMetric by viewModel.heatmapMetric.collectAsStateWithLifecycle()
    val heatmapColorMode by viewModel.heatmapColorMode.collectAsStateWithLifecycle()
    
    // Initialize with route parameters
    LaunchedEffect(muscleGroup, timeRange) {
        viewModel.initializeWithParameters(muscleGroup, timeRange)
    }
    val isHeatmapMode = contentMode == MuscleGroupDetailContentMode.HEATMAP
    
    AnalyticsDetailScreen(
        title = buildString {
            if (contentMode == MuscleGroupDetailContentMode.HEATMAP) {
                append("Muscle Heatmap")
            } else {
                append("Muscle Groups")
                selectedMuscleGroup?.let { append(" - ${it.displayName}") }
            }
        },
        onBackClick = { 
            navController.popBackStack()
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Time range selector with proper functionality
            GlobalTimeRangeSelector(
                selectedTimeRange = currentTimeRange,
                onTimeRangeChange = { newTimeRange ->
                    viewModel.updateTimeRange(newTimeRange)
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(if (isHeatmapMode) 12.dp else 16.dp))

            if (!isHeatmapMode) {
                MuscleGroupControlsCard(
                    onExportClick = {
                        viewModel.exportData()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                when (uiState) {
                    is MuscleGroupDetailViewModel.UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                message = if (isHeatmapMode) {
                                    "Loading muscle heatmap..."
                                } else {
                                    "Loading muscle group analysis..."
                                }
                            )
                        }
                    }

                    is MuscleGroupDetailViewModel.UiState.Error -> {
                        val errorState = uiState as MuscleGroupDetailViewModel.UiState.Error
                        ErrorDisplay(
                            error = errorState.error,
                            onRetry = {
                                viewModel.retryLoad()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is MuscleGroupDetailViewModel.UiState.Empty -> {
                        val emptyState = uiState as MuscleGroupDetailViewModel.UiState.Empty
                        EmptyState(
                            title = "No muscle group data",
                            message = emptyState.message,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is MuscleGroupDetailViewModel.UiState.Success -> {
                        val successState = uiState as MuscleGroupDetailViewModel.UiState.Success
                        if (isHeatmapMode) {
                            HeatmapDetailView(
                                heatmapData = heatmapData,
                                heatmapGender = heatmapGender,
                                heatmapMetric = heatmapMetric,
                                heatmapColorMode = heatmapColorMode,
                                onGenderChange = viewModel::updateHeatmapGender,
                                onMetricChange = viewModel::updateHeatmapMetric,
                                onColorModeChange = viewModel::updateHeatmapColorMode,
                                onExportClick = viewModel::exportData,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            DistributionView(
                                data = successState.data,
                                onMuscleGroupClick = { muscleGroup ->
                                    viewModel.selectMuscleGroupSegment(muscleGroup)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Distribution view with pie chart
 */
@Composable
private fun DistributionView(
    data: MuscleGroupDetailViewModel.MuscleGroupData,
    onMuscleGroupClick: (MuscleGroup) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Main pie chart
        LiftrixCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            MuscleGroupDistributionChart(
                data = data.distribution,
                selectedMuscleGroup = data.selectedMuscleGroup,
                onSliceClick = onMuscleGroupClick,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Summary statistics
        DistributionSummaryCard(
            distribution = data.distribution,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Selected muscle group details
        data.selectedMuscleGroup?.let { selected ->
            Spacer(modifier = Modifier.height(16.dp))
            
            val selectedData = data.distribution.find { it.muscleGroup == selected }
            if (selectedData != null) {
                SelectedMuscleGroupCard(
                    muscleGroup = selected,
                    distribution = selectedData,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HeatmapDetailView(
    heatmapData: MuscleHeatmapWidgetData,
    heatmapGender: MuscleHeatmapGender,
    heatmapMetric: MuscleHeatmapMetric,
    heatmapColorMode: MuscleHeatmapColorMode,
    onGenderChange: (MuscleHeatmapGender) -> Unit,
    onMetricChange: (MuscleHeatmapMetric) -> Unit,
    onColorModeChange: (MuscleHeatmapColorMode) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        MuscleHeatmapDetailCard(
            data = heatmapData,
            gender = heatmapGender,
            metric = heatmapMetric,
            colorMode = heatmapColorMode,
            onGenderChange = onGenderChange,
            onMetricChange = onMetricChange,
            onColorModeChange = onColorModeChange,
            onExportClick = onExportClick,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleHeatmapDetailCard(
    data: MuscleHeatmapWidgetData,
    gender: MuscleHeatmapGender,
    metric: MuscleHeatmapMetric,
    colorMode: MuscleHeatmapColorMode,
    onGenderChange: (MuscleHeatmapGender) -> Unit,
    onMetricChange: (MuscleHeatmapMetric) -> Unit,
    onColorModeChange: (MuscleHeatmapColorMode) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Muscle Heatmap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onExportClick,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Export data",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuscleHeatmapCanvas(
                        gender = data.gender,
                        viewSide = MuscleHeatmapViewSide.FRONT,
                        values = data.muscleValues,
                        colorMode = data.colorMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(0.6f)
                    )
                    MuscleHeatmapCanvas(
                        gender = data.gender,
                        viewSide = MuscleHeatmapViewSide.BACK,
                        values = data.muscleValues,
                        colorMode = data.colorMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(0.6f)
                    )
                }
            }

            HeatmapOptionChips(
                label = "Body",
                options = MuscleHeatmapGender.entries.toList(),
                selected = gender,
                optionLabel = { it.displayName },
                onSelected = onGenderChange
            )
            HeatmapOptionChips(
                label = "Metric",
                options = MuscleHeatmapMetric.entries.toList(),
                selected = metric,
                optionLabel = { it.displayName },
                onSelected = onMetricChange
            )
            HeatmapOptionChips(
                label = "Color",
                options = MuscleHeatmapColorMode.entries.toList(),
                selected = colorMode,
                optionLabel = { it.displayName },
                onSelected = onColorModeChange
            )

            HeatmapBreakdown(data)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> HeatmapOptionChips(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) }
                )
            }
        }
    }
}

@Composable
private fun HeatmapBreakdown(data: MuscleHeatmapWidgetData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Muscle Values",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        data.muscleValues
            .sortedByDescending { it.rawValue }
            .forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = value.displayLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = value.formattedValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}


/**
 * Distribution summary statistics card
 */
@Composable
private fun DistributionSummaryCard(
    distribution: List<MuscleGroupDetailViewModel.MuscleGroupDistribution>,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Distribution Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    label = "Muscle Groups",
                    value = "${distribution.size}",
                    color = MaterialTheme.colorScheme.primary
                )
                
                SummaryStatItem(
                    label = "Total Volume",
                    value = "${distribution.sumOf { it.totalVolume.toDouble() }.toInt()}",
                    color = MaterialTheme.colorScheme.secondary
                )
                
                SummaryStatItem(
                    label = "Exercises",
                    value = "${distribution.sumOf { it.exerciseCount }}",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Top muscle group
            val topMuscleGroup = distribution.maxByOrNull { it.percentage }
            topMuscleGroup?.let { top ->
                Text(
                    text = "Most Trained: ${top.muscleGroup.displayName} (${top.percentage.toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Selected muscle group detail card
 */
@Composable
private fun SelectedMuscleGroupCard(
    muscleGroup: MuscleGroup,
    distribution: MuscleGroupDetailViewModel.MuscleGroupDistribution,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = muscleGroup.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = distribution.color
            )
            
            Text(
                text = muscleGroup.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    label = "Percentage",
                    value = "${distribution.percentage.toInt()}%",
                    color = distribution.color
                )
                
                SummaryStatItem(
                    label = "Volume",
                    value = "${distribution.totalVolume.toInt()}",
                    color = distribution.color
                )
                
                SummaryStatItem(
                    label = "Exercises",
                    value = "${distribution.exerciseCount}",
                    color = distribution.color
                )
                
                SummaryStatItem(
                    label = "Workouts",
                    value = "${distribution.workoutCount}",
                    color = distribution.color
                )
            }
        }
    }
}


/**
 * Individual summary statistic item
 */
@Composable
private fun SummaryStatItem(
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
            style = MaterialTheme.typography.headlineSmall,
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

/**
 * Muscle group controls card with export functionality
 */
@Composable
private fun MuscleGroupControlsCard(
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Export button - compact design
            OutlinedButton(
                onClick = onExportClick,
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 80.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = "Export data",
                    modifier = Modifier.size(18.dp)
                )
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

