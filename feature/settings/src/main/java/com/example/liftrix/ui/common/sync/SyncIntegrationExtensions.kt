package com.example.liftrix.ui.common.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SyncRepository
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.service.CombinedSyncStatus
import com.example.liftrix.domain.service.SyncStatus
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun getCurrentUserId(syncStatusViewModel: SyncStatusViewModel = hiltViewModel()): Flow<String?> {
    return syncStatusViewModel.currentUserId
}

@Composable
fun WithSyncAwareness(
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
    isOffline: Boolean = false,
    showCompactIndicator: Boolean = false,
    onNavigateToSyncSettings: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val combinedSyncStatus by syncStatusViewModel.combinedSyncStatus.collectAsStateWithLifecycle(
        initialValue = CombinedSyncStatus()
    )
    val currentUserId by syncStatusViewModel.currentUserId.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val unsyncedItemCount by remember(currentUserId) {
        flow {
            emit(syncStatusViewModel.getUnsyncedCount() + syncStatusViewModel.getUnsyncedAnalyticsCount())
        }
    }.collectAsStateWithLifecycle(initialValue = 0)

    val bannerStatus = when {
        combinedSyncStatus.workoutStatus is SyncStatus.Error -> combinedSyncStatus.workoutStatus
        combinedSyncStatus.analyticsStatus is SyncStatus.Error -> combinedSyncStatus.analyticsStatus
        else -> null
    }

    Column {
        OfflineBanner(
            isOffline = isOffline,
            syncState = bannerStatus,
            unsyncedItemCount = unsyncedItemCount,
            onRetrySync = {
                coroutineScope.launch { syncStatusViewModel.triggerSync() }
            },
            onDismiss = null
        )

        if (showCompactIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LiftrixSpacing.screenPadding)
            ) {
                CompactOfflineIndicator(
                    isOffline = isOffline,
                    syncState = bannerStatus,
                    onClick = onNavigateToSyncSettings
                )
            }
        }

        content()
    }
}

@Composable
fun ProfileSyncIntegration(
    userId: String,
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
    syncRepository: SyncRepository = hiltViewModel(),
    settingsRepository: SettingsRepository = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val profileSyncStatus by syncStatusViewModel.syncStatus.collectAsStateWithLifecycle(initialValue = SyncStatus.Idle)
    SyncControlBlock(
        userId = userId,
        combinedStatus = CombinedSyncStatus(profileSyncStatus, profileSyncStatus),
        syncStatusViewModel = syncStatusViewModel,
        syncRepository = syncRepository,
        settingsRepository = settingsRepository,
        modifier = modifier
    )
}

@Composable
fun WorkoutSyncIntegration(
    userId: String,
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
    syncRepository: SyncRepository = hiltViewModel(),
    settingsRepository: SettingsRepository = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val combinedSyncStatus by syncStatusViewModel.combinedSyncStatus.collectAsStateWithLifecycle(
        initialValue = CombinedSyncStatus()
    )

    Column(modifier = modifier) {
        SyncProgressBar(
            combinedStatus = combinedSyncStatus,
            currentOperation = "Syncing workouts",
            progress = null,
            showDetailedProgress = false,
            autoHide = true
        )
        SyncControlBlock(
            userId = userId,
            combinedStatus = combinedSyncStatus,
            syncStatusViewModel = syncStatusViewModel,
            syncRepository = syncRepository,
            settingsRepository = settingsRepository
        )
    }
}

@Composable
fun ActiveWorkoutSyncIntegration(
    workoutSessionId: String,
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val syncStatus by syncStatusViewModel.syncStatus.collectAsStateWithLifecycle(initialValue = SyncStatus.Idle)
    SyncStatusIndicator(
        syncStatus = syncStatus,
        showText = true,
        autoHideSuccess = true,
        contentDescription = "Workout session sync status",
        modifier = modifier
    )
}

@Composable
fun ProgressDashboardSyncIntegration(
    userId: String,
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val combinedStatus by syncStatusViewModel.combinedSyncStatus.collectAsStateWithLifecycle(
        initialValue = CombinedSyncStatus()
    )

    Column(modifier = modifier) {
        SyncProgressBar(
            combinedStatus = combinedStatus,
            currentOperation = "Updating analytics",
            progress = null,
            showDetailedProgress = true,
            autoHide = true
        )
        CompactSyncControl(
            combinedStatus = combinedStatus,
            onSyncNow = { syncStatusViewModel.triggerAnalyticsSync() }
        )
    }
}

