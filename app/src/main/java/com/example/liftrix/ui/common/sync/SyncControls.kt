package com.example.liftrix.ui.common.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.sync.CombinedSyncStatus
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SyncControls - Manual sync controls and settings component
 * 
 * Provides user-facing controls for:
 * - Manual sync triggers with real-time feedback
 * - Background sync preferences and settings
 * - Sync history and status information
 * - Force sync options for troubleshooting
 * - Auto-sync toggle controls
 * 
 * Design System Integration:
 * - Uses UnifiedWorkoutCard layout pattern for consistency
 * - PrimaryActionButton/SecondaryActionButton hierarchy
 * - LiftrixColorsV2.Teal for primary sync actions
 * - LiftrixSpacing semantic tokens throughout
 * - Material 3 Switch and Button components
 * - Proper accessibility semantics and touch targets
 * 
 * User Experience Features:
 * - Immediate visual feedback on sync initiation
 * - Progress indication during sync operations
 * - Clear success/error states with retry options
 * - Contextual help text for sync settings
 * - Last sync timestamp for user confidence
 */
@Composable
fun ManualSyncControls(
    combinedStatus: CombinedSyncStatus,
    lastSyncTime: LocalDateTime?,
    unsyncedItemCount: Int,
    isAutoSyncEnabled: Boolean,
    onSyncNow: () -> Unit,
    onForceSyncAll: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onSyncSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "Sync Controls",
        subtitle = getSyncSubtitle(lastSyncTime, unsyncedItemCount),
        leadingIcon = Icons.Default.CloudSync,
        actions = {
            IconButton(
                onClick = onSyncSettings,
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = "Open sync settings"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open sync settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            // Sync Status Summary
            SyncStatusSummary(
                combinedStatus = combinedStatus,
                lastSyncTime = lastSyncTime,
                unsyncedItemCount = unsyncedItemCount
            )
            
            // Manual Sync Actions
            SyncActionButtons(
                combinedStatus = combinedStatus,
                onSyncNow = onSyncNow,
                onForceSyncAll = onForceSyncAll
            )
            
            // Auto-Sync Settings
            AutoSyncSettings(
                isAutoSyncEnabled = isAutoSyncEnabled,
                onToggleAutoSync = onToggleAutoSync
            )
        }
    }
}

@Composable
private fun SyncStatusSummary(
    combinedStatus: CombinedSyncStatus,
    lastSyncTime: LocalDateTime?,
    unsyncedItemCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(LiftrixSpacing.small)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.elementPaddingLarge),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
        ) {
            // Current Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SyncStatusBadge(combinedStatus = combinedStatus)
            }
            
            // Last Sync Time
            if (lastSyncTime != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Synced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatLastSyncTime(lastSyncTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Unsynced Items
            if (unsyncedItemCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pending Sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$unsyncedItemCount items",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColorsV2.DataViz.Warning,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(
    combinedStatus: CombinedSyncStatus
) {
    val (text, color, icon) = when {
        combinedStatus.isAnySync -> Triple(
            "Syncing", 
            LiftrixColorsV2.Teal, 
            Icons.Default.Sync
        )
        combinedStatus.hasAnyError -> Triple(
            "Error", 
            MaterialTheme.colorScheme.error, 
            Icons.Default.SyncProblem
        )
        combinedStatus.isAllSuccess -> Triple(
            "Up to date", 
            LiftrixColorsV2.DataViz.Positive, 
            Icons.Default.CloudSync
        )
        else -> Triple(
            "Ready", 
            MaterialTheme.colorScheme.onSurfaceVariant, 
            Icons.Default.CloudSync
        )
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (combinedStatus.isAnySync) {
            val infiniteTransition = rememberInfiniteTransition(label = "sync_badge")
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing)
                ),
                label = "badge_rotation"
            )
            
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotationAngle)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SyncActionButtons(
    combinedStatus: CombinedSyncStatus,
    onSyncNow: () -> Unit,
    onForceSyncAll: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
    ) {
        // Primary sync button
        PrimaryActionButton(
            text = when {
                combinedStatus.isAnySync -> "Syncing..."
                else -> "Sync Now"
            },
            onClick = onSyncNow,
            enabled = !combinedStatus.isAnySync,
            leadingIcon = Icons.Default.Refresh,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (combinedStatus.isAnySync) {
                        "Sync in progress"
                    } else {
                        "Sync all data now"
                    }
                }
        )
        
        // Force sync option for troubleshooting
        if (combinedStatus.hasAnyError) {
            SecondaryActionButton(
                text = "Force Complete Sync",
                onClick = onForceSyncAll,
                leadingIcon = Icons.Default.CloudSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Force complete data synchronization"
                    }
            )
        }
    }
}

