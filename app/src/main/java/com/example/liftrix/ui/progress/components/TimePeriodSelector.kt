package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType

/**
 * Time period selector component for filtering progress dashboard data.
 * 
 * Displays selectable filter chips for different time periods (Week, Month, Quarter, Year).
 * Uses Material 3 FilterChip components with proper accessibility support and theming.
 * Integrates with the progress dashboard's MVI pattern for state management.
 * 
 * @param selectedPeriod Currently selected time range
 * @param onPeriodSelected Callback when a time period is selected
 * @param modifier Modifier for styling the selector container
 */
@Composable
fun TimePeriodSelector(
    selectedPeriod: TimeRange,
    onPeriodSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
                .semantics {
                    contentDescription = "Time period selector for progress data"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Period:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            TimeRangeType.getDashboardTypes().forEach { rangeType ->
                val timeRange = when (rangeType) {
                    TimeRangeType.WEEK -> TimeRange.lastWeek()
                    TimeRangeType.MONTH -> TimeRange.lastMonth()
                    TimeRangeType.QUARTER -> TimeRange.lastQuarter()
                    TimeRangeType.THREE_MONTHS -> TimeRange.lastQuarter() // Same as quarter
                    TimeRangeType.SIX_MONTHS -> TimeRange.lastSixMonths()
                    TimeRangeType.YEAR -> TimeRange.lastYear()
                    TimeRangeType.ALL_TIME -> TimeRange.allTime()
                }
                
                FilterChip(
                    selected = selectedPeriod.type == rangeType,
                    onClick = { onPeriodSelected(timeRange) },
                    label = {
                        Text(
                            text = rangeType.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(60.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = if (selectedPeriod.type == rangeType) {
                            "Selected time period: ${rangeType.displayName}"
                        } else {
                            "Select time period: ${rangeType.displayName}"
                        }
                    }
                )
            }
        }
    }
} 