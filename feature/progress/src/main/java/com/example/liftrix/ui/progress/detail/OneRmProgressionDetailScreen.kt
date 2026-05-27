package com.example.liftrix.ui.progress.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen
import com.example.liftrix.ui.progress.detail.components.ExerciseFilterSheet
import com.example.liftrix.ui.progress.detail.components.OneRmProgressionChart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * 1RM Progression Detail Screen
 * 
 * Full-screen detailed view of 1RM progression data with interactive features:
 * - Line chart visualization with progression trends
 * - Exercise filtering with multi-select and search
 * - Time range selection (1M, 3M, 6M, 1Y, All)
 * - Toggle between estimated and actual 1RM values
 * - Export functionality
 * - Loading, error, and empty states
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun OneRmProgressionDetailScreen(
    navController: NavController,
    exerciseIds: List<String>?,
    timeRange: TimeRangeType,
    modifier: Modifier = Modifier,
    viewModel: OneRmDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedExerciseIds by viewModel.selectedExerciseIds.collectAsStateWithLifecycle()
    val currentTimeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val showEstimated by viewModel.showEstimated.collectAsStateWithLifecycle()
    val showExerciseFilter by viewModel.showExerciseFilter.collectAsStateWithLifecycle()
    val availableExercises by viewModel.availableExercises.collectAsStateWithLifecycle()
    
    // Initialize with route parameters
    LaunchedEffect(exerciseIds, timeRange) {
        viewModel.initializeWithParameters(exerciseIds, timeRange)
    }
    
    // Exercise filter sheet
    if (showExerciseFilter) {
        ExerciseFilterSheet(
            exercises = availableExercises,
            selectedIds = selectedExerciseIds,
            onSelectionChange = { newSelection ->
                viewModel.updateExerciseFilter(newSelection)
            },
            onDismiss = {
                viewModel.hideExerciseFilterSheet()
            }
        )
    }
    
    AnalyticsDetailScreen(
        title = "1RM Progression",
        onBackClick = { 
            navController.popBackStack()
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Time range selector
            GlobalTimeRangeSelector(
                selectedTimeRange = currentTimeRange,
                onTimeRangeChange = { newTimeRange ->
                    viewModel.updateTimeRange(newTimeRange)
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 1RM controls card
            OneRmControlsCard(
                onFilterClick = {
                    viewModel.showExerciseFilterSheet()
                },
                onExportClick = {
                    viewModel.exportData()
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (uiState) {
                is OneRmDetailViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            message = "Loading 1RM progression data..."
                        )
                    }
                }
                
                is OneRmDetailViewModel.UiState.Error -> {
                    val errorState = uiState as OneRmDetailViewModel.UiState.Error
                    ErrorDisplay(
                        error = errorState.error,
                        onRetry = {
                            viewModel.retryLoad()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is OneRmDetailViewModel.UiState.Empty -> {
                    val emptyState = uiState as OneRmDetailViewModel.UiState.Empty
                    EmptyState(
                        title = "No 1RM data",
                        message = emptyState.message,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is OneRmDetailViewModel.UiState.Success -> {
                    val successState = uiState as OneRmDetailViewModel.UiState.Success
                    OneRmProgressionContent(
                        data = successState.data,
                        showEstimated = showEstimated,
                        onToggleShowEstimated = { show ->
                            viewModel.toggleShowEstimated(show)
                        },
                        onRefresh = {
                            viewModel.refreshData()
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

/**
 * Content for the 1RM progression screen when data is successfully loaded
 */
@OptIn(FlowPreview::class)
@Composable
private fun OneRmProgressionContent(
    data: OneRmDetailViewModel.OneRmProgressionData,
    showEstimated: Boolean,
    onToggleShowEstimated: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    viewModel: OneRmDetailViewModel = hiltViewModel()
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
    
    // Save scroll position after scrolling settles instead of restarting per pixel.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .debounce(300)
            .collect { position ->
                viewModel.saveScrollPosition(position)
            }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Exercise filter summary
        if (data.exercisesIncluded.isNotEmpty()) {
            ExerciseFilterSummary(
                exercises = data.exercisesIncluded,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Toggle between estimated and actual 1RM
        EstimatedActualToggle(
            showEstimated = showEstimated,
            onToggle = onToggleShowEstimated,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main chart
        LiftrixCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            OneRmProgressionChart(
                data = data,
                showEstimated = showEstimated,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Summary statistics
        ProgressionSummaryCard(
            summary = data.summary,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Exercise details
        if (data.exercisesIncluded.isNotEmpty()) {
            ExerciseDetailsCard(
                exercises = data.exercisesIncluded,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Summary of currently filtered exercises
 */
@Composable
private fun ExerciseFilterSummary(
    exercises: List<OneRmDetailViewModel.ExerciseInfo>,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Filtered Exercises (${exercises.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            exercises.take(3).forEach { exercise ->
                Text(
                    text = "• ${exercise.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (exercises.size > 3) {
                Text(
                    text = "• ... and ${exercises.size - 3} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Toggle between estimated and actual 1RM values
 */
@Composable
private fun EstimatedActualToggle(
    showEstimated: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show Estimated 1RM",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Switch(
                checked = showEstimated,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Summary statistics card showing progression overview
 */
@Composable
private fun ProgressionSummaryCard(
    summary: OneRmDetailViewModel.ProgressionSummary,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Progression Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    label = "Total Growth",
                    value = "${summary.totalGrowth.toInt()}%",
                    color = if (summary.totalGrowth > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                
                SummaryStatItem(
                    label = "Avg Growth",
                    value = "${summary.averageGrowth.toInt()}%",
                    color = if (summary.averageGrowth > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                
                SummaryStatItem(
                    label = "Data Points",
                    value = "${summary.dataPointCount}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (summary.strongestExercise != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Strongest: ${summary.strongestExercise.name} (${summary.strongestExercise.latestOneRm?.toInt() ?: "N/A"} kg)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (summary.mostImprovedExercise != null) {
                Text(
                    text = "Most Improved: ${summary.mostImprovedExercise.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
 * Detailed exercise information card
 */
@Composable
private fun ExerciseDetailsCard(
    exercises: List<OneRmDetailViewModel.ExerciseInfo>,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Exercise Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            exercises.forEach { exercise ->
                ExerciseDetailItem(exercise)
                if (exercise != exercises.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Individual exercise detail item
 */
@Composable
private fun ExerciseDetailItem(
    exercise: OneRmDetailViewModel.ExerciseInfo
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = exercise.category.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${exercise.latestOneRm?.toInt() ?: "N/A"} kg",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = if (exercise.hasOneRmData) "Has Data" else "No Data",
                style = MaterialTheme.typography.bodySmall,
                color = if (exercise.hasOneRmData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 1RM controls card with filter and export functionality
 */
@Composable
private fun OneRmControlsCard(
    onFilterClick: () -> Unit,
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
            // Filter button - compact design
            OutlinedButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 80.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter exercises",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
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