@Composable
private fun AutoSyncSettings(
    isAutoSyncEnabled: Boolean,
    onToggleAutoSync: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Sync",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Automatically sync data in the background",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isAutoSyncEnabled,
                onCheckedChange = onToggleAutoSync,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LiftrixColorsV2.Teal,
                    checkedTrackColor = LiftrixColorsV2.Teal.copy(alpha = 0.5f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    role = Role.Switch
                    contentDescription = if (isAutoSyncEnabled) {
                        "Auto-sync enabled. Tap to disable."
                    } else {
                        "Auto-sync disabled. Tap to enable."
                    }
                }
            )
        }
        
        if (!isAutoSyncEnabled) {
            Text(
                text = "Data will only sync when you manually request it or when the app starts.",
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColorsV2.DataViz.Warning,
                modifier = Modifier.padding(top = LiftrixSpacing.elementPaddingSmall)
            )
        }
    }
}

/**
 * Compact sync control for smaller spaces (e.g., app bars)
 */
@Composable
fun CompactSyncControl(
    combinedStatus: CombinedSyncStatus,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !combinedStatus.isAnySync,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        IconButton(
            onClick = onSyncNow,
            modifier = Modifier.semantics {
                role = Role.Button
                contentDescription = "Sync data now"
            }
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync data now",
                tint = when {
                    combinedStatus.hasAnyError -> MaterialTheme.colorScheme.error
                    combinedStatus.isAllSuccess -> LiftrixColorsV2.DataViz.Positive
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Sync history component for detailed sync information
 */
@Composable
fun SyncHistorySection(
    syncHistory: List<SyncHistoryItem>,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "Sync History",
        subtitle = "${syncHistory.size} recent sync operations",
        leadingIcon = Icons.Default.History,
        modifier = modifier
    ) {
        if (syncHistory.isEmpty()) {
            Text(
                text = "No sync history available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LiftrixSpacing.cardPadding)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingMedium)
            ) {
                syncHistory.take(5).forEach { historyItem ->
                    SyncHistoryItemRow(historyItem = historyItem)
                }
            }
        }
    }
}

@Composable
private fun SyncHistoryItemRow(
    historyItem: SyncHistoryItem
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        val (icon, color) = when (historyItem.status) {
            SyncHistoryStatus.SUCCESS -> Icons.Default.CloudSync to LiftrixColorsV2.DataViz.Positive
            SyncHistoryStatus.ERROR -> Icons.Default.SyncProblem to MaterialTheme.colorScheme.error
            SyncHistoryStatus.PARTIAL -> Icons.Default.SyncProblem to LiftrixColorsV2.DataViz.Warning
        }
        
        Icon(
            imageVector = icon,
            contentDescription = when (historyItem.status) {
                SyncHistoryStatus.SUCCESS -> "Sync successful"
                SyncHistoryStatus.ERROR -> "Sync failed"
                SyncHistoryStatus.PARTIAL -> "Sync partially completed"
            },
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        
        // Sync details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = historyItem.operation,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${historyItem.itemCount} items • ${formatDuration(historyItem.duration)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Timestamp
        Text(
            text = formatSyncTime(historyItem.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Helper functions
 */
private fun getSyncSubtitle(lastSyncTime: LocalDateTime?, unsyncedItemCount: Int): String {
    return when {
        unsyncedItemCount > 0 -> "$unsyncedItemCount items pending sync"
        lastSyncTime != null -> "Last synced ${formatLastSyncTime(lastSyncTime)}"
        else -> "Ready to sync"
    }
}

private fun formatLastSyncTime(time: LocalDateTime): String {
    val now = LocalDateTime.now()
    val duration = java.time.Duration.between(time, now)
    
    return when {
        duration.toMinutes() < 1 -> "Just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()} minutes ago"
        duration.toHours() < 24 -> "${duration.toHours()} hours ago"
        duration.toDays() < 7 -> "${duration.toDays()} days ago"
        else -> time.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

private fun formatSyncTime(time: LocalDateTime): String {
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}

/**
 * Data classes for sync history
 */
data class SyncHistoryItem(
    val timestamp: LocalDateTime,
    val operation: String,
    val status: SyncHistoryStatus,
    val itemCount: Int,
    val duration: Long
)

enum class SyncHistoryStatus {
    SUCCESS, ERROR, PARTIAL
}
