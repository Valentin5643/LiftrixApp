package com.example.liftrix.ui.common.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.sync.CombinedSyncStatus
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.service.sync.RealtimeSyncManager
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * SyncIntegrationExtensions - Integration helpers for adding sync UI to existing screens
 * 
 * Provides extension functions and wrapper composables to seamlessly integrate
 * Firebase sync UI components into existing Liftrix screens while maintaining
 * the established design patterns and architectural consistency.
 * 
 * Integration Points:
 * 1. ProfileScreen - Sync status in profile header
 * 2. WorkoutScreen - Template sync indicators and manual sync controls  
 * 3. ActiveWorkoutScreen - Real-time session sync status
 * 4. ProgressDashboardScreen - Analytics sync indicators
 * 5. SettingsScreen - Comprehensive sync settings and controls
 * 6. Global app bar - Compact sync status and offline indicators
 * 
 * Design System Compliance:
 * - Follows existing UnifiedWorkoutCard patterns
 * - Uses LiftrixSpacing and LiftrixColorsV2 consistently
 * - Maintains Material 3 design language
 * - Preserves accessibility standards
 * - Non-intrusive integration that doesn't disrupt existing UX
 */

/**
 * Wraps any screen content with sync awareness
 * Automatically shows offline banner and sync status when needed
 */
@Composable
fun WithSyncAwareness(
    syncManager: SyncManager = hiltViewModel(),
    realtimeSyncManager: RealtimeSyncManager = hiltViewModel(),
    isOffline: Boolean = false,
    showCompactIndicator: Boolean = false,
    content: @Composable () -> Unit
) {
    val combinedSyncStatus by syncManager.getCombinedSyncStatus().collectAsState(
        initial = CombinedSyncStatus(
            workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
            analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
        )
    )
    
    val realtimeSyncState by realtimeSyncManager.syncState.collectAsState()
    
    Column {
        // Global offline banner (appears at top of screen)
        OfflineBanner(
            isOffline = isOffline,
            syncState = realtimeSyncState,
            unsyncedItemCount = 0, // TODO: Get from repository
            onRetrySync = { 
                // TODO: Implement retry sync
            },
            onDismiss = null // Don't allow dismissing offline banner
        )
        
        // Compact sync indicator for app bars
        if (showCompactIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LiftrixSpacing.screenPadding)
            ) {
                CompactOfflineIndicator(
                    isOffline = isOffline,
                    syncState = realtimeSyncState,
                    onClick = {
                        // TODO: Navigate to sync settings or show sync details
                    }
                )
            }
        }
        
        // Screen content
        content()
    }
}

/**
 * Profile screen sync integration
 * Adds sync status to profile header and sync controls to settings section
 */
@Composable
fun ProfileSyncIntegration(
    userId: String,
    modifier: Modifier = Modifier
) {
    // TODO: Get profile-specific sync status
    // This would show:
    // - Profile image upload sync status
    // - Achievement sync status  
    // - Privacy settings sync status
    
    ManualSyncControls(
        combinedStatus = CombinedSyncStatus(
            workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
            analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
        ),
        lastSyncTime = null,
        unsyncedItemCount = 0,
        isAutoSyncEnabled = true,
        onSyncNow = { /* TODO: Implement profile sync */ },
        onForceSyncAll = { /* TODO: Implement force sync */ },
        onToggleAutoSync = { /* TODO: Implement auto-sync toggle */ },
        onSyncSettings = { /* TODO: Navigate to sync settings */ },
        modifier = modifier
    )
}

/**
 * Workout screen sync integration
 * Adds template sync indicators and workout sync controls
 */
@Composable
fun WorkoutSyncIntegration(
    userId: String,
    modifier: Modifier = Modifier
) {
    // TODO: Get workout-specific sync status
    // This would show:
    // - Template sync status
    // - Workout history sync status
    // - Folder sync status
    
    Column(
        modifier = modifier
    ) {
        // Sync progress for background operations
        SyncProgressBar(
            combinedStatus = CombinedSyncStatus(
                workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
                analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
            ),
            currentOperation = null,
            progress = null,
            showDetailedProgress = false,
            autoHide = true
        )
        
        // Manual sync controls in settings card
        ManualSyncControls(
            combinedStatus = CombinedSyncStatus(
                workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
                analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
            ),
            lastSyncTime = null,
            unsyncedItemCount = 0,
            isAutoSyncEnabled = true,
            onSyncNow = { /* TODO: Implement workout sync */ },
            onForceSyncAll = { /* TODO: Implement force sync */ },
            onToggleAutoSync = { /* TODO: Implement auto-sync toggle */ },
            onSyncSettings = { /* TODO: Navigate to sync settings */ }
        )
    }
}

