package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.ui.accessibility.AccessibilityUtils
import com.example.liftrix.ui.accessibility.AccessibilityUtils.widgetToggleAccessibilitySemantics
import com.example.liftrix.domain.model.analytics.WidgetDisplaySize
import com.example.liftrix.ui.accessibility.TalkBackAnnouncements
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Widget toggle card component for individual widget controls in settings.
 * 
 * Features:
 * - Toggle switch for widget visibility
 * - Widget preview thumbnail
 * - Complexity indicator with visual cues
 * - Drag handle for reordering (visual indicator)
 * - Accessibility support with content descriptions
 * - Loading and disabled states
 * 
 * @param widget The analytics widget to display controls for
 * @param isEnabled Whether the widget is currently enabled/visible
 * @param isLoading Whether the toggle operation is in progress
 * @param canReorder Whether this widget can be reordered via drag-and-drop
 * @param onToggle Callback when widget toggle state changes
 * @param onReorder Optional callback for drag-and-drop reordering initiation
 * @param modifier Modifier for styling the card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetToggleCard(
    widget: AnalyticsWidget,
    isEnabled: Boolean,
    isLoading: Boolean = false,
    canReorder: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onReorder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Stable callbacks to prevent unnecessary recompositions
    val stableOnToggle = remember(onToggle) { onToggle }
    val stableOnReorder = remember(onReorder) { onReorder }
    
    ElevatedLiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .widgetToggleAccessibilitySemantics(
                widget = widget,
                isEnabled = isEnabled,
                isLoading = isLoading,
                canReorder = canReorder,
                onToggle = { newState ->
                    stableOnToggle(newState)
                },
                onReorder = stableOnReorder
            ),
        contentDescription = "${widget.displayName} widget control"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle with accessibility support
            if (canReorder && stableOnReorder != null) {
                IconButton(
                    onClick = { stableOnReorder() },
                    modifier = Modifier
                        .size(44.dp) // Ensure minimum touch target
                        .alpha(if (isEnabled) 1f else 0.5f)
                        .semantics {
                            contentDescription = "Reorder ${widget.displayName} widget. Use custom actions to move up or down"
                            role = Role.Button
                            if (!isEnabled) {
                                stateDescription = "Widget must be enabled to reorder"
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null, // Handled by parent semantics
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Widget info section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Widget name and complexity indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = widget.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Complexity indicator chip
                    ComplexityChip(
                        complexity = widget.complexity,
                        enabled = isEnabled
                    )
                }
                
                // Widget description
                Text(
                    text = widget.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Update frequency info
                Text(
                    text = "Updates every ${widget.complexity.defaultRefreshIntervalMinutes} minutes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Toggle switch with loading state
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { newState ->
                            stableOnToggle(newState)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "${if (isEnabled) "Disable" else "Enable"} ${widget.displayName}"
                            role = Role.Switch
                            stateDescription = "${widget.displayName} is ${if (isEnabled) "enabled" else "disabled"}"
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

/**
 * Complexity indicator chip component.
 * 
 * @param complexity Widget complexity level
 * @param enabled Whether the parent widget is enabled
 */
@Composable
private fun ComplexityChip(
    complexity: WidgetComplexity,
    enabled: Boolean = true
) {
    val (chipColor, textColor, icon) = when (complexity) {
        WidgetComplexity.SIMPLE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Circle
        )
        WidgetComplexity.MODERATE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.RadioButtonChecked
        )
        WidgetComplexity.COMPLEX -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Star
        )
    }
    
    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.6f)
            .widthIn(min = 32.dp),
        shape = RoundedCornerShape(12.dp),
        color = chipColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = when (complexity) {
                    WidgetComplexity.SIMPLE -> "S"
                    WidgetComplexity.MODERATE -> "M"
                    WidgetComplexity.COMPLEX -> "C"
                },
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Widget preview thumbnail component.
 * 
 * @param widget The analytics widget to preview
 * @param size Display size for the preview
 * @param enabled Whether the widget is enabled
 */
@Composable
fun WidgetPreviewThumbnail(
    widget: AnalyticsWidget,
    size: WidgetDisplaySize = WidgetDisplaySize.STANDARD,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val previewIcon = when (widget) {
        AnalyticsWidget.WorkoutFrequency -> Icons.Default.Timeline
        AnalyticsWidget.TotalVolume -> Icons.Default.FitnessCenter
        AnalyticsWidget.VolumeCalendar -> Icons.Default.CalendarMonth
        AnalyticsWidget.StrengthProgress -> Icons.Default.TrendingUp
        AnalyticsWidget.VolumeChart -> Icons.Default.BarChart
        AnalyticsWidget.FrequencyChart -> Icons.Default.Analytics
        AnalyticsWidget.WorkoutStreak -> Icons.Default.LocalFireDepartment
        AnalyticsWidget.PersonalRecords -> Icons.Default.EmojiEvents
        AnalyticsWidget.VolumeTrends -> Icons.Default.Analytics
        AnalyticsWidget.RecoveryMetrics -> Icons.Default.Healing
        AnalyticsWidget.AverageDuration -> Icons.Default.Timer
        AnalyticsWidget.VolumeLoadProgression -> Icons.Default.ShowChart
        AnalyticsWidget.ProgressChart -> Icons.Default.BarChart
        AnalyticsWidget.OneRMProgression -> Icons.Default.Equalizer
        AnalyticsWidget.MuscleGroupDistribution -> Icons.Default.Category
        AnalyticsWidget.MonthlySummary -> Icons.Default.DateRange
    }
    
    Surface(
        modifier = modifier
            .size(
                width = 48.dp,
                height = (48.dp * size.heightMultiplier).coerceAtLeast(32.dp)
            )
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = null
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = previewIcon,
                contentDescription = "${widget.displayName} preview",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Preview for WidgetToggleCard component
 */
@Preview(showBackground = true)
@Composable
private fun WidgetToggleCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WidgetToggleCard(
                widget = AnalyticsWidget.TotalVolume,
                isEnabled = true,
                onToggle = { },
                onReorder = { }
            )
            
            WidgetToggleCard(
                widget = AnalyticsWidget.MonthlySummary,
                isEnabled = false,
                onToggle = { },
                onReorder = { }
            )
            
            WidgetToggleCard(
                widget = AnalyticsWidget.AverageDuration,
                isEnabled = true,
                isLoading = true,
                onToggle = { }
            )
        }
    }
}

/**
 * Preview for WidgetPreviewThumbnail component
 */
@Preview(showBackground = true)
@Composable
private fun WidgetPreviewThumbnailPreview() {
    LiftrixTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WidgetPreviewThumbnail(
                widget = AnalyticsWidget.TotalVolume,
                size = WidgetDisplaySize.COMPACT
            )
            WidgetPreviewThumbnail(
                widget = AnalyticsWidget.StrengthProgress,
                size = WidgetDisplaySize.STANDARD
            )
            WidgetPreviewThumbnail(
                widget = AnalyticsWidget.MonthlySummary,
                size = WidgetDisplaySize.EXPANDED
            )
        }
    }
}