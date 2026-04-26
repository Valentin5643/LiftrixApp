package com.example.liftrix.ui.common.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.sync.CombinedSyncStatus
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.delay

/**
 * SyncStatusIndicator - Real-time sync status display component
 * 
 * Displays current Firebase synchronization status with:
 * - Real-time status updates with smooth animations
 * - Color-coded status indicators following LiftrixColorsV2
 * - Accessibility support with semantic descriptions
 * - Auto-hiding success states after 3 seconds
 * - Support for both individual and combined sync status
 * 
 * Design System Integration:
 * - Uses LiftrixColorsV2.Teal for primary sync indicators
 * - Follows LiftrixSpacing semantic tokens
 * - Material 3 shape and typography system
 * - Consistent with existing loading patterns
 * 
 * Status Mapping:
 * - Idle: Hidden (no visual indicator needed)
 * - Syncing: Teal spinner with "Syncing..." text
 * - Success: Green checkmark with sync count, auto-hide after 3s
 * - Error: Red error icon with retry capability
 * - Offline: Gray cloud-off icon with offline message
 */
@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    autoHideSuccess: Boolean = true,
    contentDescription: String? = null
) {
    var isVisible by remember(syncStatus) { 
        mutableStateOf(syncStatus !is SyncStatus.Idle) 
    }
    
    // Auto-hide success states after 3 seconds
    LaunchedEffect(syncStatus) {
        if (autoHideSuccess && syncStatus is SyncStatus.Success) {
            delay(3000)
            isVisible = false
        } else if (syncStatus !is SyncStatus.Idle) {
            isVisible = true
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        SyncStatusContent(
            syncStatus = syncStatus,
            showText = showText,
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun SyncStatusContent(
    syncStatus: SyncStatus,
    showText: Boolean,
    contentDescription: String?
) {
    val (icon, color, text, isAnimated) = when (syncStatus) {
        SyncStatus.Idle -> return // Should not render
        SyncStatus.Syncing -> SyncIndicatorData(
            icon = null, // Use CircularProgressIndicator instead
            color = LiftrixColorsV2.Teal,
            text = "Syncing...",
            isAnimated = true
        )
        is SyncStatus.Success -> SyncIndicatorData(
            icon = Icons.Default.Check,
            color = LiftrixColorsV2.DataViz.Positive,
            text = if (syncStatus.syncedCount > 0) "Synced ${syncStatus.syncedCount} items" else "Synced",
            isAnimated = false
        )
        is SyncStatus.AnalyticsSuccess -> SyncIndicatorData(
            icon = Icons.Default.Check,
            color = LiftrixColorsV2.DataViz.Positive,
            text = buildString {
                append("Synced ${syncStatus.syncedCount} items")
                if (syncStatus.conflictCount > 0) {
                    append(" • ${syncStatus.conflictCount} conflicts resolved")
                }
            },
            isAnimated = false
        )
        is SyncStatus.Error -> SyncIndicatorData(
            icon = Icons.Default.Error,
            color = MaterialTheme.colorScheme.error,
            text = "Sync failed",
            isAnimated = false
        )
    }
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LiftrixSpacing.medium))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )
            .padding(
                horizontal = LiftrixSpacing.elementPaddingMedium,
                vertical = LiftrixSpacing.elementPaddingSmall
            )
            .semantics {
                this.contentDescription = contentDescription ?: text
            },
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon or Progress Indicator
        if (isAnimated) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = color,
                strokeWidth = 2.dp
            )
        } else {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = text,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Status Text
        if (showText) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Combined sync status indicator for multiple sync operations
 */
@Composable
fun CombinedSyncStatusIndicator(
    combinedStatus: CombinedSyncStatus,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    contentDescription: String? = null
) {
    val primaryStatus = when {
        combinedStatus.isAnySync -> {
            // Show syncing if any operation is syncing
            SyncStatus.Syncing
        }
        combinedStatus.hasAnyError -> {
            // Show error if any operation has error
            when {
                combinedStatus.workoutStatus is SyncStatus.Error -> combinedStatus.workoutStatus
                combinedStatus.analyticsStatus is SyncStatus.Error -> combinedStatus.analyticsStatus
                else -> SyncStatus.Error("Unknown error")
            }
        }
        combinedStatus.isAllSuccess -> {
            // Show combined success
            val workoutCount = (combinedStatus.workoutStatus as? SyncStatus.Success)?.syncedCount ?: 0
            val analyticsCount = (combinedStatus.analyticsStatus as? SyncStatus.AnalyticsSuccess)?.syncedCount ?: 0
            SyncStatus.Success(workoutCount + analyticsCount)
        }
        else -> SyncStatus.Idle
    }
    
    SyncStatusIndicator(
        syncStatus = primaryStatus,
        modifier = modifier,
        showText = showText,
        contentDescription = contentDescription ?: "Combined sync status"
    )
}

/**
 * Compact sync status indicator for small spaces (e.g., list items)
 */
@Composable
fun CompactSyncIndicator(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (syncStatus) {
        SyncStatus.Idle -> return // No indicator for idle
        SyncStatus.Syncing -> null to LiftrixColorsV2.Teal
        is SyncStatus.Success, is SyncStatus.AnalyticsSuccess -> 
            Icons.Default.Check to LiftrixColorsV2.DataViz.Positive
        is SyncStatus.Error -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
    
    Box(
        modifier = modifier
            .size(12.dp)
            .semantics {
                contentDescription = when (syncStatus) {
                    SyncStatus.Syncing -> "Syncing"
                    is SyncStatus.Success, is SyncStatus.AnalyticsSuccess -> "Synced"
                    is SyncStatus.Error -> "Sync error"
                    else -> ""
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (syncStatus is SyncStatus.Syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = color,
                strokeWidth = 1.5.dp
            )
        } else {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = when (syncStatus) {
                        SyncStatus.Syncing -> "Syncing"
                        is SyncStatus.Success, is SyncStatus.AnalyticsSuccess -> "Synced"
                        is SyncStatus.Error -> "Sync error"
                        else -> "Sync status"
                    },
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Data class for sync indicator configuration
 */
private data class SyncIndicatorData(
    val icon: ImageVector?,
    val color: Color,
    val text: String,
    val isAnimated: Boolean
)
