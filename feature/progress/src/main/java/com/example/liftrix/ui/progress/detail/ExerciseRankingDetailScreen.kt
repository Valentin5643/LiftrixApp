package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.progress.detail.components.AnalyticsDetailScreen
import timber.log.Timber

/**
 * Exercise Ranking Detail Screen
 * 
 * Full-screen detailed view of exercise performance rankings:
 * - Exercise leaderboard with performance scores
 * - Multiple ranking metrics (performance, volume, strength, frequency, consistency, trend)
 * - Performance score calculation: (Volume Growth % + 1RM Growth %) / 2
 * - Plateau detection across 3-week windows with variance threshold
 * - Customizable ranking limits and sorting options
 * - Exercise-specific insights and recommendations
 * - Export functionality
 * - Loading, error, and empty states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseRankingDetailScreen(
    navController: NavController,
    sortBy: RankingMetric,
    limit: Int,
    modifier: Modifier = Modifier,
    viewModel: ExerciseRankingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val currentLimit by viewModel.limit.collectAsStateWithLifecycle()
    val showPlateaus by viewModel.showPlateaus.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

    // Initialize with passed parameters
    LaunchedEffect(sortBy, limit) {
        if (currentSortBy != sortBy) {
            viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.UpdateSortBy(sortBy))
        }
        if (currentLimit != limit) {
            viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.UpdateLimit(limit))
        }
        Timber.d("ExerciseRankingDetailScreen initialized: sortBy=$sortBy, limit=$limit")
    }

    AnalyticsDetailScreen(
        title = "Exercise Rankings",
        onBackClick = { navController.popBackStack() },
        topBarActions = {
            // Ranking metric selector
            var showMetricMenu by remember { mutableStateOf(false) }
            
            IconButton(onClick = { showMetricMenu = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ranking Metrics"
                )
            }
            
            DropdownMenu(
                expanded = showMetricMenu,
                onDismissRequest = { showMetricMenu = false }
            ) {
                RankingMetric.values().forEach { metric ->
                    DropdownMenuItem(
                        text = { Text(metric.displayName) },
                        onClick = {
                            viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.UpdateSortBy(metric))
                            showMetricMenu = false
                        },
                        leadingIcon = if (currentSortBy == metric) {
                            { Icon(Icons.Default.EmojiEvents, "Selected metric") }
                        } else null
                    )
                }
            }

            // Export button
            IconButton(
                onClick = { viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.ExportData) },
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
        // Display the actual data - ViewModel uses data class pattern, not sealed class
        ExerciseRankingContent(
            data = uiState,
            sortBy = currentSortBy,
            limit = currentLimit,
            showPlateaus = showPlateaus,
            onSortByChange = { newSortBy ->
                viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.UpdateSortBy(newSortBy))
            },
            onLimitChange = { newLimit ->
                viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.UpdateLimit(newLimit))
            },
            onTogglePlateaus = {
                viewModel.handleEvent(ExerciseRankingDetailViewModel.Event.TogglePlateaus)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExerciseRankingContent(
    data: ExerciseRankingDetailViewModel.UiState,
    sortBy: RankingMetric,
    limit: Int,
    showPlateaus: Boolean,
    onSortByChange: (RankingMetric) -> Unit,
    onLimitChange: (Int) -> Unit,
    onTogglePlateaus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        text = "Performance Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Performance summary",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PerformanceStatItem(
                        label = "Top Exercise",
                        value = data.topPerformer?.exerciseName ?: "N/A",
                        icon = Icons.Default.EmojiEvents
                    )
                    PerformanceStatItem(
                        label = "Avg Score",
                        value = "${String.format("%.1f", data.averagePerformanceScore)}",
                        icon = Icons.Default.TrendingUp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PerformanceStatItem(
                        label = "Exercises",
                        value = "${data.rankings.size}",
                        icon = Icons.Default.Settings
                    )
                    PerformanceStatItem(
                        label = "Plateaus",
                        value = "${data.plateauExercises.size}",
                        icon = Icons.Default.Warning,
                        isWarning = data.plateauExercises.isNotEmpty()
                    )
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = showPlateaus,
                            onCheckedChange = { onTogglePlateaus() }
                        )
                        Text(
                            text = "Show Plateaus",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Text(
                        text = "Sorted by: ${sortBy.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Exercise rankings list
        LiftrixCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                        text = "Exercise Rankings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (sortBy.isHigherBetter()) {
                                Icons.Default.TrendingUp
                            } else {
                                Icons.Default.TrendingDown
                            },
                            contentDescription = if (sortBy.isHigherBetter()) {
                                "Higher is better"
                            } else {
                                "Lower is better"
                            },
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (sortBy.isHigherBetter()) {
                                "Higher is better"
                            } else {
                                "Lower is better"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(data.rankings) { index, exercise ->
                        ExerciseRankingItem(
                            exercise = exercise,
                            sortBy = sortBy,
                            showPlateau = showPlateaus && exercise.isPlateau,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isWarning: Boolean = false,
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
            tint = if (isWarning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
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
            color = if (isWarning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ExerciseRankingItem(
    exercise: ExerciseRankingEntry,
    sortBy: RankingMetric,
    showPlateau: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (showPlateau) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank and exercise name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rank badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (exercise.rank) {
                        1 -> MaterialTheme.colorScheme.primary
                        2, 3 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                ) {
                    Text(
                        text = "#${exercise.rank}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Exercise info
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = exercise.exerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (showPlateau) {
                        Text(
                            text = "âš  Plateau detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Score and metric details
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = String.format("%.1f", exercise.score),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (showPlateau) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = sortBy.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

