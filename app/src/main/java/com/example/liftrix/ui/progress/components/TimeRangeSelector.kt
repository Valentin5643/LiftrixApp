package com.example.liftrix.ui.progress.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * TimeRangeSelector - Compact Material 3 time range selector component
 * 
 * Features:
 * - Simplified version of GlobalTimeRangeSelector for detail screens
 * - Smooth animated transitions (300ms)
 * - Haptic feedback on selection
 * - Material 3 compliant design
 * - Accessible with proper semantics
 * - Optimized for mobile touch targets
 * 
 * @param selectedTimeRange Currently selected time range
 * @param onTimeRangeChange Callback when time range changes
 * @param modifier Optional modifier for the component
 * @param enabled Whether the selector is enabled
 */
@Composable
fun TimeRangeSelector(
    selectedTimeRange: TimeRangeType,
    onTimeRangeChange: (TimeRangeType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = "Time range selector. Currently selected: ${selectedTimeRange.displayName}"
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimeRangeType.values().forEach { timeRange ->
                TimeRangeOption(
                    timeRange = timeRange,
                    isSelected = timeRange == selectedTimeRange,
                    enabled = enabled,
                    onClick = {
                        if (enabled && timeRange != selectedTimeRange) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTimeRangeChange(timeRange)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual time range option button
 */
@Composable
private fun TimeRangeOption(
    timeRange: TimeRangeType,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            LiftrixColors.Primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(300),
        label = "TimeRangeBackgroundColor"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "TimeRangeTextColor"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(300),
        label = "TimeRangeElevation"
    )
    
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${timeRange.getDisplayLabel()} time range option"
                role = Role.RadioButton
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeRange.getDisplayLabel(),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/**
 * Extension function to get display label for TimeRangeType
 */
private fun TimeRangeType.getDisplayLabel(): String {
    return when (this) {
        TimeRangeType.MONTH -> "1M"
        TimeRangeType.SIX_MONTHS -> "6M"
        TimeRangeType.ALL_TIME -> "All"
    }
}

@Preview(showBackground = true)
@Composable
private fun TimeRangeSelectorPreview() {
    LiftrixTheme {
        TimeRangeSelector(
            selectedTimeRange = TimeRangeType.MONTH,
            onTimeRangeChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "TimeRangeSelector - Six Months Selected")
@Composable
private fun TimeRangeSelectorSixMonthsPreview() {
    LiftrixTheme {
        TimeRangeSelector(
            selectedTimeRange = TimeRangeType.SIX_MONTHS,
            onTimeRangeChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "TimeRangeSelector - Disabled")
@Composable
private fun TimeRangeSelectorDisabledPreview() {
    LiftrixTheme {
        TimeRangeSelector(
            selectedTimeRange = TimeRangeType.SIX_MONTHS,
            onTimeRangeChange = {},
            enabled = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}