package com.example.liftrix.ui.common.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for social sync status presentation.
 *
 * The feature module stays on domain sync contracts; WorkManager tags, DAOs, and
 * concrete social repositories remain owned by runtime modules.
 */
@HiltViewModel
class SocialSyncStatusViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

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

    fun retrySync(syncType: SocialSyncType) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()?.value
            if (userId == null) {
                Timber.w("Cannot retry social sync without an authenticated user")
                return@launch
            }

            runSocialSync(userId, syncType, forceSync = false)
        }
    }

    fun forceSyncAll() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()?.value
            if (userId == null) {
                Timber.w("Cannot force social sync without an authenticated user")
                return@launch
            }

            runSocialSync(userId, SocialSyncType.COMBINED, forceSync = true)
        }
    }

    private fun runSocialSync(userId: String, syncType: SocialSyncType, forceSync: Boolean) {
        setStatus(syncType, SocialSyncStatus.Syncing)

        try {
            when (syncType) {
                SocialSyncType.PROFILE -> syncScheduler.enqueueSocialProfileSync(userId, forceSync)
                SocialSyncType.FOLLOWS -> syncScheduler.enqueueFollowRelationshipSync(userId, forceSync)
                SocialSyncType.POSTS -> syncScheduler.enqueueWorkoutPostSync(userId, forceSync)
                SocialSyncType.GYM_BUDDIES -> syncScheduler.enqueueGymBuddySync(userId, forceSync)
                SocialSyncType.ENGAGEMENT -> syncScheduler.enqueueWorkoutPostSync(userId, forceSync)
                SocialSyncType.COMBINED -> {
                    syncScheduler.enqueueSocialProfileSync(userId, forceSync)
                    syncScheduler.enqueueFollowRelationshipSync(userId, forceSync)
                    syncScheduler.enqueueWorkoutPostSync(userId, forceSync)
                    syncScheduler.enqueueGymBuddySync(userId, forceSync)
                }
            }

            setStatus(syncType, SocialSyncStatus.Success(0))
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue social sync for $syncType")
            setStatus(syncType, SocialSyncStatus.Error(e.message ?: "Social sync failed"))
        }
    }

    private fun setStatus(syncType: SocialSyncType, status: SocialSyncStatus) {
        when (syncType) {
            SocialSyncType.PROFILE -> _profileSyncStatus.value = status
            SocialSyncType.FOLLOWS -> _followsSyncStatus.value = status
            SocialSyncType.POSTS -> _postsSyncStatus.value = status
            SocialSyncType.GYM_BUDDIES -> _gymBuddiesSyncStatus.value = status
            SocialSyncType.ENGAGEMENT -> _postsSyncStatus.value = status
            SocialSyncType.COMBINED -> {
                _profileSyncStatus.value = status
                _followsSyncStatus.value = status
                _postsSyncStatus.value = status
                _gymBuddiesSyncStatus.value = status
            }
        }

        _overallSyncStatus.value = combineSocialStatus(
            profileSyncStatus = _profileSyncStatus.value,
            followsSyncStatus = _followsSyncStatus.value,
            postsSyncStatus = _postsSyncStatus.value,
            gymBuddiesStatus = _gymBuddiesSyncStatus.value
        )
    }

    private fun combineSocialStatus(
        profileSyncStatus: SocialSyncStatus,
        followsSyncStatus: SocialSyncStatus,
        postsSyncStatus: SocialSyncStatus,
        gymBuddiesStatus: SocialSyncStatus
    ): SocialSyncStatus {
        val statuses = listOf(profileSyncStatus, followsSyncStatus, postsSyncStatus, gymBuddiesStatus)
        return when {
            statuses.any { it is SocialSyncStatus.Syncing } -> SocialSyncStatus.Syncing
            statuses.any { it is SocialSyncStatus.Error } -> SocialSyncStatus.Error("Some social features failed to sync")
            statuses.any { it is SocialSyncStatus.Offline } ->
                SocialSyncStatus.Offline(statuses.filterIsInstance<SocialSyncStatus.Offline>().sumOf { it.pendingItemsCount })
            else -> {
                val totalSynced = statuses.filterIsInstance<SocialSyncStatus.Success>().sumOf { it.syncedItemsCount }
                if (totalSynced > 0) SocialSyncStatus.Success(totalSynced) else SocialSyncStatus.Idle
            }
        }
    }
}
