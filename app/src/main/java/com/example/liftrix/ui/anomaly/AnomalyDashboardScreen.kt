package com.example.liftrix.ui.anomaly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.anomaly.AnomalyDashboardUiState
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId

/**
 * Dashboard screen for viewing and managing workout anomaly detection
 * 
 * Features:
 * - Recent anomalies list with status indicators
 * - Quick access to anomaly settings
 * - Detection statistics and insights
 * - Filter and search functionality for historical anomalies
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnomalyDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnomalyDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadAnomalies()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Anomaly detection dashboard with recent anomalies and settings"
            }
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                LoadingState(
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is UiState.Error -> {
                ErrorState(
                    error = state.error,
                    onRetry = { viewModel.loadAnomalies() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is UiState.Success -> {
                AnomalyDashboardContent(
                    data = state.data,
                    onNavigateToSettings = onNavigateToSettings,
                    onResolveAnomaly = { anomaly, action ->
                        viewModel.resolveAnomaly(anomaly.id, action)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is UiState.Empty -> {
                EmptyState(
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Fallback case - should never reach here
                EmptyState(
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Main content for the anomaly dashboard
 */
@Composable
private fun AnomalyDashboardContent(
    data: AnomalyDashboardData,
    onNavigateToSettings: () -> Unit,
    onResolveAnomaly: (WorkoutAnomaly, UserAnomalyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Detection Statistics Card
        item {
            DetectionStatisticsCard(
                statistics = data.statistics,
                onNavigateToSettings = onNavigateToSettings
            )
        }
        
        // Recent Anomalies Section
        item {
            Text(
                text = "Recent Anomalies",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        if (data.recentAnomalies.isEmpty()) {
            item {
                NoAnomaliesCard()
            }
        } else {
            items(
                items = data.recentAnomalies,
                key = { it.id }
            ) { anomaly ->
                AnomalyCard(
                    anomaly = anomaly,
                    onResolve = { action ->
                        onResolveAnomaly(anomaly, action)
                    }
                )
            }
        }
    }
}

/**
 * Statistics card showing detection overview
 */
@Composable
private fun DetectionStatisticsCard(
    statistics: AnomalyStatistics,
    onNavigateToSettings: () -> Unit
) {
    ElevatedLiftrixCard(
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
                    text = "Detection Overview",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = onNavigateToSettings
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open anomaly settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Total Detected",
                    value = statistics.totalDetected.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                StatisticItem(
                    label = "Confirmed",
                    value = statistics.confirmedCount.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                
                StatisticItem(
                    label = "Dismissed",
                    value = statistics.dismissedCount.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Detection status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (statistics.detectionEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (statistics.detectionEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = if (statistics.detectionEnabled) "Detection Active" else "Detection Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statistics.detectionEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Individual statistic item
 */
@Composable
private fun StatisticItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Card displaying individual anomaly information
 */
@Composable
private fun AnomalyCard(
    anomaly: WorkoutAnomaly,
    onResolve: (UserAnomalyAction) -> Unit
) {
    ElevatedLiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with exercise name and confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = anomaly.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                ConfidenceBadge(confidence = anomaly.confidenceScore)
            }
            
            // Anomaly description
            Text(
                text = anomaly.getDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Detection time
            Text(
                text = "Detected ${anomaly.detectedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Resolution status or action buttons
            if (anomaly.isResolved()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Resolved: ${anomaly.userAction?.name?.lowercase()?.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            } else {
                // Action buttons for unresolved anomalies
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onResolve(UserAnomalyAction.CONFIRMED) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm")
                    }
                    
                    OutlinedButton(
                        onClick = { onResolve(UserAnomalyAction.DISMISSED) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

/**
 * Confidence level badge
 */
@Composable
private fun ConfidenceBadge(confidence: Float) {
    val (color, text) = when {
        confidence >= WorkoutAnomaly.HIGH_CONFIDENCE_THRESHOLD -> {
            MaterialTheme.colorScheme.error to "High"
        }
        confidence >= WorkoutAnomaly.MEDIUM_CONFIDENCE_THRESHOLD -> {
            Color(0xFFFF9800) to "Medium"
        }
        else -> {
            MaterialTheme.colorScheme.onSurfaceVariant to "Low"
        }
    }
    
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = color
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Card shown when no anomalies are detected
 */
@Composable
private fun NoAnomaliesCard() {
    ElevatedLiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(48.dp)
            )
            
            Text(
                text = "No Anomalies Detected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Your workout data looks consistent and accurate. Keep up the great work!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Loading state component
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading anomaly data...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorState(
    error: LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
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
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Failed to Load Anomalies",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Empty state component
 */
@Composable
private fun EmptyState(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Anomaly Detection Ready",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Start working out to enable anomaly detection. We'll help catch potential data entry errors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Button(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configure Settings")
            }
        }
    }
}

/**
 * Data class for anomaly dashboard data
 */
data class AnomalyDashboardData(
    val recentAnomalies: List<WorkoutAnomaly>,
    val statistics: AnomalyStatistics
)

/**
 * Data class for anomaly statistics
 */
data class AnomalyStatistics(
    val totalDetected: Int,
    val confirmedCount: Int,
    val dismissedCount: Int,
    val detectionEnabled: Boolean
)