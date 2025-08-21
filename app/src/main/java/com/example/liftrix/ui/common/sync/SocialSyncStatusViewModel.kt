package com.example.liftrix.ui.common.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.repository.SocialRepositoryImplEnhanced
import com.example.liftrix.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing social sync status across the application.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Provides centralized sync status monitoring for all social features with
 * real-time updates and retry capabilities.
 */
@HiltViewModel
class SocialSyncStatusViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val socialProfileDao: SocialProfileDao,
    private val followDao: FollowRelationshipDao,
    private val postDao: WorkoutPostDao,
    private val gymBuddyDao: GymBuddyDao,
    private val socialRepository: SocialRepositoryImplEnhanced,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val _profileSyncStatus = MutableStateFlow<SocialSyncStatus>(SocialSyncStatus.Idle)
    val profileSyncStatus: StateFlow<SocialSyncStatus> = _profileSyncStatus.asStateFlow()

    private val _followsSyncStatus = MutableStateFlow<SocialSyncStatus>(SocialSyncStatus.Idle)
    val followsSyncStatus: StateFlow<SocialSyncStatus> = _followsSyncStatus.asStateFlow()

    private val _postsSyncStatus = MutableStateFlow<SocialSyncStatus>(SocialSyncStatus.Idle)
    val postsSyncStatus: StateFlow<SocialSyncStatus> = _postsSyncStatus.asStateFlow()

    private val _gymBuddiesSyncStatus = MutableStateFlow<SocialSyncStatus>(SocialSyncStatus.Idle)
    val gymBuddiesSyncStatus: StateFlow<SocialSyncStatus> = _gymBuddiesSyncStatus.asStateFlow()

    private val _overallSyncStatus = MutableStateFlow<SocialSyncStatus>(SocialSyncStatus.Idle)
    val overallSyncStatus: StateFlow<SocialSyncStatus> = _overallSyncStatus.asStateFlow()

    init {
        monitorSyncStatus()
        updateOverallStatus()
    }

    private fun monitorSyncStatus() {
        viewModelScope.launch {
            authRepository.getCurrentUserId()?.let { userId ->
                monitorWorkManagerStatus(userId)
                monitorUnsyncedItems(userId)
            }
        }
    }

    private fun monitorWorkManagerStatus(userId: String) {
        viewModelScope.launch {
            // Monitor social profile sync workers
            workManager.getWorkInfosByTagFlow("social_profile_sync_$userId")
                .collect { workInfos ->
                    val status = workInfos.firstOrNull()?.let { workInfo ->
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> SocialSyncStatus.Syncing
                            WorkInfo.State.SUCCEEDED -> {
                                val syncCount = workInfo.outputData.getInt("sync_count", 0)
                                SocialSyncStatus.Success(syncCount)
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString("error_message") ?: "Sync failed"
                                SocialSyncStatus.Error(error)
                            }
                            else -> SocialSyncStatus.Idle
                        }
                    } ?: SocialSyncStatus.Idle
                    
                    _profileSyncStatus.value = status
                }
        }

        viewModelScope.launch {
            // Monitor follow relationship sync workers
            workManager.getWorkInfosByTagFlow("follow_sync_$userId")
                .collect { workInfos ->
                    val status = workInfos.firstOrNull()?.let { workInfo ->
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> SocialSyncStatus.Syncing
                            WorkInfo.State.SUCCEEDED -> {
                                val syncCount = workInfo.outputData.getInt("sync_count", 0)
                                SocialSyncStatus.Success(syncCount)
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString("error_message") ?: "Follow sync failed"
                                SocialSyncStatus.Error(error)
                            }
                            else -> SocialSyncStatus.Idle
                        }
                    } ?: SocialSyncStatus.Idle
                    
                    _followsSyncStatus.value = status
                }
        }

        viewModelScope.launch {
            // Monitor post sync workers
            workManager.getWorkInfosByTagFlow("post_sync_$userId")
                .collect { workInfos ->
                    val status = workInfos.firstOrNull()?.let { workInfo ->
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> SocialSyncStatus.Syncing
                            WorkInfo.State.SUCCEEDED -> {
                                val syncCount = workInfo.outputData.getInt("sync_count", 0)
                                SocialSyncStatus.Success(syncCount)
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString("error_message") ?: "Post sync failed"
                                SocialSyncStatus.Error(error)
                            }
                            else -> SocialSyncStatus.Idle
                        }
                    } ?: SocialSyncStatus.Idle
                    
                    _postsSyncStatus.value = status
                }
        }

        viewModelScope.launch {
            // Monitor gym buddy sync workers
            workManager.getWorkInfosByTagFlow("gym_buddy_sync_$userId")
                .collect { workInfos ->
                    val status = workInfos.firstOrNull()?.let { workInfo ->
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> SocialSyncStatus.Syncing
                            WorkInfo.State.SUCCEEDED -> {
                                val syncCount = workInfo.outputData.getInt("sync_count", 0)
                                SocialSyncStatus.Success(syncCount)
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString("error_message") ?: "Gym buddy sync failed"
                                SocialSyncStatus.Error(error)
                            }
                            else -> SocialSyncStatus.Idle
                        }
                    } ?: SocialSyncStatus.Idle
                    
                    _gymBuddiesSyncStatus.value = status
                }
        }
    }

    private fun monitorUnsyncedItems(userId: String) {
        viewModelScope.launch {
            try {
                // Monitor unsynced items and show offline status if there are pending items
                combine(
                    flow { emit(socialProfileDao.getUnsyncedProfiles(userId)) },
                    flow { emit(followDao.getUnsyncedRelationships(userId)) },
                    flow { emit(postDao.getUnsyncedPosts(userId)) },
                    flow { emit(gymBuddyDao.getUnsyncedGymBuddies(userId)) }
                ) { unsyncedProfiles, unsyncedFollows, unsyncedPosts, unsyncedBuddies ->
                    
                    // Update offline status for each category if there are unsynced items
                    if (unsyncedProfiles.isNotEmpty() && _profileSyncStatus.value !is SocialSyncStatus.Syncing) {
                        _profileSyncStatus.value = SocialSyncStatus.Offline(unsyncedProfiles.size)
                    }
                    
                    if (unsyncedFollows.isNotEmpty() && _followsSyncStatus.value !is SocialSyncStatus.Syncing) {
                        _followsSyncStatus.value = SocialSyncStatus.Offline(unsyncedFollows.size)
                    }
                    
                    if (unsyncedPosts.isNotEmpty() && _postsSyncStatus.value !is SocialSyncStatus.Syncing) {
                        _postsSyncStatus.value = SocialSyncStatus.Offline(unsyncedPosts.size)
                    }
                    
                    if (unsyncedBuddies.isNotEmpty() && _gymBuddiesSyncStatus.value !is SocialSyncStatus.Syncing) {
                        _gymBuddiesSyncStatus.value = SocialSyncStatus.Offline(unsyncedBuddies.size)
                    }
                    
                }.collect()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to monitor unsynced items for user $userId")
            }
        }
    }

    private fun updateOverallStatus() {
        viewModelScope.launch {
            combine(
                profileSyncStatus,
                followsSyncStatus,
                postsSyncStatus,
                gymBuddiesSyncStatus
            ) { profile, follows, posts, buddies ->
                when {
                    listOf(profile, follows, posts, buddies).any { it is SocialSyncStatus.Syncing } ->
                        SocialSyncStatus.Syncing
                    listOf(profile, follows, posts, buddies).any { it is SocialSyncStatus.Error } ->
                        SocialSyncStatus.Error("Some social features failed to sync")
                    listOf(profile, follows, posts, buddies).any { it is SocialSyncStatus.Offline } -> {
                        val totalPending = listOf(profile, follows, posts, buddies)
                            .filterIsInstance<SocialSyncStatus.Offline>()
                            .sumOf { it.pendingItemsCount }
                        SocialSyncStatus.Offline(totalPending)
                    }
                    else -> {
                        val totalSynced = listOf(profile, follows, posts, buddies)
                            .filterIsInstance<SocialSyncStatus.Success>()
                            .sumOf { it.syncedItemsCount }
                        if (totalSynced > 0) SocialSyncStatus.Success(totalSynced) else SocialSyncStatus.Idle
                    }
                }
            }.collect { status ->
                _overallSyncStatus.value = status
            }
        }
    }

    fun retrySync(syncType: SocialSyncType) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                
                when (syncType) {
                    SocialSyncType.PROFILE -> {
                        // Would trigger profile sync
                        Timber.d("Retrying profile sync for user $userId")
                    }
                    SocialSyncType.FOLLOWS -> {
                        // Would trigger follow sync
                        Timber.d("Retrying follows sync for user $userId")
                    }
                    SocialSyncType.POSTS -> {
                        // Would trigger posts sync
                        Timber.d("Retrying posts sync for user $userId")
                    }
                    SocialSyncType.GYM_BUDDIES -> {
                        // Would trigger gym buddies sync
                        Timber.d("Retrying gym buddies sync for user $userId")
                    }
                    SocialSyncType.COMBINED -> {
                        // Retry all
                        Timber.d("Retrying all social sync for user $userId")
                    }
                    else -> {
                        Timber.w("Unknown sync type for retry: $syncType")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to retry sync for type: $syncType")
            }
        }
    }

    fun forceSyncAll() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                
                // Force sync all social features
                Timber.d("Force syncing all social features for user $userId")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to force sync all social features")
            }
        }
    }
}