package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.AnalyticsWidgetData
import com.example.liftrix.domain.model.analytics.Insight
import com.example.liftrix.domain.model.analytics.InsightCategory
import com.example.liftrix.domain.model.analytics.Recommendation
import com.example.liftrix.ui.progress.components.charts.MuscleGroup
import com.example.liftrix.ui.progress.components.charts.MuscleGroupPieChart
import com.example.liftrix.ui.progress.components.widgets.FolderStyleWidget
import com.example.liftrix.ui.theme.LiftrixColors
import timber.log.Timber

/**
 * Analytics category widgets for complex insights and recommendations.
 * 
 * All analytics widgets follow consistent patterns:
 * - Insights and recommendations display
 * - Confidence levels and metrics
 * - Time range indicators
 * - Actionable intelligence presentation
 */

@Composable
fun VolumeAnalyticsWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Volume Analytics",
            icon = Icons.Default.Analytics,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Volume Analytics",
            subtitle = data?.timeRange,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { analyticsData ->
                VolumeAnalyticsDisplay(
                    analyticsData = analyticsData,
                    primaryColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RecoveryMetricsWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Recovery Metrics",
            icon = Icons.Default.SelfImprovement,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Recovery Metrics",
            subtitle = data?.timeRange,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { analyticsData ->
                RecoveryDisplay(
                    analyticsData = analyticsData,
                    primaryColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun PerformanceAnalysisWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Performance Analysis",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            PerformanceDisplay(
                analyticsData = analyticsData,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun WeeklyTrendsWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Weekly Trends",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            AnalyticsDisplay(
                analyticsData = analyticsData,
                icon = Icons.Default.CalendarMonth,
                primaryColor = MaterialTheme.colorScheme.secondary,
                showMetrics = false
            )
        }
    }
}

