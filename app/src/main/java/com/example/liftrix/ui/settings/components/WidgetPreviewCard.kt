package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.analytics.WidgetDisplaySize
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Widget preview card component showing detailed widget thumbnails and information.
 * 
 * Features:
 * - Realistic widget preview with mock data
 * - Size adjustment controls
 * - Complexity and category indicators
 * - Interactive preview with hover states
 * - Accessibility support
 * 
 * @param widget The analytics widget to preview
 * @param currentSize Current display size setting
 * @param isSelected Whether this widget is currently selected
 * @param onSizeChange Callback when size adjustment is requested
 * @param onPreviewClick Optional callback when preview is clicked
 * @param modifier Modifier for styling the card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPreviewCard(
    widget: AnalyticsWidget,
    currentSize: WidgetDisplaySize = WidgetDisplaySize.STANDARD,
    isSelected: Boolean = false,
    onSizeChange: ((WidgetDisplaySize) -> Unit)? = null,
    onPreviewClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Stable callbacks to prevent unnecessary recompositions
    val stableOnSizeChange = remember(onSizeChange) { onSizeChange }
    val stableOnPreviewClick = remember(onPreviewClick) { onPreviewClick }
    
    ElevatedLiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${widget.displayName} widget preview. Current size: ${currentSize.displayName}. ${widget.description}"
            },
        contentDescription = "${widget.displayName} widget preview",
        onClick = stableOnPreviewClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with widget info and size controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Widget info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = widget.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryChip(widget = widget)
                        ComplexityIndicator(complexity = widget.complexity)
                    }
                }
                
                // Size adjustment controls
                if (stableOnSizeChange != null) {
                    SizeAdjustmentControls(
                        currentSize = currentSize,
                        onSizeChange = stableOnSizeChange
                    )
                }
            }
            
            // Widget preview area
            WidgetPreviewArea(
                widget = widget,
                size = currentSize,
                isSelected = isSelected
            )
            
            // Widget description
            Text(
                text = widget.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Category chip for widget categorization.
 */
@Composable
private fun CategoryChip(widget: AnalyticsWidget) {
    val categoryColor = when (widget.category) {
        com.example.liftrix.domain.model.analytics.WidgetCategory.METRICS -> MaterialTheme.colorScheme.primaryContainer
        com.example.liftrix.domain.model.analytics.WidgetCategory.CHARTS -> MaterialTheme.colorScheme.secondaryContainer
        com.example.liftrix.domain.model.analytics.WidgetCategory.PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer
        com.example.liftrix.domain.model.analytics.WidgetCategory.ANALYTICS -> MaterialTheme.colorScheme.errorContainer
    }
    
    val textColor = when (widget.category) {
        com.example.liftrix.domain.model.analytics.WidgetCategory.METRICS -> MaterialTheme.colorScheme.onPrimaryContainer
        com.example.liftrix.domain.model.analytics.WidgetCategory.CHARTS -> MaterialTheme.colorScheme.onSecondaryContainer
        com.example.liftrix.domain.model.analytics.WidgetCategory.PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
        com.example.liftrix.domain.model.analytics.WidgetCategory.ANALYTICS -> MaterialTheme.colorScheme.onErrorContainer
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = categoryColor
    ) {
        Text(
            text = widget.category.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Complexity indicator with icon.
 */
@Composable
private fun ComplexityIndicator(complexity: WidgetComplexity) {
    val (icon, color) = when (complexity) {
        WidgetComplexity.SIMPLE -> Icons.Default.Circle to MaterialTheme.colorScheme.onSurfaceVariant
        WidgetComplexity.MODERATE -> Icons.Default.RadioButtonChecked to MaterialTheme.colorScheme.primary
        WidgetComplexity.COMPLEX -> Icons.Default.Star to MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = Modifier.widthIn(min = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "${complexity.displayName} complexity",
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = when (complexity) {
                WidgetComplexity.SIMPLE -> "S"
                WidgetComplexity.MODERATE -> "M"
                WidgetComplexity.COMPLEX -> "C"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Size adjustment controls component.
 */
@Composable
private fun SizeAdjustmentControls(
    currentSize: WidgetDisplaySize,
    onSizeChange: (WidgetDisplaySize) -> Unit
) {
    val sizes = WidgetDisplaySize.values()
    val stableOnSizeChange = remember(onSizeChange) { onSizeChange }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sizes.forEach { size ->
            val isSelected = size == currentSize
            
            FilterChip(
                selected = isSelected,
                onClick = { stableOnSizeChange(size) },
                label = {
                    Text(
                        text = size.displayName.first().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Widget preview area with mock data visualization.
 */
@Composable
private fun WidgetPreviewArea(
    widget: AnalyticsWidget,
    size: WidgetDisplaySize,
    isSelected: Boolean
) {
    val previewHeight = when (size) {
        WidgetDisplaySize.COMPACT -> 60.dp
        WidgetDisplaySize.STANDARD -> 80.dp
        WidgetDisplaySize.EXPANDED -> 120.dp
    }
    
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewHeight)
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        when (widget) {
            AnalyticsWidget.TotalVolume -> {
                MetricPreview(
                    title = "Total Volume",
                    value = "12.5K",
                    subtitle = "lbs this month",
                    icon = Icons.Default.FitnessCenter,
                    trend = "+8.2%"
                )
            }
            
            AnalyticsWidget.WorkoutFrequency -> {
                MetricPreview(
                    title = "Frequency",
                    value = "4.2",
                    subtitle = "workouts/week",
                    icon = Icons.Default.Timeline,
                    trend = "+0.3"
                )
            }
            
            AnalyticsWidget.ConsistencyStreak -> {
                MetricPreview(
                    title = "Streak",
                    value = "12",
                    subtitle = "days",
                    icon = Icons.Default.LocalFireDepartment,
                    trend = "Personal Best!"
                )
            }
            
            AnalyticsWidget.StrengthProgress -> {
                ChartPreview(
                    title = "Strength",
                    subtitle = "1RM Progress",
                    icon = Icons.Default.TrendingUp
                )
            }
            
            AnalyticsWidget.CaloriesBurned -> {
                MetricPreview(
                    title = "Calories",
                    value = "2,450",
                    subtitle = "burned today",
                    icon = Icons.Default.LocalFireDepartment,
                    trend = "85% of goal"
                )
            }
            
            AnalyticsWidget.DailyCalories -> {
                MetricPreview(
                    title = "Today",
                    value = "850",
                    subtitle = "calories burned",
                    icon = Icons.Default.Today,
                    trend = "vs 720 yesterday"
                )
            }
            
            AnalyticsWidget.VolumeCalendar -> {
                CalendarPreview()
            }
            
            else -> {
                // Generic preview for other widgets
                MetricPreview(
                    title = widget.displayName.take(8),
                    value = "---",
                    subtitle = "sample data",
                    icon = Icons.Default.Analytics
                )
            }
        }
    }
}

/**
 * Metric-style widget preview.
 */
@Composable
private fun MetricPreview(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    trend: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trend != null) {
                Text(
                    text = trend,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Chart-style widget preview.
 */
@Composable
private fun ChartPreview(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Mock chart visualization
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(8) { index ->
                val height = listOf(12, 8, 16, 14, 20, 18, 24, 22)[index]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(height.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                        )
                )
            }
        }
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Calendar-style widget preview.
 */
@Composable
private fun CalendarPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "January 2025",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Mock calendar grid
        repeat(3) { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(7) { day ->
                    val hasWorkout = (week * 7 + day) % 3 == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                color = if (hasWorkout) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Preview for WidgetPreviewCard component
 */
@Preview(showBackground = true)
@Composable
private fun WidgetPreviewCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WidgetPreviewCard(
                widget = AnalyticsWidget.TotalVolume,
                currentSize = WidgetDisplaySize.STANDARD,
                onSizeChange = { },
                onPreviewClick = { }
            )
            
            WidgetPreviewCard(
                widget = AnalyticsWidget.CaloriesBurned,
                currentSize = WidgetDisplaySize.COMPACT,
                isSelected = true,
                onSizeChange = { }
            )
            
            WidgetPreviewCard(
                widget = AnalyticsWidget.VolumeCalendar,
                currentSize = WidgetDisplaySize.EXPANDED,
                onSizeChange = { }
            )
        }
    }
}