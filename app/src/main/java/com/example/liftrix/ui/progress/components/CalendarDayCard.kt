package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.ui.theme.LiftrixColors
import kotlinx.datetime.LocalDate

/**
 * Calendar day card component for volume calendar display
 *
 * Displays a single day in the calendar grid with volume-based color intensity,
 * proper accessibility support, and Material 3 theming. Features 48dp minimum
 * touch target size and TalkBack content descriptions.
 *
 * Color intensity visualization:
 * - No volume: Transparent/minimal color
 * - Low volume: Light color opacity
 * - High volume: Full color intensity using LiftrixColors.Primary
 *
 * @param date The date this card represents
 * @param volume The workout volume for this date
 * @param intensity Volume intensity from 0.0 to 1.0 for color coding
 * @param isCurrentMonth Whether this date is in the current displayed month
 * @param onClick Callback invoked when the card is clicked
 */
@Composable
fun CalendarDayCard(
    date: LocalDate,
    volume: Weight,
    intensity: Float,
    isCurrentMonth: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp) // Minimum touch target size for accessibility
            .clip(RoundedCornerShape(8.dp))
            .background(getVolumeColor(intensity, isCurrentMonth))
            .clickable { onClick() }
            .semantics {
                contentDescription = buildContentDescription(date, volume, isCurrentMonth)
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = getDayTextColor(intensity, isCurrentMonth)
        )
    }
}

/**
 * Calculates background color based on volume intensity and month context
 *
 * Uses LiftrixColors.Primary gradient system with alpha variations to represent
 * workout volume intensity. Non-current month days are displayed with reduced
 * opacity for visual hierarchy.
 *
 * @param intensity Volume intensity from 0.0 to 1.0
 * @param isCurrentMonth Whether this date is in the current displayed month
 * @return Color for the calendar day background
 */
@Composable
fun getVolumeColor(intensity: Float, isCurrentMonth: Boolean): Color {
    return if (isCurrentMonth) {
        if (intensity > 0.0f) {
            // Use LiftrixColors.Primary with intensity-based alpha
            LiftrixColors.Primary.copy(alpha = 0.1f + (intensity * 0.8f))
        } else {
            // No volume - use subtle surface color
            MaterialTheme.colorScheme.surface
        }
    } else {
        // Non-current month - minimal visual weight
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f)
    }
}

/**
 * Determines text color based on background intensity and month context
 *
 * Ensures proper contrast ratios for accessibility compliance while maintaining
 * visual hierarchy between current and non-current month days.
 *
 * @param intensity Volume intensity from 0.0 to 1.0
 * @param isCurrentMonth Whether this date is in the current displayed month
 * @return Color for the day number text
 */
@Composable
private fun getDayTextColor(intensity: Float, isCurrentMonth: Boolean): Color {
    return if (isCurrentMonth) {
        if (intensity > 0.6f) {
            // High intensity background - use contrasting text
            MaterialTheme.colorScheme.onPrimary
        } else {
            // Low intensity or no volume - use standard text
            MaterialTheme.colorScheme.onSurface
        }
    } else {
        // Non-current month - reduced opacity
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
}

/**
 * Builds accessibility content description for screen readers
 *
 * Provides comprehensive information about the calendar day including date,
 * volume data, and context for users with visual impairments.
 *
 * @param date The date for this calendar day
 * @param volume The workout volume for this date
 * @param isCurrentMonth Whether this date is in the current displayed month
 * @return Descriptive text for screen readers
 */
private fun buildContentDescription(
    date: LocalDate,
    volume: Weight,
    isCurrentMonth: Boolean
): String {
    val monthContext = if (isCurrentMonth) "" else " (previous/next month)"
    val volumeText = if (volume.kilograms > 0.0) {
        "Volume: ${volume.kilograms}kg"
    } else {
        "No workout data"
    }
    
    return "Day ${date.dayOfMonth}$monthContext. $volumeText. Tap for details."
}