/**
 * Active workout sync integration
 * Shows real-time session sync status during workouts
 */
@Composable
fun ActiveWorkoutSyncIntegration(
    workoutSessionId: String,
    modifier: Modifier = Modifier
) {
    // TODO: Get active workout sync status
    // This would show:
    // - Real-time session sync status
    // - Exercise completion sync
    // - PR detection sync
    
    SyncStatusIndicator(
        syncStatus = com.example.liftrix.sync.SyncStatus.Idle,
        showText = true,
        autoHideSuccess = true,
        contentDescription = "Workout session sync status",
        modifier = modifier
    )
}

/**
 * Progress dashboard sync integration  
 * Shows analytics sync status and manual recalculation controls
 */
@Composable
fun ProgressDashboardSyncIntegration(
    userId: String,
    modifier: Modifier = Modifier
) {
    // TODO: Get analytics-specific sync status
    // This would show:
    // - Widget data sync status
    // - Analytics calculation sync
    // - Chart data sync status
    
    Column(
        modifier = modifier
    ) {
        // Analytics sync progress
        SyncProgressBar(
            combinedStatus = CombinedSyncStatus(
                workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
                analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
            ),
            currentOperation = "Updating analytics",
            progress = null,
            showDetailedProgress = true,
            autoHide = true
        )
        
        // Compact sync control for manual analytics refresh
        CompactSyncControl(
            combinedStatus = CombinedSyncStatus(
                workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
                analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
            ),
            onSyncNow = { /* TODO: Implement analytics sync */ }
        )
    }
}

/**
 * Settings screen comprehensive sync controls
 * Full sync management interface with history and advanced options
 */
@Composable
fun SettingsSyncIntegration(
    userId: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Main sync controls
        ManualSyncControls(
            combinedStatus = CombinedSyncStatus(
                workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
                analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
            ),
            lastSyncTime = null,
            unsyncedItemCount = 0,
            isAutoSyncEnabled = true,
            onSyncNow = { /* TODO: Implement sync */ },
            onForceSyncAll = { /* TODO: Implement force sync */ },
            onToggleAutoSync = { /* TODO: Implement auto-sync toggle */ },
            onSyncSettings = { /* TODO: Navigate to advanced sync settings */ }
        )
        
        // Sync history
        SyncHistorySection(
            syncHistory = emptyList() // TODO: Get sync history from repository
        )
    }
}

/**
 * Individual workout/template item sync indicators
 * Shows compact sync status for individual items in lists
 */
@Composable
fun WorkoutItemSyncIndicator(
    workoutId: String,
    syncStatus: com.example.liftrix.sync.SyncStatus,
    modifier: Modifier = Modifier
) {
    CompactSyncIndicator(
        syncStatus = syncStatus,
        modifier = modifier
    )
}

/**
 * Achievement badge sync integration
 * Shows sync status for individual achievements
 */
@Composable
fun AchievementSyncIndicator(
    achievementId: String,
    syncStatus: com.example.liftrix.sync.SyncStatus,
    modifier: Modifier = Modifier
) {
    CompactSyncIndicator(
        syncStatus = syncStatus,
        modifier = modifier
    )
}

/**
 * Template sync integration for workout creation/editing
 * Shows template-specific sync status and controls
 */
@Composable
fun TemplateSyncIntegration(
    templateId: String,
    modifier: Modifier = Modifier
) {
    SyncStatusIndicator(
        syncStatus = com.example.liftrix.sync.SyncStatus.Idle,
        showText = false,
        autoHideSuccess = true,
        contentDescription = "Template sync status",
        modifier = modifier
    )
}

/**
 * Extension functions for existing ViewModels to integrate sync awareness
 */

/**
 * Extension for ProfileViewModel to add sync status
 */
// TODO: Add extension functions for existing ViewModels
// fun ProfileViewModel.getSyncStatus(): StateFlow<SyncStatus>

/**
 * Extension for WorkoutViewModel to add sync status  
 */
// TODO: Add extension functions for existing ViewModels
// fun WorkoutViewModel.getSyncStatus(): StateFlow<SyncStatus>

/**
 * Extension for ProgressDashboardViewModel to add analytics sync status
 */
// TODO: Add extension functions for existing ViewModels  
// fun ProgressDashboardViewModel.getAnalyticsSyncStatus(): StateFlow<SyncStatus>