package com.example.liftrix.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.progress.components.ProgressSummaryCards
import com.example.liftrix.ui.progress.components.TimePeriodSelector
import com.example.liftrix.ui.progress.components.WorkoutDurationChart
import com.example.liftrix.ui.progress.components.WorkoutFrequencyHeatmap
import com.example.liftrix.ui.progress.components.WorkoutVolumeChart

/**
 * Modern progress dashboard screen with data dashboard styling.
 * 
 * Features enhanced Material 3 design with LiftrixCard system, 8pt grid spacing,
 * and professional athletic interface. Displays comprehensive analytics including
 * volume charts, duration tracking, frequency heatmaps, and summary statistics.
 * 
 * Key improvements:
 * - Modern card-based layout with 24dp border radius
 * - 8pt grid system for consistent spacing
 * - Enhanced visual hierarchy with brand colors
 * - Responsive breakpoints for different screen sizes
 * - Professional data dashboard styling
 * 
 * @param modifier Modifier for styling the screen
 * @param viewModel ViewModel for state management and data loading
 */
@Composable
fun ProgressDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        uiState.run {
            when {
                error != null -> ErrorStateContent(
                    error = error!!,
                    onRetry = { viewModel.onEvent(ProgressDashboardEvent.RefreshData) },
                    onDismiss = { viewModel.onEvent(ProgressDashboardEvent.ClearError) }
                )
                isEmpty && !isAnyChartLoading -> EmptyStateContent(
                    onRefresh = { viewModel.onEvent(ProgressDashboardEvent.RefreshData) }
                )
                else -> ProgressDashboardContent(
                    uiState = this@run,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }
}

/**
 * Main dashboard content with modern card-based layout and 8pt grid spacing
 */
@Composable
private fun ProgressDashboardContent(
    uiState: ProgressDashboardUiState,
    onEvent: (ProgressDashboardEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GridSystem.screenPadding),
        verticalArrangement = Arrangement.spacedBy(GridSystem.gapMedium)
    ) {
        item {
            Spacer(modifier = Modifier.height(GridSystem.spacing2))
        }

        // Modern screen header with enhanced typography
        item {
            Text(
                text = "Progress Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = GridSystem.spacing2)
            )
        }

        // Time period selector with modern styling
        item {
            TimePeriodSelector(
                selectedPeriod = uiState.selectedTimePeriod,
                onPeriodSelected = { onEvent(ProgressDashboardEvent.TimePeriodChanged(it)) }
            )
        }

        // Enhanced summary stats with modern card design
        item {
            uiState.run {
                ProgressSummaryCards(
                    summaryData = summaryData,
                    isLoading = isSummaryLoading
                )
            }
        }

        // Modern workout volume chart with enhanced design
        item {
            uiState.run {
                WorkoutVolumeChart(
                    data = volumeData,
                    isLoading = isVolumeLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Enhanced workout duration chart
        item {
            uiState.run {
                WorkoutDurationChart(
                    data = durationData,
                    isLoading = isDurationLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Modern workout frequency heatmap with improved accessibility
        item {
            uiState.run {
                WorkoutFrequencyHeatmap(
                    data = frequencyData,
                    isLoading = isFrequencyLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(GridSystem.spacing3))
        }
    }
}

/**
 * Reusable container for chart sections
 */
@Composable
private fun ChartContainer(
    title: String,
    icon: ImageVector,
    isLoading: Boolean,
    hasData: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chart header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh $title",
                        tint = if (isLoading) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        LoadingChartPlaceholder()
                    }
                    hasData -> {
                        content()
                    }
                    else -> {
                        NoDataPlaceholder()
                    }
                }
            }
        }
    }
}

/**
 * Loading state for charts
 */
@Composable
private fun LoadingChartPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Loading chart data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Placeholder when no data is available for a chart
 */
@Composable
private fun NoDataPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Complete some workouts to see your progress",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}



/**
 * Volume chart placeholder
 */
@Composable
private fun VolumeChartPlaceholder(dataPoints: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Volume Chart",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$dataPoints data points available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Chart visualization coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Duration chart placeholder
 */
@Composable
private fun DurationChartPlaceholder(dataPoints: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Duration Chart",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$dataPoints data points available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Chart visualization coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Frequency heatmap placeholder
 */
@Composable
private fun FrequencyHeatmapPlaceholder(dataPoints: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Frequency Heatmap",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$dataPoints data points available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Heatmap visualization coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Modern error state content with enhanced visual design
 */
@Composable
private fun ErrorStateContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LiftrixCard(
            modifier = Modifier.padding(GridSystem.screenPadding),
            contentDescription = "Error loading progress data"
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(GridSystem.iconXLarge)
                )
                Text(
                    text = "Error Loading Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
                ) {
                    Button(
                        onClick = onRetry
                    ) {
                        Text("Retry")
                    }
                    OutlinedButton(
                        onClick = onDismiss
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

/**
 * Modern empty state content with enhanced visual hierarchy
 */
@Composable
private fun EmptyStateContent(
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LiftrixCard(
            modifier = Modifier.padding(GridSystem.screenPadding),
            contentDescription = "No progress data available"
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(GridSystem.iconXLarge)
                )
                Text(
                    text = "No Progress Data Yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Start working out to see your progress analytics and charts here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRefresh
                ) {
                    Text("Check for Data")
                }
            }
        }
    }
} 