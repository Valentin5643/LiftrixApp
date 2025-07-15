package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Volume calendar navigation component for month selection
 *
 * Provides navigation controls for moving between months in the volume calendar.
 * Features Material 3 theming, accessibility support, and smooth transition animations.
 *
 * Navigation includes:
 * - Previous month button with chevron left icon
 * - Current month/year display with formatted text
 * - Next month button with chevron right icon
 * - Accessibility content descriptions for screen readers
 *
 * @param currentMonth The currently displayed month and year
 * @param onMonthChange Callback invoked when month navigation is requested
 */
@Composable
fun VolumeCalendarNavigation(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous month navigation
        IconButton(
            onClick = { onMonthChange(currentMonth.minusMonths(1)) },
            modifier = Modifier.semantics {
                contentDescription = "Go to previous month: ${getPreviousMonthDescription(currentMonth)}"
            }
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null, // Handled by button semantics
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Current month and year display
        Text(
            text = formatMonthYear(currentMonth),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                contentDescription = "Currently viewing ${formatMonthYearForAccessibility(currentMonth)}"
            }
        )
        
        // Next month navigation
        IconButton(
            onClick = { onMonthChange(currentMonth.plusMonths(1)) },
            modifier = Modifier.semantics {
                contentDescription = "Go to next month: ${getNextMonthDescription(currentMonth)}"
            }
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null, // Handled by button semantics
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Formats month and year for display
 *
 * Uses localized month names and four-digit year format for clear readability.
 *
 * @param yearMonth The month and year to format
 * @return Formatted string (e.g., "January 2024")
 */
private fun formatMonthYear(yearMonth: YearMonth): String {
    return yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}

/**
 * Formats month and year for accessibility content descriptions
 *
 * Provides full month name and year information for screen readers.
 *
 * @param yearMonth The month and year to format
 * @return Accessibility-friendly formatted string
 */
private fun formatMonthYearForAccessibility(yearMonth: YearMonth): String {
    return yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}

/**
 * Gets previous month description for accessibility
 *
 * @param currentMonth The current month context
 * @return Description of the previous month
 */
private fun getPreviousMonthDescription(currentMonth: YearMonth): String {
    val previousMonth = currentMonth.minusMonths(1)
    return formatMonthYearForAccessibility(previousMonth)
}

/**
 * Gets next month description for accessibility
 *
 * @param currentMonth The current month context
 * @return Description of the next month
 */
private fun getNextMonthDescription(currentMonth: YearMonth): String {
    val nextMonth = currentMonth.plusMonths(1)
    return formatMonthYearForAccessibility(nextMonth)
}