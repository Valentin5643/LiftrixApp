package com.example.liftrix.ui.common.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Social sync status indicator component for Firebase social sync features.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Displays sync status for social features including follows, posts, gym buddies,
 * and engagement metrics with contextual icons and colors.
 */

/**
 * Represents the sync status of social features
 */
sealed class SocialSyncStatus {
    object Idle : SocialSyncStatus()
    object Syncing : SocialSyncStatus()
    data class Success(val syncedItemsCount: Int) : SocialSyncStatus()
    data class Error(val message: String, val retryable: Boolean = true) : SocialSyncStatus()
    data class Offline(val pendingItemsCount: Int) : SocialSyncStatus()
}

/**
 * Represents different types of social content being synced
 */
enum class SocialSyncType {
    PROFILE,
    FOLLOWS,
    POSTS,
    GYM_BUDDIES,
    ENGAGEMENT,
    COMBINED
}

/**
 * Main social sync status indicator
 */
@Composable
fun SocialSyncStatusIndicator(
    syncStatus: SocialSyncStatus,
    syncType: SocialSyncType,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    compactMode: Boolean = false,
    onRetryClick: (() -> Unit)? = null
) {
    val (icon, color, text) = when (syncStatus) {
        is SocialSyncStatus.Idle -> Triple(
            Icons.Default.Check,
            LiftrixColorsV2.DataViz.Positive,
            "Social data up to date"
        )
        is SocialSyncStatus.Syncing -> Triple(
            Icons.Default.Sync,
            LiftrixColorsV2.Teal,
            "Syncing ${syncType.name.lowercase().replace('_', ' ')}..."
        )
        is SocialSyncStatus.Success -> Triple(
            Icons.Default.CheckCircle,
            LiftrixColorsV2.DataViz.Positive,
            "Synced ${syncStatus.syncedItemsCount} ${syncType.name.lowercase().replace('_', ' ')} items"
        )
        is SocialSyncStatus.Error -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            "Sync failed: ${syncStatus.message}"
        )
        is SocialSyncStatus.Offline -> Triple(
            Icons.Default.CloudOff,
            LiftrixColorsV2.DataViz.Warning,
            "${syncStatus.pendingItemsCount} items pending sync"
        )
    }

    val contentDesc = when (syncStatus) {
        is SocialSyncStatus.Syncing -> "Social data synchronization in progress"
        is SocialSyncStatus.Success -> "Social data sync completed. ${syncStatus.syncedItemsCount} items synced"
        is SocialSyncStatus.Error -> "Social data sync failed. ${syncStatus.message}"
        is SocialSyncStatus.Offline -> "${syncStatus.pendingItemsCount} social items waiting to sync"
        else -> "Social data synchronized"
    }

    if (compactMode) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = color,
            modifier = modifier
                .size(16.dp)
                .semantics { this.contentDescription = contentDesc }
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.semantics { this.contentDescription = contentDesc }
        ) {
            if (syncStatus is SocialSyncStatus.Syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            if (showText) {
                Spacer(modifier = Modifier.width(LiftrixSpacing.extraSmall))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (syncStatus is SocialSyncStatus.Error && syncStatus.retryable && onRetryClick != null) {
                Spacer(modifier = Modifier.width(LiftrixSpacing.extraSmall))
                TextButton(
                    onClick = onRetryClick,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Combined social sync status for multiple types
 */
@Composable
fun CombinedSocialSyncStatusIndicator(
    profileStatus: SocialSyncStatus,
    followsStatus: SocialSyncStatus,
    postsStatus: SocialSyncStatus,
    gymBuddiesStatus: SocialSyncStatus,
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    val combinedStatus = when {
        listOf(profileStatus, followsStatus, postsStatus, gymBuddiesStatus).any { it is SocialSyncStatus.Syncing } ->
            SocialSyncStatus.Syncing
        listOf(profileStatus, followsStatus, postsStatus, gymBuddiesStatus).any { it is SocialSyncStatus.Error } ->
            SocialSyncStatus.Error("Some social features failed to sync")
        listOf(profileStatus, followsStatus, postsStatus, gymBuddiesStatus).any { it is SocialSyncStatus.Offline } -> {
            val totalPending = listOf(profileStatus, followsStatus, postsStatus, gymBuddiesStatus)
                .filterIsInstance<SocialSyncStatus.Offline>()
                .sumOf { it.pendingItemsCount }
            SocialSyncStatus.Offline(totalPending)
        }
        else -> {
            val totalSynced = listOf(profileStatus, followsStatus, postsStatus, gymBuddiesStatus)
                .filterIsInstance<SocialSyncStatus.Success>()
                .sumOf { it.syncedItemsCount }
            if (totalSynced > 0) SocialSyncStatus.Success(totalSynced) else SocialSyncStatus.Idle
        }
    }

    Column(modifier = modifier) {
        SocialSyncStatusIndicator(
            syncStatus = combinedStatus,
            syncType = SocialSyncType.COMBINED,
            showText = true
        )
        
        if (expanded) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.extraSmall))
            
            // Individual status indicators
            SocialSyncStatusIndicator(
                syncStatus = profileStatus,
                syncType = SocialSyncType.PROFILE,
                compactMode = true,
                showText = true,
                modifier = Modifier.padding(start = LiftrixSpacing.elementSpacing)
            )
            
            SocialSyncStatusIndicator(
                syncStatus = followsStatus,
                syncType = SocialSyncType.FOLLOWS,
                compactMode = true,
                showText = true,
                modifier = Modifier.padding(start = LiftrixSpacing.elementSpacing)
            )
            
            SocialSyncStatusIndicator(
                syncStatus = postsStatus,
                syncType = SocialSyncType.POSTS,
                compactMode = true,
                showText = true,
                modifier = Modifier.padding(start = LiftrixSpacing.elementSpacing)
            )
            
            SocialSyncStatusIndicator(
                syncStatus = gymBuddiesStatus,
                syncType = SocialSyncType.GYM_BUDDIES,
                compactMode = true,
                showText = true,
                modifier = Modifier.padding(start = LiftrixSpacing.elementSpacing)
            )
        }
    }
}

/**
 * Social sync status for specific features (e.g., just follows or posts)
 */
@Composable
fun FeatureSyncStatusIndicator(
    syncStatus: SocialSyncStatus,
    featureName: String,
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (syncStatus) {
                is SocialSyncStatus.Error -> MaterialTheme.colorScheme.errorContainer
                is SocialSyncStatus.Offline -> LiftrixColorsV2.DataViz.Warning.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(LiftrixSpacing.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SocialSyncStatusIndicator(
                syncStatus = syncStatus,
                syncType = SocialSyncType.COMBINED,
                showText = false
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.elementSpacing))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = featureName,
                    style = MaterialTheme.typography.titleSmall
                )
                
                val statusText = when (syncStatus) {
                    is SocialSyncStatus.Syncing -> "Syncing..."
                    is SocialSyncStatus.Success -> "Up to date"
                    is SocialSyncStatus.Error -> syncStatus.message
                    is SocialSyncStatus.Offline -> "${syncStatus.pendingItemsCount} items pending"
                    else -> "Synchronized"
                }
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (syncStatus is SocialSyncStatus.Error && syncStatus.retryable && onRetryClick != null) {
                TextButton(onClick = onRetryClick) {
                    Text("Retry")
                }
            }
        }
    }
}