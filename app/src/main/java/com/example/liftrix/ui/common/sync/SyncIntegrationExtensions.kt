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
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SyncRepository
import com.example.liftrix.ui.common.sync.SyncHistoryItem
import com.example.liftrix.ui.common.sync.SyncHistoryStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.State
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
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
    settingsRepository: SettingsRepository = hiltViewModel(),
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
        lastSyncTime = getLastSyncTime(userId, syncRepository = hiltViewModel()),
        // Using SyncStatusRepository to get the last profile sync time
        unsyncedItemCount = unsyncedProfileItemCount,
        isAutoSyncEnabled = getAutoSyncEnabled(userId, settingsRepository = hiltViewModel()),
        // Using SettingsRepository to get auto-sync preference from user settings
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
                    // Update user settings with proper persistence
                    val updateResult = settingsRepository.updateAutoSyncEnabled(userId, enabled)
                    updateResult.fold(
                        onSuccess = {
                            if (enabled) {
                                syncCoordinator.schedulePeriodicSync(userId)
                                Timber.d("ProfileSyncIntegration: Auto-sync enabled for user: $userId")
                            } else {
                                syncManager.cancelSync()
                                Timber.d("ProfileSyncIntegration: Auto-sync disabled for user: $userId")
                            }
                        },
                        onFailure = { error ->
                            Timber.e("ProfileSyncIntegration: Failed to update auto-sync setting for user $userId: $error")
                        }
                    )
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
    settingsRepository: SettingsRepository = hiltViewModel(),
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
    val isAutoSyncEnabled = getAutoSyncEnabled(userId, settingsRepository)
    
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
            lastSyncTime = getLastWorkoutSyncTime(userId, syncRepository = hiltViewModel()),
            // Using SyncRepository to get the last workout sync time
            unsyncedItemCount = unsyncedWorkoutItemCount,
            isAutoSyncEnabled = isAutoSyncEnabled,
            onSyncNow = { 
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
                        // Update user settings with proper persistence
                        val updateResult = settingsRepository.updateAutoSyncEnabled(userId, enabled)
                        updateResult.fold(
                            onSuccess = {
                                if (enabled) {
                                    syncCoordinator.schedulePeriodicSync(userId)
                                    Timber.d("WorkoutSyncIntegration: Auto-sync enabled for user: $userId")
                                } else {
                                    syncManager.cancelSync()
                                    Timber.d("WorkoutSyncIntegration: Auto-sync disabled for user: $userId")
                                }
                            },
                            onFailure = { error ->
                                Timber.e("WorkoutSyncIntegration: Failed to update auto-sync setting for user $userId: $error")
                            }
                        )
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
    // Get active workout sync status from RealtimeSyncManager
    val realtimeSyncManager: RealtimeSyncManager = hiltViewModel()
    val realtimeSyncState by realtimeSyncManager.syncState.collectAsState()
    
    val syncStatus = when (val state = realtimeSyncState) {
        is RealtimeSyncManager.SyncState.Active -> com.example.liftrix.sync.SyncStatus.Success(1)
        is RealtimeSyncManager.SyncState.Syncing -> com.example.liftrix.sync.SyncStatus.Syncing
        is RealtimeSyncManager.SyncState.Error -> com.example.liftrix.sync.SyncStatus.Error(state.error)
        else -> com.example.liftrix.sync.SyncStatus.Idle
    }
    
    SyncStatusIndicator(
        syncStatus = syncStatus,
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
    // Get analytics-specific sync status from SyncManager
    val syncManager: SyncManager = hiltViewModel()
    val analyticsStatus by syncManager.getAnalyticsSyncStatus().collectAsState(
        initial = com.example.liftrix.sync.SyncStatus.Idle
    )
    
    val combinedStatus = CombinedSyncStatus(
        workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
        analyticsStatus = analyticsStatus
    )
    
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
    ) {
        // Analytics sync progress
        SyncProgressBar(
            combinedStatus = combinedStatus,
            currentOperation = "Updating analytics",
            progress = null,
            showDetailedProgress = true,
            autoHide = true
        )
        
        // Compact sync control for manual analytics refresh
        CompactSyncControl(
            combinedStatus = combinedStatus,
            onSyncNow = {
                // Implement analytics sync functionality using SyncManager
                coroutineScope.launch {
                    try {
                        Timber.d("ProgressDashboardSyncIntegration: Triggering analytics sync for user: $userId")
                        val result = syncManager.syncAnalyticsNow(userId)
                        result.fold(
                            onSuccess = {
                                Timber.d("ProgressDashboardSyncIntegration: Analytics sync initiated successfully for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("ProgressDashboardSyncIntegration: Analytics sync failed for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "ProgressDashboardSyncIntegration: Exception during analytics sync for user: $userId")
                    }
                }
            }
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
    syncCoordinator: SyncCoordinator = hiltViewModel(),
    syncManager: SyncManager = hiltViewModel(),
    syncRepository: SyncRepository = hiltViewModel(),
    settingsRepository: SettingsRepository = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Get combined sync status from sync manager
    val combinedSyncStatus by syncManager.getCombinedSyncStatus().collectAsState(
        initial = CombinedSyncStatus(
            workoutStatus = com.example.liftrix.sync.SyncStatus.Idle,
            analyticsStatus = com.example.liftrix.sync.SyncStatus.Idle
        )
    )
    
    // Get unsynced item count
    val unsyncedItemCount by remember(userId) {
        flow {
            try {
                val workoutCount = syncManager.getUnsyncedCount(userId)
                val analyticsCount = syncManager.getUnsyncedAnalyticsCount(userId)
                emit(workoutCount + analyticsCount)
            } catch (e: Exception) {
                Timber.e(e, "SettingsSyncIntegration: Failed to get unsynced count for user: $userId")
                emit(0)
            }
        }
    }.collectAsState(initial = 0)
    
    Column(
        modifier = modifier
    ) {
        // Main sync controls
        ManualSyncControls(
            combinedStatus = combinedSyncStatus,
            lastSyncTime = getLastSyncTime(userId, syncRepository),
            unsyncedItemCount = unsyncedItemCount,
            isAutoSyncEnabled = getAutoSyncEnabled(userId, settingsRepository),
            onSyncNow = {
                // Implement sync functionality
                coroutineScope.launch {
                    try {
                        Timber.d("SettingsSyncIntegration: Triggering settings sync for user: $userId")
                        val result = syncCoordinator.triggerImmediateSync(userId)
                        result.fold(
                            onSuccess = {
                                Timber.d("SettingsSyncIntegration: Settings sync initiated successfully for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("SettingsSyncIntegration: Settings sync failed for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "SettingsSyncIntegration: Exception during settings sync for user: $userId")
                    }
                }
            },
            onForceSyncAll = {
                // Implement force sync functionality
                coroutineScope.launch {
                    try {
                        Timber.d("SettingsSyncIntegration: Triggering force settings sync for user: $userId")
                        // Force sync by canceling existing work and triggering new sync
                        syncManager.cancelSync()
                        val result = syncCoordinator.triggerImmediateSync(userId)
                        result.fold(
                            onSuccess = {
                                Timber.d("SettingsSyncIntegration: Force settings sync initiated successfully for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("SettingsSyncIntegration: Force settings sync failed for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "SettingsSyncIntegration: Exception during force settings sync for user: $userId")
                    }
                }
            },
            onToggleAutoSync = { enabled ->
                // Implement auto-sync toggle functionality with settings persistence
                coroutineScope.launch {
                    try {
                        Timber.d("SettingsSyncIntegration: Toggling auto-sync to $enabled for user: $userId")
                        // Update user settings
                        val updateResult = settingsRepository.updateAutoSyncEnabled(userId, enabled)
                        updateResult.fold(
                            onSuccess = {
                                if (enabled) {
                                    syncCoordinator.schedulePeriodicSync(userId)
                                    Timber.d("SettingsSyncIntegration: Auto-sync enabled for user: $userId")
                                } else {
                                    syncManager.cancelSync()
                                    Timber.d("SettingsSyncIntegration: Auto-sync disabled for user: $userId")
                                }
                            },
                            onFailure = { error ->
                                Timber.e("SettingsSyncIntegration: Failed to update auto-sync setting for user $userId: $error")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "SettingsSyncIntegration: Exception during auto-sync toggle for user: $userId")
                    }
                }
            },
            onSyncSettings = {
                // Navigate to advanced sync settings - trigger comprehensive sync
                coroutineScope.launch {
                    try {
                        Timber.d("SettingsSyncIntegration: Advanced sync settings requested - triggering comprehensive sync for user: $userId")
                        val result = syncCoordinator.triggerImmediateSync(userId)
                        result.fold(
                            onSuccess = {
                                Timber.d("SettingsSyncIntegration: Comprehensive sync initiated from advanced settings for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("SettingsSyncIntegration: Comprehensive sync failed from advanced settings for user $userId: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "SettingsSyncIntegration: Exception during comprehensive sync from advanced settings for user: $userId")
                    }
                }
            }
        )
        
        // Sync history
        val syncHistory by getSyncHistory(userId, syncRepository = hiltViewModel())
        SyncHistorySection(
            syncHistory = syncHistory
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
 * Helper functions for sync status and preferences
 */

/**
 * Gets the last sync time for profile data
 */
@Composable
private fun getLastSyncTime(userId: String, syncRepository: SyncRepository): LocalDateTime? {
    val syncStatus by syncRepository.observeSyncStatus().collectAsState(
        initial = com.example.liftrix.domain.repository.SyncStatus(
            isSyncing = false,
            lastSyncTime = null,
            pendingItems = 0,
            errors = emptyList()
        )
    )
    
    return remember(syncStatus) {
        syncStatus.lastSyncTime?.let { timestamp ->
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
            )
        }
    }
}

/**
 * Gets the auto-sync enabled status from user settings
 */
@Composable
private fun getAutoSyncEnabled(userId: String, settingsRepository: SettingsRepository): Boolean {
    val userSettings by settingsRepository.getUserSettings(userId).collectAsState(initial = null)
    
    return remember(userSettings) {
        userSettings?.autoSyncEnabled ?: true // Default to enabled for invisible sync UX
    }
}

/**
 * Gets the last workout sync time from sync repository
 */
@Composable
private fun getLastWorkoutSyncTime(userId: String, syncRepository: SyncRepository): LocalDateTime? {
    val syncStatus by syncRepository.observeSyncStatus().collectAsState(
        initial = com.example.liftrix.domain.repository.SyncStatus(
            isSyncing = false,
            lastSyncTime = null,
            pendingItems = 0,
            errors = emptyList()
        )
    )
    
    return remember(syncStatus) {
        syncStatus.lastSyncTime?.let { timestamp ->
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
            )
        }
    }
}

/**
 * Gets sync history from repository
 */
@Composable
private fun getSyncHistory(userId: String, syncRepository: SyncRepository): State<List<SyncHistoryItem>> {
    return remember(userId) {
        flow {
            try {
                val syncStatus = syncRepository.observeSyncStatus().first()
                val history = buildList {
                    syncStatus.lastSyncTime?.let { timestamp ->
                        add(SyncHistoryItem(
                            timestamp = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(timestamp),
                                ZoneId.systemDefault()
                            ),
                            operation = "Full Sync",
                            status = if (syncStatus.errors.isEmpty()) SyncHistoryStatus.SUCCESS else SyncHistoryStatus.ERROR,
                            itemCount = syncStatus.pendingItems,
                            duration = 0L // Could be calculated if stored
                        ))
                    }
                }
                emit(history)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get sync history for user: $userId")
                emit(emptyList<SyncHistoryItem>())
            }
        }
    }.collectAsState(initial = emptyList())
}

/**
 * Extension functions for existing ViewModels to integrate sync awareness
 */

/**
 * Extension for ProfileViewModel to add sync status
 */
fun getProfileViewModelSyncStatus(userId: String, syncStatusRepository: SyncStatusRepository): StateFlow<com.example.liftrix.sync.SyncStatus> {
    return syncStatusRepository.getSyncStatus(userId).map { status ->
        status ?: com.example.liftrix.sync.SyncStatus.Idle
    }.stateIn(
        scope = kotlinx.coroutines.GlobalScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.liftrix.sync.SyncStatus.Idle
    )
}

/**
 * Extension for WorkoutViewModel to add sync status  
 */
fun getWorkoutViewModelSyncStatus(userId: String, syncManager: SyncManager): StateFlow<com.example.liftrix.sync.SyncStatus> {
    return syncManager.getSyncStatus().stateIn(
        scope = kotlinx.coroutines.GlobalScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.liftrix.sync.SyncStatus.Idle
    )
}

/**
 * Extension for ProgressDashboardViewModel to add analytics sync status
 */
fun getProgressDashboardViewModelAnalyticsSyncStatus(userId: String, syncManager: SyncManager): StateFlow<com.example.liftrix.sync.SyncStatus> {
    return syncManager.getAnalyticsSyncStatus().stateIn(
        scope = kotlinx.coroutines.GlobalScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.liftrix.sync.SyncStatus.Idle
    )
}