@Composable
fun MuscleGroupDistributionWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Muscle Groups",
            icon = Icons.Default.DonutLarge,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
        BaseWidget(
            title = "Muscle Group Distribution",
            subtitle = data?.timeRange,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { analyticsData ->
                MuscleGroupDisplay(
                    analyticsData = analyticsData,
                    primaryColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RecoveryPatternsWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Recovery Patterns",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            RecoveryPatternsDisplay(
                analyticsData = analyticsData,
                primaryColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun TrainingIntensityWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Training Intensity",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            IntensityDisplay(
                analyticsData = analyticsData,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ExerciseVarietyWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Exercise Variety",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            VarietyDisplay(
                analyticsData = analyticsData,
                primaryColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun TimeOfDayAnalysisWidget(
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Optimal Timing",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            TimeAnalysisDisplay(
                analyticsData = analyticsData,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CompactAnalyticsWidget(
    widget: AnalyticsWidget,
    data: AnalyticsWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactBaseWidget(
        title = widget.displayName,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { analyticsData ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (analyticsData.insights.isNotEmpty()) {
                        Text(
                            text = analyticsData.insights.first().title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    
                    Text(
                        text = "Confidence: ${(analyticsData.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                ConfidenceIndicator(
                    confidence = analyticsData.confidence,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun VolumeAnalyticsDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Volume analytics header with key metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                // Current volume metric from analytics data
                val currentVolume = analyticsData.metrics["current_volume"] ?: "0"
                val volumeChange = analyticsData.metrics["volume_change"] ?: "0%"
                
                Text(
                    text = "$currentVolume kg total volume",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "$volumeChange change • ${analyticsData.insights.size} insights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Volume trend visualization (mini chart)
        if (analyticsData.metrics.isNotEmpty()) {
            VolumeProgressIndicator(
                metrics = analyticsData.metrics,
                primaryColor = primaryColor
            )
        }
        
        // Volume insights and trends
        if (analyticsData.insights.isNotEmpty()) {
            InsightsDisplay(
                insights = analyticsData.insights.take(2),
                primaryColor = primaryColor
            )
        }
        
        // Load progression metrics
        if (analyticsData.metrics.containsKey("progression_rate")) {
            LoadProgressionDisplay(
                progressionRate = analyticsData.metrics["progression_rate"] ?: "0%",
                primaryColor = primaryColor
            )
        }
        
        // Volume recommendations
        if (analyticsData.recommendations.isNotEmpty()) {
            RecommendationsDisplay(
                recommendations = analyticsData.recommendations.take(1),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun VolumeProgressIndicator(
    metrics: Map<String, String>,
    primaryColor: Color
) {
    val progressPercentage = metrics["progress_percentage"]?.toFloatOrNull() ?: 0f
    val targetVolume = metrics["target_volume"] ?: "N/A"
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume Progression",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Target: $targetVolume kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        LinearProgressIndicator(
            progress = (progressPercentage / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = primaryColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun LoadProgressionDisplay(
    progressionRate: String,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = primaryColor.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
            
            Column {
                Text(
                    text = "Load Progression",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "$progressionRate weekly increase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun AnalyticsDisplay(
    analyticsData: AnalyticsWidgetData,
    icon: ImageVector,
    primaryColor: Color,
    showMetrics: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Analytics header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${analyticsData.insights.size} insights generated",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                ConfidenceDisplay(
                    confidence = analyticsData.confidence,
                    primaryColor = primaryColor
                )
            }
        }
        
        // Insights
        if (analyticsData.insights.isNotEmpty()) {
            InsightsDisplay(
                insights = analyticsData.insights.take(3),
                primaryColor = primaryColor
            )
        }
        
        // Metrics
        if (showMetrics && analyticsData.metrics.isNotEmpty()) {
            MetricsDisplay(
                metrics = analyticsData.metrics,
                primaryColor = primaryColor
            )
        }
        
        // Recommendations
        if (analyticsData.recommendations.isNotEmpty()) {
            RecommendationsDisplay(
                recommendations = analyticsData.recommendations.take(2),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun RecoveryDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Recovery score
        val recoveryScore = analyticsData.metrics["recovery_score"]?.toFloatOrNull() ?: 0f
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Recovery Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "${(recoveryScore * 100).toInt()}/100",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }
            
            CircularProgressIndicator(
                progress = recoveryScore,
                modifier = Modifier.size(48.dp),
                color = primaryColor,
                strokeWidth = 4.dp
            )
        }
        
        // Recovery insights
        if (analyticsData.insights.isNotEmpty()) {
            InsightsDisplay(
                insights = analyticsData.insights.take(2),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun PerformanceDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Performance metrics grid
        val performanceMetrics = analyticsData.metrics.entries.take(4)
        
        if (performanceMetrics.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(performanceMetrics) { entry ->
                    val (key, value) = entry
                    PerformanceMetricCard(
                        label = key.replace("_", " ").replaceFirstChar { it.uppercase() },
                        value = value,
                        primaryColor = primaryColor
                    )
                }
            }
        }
        
        // Performance insights
        if (analyticsData.insights.isNotEmpty()) {
            InsightsDisplay(
                insights = analyticsData.insights.take(2),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun MuscleGroupDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Convert analytics data to MuscleGroup map for pie chart
        Timber.d("MuscleGroup Debug: Processing ${analyticsData.metrics.size} metrics: ${analyticsData.metrics}")
        
        val muscleGroupData = analyticsData.metrics.entries.take(6)
            .mapNotNull { entry ->
                val (muscleGroupName, percentage) = entry
                val percentageFloat = percentage.toFloatOrNull() ?: return@mapNotNull null
                
                Timber.d("MuscleGroup Debug: Converting '$muscleGroupName' -> $percentageFloat")
                
                // Map muscle group name to MuscleGroup enum
                val muscleGroup = mapStringToMuscleGroup(muscleGroupName)
                if (muscleGroup != null) {
                    Timber.d("MuscleGroup Debug: Mapped '$muscleGroupName' to ${muscleGroup.displayName}")
                } else {
                    Timber.w("MuscleGroup Debug: Failed to map '$muscleGroupName'")
                }
                muscleGroup?.let { it to percentageFloat }
            }
            .toMap()
            
        Timber.d("MuscleGroup Debug: Final pie chart data has ${muscleGroupData.size} entries: $muscleGroupData")
        
        if (muscleGroupData.isNotEmpty()) {
            Timber.d("MuscleGroup Debug: Rendering pie chart with ${muscleGroupData.size} slices")
            // Modern pie chart for muscle group distribution
            MuscleGroupPieChart(
                data = muscleGroupData,
                onSliceClick = { muscleGroup ->
                    // Handle muscle group slice selection for future navigation
                    Timber.d("Selected muscle group: ${muscleGroup.displayName}")
                },
                showPercentages = true,
                showLegend = true,
                animationDuration = 400,
                modifier = Modifier.height(200.dp)
            )
        } else {
            Timber.w("MuscleGroup Debug: No muscle group data available, showing fallback bars")
            // Fallback to bars if data conversion fails
            val muscleGroups = analyticsData.metrics.entries.take(5)
            
            muscleGroups.forEachIndexed { index, entry ->
                val (muscleGroup, percentage) = entry
                key(index) {
                    MuscleGroupBar(
                        label = muscleGroup.replace("_", " ").replaceFirstChar { it.uppercase() },
                        percentage = percentage.toFloatOrNull() ?: 0f,
                        primaryColor = primaryColor
                    )
                }
            }
        }
        
        // Balance insights
        if (analyticsData.insights.isNotEmpty()) {
            val balanceInsight = analyticsData.insights.find { it.category == InsightCategory.PERFORMANCE }
            balanceInsight?.let { insight ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = primaryColor.copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = insight.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Maps string muscle group names to MuscleGroup enum (UI chart version)
 */
private fun mapStringToMuscleGroup(muscleGroupName: String): MuscleGroup? {
    return when (muscleGroupName.lowercase().replace("_", "").replace(" ", "")) {
        "chest" -> MuscleGroup.CHEST
        "back" -> MuscleGroup.BACK
        "shoulders" -> MuscleGroup.SHOULDERS
        "biceps", "triceps", "forearms", "arms" -> MuscleGroup.ARMS
        "abs", "abdominals", "core" -> MuscleGroup.CORE
        "glutes", "quadriceps", "quads", "hamstrings", "calves", "legs" -> MuscleGroup.LEGS
        "traps", "trapezius" -> MuscleGroup.BACK // Map traps to back
        "lats", "latissimus" -> MuscleGroup.BACK // Map lats to back
        "delts", "deltoids" -> MuscleGroup.SHOULDERS // Alias for shoulders
        "cardio", "cardiovascular" -> MuscleGroup.CARDIO
        else -> {
            Timber.w("Unknown muscle group: $muscleGroupName")
            null
        }
    }
}

@Composable
private fun RecoveryPatternsDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    AnalyticsDisplay(
        analyticsData = analyticsData,
        icon = Icons.Default.Bedtime,
        primaryColor = primaryColor,
        showMetrics = true
    )
}

@Composable
private fun IntensityDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Intensity gauge
        val avgIntensity = analyticsData.metrics["avg_intensity"]?.toFloatOrNull() ?: 0f
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = avgIntensity / 10f,
                modifier = Modifier.size(80.dp),
                color = primaryColor,
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${avgIntensity}/10",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                
                Text(
                    text = "Avg RPE",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Intensity insights
        if (analyticsData.insights.isNotEmpty()) {
            InsightsDisplay(
                insights = analyticsData.insights.take(2),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun VarietyDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Variety score
        val varietyScore = analyticsData.metrics["variety_score"]?.toFloatOrNull() ?: 0f
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Variety Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "${(varietyScore * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }
            
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Diversity1,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Variety recommendations
        if (analyticsData.recommendations.isNotEmpty()) {
            RecommendationsDisplay(
                recommendations = analyticsData.recommendations.take(1),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun TimeAnalysisDisplay(
    analyticsData: AnalyticsWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Optimal time
        val optimalTime = analyticsData.metrics["optimal_time"] ?: "Not determined"
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column {
                Text(
                    text = "Optimal Time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Text(
                    text = optimalTime,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }
        }
        
        // Time insights
        if (analyticsData.insights.isNotEmpty()) {
            InsightsDisplay(
                insights = analyticsData.insights.take(2),
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun InsightsDisplay(
    insights: List<Insight>,
    primaryColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        insights.forEach { insight ->
            InsightItem(
                insight = insight,
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun InsightItem(
    insight: Insight,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfidenceIndicator(
                confidence = insight.confidence,
                modifier = Modifier.size(16.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun RecommendationsDisplay(
    recommendations: List<Recommendation>,
    primaryColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Recommendations",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        recommendations.forEach { recommendation ->
            RecommendationItem(
                recommendation = recommendation,
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
private fun RecommendationItem(
    recommendation: Recommendation,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = primaryColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Text(
                text = recommendation.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MetricsDisplay(
    metrics: Map<String, String>,
    primaryColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Key Metrics",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(metrics.entries.take(4).toList()) { (key, value) ->
                MetricCard(
                    label = key.replace("_", " ").replaceFirstChar { it.uppercase() },
                    value = value,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PerformanceMetricCard(
    label: String,
    value: String,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.width(100.dp),
        shape = RoundedCornerShape(8.dp),
        color = primaryColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MuscleGroupBar(
    label: String,
    percentage: Float,
    primaryColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        LinearProgressIndicator(
            progress = (percentage / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = primaryColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun ConfidenceDisplay(
    confidence: Float,
    primaryColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Confidence:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = primaryColor
        )
        
        ConfidenceIndicator(
            confidence = confidence,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.primary  // Persian Green for high confidence
        confidence >= 0.6f -> MaterialTheme.colorScheme.secondary  // Tiffany Blue for medium confidence
        else -> MaterialTheme.colorScheme.error  // Preserve red for low confidence (exception to 5-color rule)
    }
    
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(50))
    )
}