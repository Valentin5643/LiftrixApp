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
import com.example.liftrix.ui.progress.components.SummaryStatsCard
import com.example.liftrix.ui.progress.components.TimePeriodSelector
import com.example.liftrix.ui.progress.components.WorkoutDurationChart
import com.example.liftrix.ui.progress.components.WorkoutFrequencyHeatmap
import com.example.liftrix.ui.progress.components.WorkoutVolumeChart

/**
 * Progress dashboard screen with comprehensive analytics and charts.
 * 
 * This screen displays workout progress analytics including volume charts,
 * duration tracking, frequency heatmaps, and summary statistics. It integrates
 * with ProgressDashboardViewModel for state management and data loading.
 * 
 * Features:
 * - LazyColumn layout with chart containers
 * - Loading states for all chart types
 * - Error handling with retry functionality
 * - Empty state for users with no workout data
 * - Material3 design with proper accessibility
 * - Foundation for future chart component integration
 * 
 * @param modifier Modifier for styling the screen
 * @param viewModel ViewModel for state management and data loading
 */
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.error != null -> {
                ErrorStateContent(
                    error = uiState.error!!,
                    onRetry = { viewModel.onEvent(ProgressDashboardEvent.RefreshData) },
                    onDismiss = { viewModel.onEvent(ProgressDashboardEvent.ClearError) }
                )
            }
            uiState.isEmpty && !uiState.isAnyChartLoading -> {
                EmptyStateContent(
                    onRefresh = { viewModel.onEvent(ProgressDashboardEvent.RefreshData) }
                )
            }
            else -> {
                ProgressDashboardContent(
                    uiState = uiState,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }
}

/**
 * Main dashboard content with LazyColumn layout
 */
@Composable
private fun ProgressDashboardContent(
    uiState: ProgressDashboardUiState,
    onEvent: (ProgressDashboardEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Screen title
        item {
            Text(
                text = "Progress Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Time period selector
        item {
            TimePeriodSelector(
                selectedPeriod = uiState.selectedTimePeriod,
                onPeriodSelected = { period ->
                    onEvent(ProgressDashboardEvent.TimePeriodChanged(period))
                }
            )
        }

        // Summary stats
        item {
            SummaryStatsCard(
                summaryData = uiState.summaryData,
                isLoading = uiState.isSummaryLoading
            )
        }

        // Workout volume chart
        item {
            WorkoutVolumeChart(
                data = uiState.volumeData,
                isLoading = uiState.isVolumeLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Workout duration chart
        item {
            WorkoutDurationChart(
                data = uiState.durationData,
                isLoading = uiState.isDurationLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Workout frequency heatmap
        item {
            WorkoutFrequencyHeatmap(
                data = uiState.frequencyData,
                isLoading = uiState.isFrequencyLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}





/**
 * Error state content
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Error Loading Progress",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

/**
 * Empty state content for users with no workout data
 */
@Composable
private fun EmptyStateContent(
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "No Progress Data Yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Start working out to see your progress analytics and charts here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Button(
                onClick = onRefresh
            ) {
                Text("Check for Data")
            }
        }
    }
} 