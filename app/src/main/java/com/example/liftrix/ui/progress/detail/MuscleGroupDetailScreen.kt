package com.example.liftrix.ui.progress.detail

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
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen
import com.example.liftrix.ui.progress.detail.components.MuscleGroupDistributionChart
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import timber.log.Timber

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupDetailScreen(
    navController: NavController,
    muscleGroup: MuscleGroup?,
    timeRange: TimeRangeType,
    modifier: Modifier = Modifier,
    viewModel: MuscleGroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedMuscleGroup by viewModel.selectedMuscleGroup.collectAsState()
    val currentTimeRange by viewModel.timeRange.collectAsState()
    
    // Initialize with route parameters
    LaunchedEffect(muscleGroup, timeRange) {
        viewModel.initializeWithParameters(muscleGroup, timeRange)
    }
    
    AnalyticsDetailScreen(
        title = buildString {
            append("Muscle Groups")
            selectedMuscleGroup?.let { append(" - ${it.displayName}") }
        },
        onBackClick = { 
            navController.popBackStack()
        },
        topBarActions = {
            // Export button
            IconButton(
                onClick = { 
                    viewModel.handleEvent(MuscleGroupDetailViewModel.Event.ExportData)
                }
            ) {
                Icon(
                    Icons.Default.FileDownload, 
                    contentDescription = "Export data",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
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
                    viewModel.handleEvent(MuscleGroupDetailViewModel.Event.UpdateTimeRange(newTimeRange))
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (uiState) {
                is MuscleGroupDetailViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            message = "Loading muscle group analysis..."
                        )
                    }
                }
                
                is MuscleGroupDetailViewModel.UiState.Error -> {
                    val errorState = uiState as MuscleGroupDetailViewModel.UiState.Error
                    ErrorDisplay(
                        error = errorState.error,
                        onRetry = {
                            viewModel.handleEvent(MuscleGroupDetailViewModel.Event.RetryLoad)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is MuscleGroupDetailViewModel.UiState.Empty -> {
                    val emptyState = uiState as MuscleGroupDetailViewModel.UiState.Empty
                    EmptyState(
                        message = emptyState.message,
                        actionText = "Clear Selection",
                        onActionClick = {
                            viewModel.handleEvent(MuscleGroupDetailViewModel.Event.ClearMuscleGroupSelection)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is MuscleGroupDetailViewModel.UiState.Success -> {
                    val successState = uiState as MuscleGroupDetailViewModel.UiState.Success
                    // Only show distribution view - remove the other tabs
                    DistributionView(
                        data = successState.data,
                        onMuscleGroupClick = { muscleGroup ->
                            viewModel.handleEvent(MuscleGroupDetailViewModel.Event.SelectMuscleGroupSegment(muscleGroup))
                        }
                    )
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