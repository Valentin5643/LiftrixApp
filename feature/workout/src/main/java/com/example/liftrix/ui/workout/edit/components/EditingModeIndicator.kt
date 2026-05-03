package com.example.liftrix.ui.workout.edit.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixSpacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * EditingModeIndicator - Visual indicator component for editing states
 * 
 * This component provides clear visual differentiation between different editing modes:
 * - Routine editing (workout templates/plans)
 * - Session editing (completed workout sessions)
 * - Historical data modification indicators
 * 
 * Key Features:
 * - Distinct visual indicators for routine vs session editing
 * - Original creation/completion date display
 * - Last modified timestamp tracking
 * - Color-coded theming for different editing contexts
 * - Accessibility support with proper semantic descriptions
 * - Integration with Material 3 design system
 * 
 * @param isEditingHistorical Whether editing historical data (session) vs routine
 * @param originalDate Original creation/completion date of the record
 * @param lastModified Last modification timestamp (nullable if never modified)
 * @param modifier Optional modifier for customizing layout and behavior
 * @param workoutName Optional workout name for context
 */
@Composable
fun EditingModeIndicator(
    isEditingHistorical: Boolean,
    originalDate: Instant?,
    lastModified: Instant? = null,
    modifier: Modifier = Modifier,
    workoutName: String? = null
) {
    val indicatorColor = if (isEditingHistorical) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    
    val contentColor = if (isEditingHistorical) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    
    val iconTint = if (isEditingHistorical) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = indicatorColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode icon
            Icon(
                imageVector = if (isEditingHistorical) Icons.Default.History else Icons.Default.Edit,
                contentDescription = if (isEditingHistorical) {
                    "Editing historical session"
                } else {
                    "Editing workout routine"
                },
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                // Main editing mode title
                Text(
                    text = if (isEditingHistorical) {
                        "Editing workout session"
                    } else {
                        "Editing workout routine"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Workout name if provided
                workoutName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Original date information
                originalDate?.let { date ->
                    Text(
                        text = if (isEditingHistorical) {
                            "Completed ${formatDate(date)}"
                        } else {
                            "Created ${formatDate(date)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
                
                // Last modified information
                lastModified?.let { modified ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Last modified",
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Modified ${formatRelativeTime(modified)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact version of EditingModeIndicator for use in smaller spaces
 */
@Composable
fun CompactEditingModeIndicator(
    isEditingHistorical: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (isEditingHistorical) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isEditingHistorical) Icons.Default.History else Icons.Default.Edit,
                contentDescription = if (isEditingHistorical) "Editing session" else "Editing routine",
                tint = if (isEditingHistorical) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = if (isEditingHistorical) "Session" else "Routine",
                style = MaterialTheme.typography.labelMedium,
                color = if (isEditingHistorical) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

/**
 * EditingModeIndicator for use in app bars or headers
 */
@Composable
fun HeaderEditingModeIndicator(
    isEditingHistorical: Boolean,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isEditingHistorical) Icons.Default.History else Icons.Default.Edit,
            contentDescription = if (isEditingHistorical) {
                "Editing session"
            } else {
                "Editing routine"
            },
            tint = if (isEditingHistorical) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(20.dp)
        )
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Helper functions for date formatting
 */
private fun formatDate(instant: Instant): String {
    return try {
        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        "Unknown date"
    }
}

private fun formatRelativeTime(instant: Instant): String {
    return try {
        val now = Instant.now()
        val duration = java.time.Duration.between(instant, now)
        
        when {
            duration.toDays() > 0 -> "${duration.toDays()} day${if (duration.toDays() == 1L) "" else "s"} ago"
            duration.toHours() > 0 -> "${duration.toHours()} hour${if (duration.toHours() == 1L) "" else "s"} ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} minute${if (duration.toMinutes() == 1L) "" else "s"} ago"
            else -> "Just now"
        }
    } catch (e: Exception) {
        "Recently"
    }
}
