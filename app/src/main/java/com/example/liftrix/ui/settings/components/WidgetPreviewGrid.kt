package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Widget preview grid component for showing dashboard layout preview
 * 
 * Features:
 * - Adaptive grid layout based on dashboard layout mode
 * - Mini widget previews with live preview data
 * - Interactive preview items with click handling
 * - Layout mode responsive design (1-3 columns)
 * - Performance optimized with proper key management
 * - Accessibility support with semantic descriptions
 * - Empty state handling for no widgets
 * 
 * @param widgets List of widgets to preview in grid
 * @param layoutMode Current dashboard layout mode affecting grid structure
 * @param onWidgetClick Optional callback when preview widget is clicked
 * @param modifier Modifier for styling the grid
 */
@Composable
fun WidgetPreviewGrid(
    widgets: List<AnalyticsWidget>,
    layoutMode: DashboardLayoutMode,
    onWidgetClick: ((AnalyticsWidget) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (widgets.isEmpty()) {
        PreviewEmptyState(
            modifier = modifier
        )
        return
    }
    
    // Calculate grid columns based on layout mode
    val columns = when (layoutMode) {
        DashboardLayoutMode.GRID -> 3
        DashboardLayoutMode.SECTIONS -> 2
        DashboardLayoutMode.LIST -> 1
        DashboardLayoutMode.CUSTOM -> 3
    }
    
    // Stable callback
    val stableOnWidgetClick = remember(onWidgetClick) { onWidgetClick }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Dashboard preview grid showing ${widgets.size} widgets in ${layoutMode.displayName} layout"
            },
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = widgets,
            key = { widget -> widget.id }
        ) { widget ->
            PreviewWidgetCard(
                widget = widget,
                layoutMode = layoutMode,
                onClick = stableOnWidgetClick?.let { { it(widget) } }
            )
        }
    }
}

/**
 * Individual preview widget card component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewWidgetCard(
    widget: AnalyticsWidget,
    layoutMode: DashboardLayoutMode,
    onClick: (() -> Unit)? = null
) {
    // Calculate card height based on layout mode
    val cardHeight = when (layoutMode) {
        DashboardLayoutMode.GRID -> 80.dp
        DashboardLayoutMode.SECTIONS -> 90.dp
        DashboardLayoutMode.LIST -> 60.dp
        DashboardLayoutMode.CUSTOM -> 85.dp
    }
    
    val stableOnClick = remember(onClick) { onClick }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .semantics {
                contentDescription = "${widget.displayName} widget preview. Category: ${widget.category.displayName}"
            },
        onClick = stableOnClick ?: {},
        enabled = stableOnClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = if (stableOnClick != null) 4.dp else 2.dp
        )
    ) {
        when (layoutMode) {
            DashboardLayoutMode.GRID -> GridPreviewContent(widget)
            DashboardLayoutMode.SECTIONS -> SectionPreviewContent(widget)
            DashboardLayoutMode.LIST -> ListPreviewContent(widget)
            DashboardLayoutMode.CUSTOM -> CustomPreviewContent(widget)
        }
    }
}

/**
 * Grid layout preview content
 */
@Composable
private fun GridPreviewContent(widget: AnalyticsWidget) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Widget icon
        Icon(
            imageVector = widget.getPreviewIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        // Widget name (truncated for grid)
        Text(
            text = widget.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Complexity indicator
        ComplexityIndicatorDot(complexity = widget.complexity)
    }
}

/**
 * Section layout preview content
 */
@Composable
private fun SectionPreviewContent(widget: AnalyticsWidget) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Widget icon
        Icon(
            imageVector = widget.getPreviewIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        
        // Widget info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = widget.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = widget.category.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Sample preview data
            Text(
                text = generatePreviewData(widget),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * List layout preview content
 */
@Composable
private fun ListPreviewContent(widget: AnalyticsWidget) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Widget icon
        Icon(
            imageVector = widget.getPreviewIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        // Widget info in row
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = widget.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Preview value
        Text(
            text = generatePreviewData(widget),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End
        )
        
        // Complexity indicator
        ComplexityIndicatorDot(complexity = widget.complexity)
    }
}

/**
 * Custom layout preview content
 */
@Composable
private fun CustomPreviewContent(widget: AnalyticsWidget) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header with icon and complexity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = widget.getPreviewIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            ComplexityIndicatorDot(complexity = widget.complexity)
        }
        
        // Widget name and preview data
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = widget.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = generatePreviewData(widget),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Complexity indicator dot
 */
@Composable
private fun ComplexityIndicatorDot(complexity: WidgetComplexity) {
    val (color, size) = when (complexity) {
        WidgetComplexity.SIMPLE -> MaterialTheme.colorScheme.onSurfaceVariant to 6.dp
        WidgetComplexity.MODERATE -> MaterialTheme.colorScheme.primary to 8.dp
        WidgetComplexity.COMPLEX -> MaterialTheme.colorScheme.error to 10.dp
    }
    
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(50),
        color = color
    ) {}
}

/**
 * Empty state for preview grid
 */
