package com.example.liftrix.ui.progress.components

import com.example.liftrix.domain.model.analytics.DateRange

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.domain.model.analytics.TimeRangeType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaLocalDate

/**
 * GlobalTimeRangeSelector - Material 3 segmented control for time range selection
 *
 * Features:
 * - Smooth animated transitions between selections (300ms)
 * - Haptic feedback on selection change
 * - Persian Green/Tiffany Blue theming with Material 3 compliance
 * - Mobile-optimized touch targets (44dp minimum height)
 * - Accessibility support with proper semantics and roles
 * - State management integration for chart synchronization
 * - Responsive design adapting to container width
 */
@Composable
fun GlobalTimeRangeSelector(
    selectedTimeRange: TimeRangeType,
    onTimeRangeChange: (TimeRangeType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animationDuration: Int = 300
) {
    val haptic = LocalHapticFeedback.current
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // Material 3 minimum touch target
            .semantics {
                contentDescription = "Time range selector. Currently selected: ${selectedTimeRange.displayName}"
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimeRangeType.values().forEach { timeRange ->
                TimeRangeButton(
                    timeRange = timeRange,
                    isSelected = timeRange == selectedTimeRange,
                    onClick = {
                        if (enabled && timeRange != selectedTimeRange) {
                            onTimeRangeChange(timeRange)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    enabled = enabled,
                    animationDuration = animationDuration,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual time range button with smooth animations
 */
@Composable
private fun TimeRangeButton(
    timeRange: TimeRangeType,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    animationDuration: Int,
    modifier: Modifier = Modifier
) {
    // Animated background color
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            LiftrixColors.PersianGreen
        } else {
            Color.Transparent
        },
        animationSpec = tween(animationDuration),
        label = "background_color_animation"
    )
    
    // Animated text color
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            LiftrixColors.Snow
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(animationDuration),
        label = "text_color_animation"
    )
    
    // Animated corner radius for smooth morphing effect
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 16.dp,
        animationSpec = tween(animationDuration),
        label = "corner_radius_animation"
    )
    
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .clickable(enabled = enabled) { onClick() }
            .semantics {
                contentDescription = "${timeRange.displayName} time range${if (isSelected) ", selected" else ""}"
                role = Role.RadioButton
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeRange.getShortDisplayName(),
            color = textColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Compact version for smaller screens or horizontal layouts
 */
@Composable
fun CompactTimeRangeSelector(
    selectedTimeRange: TimeRangeType,
    onTimeRangeChange: (TimeRangeType) -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 3
) {
    val haptic = LocalHapticFeedback.current
    val visibleRanges = remember(maxItems) {
        TimeRangeType.values().take(maxItems)
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleRanges.forEach { timeRange ->
            val isSelected = timeRange == selectedTimeRange
            
            Surface(
                onClick = {
                    if (timeRange != selectedTimeRange) {
                        onTimeRangeChange(timeRange)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                modifier = Modifier
                    .height(32.dp)
                    .semantics {
                        contentDescription = "${timeRange.displayName}${if (isSelected) ", selected" else ""}"
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    LiftrixColors.PersianGreen
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = timeRange.getShortDisplayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            LiftrixColors.Snow
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// TimeRange enum removed - using consolidated TimeRangeType from domain layer
// Import: import com.example.liftrix.domain.model.analytics.TimeRangeType

/**
 * Time range state management for chart synchronization
 */
class TimeRangeState(
    initialTimeRange: TimeRangeType = TimeRangeType.MONTH
) {
    private var _selectedTimeRange by mutableStateOf(initialTimeRange)
    
    val selectedTimeRange: TimeRangeType get() = _selectedTimeRange
    
    fun selectTimeRange(timeRange: TimeRangeType) {
        _selectedTimeRange = timeRange
    }
    
    fun getDateRange(referenceDate: LocalDate? = null): DateRange {
        val reference = referenceDate ?: getCurrentDate()
        val endDate = reference
        val startDate = reference.minus(DatePeriod(days = _selectedTimeRange.durationInDays))
        return DateRange(
            start = startDate,
            end = endDate
        )
    }
    
    private fun getCurrentDate(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
}


/**
 * Extension functions for TimeRangeType integration
 */

/**
 * Remember time range state with automatic persistence
 */
@Composable
fun rememberTimeRangeState(
    initialTimeRange: TimeRangeType = TimeRangeType.MONTH
): TimeRangeState {
    return remember { TimeRangeState(initialTimeRange) }
}

/**
 * Get appropriate time range for data visualization based on data density
 */
fun suggestOptimalTimeRange(dataPointCount: Int): TimeRangeType {
    return when {
        dataPointCount < 30 -> TimeRangeType.MONTH
        dataPointCount < 90 -> TimeRangeType.QUARTER
        else -> TimeRangeType.YEAR
    }
}

@Preview(showBackground = true)
@Composable
private fun GlobalTimeRangeSelectorPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Standard selector
            GlobalTimeRangeSelector(
                selectedTimeRange = TimeRangeType.MONTH,
                onTimeRangeChange = { }
            )
            
            // Compact selector
            CompactTimeRangeSelector(
                selectedTimeRange = TimeRangeType.WEEK,
                onTimeRangeChange = { },
                maxItems = 3
            )
        }
    }
}