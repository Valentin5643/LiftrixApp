package com.example.liftrix.ui.common.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.sync.CombinedSyncStatus
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.service.sync.RealtimeSyncManager
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber

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
 * Extension function to get current user ID as Flow
 */
@Composable
fun getCurrentUserId(getCurrentUserIdUseCase: GetCurrentUserIdUseCase = hiltViewModel()): Flow<String?> {
    return remember {
        flow {
            try {
                val userId = getCurrentUserIdUseCase()
                emit(userId)
            } catch (e: Exception) {
                Timber.e(e, "WithSyncAwareness: Failed to get current user ID")
                emit(null)
            }
        }
    }
}

/**
 * Wraps any screen content with sync awareness
 * Automatically shows offline banner and sync status when needed
 */
@Composable
fun WithSyncAwareness(
    syncManager: SyncManager = hiltViewModel(),
    realtimeSyncManager: RealtimeSyncManager = hiltViewModel(),
    syncCoordinator: SyncCoordinator = hiltViewModel(),
    isOffline: Boolean = false,
    showCompactIndicator: Boolean = false,
    onNavigateToSyncSettings: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val combinedSyncStatus by syncManager.getCombinedSyncStatus().collectAsState(
        initial = CombinedSyncStatus(
            workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
            analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
        )
    )
    
    val realtimeSyncState by realtimeSyncManager.syncState.collectAsState()
    
    // TODO Line 66 Implementation: Get unsynced item count from repository
    val currentUserId by getCurrentUserId().collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    
    // Get unsynced count using Flow to handle async operations properly
    val unsyncedItemCount by remember(currentUserId) {
        flow {
            currentUserId?.let { userId ->
                try {
                    // Combine counts from all repositories
                    val workoutCount = syncManager.getUnsyncedCount(userId)
                    val analyticsCount = syncManager.getUnsyncedAnalyticsCount(userId)
                    val totalCount = workoutCount + analyticsCount
                    Timber.d("WithSyncAwareness: Unsynced count for user $userId: workout=$workoutCount, analytics=$analyticsCount, total=$totalCount")
                    emit(totalCount)
                } catch (e: Exception) {
                    Timber.e(e, "WithSyncAwareness: Failed to get unsynced count for user: $userId")
                    emit(0)
                }
            } ?: emit(0)
        }
    }.collectAsState(initial = 0)
    
    Column {
        // Global offline banner (appears at top of screen)
        OfflineBanner(
            isOffline = isOffline,
            syncState = realtimeSyncState,
            unsyncedItemCount = unsyncedItemCount,
            onRetrySync = { 
                // TODO Line 68: Implement retry sync
                coroutineScope.launch {
                    currentUserId?.let { userId ->
                        try {
                            Timber.d("WithSyncAwareness: Triggering retry sync for user: $userId")
                            val syncResult = syncCoordinator.triggerImmediateSync(userId)
                            syncResult.fold(
                                onSuccess = { 
                                    Timber.d("WithSyncAwareness: Retry sync initiated successfully for user: $userId")
                                },
                                onFailure = { error ->
                                    Timber.e("WithSyncAwareness: Retry sync failed for user $userId: ${error.message}")
                                }
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "WithSyncAwareness: Exception during retry sync for user: $userId")
                        }
                    } ?: run {
                        Timber.w("WithSyncAwareness: Cannot retry sync - no authenticated user")
                    }
                }
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
                        // TODO Line 84: Navigate to sync settings or show sync details
                        onNavigateToSyncSettings?.invoke() ?: run {
                            Timber.d("WithSyncAwareness: Navigate to sync settings requested but no handler provided")
                        }
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
    syncManager: SyncManager = hiltViewModel(),
    syncCoordinator: SyncCoordinator = hiltViewModel(),
    onNavigateToSyncSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Profile-specific sync status combining multiple profile entities
    val profileSyncStatus by syncManager.getSyncStatus().collectAsState(initial = SyncStatus.Idle)
    
    // Get unsynced profile items count from various profile-related entities
    val unsyncedProfileItemCount by remember(userId) {
        flow {
            try {
                // Get counts from available sync entities
                val workoutCount = syncManager.getUnsyncedCount(userId)
                val analyticsCount = syncManager.getUnsyncedAnalyticsCount(userId)
                val totalCount = workoutCount + analyticsCount
                
                Timber.d("ProfileSyncIntegration: Profile unsynced count for user $userId: workouts=$workoutCount, analytics=$analyticsCount, total=$totalCount")
                emit(totalCount)
            } catch (e: Exception) {
                Timber.e(e, "ProfileSyncIntegration: Failed to get unsynced profile count for user: $userId")
                emit(0)
            }
        }
    }.collectAsState(initial = 0)
    
    ManualSyncControls(
        combinedStatus = CombinedSyncStatus(
            workoutStatus = profileSyncStatus,
            analyticsStatus = profileSyncStatus // Use same status for both as profile sync is unified
        ),
        lastSyncTime = null, // TODO: Get last profile sync time from sync manager
        unsyncedItemCount = unsyncedProfileItemCount,
        isAutoSyncEnabled = true, // TODO: Get from user preferences
        onSyncNow = { 
            coroutineScope.launch {
                try {
                    Timber.d("ProfileSyncIntegration: Triggering profile sync for user: $userId")
                    val syncResult = syncCoordinator.triggerImmediateSync(userId)
                    syncResult.fold(
                        onSuccess = {
                            Timber.d("ProfileSyncIntegration: Profile sync initiated successfully for user: $userId")
                        },
                        onFailure = { error ->
                            Timber.e("ProfileSyncIntegration: Profile sync failed for user $userId: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "ProfileSyncIntegration: Exception during profile sync for user: $userId")
                }
            }
        },
        onForceSyncAll = { 
            coroutineScope.launch {
                try {
                    Timber.d("ProfileSyncIntegration: Triggering force profile sync for user: $userId")
                    // Force sync by canceling existing work and triggering new sync
                    syncManager.cancelSync()
                    val syncResult = syncCoordinator.triggerImmediateSync(userId)
                    syncResult.fold(
                        onSuccess = {
                            Timber.d("ProfileSyncIntegration: Force profile sync initiated successfully for user: $userId")
                        },
                        onFailure = { error ->
                            Timber.e("ProfileSyncIntegration: Force profile sync failed for user $userId: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "ProfileSyncIntegration: Exception during force profile sync for user: $userId")
                }
            }
        },
        onToggleAutoSync = { enabled ->
            coroutineScope.launch {
                try {
                    Timber.d("ProfileSyncIntegration: Toggling auto-sync to $enabled for user: $userId")
                    // Auto-sync toggle implementation using available API
                    if (enabled) {
                        syncCoordinator.schedulePeriodicSync(userId)
                        Timber.d("ProfileSyncIntegration: Auto-sync enabled for user: $userId")
                    } else {
                        // Cancel sync and log the toggle
                        syncManager.cancelSync()
                        Timber.d("ProfileSyncIntegration: Auto-sync disabled for user: $userId")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "ProfileSyncIntegration: Exception during auto-sync toggle for user: $userId")
                }
            }
        },
        onSyncSettings = { 
            // Sync settings navigation removed per user request - just sync the information
            coroutineScope.launch {
                try {
                    Timber.d("ProfileSyncIntegration: Sync settings requested - triggering profile data sync for user: $userId")
                    val syncResult = syncCoordinator.triggerImmediateSync(userId)
                    syncResult.fold(
                        onSuccess = {
                            Timber.d("ProfileSyncIntegration: Profile data sync initiated from settings action for user: $userId")
                        },
                        onFailure = { error ->
                            Timber.e("ProfileSyncIntegration: Profile data sync failed from settings action for user $userId: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "ProfileSyncIntegration: Exception during profile data sync from settings action for user: $userId")
                }
            }
        },
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
    syncManager: SyncManager = hiltViewModel(),
    syncCoordinator: SyncCoordinator = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Workout-specific sync status combining templates, workouts, and folders
    val workoutSyncStatus by syncManager.getCombinedSyncStatus().collectAsState(
        initial = CombinedSyncStatus(
            workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
            analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
        )
    )
    
    // Get unsynced workout-related items count
    val unsyncedWorkoutItemCount by remember(userId) {
        flow {
            try {
                // Get counts from available workout-related sync entities
                val workoutCount = syncManager.getUnsyncedCount(userId)
                val analyticsCount = syncManager.getUnsyncedAnalyticsCount(userId)
                val totalCount = workoutCount + analyticsCount
                
                Timber.d("WorkoutSyncIntegration: Workout unsynced count for user $userId: workouts=$workoutCount, analytics=$analyticsCount, total=$totalCount")
                emit(totalCount)
            } catch (e: Exception) {
                Timber.e(e, "WorkoutSyncIntegration: Failed to get unsynced workout count for user: $userId")
                emit(0)
            }
        }
    }.collectAsState(initial = 0)
    
    // Check if auto-sync is enabled for this user
    val isAutoSyncEnabled by remember(userId) {
        flow {
            try {
                // Since there's no isAutoSyncEnabled method, default to true
                emit(true)
            } catch (e: Exception) {
                Timber.e(e, "WorkoutSyncIntegration: Failed to get auto-sync status for user: $userId")
                emit(true) // Default to enabled
            }
        }
    }.collectAsState(initial = true)
    
    Column(
        modifier = modifier
    ) {
        // Sync progress for background operations
        SyncProgressBar(
            combinedStatus = workoutSyncStatus,
            currentOperation = "Syncing workouts",
            progress = null,
            showDetailedProgress = false,
            autoHide = true
        )
        
        // Manual sync controls in settings card
        ManualSyncControls(
            combinedStatus = workoutSyncStatus,
            lastSyncTime = null, // TODO: Get last workout sync time from sync manager
            unsyncedItemCount = unsyncedWorkoutItemCount,
            isAutoSyncEnabled = isAutoSyncEnabled,
            onSyncNow = { 
                // TODO Line 165: Implement workout sync
                coroutineScope.launch {
                    try {
                        Timber.d("WorkoutSyncIntegration: Triggering workout sync for user: $userId")
                        val syncResult = syncCoordinator.triggerImmediateSync(userId)
                        syncResult.fold(
                            onSuccess = {
                                Timber.d("WorkoutSyncIntegration: Workout sync initiated successfully for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("WorkoutSyncIntegration: Workout sync failed for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "WorkoutSyncIntegration: Exception during workout sync for user: $userId")
                    }
                }
            },
            onForceSyncAll = { 
                // TODO Line 166: Implement force workout sync
                coroutineScope.launch {
                    try {
                        Timber.d("WorkoutSyncIntegration: Triggering force workout sync for user: $userId")
                        // Force sync by canceling existing work and triggering new sync
                        syncManager.cancelSync()
                        val syncResult = syncCoordinator.triggerImmediateSync(userId)
                        syncResult.fold(
                            onSuccess = {
                                Timber.d("WorkoutSyncIntegration: Force workout sync initiated successfully for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("WorkoutSyncIntegration: Force workout sync failed for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "WorkoutSyncIntegration: Exception during force workout sync for user: $userId")
                    }
                }
            },
            onToggleAutoSync = { enabled ->
                coroutineScope.launch {
                    try {
                        Timber.d("WorkoutSyncIntegration: Toggling auto-sync to $enabled for user: $userId")
                        // Auto-sync toggle implementation using available API
                        if (enabled) {
                            syncCoordinator.schedulePeriodicSync(userId)
                            Timber.d("WorkoutSyncIntegration: Auto-sync enabled for user: $userId")
                        } else {
                            // Cancel sync and log the toggle
                            syncManager.cancelSync()
                            Timber.d("WorkoutSyncIntegration: Auto-sync disabled for user: $userId")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "WorkoutSyncIntegration: Exception during auto-sync toggle for user: $userId")
                    }
                }
            },
            onSyncSettings = {
                // Sync settings navigation removed per user request - just sync the information
                coroutineScope.launch {
                    try {
                        Timber.d("WorkoutSyncIntegration: Sync settings requested - triggering workout data sync for user: $userId")
                        val syncResult = syncCoordinator.triggerImmediateSync(userId)
                        syncResult.fold(
                            onSuccess = {
                                Timber.d("WorkoutSyncIntegration: Workout data sync initiated from settings action for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("WorkoutSyncIntegration: Workout data sync failed from settings action for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "WorkoutSyncIntegration: Exception during workout data sync from settings action for user: $userId")
                    }
                }
            }
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