@Composable
private fun PreviewEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ViewModule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp)
        )
        
        Text(
            text = "No Active Widgets",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Enable some widgets to see the preview",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Extension function to get preview icon for widget
 */
private fun AnalyticsWidget.getPreviewIcon(): ImageVector {
    return when (this) {
        AnalyticsWidget.WorkoutFrequency -> Icons.Default.Timeline
        AnalyticsWidget.TotalVolume -> Icons.Default.FitnessCenter
        AnalyticsWidget.VolumeCalendar -> Icons.Default.CalendarMonth
        AnalyticsWidget.StrengthProgress -> Icons.Default.TrendingUp
        // ConsistencyStreak renamed to WorkoutStreak
        AnalyticsWidget.VolumeChart -> Icons.Default.BarChart
        // DurationChart renamed to ProgressChart
        AnalyticsWidget.FrequencyChart -> Icons.Default.Analytics
        AnalyticsWidget.WorkoutStreak -> Icons.Default.LocalFireDepartment
        AnalyticsWidget.PersonalRecords -> Icons.Default.EmojiEvents
        AnalyticsWidget.VolumeTrends -> Icons.Default.Analytics
        AnalyticsWidget.RecoveryMetrics -> Icons.Default.Healing
        // PerformanceAnalysis renamed to MonthlySummary
        // CaloriesBurned widget removed
        // DailyCalories widget removed
        // WeeklyCalorieTrend widget removed
        AnalyticsWidget.AverageDuration -> Icons.Default.Timer
        AnalyticsWidget.VolumeLoadProgression -> Icons.Default.ShowChart
        AnalyticsWidget.ProgressChart -> Icons.Default.BarChart
        AnalyticsWidget.OneRMProgression -> Icons.Default.Equalizer
        // WeeklyTrends renamed to VolumeTrends
        AnalyticsWidget.MuscleGroupDistribution -> Icons.Default.Category
        // RecoveryPatterns renamed to RecoveryMetrics
        // TrainingIntensity widget removed
        // ExerciseVariety widget removed
        // TimeOfDayAnalysis widget removed
        // SetCompletionRate widget removed
        AnalyticsWidget.MonthlySummary -> Icons.Default.CalendarViewMonth
        // GoalAchievement widget removed
    }
}

/**
 * Generate sample preview data for widgets
 */
private fun generatePreviewData(widget: AnalyticsWidget): String {
    return when (widget) {
        AnalyticsWidget.WorkoutFrequency -> "4.2/week"
        AnalyticsWidget.TotalVolume -> "12.5K lbs"
        AnalyticsWidget.VolumeCalendar -> "Jan 2025"
        AnalyticsWidget.StrengthProgress -> "+8.2%"
        // ConsistencyStreak renamed to WorkoutStreak
        AnalyticsWidget.VolumeChart -> "Trending ↗"
        // DurationChart renamed to ProgressChart
        AnalyticsWidget.FrequencyChart -> "Weekly view"
        AnalyticsWidget.WorkoutStreak -> "7 days"
        AnalyticsWidget.PersonalRecords -> "3 new PRs"
        AnalyticsWidget.VolumeTrends -> "↗ +15%"
        AnalyticsWidget.RecoveryMetrics -> "Good"
        // PerformanceAnalysis renamed to MonthlySummary
        // CaloriesBurned widget removed
        // DailyCalories widget removed
        // WeeklyCalorieTrend widget removed
        AnalyticsWidget.AverageDuration -> "42 min"
        AnalyticsWidget.VolumeLoadProgression -> "Progressive"
        AnalyticsWidget.ProgressChart -> "All-time"
        AnalyticsWidget.OneRMProgression -> "225 lbs"
        // WeeklyTrends renamed to VolumeTrends
        AnalyticsWidget.MuscleGroupDistribution -> "Balanced"
        // RecoveryPatterns renamed to RecoveryMetrics
        // TrainingIntensity widget removed
        // ExerciseVariety widget removed
        // TimeOfDayAnalysis widget removed
        // SetCompletionRate widget removed
        AnalyticsWidget.MonthlySummary -> "Jan recap"
        // GoalAchievement widget removed
    }
}

/**
 * Preview for WidgetPreviewGrid
 */
@Preview(showBackground = true)
@Composable
private fun WidgetPreviewGridPreview() {
    LiftrixTheme {
        val sampleWidgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.WorkoutStreak,
            AnalyticsWidget.AverageDuration,
            AnalyticsWidget.StrengthProgress,
            AnalyticsWidget.VolumeChart
        )
        
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Grid Layout",
                style = MaterialTheme.typography.titleMedium
            )
            
            WidgetPreviewGrid(
                widgets = sampleWidgets,
                layoutMode = DashboardLayoutMode.GRID,
                onWidgetClick = { }
            )
            
            Text(
                text = "Section Layout",
                style = MaterialTheme.typography.titleMedium
            )
            
            WidgetPreviewGrid(
                widgets = sampleWidgets.take(3),
                layoutMode = DashboardLayoutMode.SECTIONS,
                onWidgetClick = { }
            )
        }
    }
}