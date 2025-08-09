package com.example.liftrix.ui.progress.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.liftrix.ui.progress.detail.components.MuscleGroupExercisesList
import com.example.liftrix.ui.progress.detail.components.BalanceAnalysisCard
import com.example.liftrix.ui.progress.detail.components.WeeklyComparisonChart
import timber.log.Timber

/**
 * Muscle Group Distribution Detail Screen
 * 
 * Full-screen detailed view of muscle group analysis with interactive features:
 * - Interactive pie chart with drill-down functionality
 * - Multiple view modes: Distribution, Comparison, Exercises, Balance
 * - Time range selection and filtering
 * - Muscle group balance analysis with recommendations
 * - Weekly comparison views
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
    val viewMode by viewModel.viewMode.collectAsState()
    
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
            // Time range selector - TODO: Need to create a proper TimeRangeSelector component
            LiftrixCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Time Range: ${currentTimeRange.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // View mode selector
            ViewModeSelector(
                currentMode = viewMode,
                onModeChange = { mode ->
                    viewModel.handleEvent(MuscleGroupDetailViewModel.Event.UpdateViewMode(mode))
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
                    MuscleGroupDetailContent(
                        data = successState.data,
                        onMuscleGroupClick = { muscleGroup ->
                            viewModel.handleEvent(MuscleGroupDetailViewModel.Event.SelectMuscleGroupSegment(muscleGroup))
                        },
                        onRefresh = {
                            viewModel.handleEvent(MuscleGroupDetailViewModel.Event.RefreshData)
                        }
                    )
                }
            }
        }
    }
}

/**
 * View mode selector with tabs
 */
@Composable
private fun ViewModeSelector(
    currentMode: MuscleGroupDetailViewModel.ViewMode,
    onModeChange: (MuscleGroupDetailViewModel.ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MuscleGroupDetailViewModel.ViewMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                val icon = when (mode) {
                    MuscleGroupDetailViewModel.ViewMode.DISTRIBUTION -> Icons.Default.PieChart
                    MuscleGroupDetailViewModel.ViewMode.COMPARISON -> Icons.Default.BarChart
                    MuscleGroupDetailViewModel.ViewMode.EXERCISES -> Icons.Default.TrendingUp
                    MuscleGroupDetailViewModel.ViewMode.RECOMMENDATIONS -> Icons.Default.Balance
                }
                
                ViewModeTab(
                    mode = mode,
                    icon = icon,
                    isSelected = isSelected,
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual view mode tab
 */
@Composable
private fun ViewModeTab(
    mode: MuscleGroupDetailViewModel.ViewMode,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                Color.Transparent
            }
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * Content for the muscle group detail screen when data is successfully loaded
 */
@Composable
private fun MuscleGroupDetailContent(
    data: MuscleGroupDetailViewModel.MuscleGroupData,
    onMuscleGroupClick: (MuscleGroup) -> Unit,
    onRefresh: () -> Unit
) {
    when (data.viewMode) {
        MuscleGroupDetailViewModel.ViewMode.DISTRIBUTION -> {
            DistributionView(
                data = data,
                onMuscleGroupClick = onMuscleGroupClick
            )
        }
        
        MuscleGroupDetailViewModel.ViewMode.COMPARISON -> {
            ComparisonView(
                data = data
            )
        }
        
        MuscleGroupDetailViewModel.ViewMode.EXERCISES -> {
            ExercisesView(
                data = data
            )
        }
        
        MuscleGroupDetailViewModel.ViewMode.RECOMMENDATIONS -> {
            BalanceRecommendationsView(
                data = data
            )
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
 * Weekly comparison view
 */
@Composable
private fun ComparisonView(
    data: MuscleGroupDetailViewModel.MuscleGroupData
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Weekly comparison chart
        LiftrixCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            WeeklyComparisonChart(
                weeklyData = data.weeklyComparison,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Trend analysis
        WeeklyTrendAnalysisCard(
            weeklyData = data.weeklyComparison,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Exercises view for selected muscle group
 */
@Composable
private fun ExercisesView(
    data: MuscleGroupDetailViewModel.MuscleGroupData
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (data.selectedMuscleGroup != null && data.selectedMuscleGroupExercises.isNotEmpty()) {
            // Exercises list
            MuscleGroupExercisesList(
                exercises = data.selectedMuscleGroupExercises,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            EmptyState(
                message = "Select a muscle group to view exercises",
                actionText = "View Distribution",
                onActionClick = { /* Navigate back to distribution */ }
            )
        }
    }
}

/**
 * Balance recommendations view
 */
@Composable
private fun BalanceRecommendationsView(
    data: MuscleGroupDetailViewModel.MuscleGroupData
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Balance analysis
        BalanceAnalysisCard(
            balanceAnalysis = data.balanceAnalysis,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recommendations list
        RecommendationsList(
            recommendations = data.balanceAnalysis.recommendations,
            modifier = Modifier.fillMaxWidth()
        )
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
 * Weekly trend analysis card
 */
@Composable
private fun WeeklyTrendAnalysisCard(
    weeklyData: List<MuscleGroupDetailViewModel.WeeklyComparison>,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Trend Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (weeklyData.size >= 2) {
                val recent = weeklyData.last()
                val previous = weeklyData[weeklyData.size - 2]
                val volumeChange = recent.totalVolume - previous.totalVolume
                val changePercentage = (volumeChange / previous.totalVolume) * 100f
                
                Text(
                    text = "Volume Change: ${if (volumeChange >= 0) "+" else ""}${volumeChange.toInt()} (${changePercentage.toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (volumeChange >= 0) Color.Green else Color.Red
                )
            } else {
                Text(
                    text = "More data needed for trend analysis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Recommendations list
 */
@Composable
private fun RecommendationsList(
    recommendations: List<MuscleGroupDetailViewModel.BalanceRecommendation>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(recommendations) { recommendation ->
            RecommendationCard(recommendation = recommendation)
        }
    }
}

/**
 * Individual recommendation card
 */
@Composable
private fun RecommendationCard(
    recommendation: MuscleGroupDetailViewModel.BalanceRecommendation
) {
    LiftrixCard {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = recommendation.muscleGroup.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Badge {
                    Text(
                        text = recommendation.recommendationType.name,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (recommendation.suggestedExercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Suggested: ${recommendation.suggestedExercises.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
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