@Composable
fun SettingsSyncIntegration(
    userId: String,
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
    syncRepository: SyncRepository = hiltViewModel(),
    settingsRepository: SettingsRepository = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val combinedSyncStatus by syncStatusViewModel.combinedSyncStatus.collectAsStateWithLifecycle(
        initialValue = CombinedSyncStatus()
    )

    Column(modifier = modifier) {
        SyncControlBlock(
            userId = userId,
            combinedStatus = combinedSyncStatus,
            syncStatusViewModel = syncStatusViewModel,
            syncRepository = syncRepository,
            settingsRepository = settingsRepository
        )

        val syncHistory by getSyncHistory(userId, syncRepository)
        SyncHistorySection(syncHistory = syncHistory)
    }
}

@Composable
private fun SyncControlBlock(
    userId: String,
    combinedStatus: CombinedSyncStatus,
    syncStatusViewModel: SyncStatusViewModel,
    syncRepository: SyncRepository,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val unsyncedItemCount by remember(userId) {
        flow {
            emit(syncStatusViewModel.getUnsyncedCount() + syncStatusViewModel.getUnsyncedAnalyticsCount())
        }
    }.collectAsStateWithLifecycle(initialValue = 0)

    ManualSyncControls(
        combinedStatus = combinedStatus,
        lastSyncTime = getLastSyncTime(userId, syncRepository),
        unsyncedItemCount = unsyncedItemCount,
        isAutoSyncEnabled = getAutoSyncEnabled(userId, settingsRepository),
        onSyncNow = { syncStatusViewModel.triggerSync() },
        onForceSyncAll = {
            syncStatusViewModel.cancelSync()
            syncStatusViewModel.triggerSync()
        },
        onToggleAutoSync = { enabled ->
            coroutineScope.launch {
                settingsRepository.updateAutoSyncEnabled(userId, enabled).onFailure { error ->
                    Timber.e(error, "Failed to update auto-sync setting for user $userId")
                }
            }
        },
        onSyncSettings = { syncStatusViewModel.triggerSync() },
        modifier = modifier
    )
}

@Composable
fun WorkoutItemSyncIndicator(
    workoutId: String,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    CompactSyncIndicator(syncStatus = syncStatus, modifier = modifier)
}

@Composable
fun AchievementSyncIndicator(
    achievementId: String,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    CompactSyncIndicator(syncStatus = syncStatus, modifier = modifier)
}

@Composable
fun TemplateSyncIntegration(
    templateId: String,
    modifier: Modifier = Modifier
) {
    SyncStatusIndicator(
        syncStatus = SyncStatus.Idle,
        showText = false,
        autoHideSuccess = true,
        contentDescription = "Template sync status",
        modifier = modifier
    )
}

@Composable
private fun getLastSyncTime(userId: String, syncRepository: SyncRepository): LocalDateTime? {
    val syncStatus by syncRepository.observeSyncStatus().collectAsStateWithLifecycle(
        initialValue = com.example.liftrix.domain.repository.SyncStatus(
            isSyncing = false,
            lastSyncTime = null,
            pendingItems = 0,
            errors = emptyList()
        )
    )

    return remember(syncStatus) {
        syncStatus.lastSyncTime?.let { timestamp ->
            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        }
    }
}

@Composable
private fun getAutoSyncEnabled(userId: String, settingsRepository: SettingsRepository): Boolean {
    val userSettings by settingsRepository.getUserSettings(userId).collectAsStateWithLifecycle(initialValue = null)
    return remember(userSettings) { userSettings?.autoSyncEnabled ?: true }
}

@Composable
private fun getSyncHistory(userId: String, syncRepository: SyncRepository): State<List<SyncHistoryItem>> {
    return remember(userId) {
        flow {
            try {
                val syncStatus = syncRepository.observeSyncStatus().first()
                emit(
                    syncStatus.lastSyncTime?.let { timestamp ->
                        listOf(
                            SyncHistoryItem(
                                timestamp = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(timestamp),
                                    ZoneId.systemDefault()
                                ),
                                operation = "Full Sync",
                                status = if (syncStatus.errors.isEmpty()) {
                                    SyncHistoryStatus.SUCCESS
                                } else {
                                    SyncHistoryStatus.ERROR
                                },
                                itemCount = syncStatus.pendingItems,
                                duration = 0L
                            )
                        )
                    } ?: emptyList()
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get sync history for user: $userId")
                emit(emptyList())
            }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
}

fun getProfileViewModelSyncStatus(
    userId: String,
    syncStatusRepository: SyncStatusRepository,
    scope: CoroutineScope
) = syncStatusRepository.getSyncStatus(userId).stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SyncStatus.Idle
)

fun getWorkoutViewModelSyncStatus(
    userId: String,
    syncScheduler: SyncScheduler,
    scope: CoroutineScope
) = syncScheduler.observeWorkoutSyncStatus().stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SyncStatus.Idle
)

fun getProgressDashboardViewModelAnalyticsSyncStatus(
    userId: String,
    syncScheduler: SyncScheduler,
    scope: CoroutineScope
) = syncScheduler.observeAnalyticsSyncStatus().stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SyncStatus.Idle
)
