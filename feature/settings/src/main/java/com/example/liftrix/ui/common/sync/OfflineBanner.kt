package com.example.liftrix.ui.common.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.service.SyncStatus
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * OfflineBanner - Offline and sync error notification component
 * 
 * Displays contextual banners for:
 * - Network connectivity issues with auto-retry
 * - Sync failures with manual retry options
 * - Offline mode indication with data staleness warnings
 * - Background sync status with user controls
 * 
 * Design System Integration:
 * - Uses LiftrixColorsV2.DataViz.Warning for offline states
 * - Error states use MaterialTheme.colorScheme.error
 * - Follows LiftrixSpacing semantic tokens
 * - Material 3 Card elevation and shape system
 * - Smooth slide-in/out animations (300ms)
 * 
 * Accessibility Features:
 * - Semantic descriptions for screen readers
 * - Clear retry actions with proper touch targets
 * - High contrast error states
 * - Role-based navigation support
 */
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    syncState: SyncStatus?,
    unsyncedItemCount: Int = 0,
    onRetrySync: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bannerData = getBannerData(
        isOffline = isOffline,
        syncState = syncState,
        unsyncedItemCount = unsyncedItemCount
    )
    
    AnimatedVisibility(
        visible = bannerData != null,
        enter = slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { -it }
        ),
        exit = slideOutVertically(
            animationSpec = tween(300),
            targetOffsetY = { -it }
        ),
        modifier = modifier
    ) {
        bannerData?.let { data ->
            OfflineBannerContent(
                bannerData = data,
                onRetrySync = onRetrySync,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun OfflineBannerContent(
    bannerData: OfflineBannerData,
    onRetrySync: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.screenPadding)
            .semantics {
                contentDescription = "${bannerData.severity} notification: ${bannerData.title}"
            },
        colors = CardDefaults.cardColors(
            containerColor = bannerData.backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(LiftrixSpacing.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
            verticalAlignment = Alignment.Top
        ) {
            // Status Icon
            Icon(
                imageVector = bannerData.icon,
                contentDescription = bannerData.title,
                tint = bannerData.iconColor,
                modifier = Modifier.padding(top = 2.dp)
            )
            
            // Content Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                // Title
                Text(
                    text = bannerData.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = bannerData.textColor
                )
                
                // Description
                if (bannerData.description.isNotBlank()) {
                    Text(
                        text = bannerData.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = bannerData.textColor.copy(alpha = 0.8f)
                    )
                }
                
                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing),
                    modifier = Modifier.padding(top = LiftrixSpacing.elementPaddingSmall)
                ) {
                    if (bannerData.showRetryAction) {
                        Button(
                            onClick = onRetrySync,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = bannerData.actionButtonColor,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.semantics {
                                role = Role.Button
                                contentDescription = "Retry synchronization"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry sync",
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Retry")
                        }
                    }
                    
                    if (onDismiss != null && bannerData.showDismissAction) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics {
                                role = Role.Button
                                contentDescription = "Dismiss notification"
                            }
                        ) {
                            Text(
                                "Dismiss",
                                color = bannerData.textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact offline indicator for app bars and status areas
 */
@Composable
fun CompactOfflineIndicator(
    isOffline: Boolean,
    syncState: SyncStatus?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val indicatorData = getCompactIndicatorData(isOffline, syncState)
    
    AnimatedVisibility(
        visible = indicatorData != null,
        modifier = modifier
    ) {
        indicatorData?.let { data ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(LiftrixSpacing.small))
                    .background(data.backgroundColor)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                onClickLabel = "Show sync details"
                            ) { onClick() }
                        } else Modifier
                    )
                    .padding(
                        horizontal = LiftrixSpacing.elementPaddingMedium,
                        vertical = LiftrixSpacing.elementPaddingSmall
                    )
                    .semantics {
                        contentDescription = data.contentDescription
                        if (onClick != null) {
                            role = Role.Button
                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = data.icon,
                        contentDescription = data.text,
                        tint = data.iconColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = data.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = data.textColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Helper functions for banner data configuration
 */
@Composable
private fun getBannerData(
    isOffline: Boolean,
    syncState: SyncStatus?,
    unsyncedItemCount: Int
): OfflineBannerData? {
    return when {
        isOffline -> OfflineBannerData(
            severity = "Warning",
            title = "No Internet Connection",
            description = if (unsyncedItemCount > 0) {
                "You're offline. $unsyncedItemCount items are waiting to sync when connection is restored."
            } else {
                "You're offline. Some features may not be available."
            },
            icon = Icons.Default.WifiOff,
            iconColor = LiftrixColorsV2.DataViz.Warning,
            backgroundColor = LiftrixColorsV2.DataViz.Warning.copy(alpha = 0.1f),
            textColor = MaterialTheme.colorScheme.onSurface,
            actionButtonColor = LiftrixColorsV2.DataViz.Warning,
            showRetryAction = true,
            showDismissAction = false
        )
        
        syncState is SyncStatus.Error -> OfflineBannerData(
            severity = "Error",
            title = "Sync Failed",
            description = syncState.message + if (unsyncedItemCount > 0) {
                " $unsyncedItemCount items need to be synced."
            } else "",
            icon = Icons.Default.CloudOff,
            iconColor = Color.White,
            backgroundColor = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.onError,
            actionButtonColor = MaterialTheme.colorScheme.onError,
            showRetryAction = true,
            showDismissAction = true
        )
        
        unsyncedItemCount > 5 -> OfflineBannerData(
            severity = "Info",
            title = "Sync Pending",
            description = "$unsyncedItemCount items are waiting to sync to the cloud.",
            icon = Icons.Default.CloudOff,
            iconColor = LiftrixColorsV2.Teal,
            backgroundColor = LiftrixColorsV2.Teal.copy(alpha = 0.1f),
            textColor = MaterialTheme.colorScheme.onSurface,
            actionButtonColor = LiftrixColorsV2.Teal,
            showRetryAction = true,
            showDismissAction = true
        )
        
        else -> null
    }
}

@Composable
private fun getCompactIndicatorData(
    isOffline: Boolean,
    syncState: SyncStatus?
): CompactIndicatorData? {
    return when {
        isOffline -> CompactIndicatorData(
            icon = Icons.Default.WifiOff,
            iconColor = LiftrixColorsV2.DataViz.Warning,
            backgroundColor = LiftrixColorsV2.DataViz.Warning.copy(alpha = 0.15f),
            textColor = LiftrixColorsV2.DataViz.Warning,
            text = "Offline",
            contentDescription = "App is offline"
        )
        
        syncState is SyncStatus.Error -> CompactIndicatorData(
            icon = Icons.Default.CloudOff,
            iconColor = MaterialTheme.colorScheme.error,
            backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            textColor = MaterialTheme.colorScheme.error,
            text = "Sync Error",
            contentDescription = "Sync error occurred"
        )
        
        else -> null
    }
}

/**
 * Data classes for banner configuration
 */
private data class OfflineBannerData(
    val severity: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val actionButtonColor: Color,
    val showRetryAction: Boolean,
    val showDismissAction: Boolean
)

private data class CompactIndicatorData(
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val text: String,
    val contentDescription: String
)
