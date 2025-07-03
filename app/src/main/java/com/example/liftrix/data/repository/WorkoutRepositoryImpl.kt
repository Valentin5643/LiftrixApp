package com.example.liftrix.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.toSummary
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import com.example.liftrix.sync.WorkoutSyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutMapper: WorkoutMapper,
    private val workManager: WorkManager,
    private val socialRepository: SocialRepository,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor
) : WorkoutRepository {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        private const val MAX_FEED_WORKOUTS = 40
    }

    // Network connectivity and offline support
    override val isOffline: StateFlow<Boolean> = networkConnectivityMonitor.isConnected.map { !it }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = !networkConnectivityMonitor.isCurrentlyConnected()
        )

    override fun isCurrentlyOffline(): Boolean {
        return !networkConnectivityMonitor.isCurrentlyConnected()
    }

    // User-scoped methods implementation
    override fun getAllWorkoutsForUser(userId: String): Flow<List<Workout>> {
        return workoutDao.getAllWorkoutsForUser(userId).map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts().map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override suspend fun getWorkoutByIdForUser(id: WorkoutId, userId: String): Workout? {
        return runCatching {
            workoutDao.getWorkoutByIdForUser(id.value, userId)?.let(workoutMapper::toDomain)
        }.onFailure { e ->
            Timber.e(e, "Failed to get workout by ID for user: ${id.value}, user: $userId")
        }.getOrNull()
    }

    override suspend fun getWorkoutById(id: WorkoutId): Workout? {
        return runCatching {
            workoutDao.getWorkoutById(id.value)?.let(workoutMapper::toDomain)
        }.onFailure { e ->
            Timber.e(e, "Failed to get workout by ID: ${id.value}")
        }.getOrNull()
    }

    override fun getWorkoutsByDateForUser(date: LocalDate, userId: String): Flow<List<Workout>> {
        return date.format(DATE_FORMATTER).let { dateString ->
            workoutDao.getWorkoutsByDateForUser(dateString, userId).map { entities ->
                entities.map(workoutMapper::toDomain)
            }
        }
    }

    override fun getWorkoutsByDate(date: LocalDate): Flow<List<Workout>> {
        return date.format(DATE_FORMATTER).let { dateString ->
            workoutDao.getWorkoutsByDate(dateString).map { entities ->
                entities.map(workoutMapper::toDomain)
            }
        }
    }

    override suspend fun getActiveWorkoutForUser(userId: String): Workout? {
        return runCatching {
            workoutDao.getActiveWorkoutForUser(userId)?.let(workoutMapper::toDomain)
        }.onFailure { e ->
            Timber.e(e, "Failed to get active workout for user: $userId")
        }.getOrNull()
    }

    override suspend fun getUnsyncedCountForUser(userId: String): Int {
        return runCatching {
            workoutDao.getUnsyncedCountForUser(userId)
        }.onFailure { e ->
            Timber.e(e, "Failed to get unsynced count for user: $userId")
        }.getOrDefault(0)
    }

    override suspend fun getUnsyncedWorkoutsForUser(userId: String): List<Workout> {
        return try {
            workoutDao.getUnsyncedWorkoutsForUser(userId).map { entity ->
                workoutMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced workouts for user: $userId")
            emptyList()
        }
    }

    override suspend fun saveWorkout(workout: Workout): Result<Unit> {
        return try {
            // Validate workout has userId
            require(workout.userId.isNotBlank()) { "Workout must have a valid user ID" }
            
            Timber.v("💾 Starting workout save operation (should be silent)")
            
            // Save to local database first (offline-first approach)
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            workoutDao.insertWorkout(entity)
            
            Timber.v("✅ Workout DAO operation completed silently")
            
            // Sync completed workouts to Firebase for real-time feed
            if (workout.status == WorkoutStatus.COMPLETED) {
                syncWorkoutToFirebase(workout)
            }
            
            // Queue sync in background for this user
            queueSyncForUser(workout.userId)
            
            Timber.d("Workout saved successfully: ${workout.id.value} for user: ${workout.userId}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save workout: ${workout.id.value}")
            Result.failure(e)
        }
    }

    override suspend fun updateWorkout(workout: Workout): Result<Unit> {
        return try {
            // Validate workout has userId
            require(workout.userId.isNotBlank()) { "Workout must have a valid user ID" }
            
            // Update in local database
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            workoutDao.updateWorkout(entity)
            
            // Queue sync in background for this user
            queueSyncForUser(workout.userId)
            
            Timber.d("Workout updated successfully: ${workout.id.value} for user: ${workout.userId}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update workout: ${workout.id.value}")
            Result.failure(e)
        }
    }

    override suspend fun deleteWorkoutForUser(workoutId: WorkoutId, userId: String): Result<Unit> {
        return try {
            workoutDao.deleteWorkoutByIdForUser(workoutId.value, userId)
            
            Timber.d("Workout deleted successfully: ${workoutId.value} for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete workout: ${workoutId.value} for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun deleteAllWorkoutsForUser(userId: String): Result<Unit> {
        return try {
            workoutDao.deleteAllWorkoutsForUser(userId)
            
            // Cancel any pending sync work for this user
            workManager.cancelUniqueWork("${WorkoutSyncWorker.WORK_NAME}_$userId")
            
            Timber.d("All workouts cleared successfully for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all workouts for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun deleteWorkout(workoutId: WorkoutId): Result<Unit> {
        return try {
            workoutDao.deleteWorkoutById(workoutId.value)
            
            Timber.d("Workout deleted successfully: ${workoutId.value}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete workout: ${workoutId.value}")
            Result.failure(e)
        }
    }

    override suspend fun queueSyncForUser(userId: String): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            
            if (unsyncedCount > 0) {
                val syncWorkRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString("userId", userId)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    "${WorkoutSyncWorker.WORK_NAME}_$userId",
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest
                )
                
                Timber.d("Queued sync for $unsyncedCount unsynced workouts for user: $userId")
            } else {
                Timber.d("No unsynced workouts to queue for user: $userId")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue sync for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun syncNowForUser(userId: String): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            
            if (unsyncedCount > 0) {
                val immediateSync = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString("userId", userId)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    "${WorkoutSyncWorker.WORK_NAME}_immediate_$userId",
                    ExistingWorkPolicy.REPLACE,
                    immediateSync
                )
                
                Timber.d("Initiated immediate sync for $unsyncedCount workouts for user: $userId")
            } else {
                Timber.d("No unsynced workouts for immediate sync for user: $userId")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate sync for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun markWorkoutsAsSyncedForUser(workoutIds: List<String>, userId: String): Result<Unit> {
        return try {
            workoutDao.markWorkoutsAsSyncedForUser(workoutIds, userId, System.currentTimeMillis())
            Timber.d("Marked ${workoutIds.size} workouts as synced for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark workouts as synced for user: $userId")
            Result.failure(e)
        }
    }

    // Pagination methods implementation
    override fun getUserWorkoutHistory(userId: String, limit: Int, offset: Int): Flow<List<WorkoutSummary>> {
        return try {
            workoutDao.getWorkoutHistoryPaginated(userId, limit, offset).map { entities ->
                entities.map { entity ->
                    val workout = workoutMapper.toDomain(entity)
                    workout.toSummary()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout history for user: $userId, limit: $limit, offset: $offset")
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    override suspend fun getWorkoutHistoryCount(userId: String): Int {
        return try {
            workoutDao.getWorkoutCountForUser(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout history count for user: $userId")
            0
        }
    }

    // Home screen methods implementation
    override fun getRecentWorkouts(userId: String, limit: Int): Flow<List<Workout>> {
        return workoutDao.getRecentCompletedWorkouts(userId, limit).map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override fun getWorkoutStats(userId: String): Flow<WorkoutStats> {
        return getAllWorkoutsForUser(userId).map { workouts ->
            calculateWorkoutStats(workouts)
        }
    }

    override suspend fun queueSync(): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCount()
            
            if (unsyncedCount > 0) {
                val syncWorkRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    WorkoutSyncWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest
                )
                
                Timber.d("Queued sync for $unsyncedCount unsynced workouts")
            } else {
                Timber.d("No unsynced workouts to queue")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue sync")
            Result.failure(e)
        }
    }

    override suspend fun getUnsyncedCount(): Int {
        return try {
            workoutDao.getUnsyncedCount()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced count")
            0
        }
    }

    override suspend fun syncNow(): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCount()
            
            if (unsyncedCount > 0) {
                val immediateSync = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    "${WorkoutSyncWorker.WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    immediateSync
                )
                
                Timber.d("Initiated immediate sync for $unsyncedCount workouts")
            } else {
                Timber.d("No unsynced workouts for immediate sync")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate sync")
            Result.failure(e)
        }
    }

    override suspend fun clearAllWorkouts(): Result<Unit> {
        return try {
            workoutDao.deleteAllWorkouts()
            
            // Cancel any pending sync work
            workManager.cancelUniqueWork(WorkoutSyncWorker.WORK_NAME)
            
            Timber.d("All workouts cleared successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all workouts")
            Result.failure(e)
        }
    }

    // Feed methods implementation
    override fun getFeedWorkouts(userId: String, limit: Int, offset: Int): Flow<List<FeedWorkout>> {
        return kotlinx.coroutines.flow.flow {
            try {
                Timber.v("Loading feed workouts for user: $userId, limit: $limit, offset: $offset")
                
                // Get accepted friend IDs using the new DAO method
                val friendIds = workoutDao.getAcceptedFriendIds(userId)
                Timber.v("Found ${friendIds.size} accepted friends for user: $userId")
                
                // Get combined workout feed from DAO
                workoutDao.getFeedWorkouts(userId, friendIds, limit, offset).collect { workoutEntities ->
                    val feedWorkouts = workoutEntities.map { entity ->
                        try {
                            val workout = workoutMapper.toDomain(entity)
                            val isPersonal = entity.userId == userId
                            
                            if (isPersonal) {
                                FeedWorkout.forPersonalWorkout(workout)
                            } else {
                                // Get user information for friend's workout
                                val user = socialRepository.getUserById(entity.userId)
                                if (user != null) {
                                    FeedWorkout.forFriendWorkout(workout, user)
                                } else {
                                    Timber.w("User info not found for friend workout: ${entity.userId}")
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to map workout entity to feed workout: ${entity.id}")
                            null
                        }
                    }.filterNotNull()
                    
                    Timber.d("Loaded ${feedWorkouts.size} feed workouts for user: $userId")
                    emit(feedWorkouts)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to load feed workouts for user: $userId")
                emit(emptyList<FeedWorkout>())
            }
        }
    }
    
    override suspend fun hasMoreFeedWorkouts(userId: String, offset: Int): Boolean {
        return try {
            // Check if we've reached the maximum feed limit
            if (offset >= MAX_FEED_WORKOUTS) {
                Timber.v("Feed limit reached for user: $userId at offset: $offset")
                return false
            }
            
            // Get accepted friend IDs
            val friendIds = workoutDao.getAcceptedFriendIds(userId)
            
            // Get total count of available feed workouts
            val totalCount = workoutDao.getFeedWorkoutCount(userId, friendIds)
            val hasMore = offset < totalCount && offset < MAX_FEED_WORKOUTS
            
            Timber.v("Feed pagination check for user: $userId - offset: $offset, total: $totalCount, hasMore: $hasMore")
            hasMore
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to check feed pagination for user: $userId")
            false
        }
    }

    /**
     * Calculate workout statistics from a list of workouts
     */
    private fun calculateWorkoutStats(workouts: List<Workout>): WorkoutStats {
        if (workouts.isEmpty()) {
            return WorkoutStats.EMPTY
        }
        
        val completedWorkouts = workouts.filter { it.status == WorkoutStatus.COMPLETED }
        
        if (completedWorkouts.isEmpty()) {
            return WorkoutStats.EMPTY
        }
        
        val totalWorkouts = completedWorkouts.size
        val currentStreak = calculateCurrentStreak(completedWorkouts)
        val weeklyVolume = calculateWeeklyVolume(completedWorkouts)
        val averageWorkoutDuration = calculateAverageDuration(completedWorkouts)
        
        return WorkoutStats(
            totalWorkouts = totalWorkouts,
            currentStreak = currentStreak,
            weeklyVolume = weeklyVolume,
            averageWorkoutDuration = averageWorkoutDuration
        )
    }
    
    /**
     * Calculate current workout streak (consecutive days with workouts)
     */
    private fun calculateCurrentStreak(workouts: List<Workout>): Int {
        if (workouts.isEmpty()) return 0
        
        val workoutsByDate = workouts
            .sortedByDescending { it.date }
            .groupBy { it.date }
            .keys
            .toList()
        
        var streak = 0
        var currentDate = LocalDate.now()
        
        for (workoutDate in workoutsByDate) {
            val daysDifference = ChronoUnit.DAYS.between(workoutDate, currentDate)
            
            when {
                daysDifference == 0L || daysDifference == 1L -> {
                    streak++
                    currentDate = workoutDate
                }
                else -> break
            }
        }
        
        return streak
    }
    
    /**
     * Calculate weekly volume (total workout time for current week)
     */
    private fun calculateWeeklyVolume(workouts: List<Workout>): Duration {
        val startOfWeek = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)
        val endOfWeek = startOfWeek.plusDays(6)
        
        return workouts
            .filter { it.date in startOfWeek..endOfWeek }
            .mapNotNull { workout ->
                if (workout.startTime != null && workout.endTime != null) {
                    Duration.between(workout.startTime, workout.endTime)
                } else null
            }
            .fold(Duration.ZERO) { acc, duration -> acc.plus(duration) }
    }
    
    /**
     * Calculate average workout duration across all completed workouts
     */
    private fun calculateAverageDuration(workouts: List<Workout>): Duration {
        val durationsWithTimes = workouts.mapNotNull { workout ->
            if (workout.startTime != null && workout.endTime != null) {
                Duration.between(workout.startTime, workout.endTime)
            } else null
        }
        
        return if (durationsWithTimes.isNotEmpty()) {
            val totalDuration = durationsWithTimes.fold(Duration.ZERO) { acc, duration -> acc.plus(duration) }
            totalDuration.dividedBy(durationsWithTimes.size.toLong())
        } else {
            Duration.ZERO
        }
    }

    /**
     * Internal method to handle sync retries and failures
     */
    internal suspend fun handleSyncResult(workoutIds: List<String>, userId: String, success: Boolean) {
        try {
            if (success) {
                workoutDao.markWorkoutsAsSyncedForUser(workoutIds, userId, System.currentTimeMillis())
                Timber.d("Marked ${workoutIds.size} workouts as synced for user: $userId")
            } else {
                Timber.w("Sync failed for ${workoutIds.size} workouts for user: $userId")
                // Could implement exponential backoff or other retry strategies here
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle sync result for user: $userId")
        }
    }

    /**
     * Sync personal workout to Firebase for real-time feed sharing
     */
    private suspend fun syncWorkoutToFirebase(workout: Workout) {
        try {
            if (workout.status != WorkoutStatus.COMPLETED) {
                Timber.v("Skipping Firebase sync for non-completed workout: ${workout.id.value}")
                return
            }

            val workoutData = mapOf(
                "id" to workout.id.value,
                "userId" to workout.userId,
                "name" to workout.name,
                "date" to workout.date.toString(),
                "status" to workout.status.name,
                "completedAt" to workout.endTime?.let { com.google.firebase.Timestamp.now() },
                "exerciseCount" to workout.exercises.size,
                "duration" to if (workout.startTime != null && workout.endTime != null) {
                    java.time.Duration.between(workout.startTime, workout.endTime).toMinutes()
                } else null,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "updatedAt" to com.google.firebase.Timestamp.now()
            )

            firestore.collection("workouts")
                .document(workout.id.value)
                .set(workoutData)
                .await()

            Timber.d("Workout synced to Firebase for feed sharing: ${workout.id.value}")

        } catch (e: Exception) {
            Timber.e(e, "Failed to sync workout to Firebase: ${workout.id.value}")
            // Don't fail the local operation - offline-first approach
        }
    }

    /**
     * Setup real-time listener for workout feed updates
     * Integrates with SocialRepository's real-time feed listener
     */
    fun setupRealtimeFeedListener(): kotlinx.coroutines.flow.Flow<Unit> {
        return kotlinx.coroutines.flow.callbackFlow {
            var listener: com.google.firebase.firestore.ListenerRegistration? = null

            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    Timber.w("Cannot setup workout feed listener: user not authenticated")
                    trySend(Unit)
                    close()
                    return@callbackFlow
                }

                Timber.d("Setting up real-time workout feed listener for user: $currentUserId")

                // Listen to current user's own workouts for real-time updates
                listener = firestore.collection("workouts")
                    .whereEqualTo("userId", currentUserId)
                    .whereNotEqualTo("completedAt", null)
                    .orderBy("completedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Error in workout feed listener")
                            return@addSnapshotListener
                        }

                        snapshot?.let { querySnapshot ->
                            val workoutCount = querySnapshot.documents.size
                            Timber.d("Real-time workout feed update: $workoutCount personal workouts")

                            // Notify that personal workout data has been updated
                            trySend(Unit)
                        }
                    }

                Timber.d("Real-time workout feed listener established")
                trySend(Unit)

            } catch (e: Exception) {
                Timber.e(e, "Failed to setup real-time workout feed listener")
                close(e)
            }

            awaitClose {
                listener?.remove()
                Timber.d("Real-time workout feed listener removed")
            }
        }
    } 
} 