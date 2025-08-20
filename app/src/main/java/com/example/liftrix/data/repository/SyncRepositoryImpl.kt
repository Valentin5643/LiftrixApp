package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SyncRepository
import com.example.liftrix.domain.repository.SyncStatus
import com.example.liftrix.domain.repository.SyncResult
import com.example.liftrix.domain.repository.WorkoutUpdate
import com.example.liftrix.domain.repository.SyncError
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SyncRepository that manages data synchronization between Room and Firebase.
 * 
 * Follows offline-first architecture where Room database serves as the single source of truth
 * and Firebase acts as the network synchronization layer.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userProfileDao: UserProfileDao,
    private val workoutDao: WorkoutDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val achievementDao: AchievementDao,
    private val syncQueueDao: SyncQueueDao
) : SyncRepository {

    private val _syncStatus = MutableStateFlow(
        SyncStatus(
            isSyncing = false,
            lastSyncTime = null,
            pendingItems = 0,
            errors = emptyList()
        )
    )

    private val _realtimeWorkoutUpdates = MutableStateFlow<WorkoutUpdate?>(null)

    override suspend fun syncAll(userId: String): LiftrixResult<SyncResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SYNC_ALL_FAILED",
                errorMessage = "Failed to sync all user data: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SYNC_ALL",
                    "user_id" to userId
                )
            )
        }
    ) {
        updateSyncStatus(isSyncing = true)
        
        var totalSuccessful = 0
        var totalFailed = 0
        var totalConflicts = 0

        try {
            // Sync profile data
            syncProfile(userId).fold(
                onSuccess = { totalSuccessful++ },
                onFailure = { 
                    totalFailed++ 
                    Timber.e(it, "Profile sync failed for user: $userId")
                }
            )

            // Sync workouts
            syncWorkouts(userId).fold(
                onSuccess = { totalSuccessful++ },
                onFailure = { 
                    totalFailed++ 
                    Timber.e(it, "Workout sync failed for user: $userId")
                }
            )

            // Sync templates
            syncTemplates(userId).fold(
                onSuccess = { totalSuccessful++ },
                onFailure = { 
                    totalFailed++ 
                    Timber.e(it, "Template sync failed for user: $userId")
                }
            )

            // Sync achievements
            syncAchievements(userId).fold(
                onSuccess = { totalSuccessful++ },
                onFailure = { 
                    totalFailed++ 
                    Timber.e(it, "Achievement sync failed for user: $userId")
                }
            )

            val result = SyncResult(
                successful = totalSuccessful,
                failed = totalFailed,
                conflicts = totalConflicts
            )

            updateSyncStatus(isSyncing = false, lastSyncTime = System.currentTimeMillis())
            result
        } catch (e: Exception) {
            updateSyncStatus(isSyncing = false)
            throw e
        }
    }

    override suspend fun syncWorkouts(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to sync workouts: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SYNC_WORKOUTS",
                    "user_id" to userId
                )
            )
        }
    ) {
        // Implementation will be completed in FB-003 (Firebase DataSource)
        // For now, just mark as synced in local database
        val unsyncedWorkouts = workoutDao.getUnsyncedWorkoutsForUser(userId)
        if (unsyncedWorkouts.isNotEmpty()) {
            val workoutIds = unsyncedWorkouts.map { it.id }
            workoutDao.markWorkoutsAsSyncedForUser(workoutIds, userId, System.currentTimeMillis())
        }
        
        Timber.d("Synced ${unsyncedWorkouts.size} workouts for user: $userId")
    }

    override suspend fun syncTemplates(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to sync templates: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SYNC_TEMPLATES",
                    "user_id" to userId
                )
            )
        }
    ) {
        // Implementation will be completed in FB-003 (Firebase DataSource)
        val unsyncedTemplates = workoutTemplateDao.getUnsyncedTemplates(userId)
        if (unsyncedTemplates.isNotEmpty()) {
            val templateIds = unsyncedTemplates.map { it.id }
            workoutTemplateDao.markTemplatesAsSynced(templateIds)
        }
        
        Timber.d("Synced ${unsyncedTemplates.size} templates for user: $userId")
    }

    override suspend fun syncAchievements(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to sync achievements: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SYNC_ACHIEVEMENTS",
                    "user_id" to userId
                )
            )
        }
    ) {
        // Implementation will be completed in FB-003 (Firebase DataSource)
        val unsyncedAchievements = achievementDao.getUnsyncedAchievements(userId)
        unsyncedAchievements.forEach { achievement ->
            achievementDao.markAsSynced(achievement.id, System.currentTimeMillis())
        }
        
        Timber.d("Synced ${unsyncedAchievements.size} achievements for user: $userId")
    }

    override suspend fun syncProfile(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to sync profile: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SYNC_PROFILE",
                    "user_id" to userId
                )
            )
        }
    ) {
        // Implementation will be completed in FB-003 (Firebase DataSource)
        val unsyncedProfiles = userProfileDao.getUnsyncedProfiles(userId)
        if (unsyncedProfiles.isNotEmpty()) {
            // For now, just mark as synced locally
            // TODO: Implement actual Firebase sync in FB-003
            Timber.d("Found ${unsyncedProfiles.size} unsynced profiles for user: $userId")
        }
    }

    override fun observeRealtimeWorkout(workoutId: String): Flow<WorkoutUpdate> {
        // Implementation will be completed in INF-004 (RealtimeSyncService)
        return _realtimeWorkoutUpdates.asStateFlow().filterNotNull()
    }

    override fun enableRealtimeSync(userId: String) {
        // Implementation will be completed in INF-004 (RealtimeSyncService)
        Timber.d("Real-time sync enabled for user: $userId")
    }

    override fun disableRealtimeSync() {
        // Implementation will be completed in INF-004 (RealtimeSyncService)
        Timber.d("Real-time sync disabled")
    }

    override fun observeSyncStatus(): Flow<SyncStatus> = _syncStatus.asStateFlow()

    override suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to get unsynced count: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_UNSYNCED_COUNT",
                    "user_id" to userId
                )
            )
        }
    ) {
        syncQueueDao.getPendingItemsCount(userId)
    }

    override suspend fun clearSyncQueue(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to clear sync queue: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CLEAR_SYNC_QUEUE",
                    "user_id" to userId
                )
            )
        }
    ) {
        syncQueueDao.clearQueueForUser(userId)
        updateSyncStatus(pendingItems = 0)
        Timber.d("Cleared sync queue for user: $userId")
    }

    private fun updateSyncStatus(
        isSyncing: Boolean = _syncStatus.value.isSyncing,
        lastSyncTime: Long? = _syncStatus.value.lastSyncTime,
        pendingItems: Int = _syncStatus.value.pendingItems,
        errors: List<SyncError> = _syncStatus.value.errors
    ) {
        _syncStatus.value = SyncStatus(
            isSyncing = isSyncing,
            lastSyncTime = lastSyncTime,
            pendingItems = pendingItems,
            errors = errors
        )
    }
}

// Extension function to create Flow.filterNotNull() equivalent for our case
private fun <T> Flow<T?>.filterNotNull(): Flow<T> = kotlinx.coroutines.flow.flow {
    collect { value -> if (value != null) emit(value) }
}