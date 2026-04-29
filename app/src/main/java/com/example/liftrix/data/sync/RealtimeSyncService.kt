package com.example.liftrix.data.sync

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time sync service for handling Firestore listeners during active workout sessions.
 *
 * This service implements the core real-time synchronization requirements as specified
 * in the Firebase sync infrastructure. It provides:
 * - Real-time workout synchronization during active sessions
 * - Automatic listener management with proper cleanup
 * - Error handling with authentication validation
 * - Direct integration with Room database for local updates
 * - Lifecycle-aware listener management to prevent memory leaks
 *
 * Key Features:
 * - Firestore snapshot listeners for < 1 second update latency
 * - User-scoped data access with Firebase Auth integration
 * - Memory leak prevention through lifecycle observers and proper cleanup
 * - Error resilience with graceful degradation
 * - Automatic cleanup when app goes to background
 *
 * Refactored for offline-first architecture (SPEC-20241228):
 * - Parses Firestore data inline to WorkoutEntity
 * - Uses upsertFromRemote() for idempotent listener writes
 * - Feature-flag gated for rollback support
 */
@Singleton
class RealtimeSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val workoutDao: WorkoutDao,
    private val gson: Gson
) {
    
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)
    
    // Thread-safe map to manage multiple listeners
    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private var currentUserId: String? = null
    
    /**
     * Starts real-time workout synchronization for the specified workout during active sessions.
     * 
     * This method sets up a Firestore snapshot listener that provides real-time updates
     * with < 1 second latency as required by the specification.
     * 
     * @param workoutId The ID of the workout to sync in real-time
     * @param userId The user ID for authentication and data scoping
     */
    fun startRealtimeWorkoutSync(workoutId: String, userId: String): LiftrixResult<Unit> {
        return try {
            Timber.d("RealtimeSyncService: Starting real-time sync for workout $workoutId")
            
            // Validate authentication
            val currentUser = auth.currentUser
            if (currentUser == null || currentUser.uid != userId) {
                return Result.failure(
                    LiftrixError.AuthenticationError(
                        errorMessage = "User must be authenticated to start real-time sync",
                        analyticsContext = mapOf("workout_id" to workoutId)
                    )
                )
            }
            
            // Stop any existing listener for this workout to prevent memory leaks
            stopWorkoutListener(workoutId)
            
            currentUserId = userId
            
            // Set up Firestore snapshot listener for the specific workout
            val listener = firestore
                .collection("users")
                .document(userId)
                .collection("workouts")
                .document(workoutId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "RealtimeSyncService: Firestore listener error for workout $workoutId")
                        handleListenerError(workoutId, error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        Timber.d("RealtimeSyncService: Received real-time update for workout $workoutId")
                        
                        scope.launch {
                            try {
                                val remoteWorkoutData = snapshot.data
                                if (remoteWorkoutData != null) {
                                    processWorkoutUpdate(workoutId, remoteWorkoutData)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "RealtimeSyncService: Error processing workout update")
                            }
                        }
                    }
                }
            
            // Store the listener reference for proper cleanup
            activeListeners[workoutId] = listener
            
            Timber.i("RealtimeSyncService: Real-time sync started for workout $workoutId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "RealtimeSyncService: Failed to start real-time sync for workout $workoutId")
            Result.failure(
                LiftrixError.NetworkError(
                    errorMessage = "Failed to start real-time sync: ${e.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf(
                        "workout_id" to workoutId,
                        "user_id" to userId
                    )
                )
            )
        }
    }
    
    /**
     * Stops a specific workout listener.
     * 
     * @param workoutId The workout ID to stop listening to
     */
    fun stopWorkoutListener(workoutId: String) {
        activeListeners.remove(workoutId)?.let { listener ->
            listener.remove()
            Timber.d("RealtimeSyncService: Stopped listener for workout $workoutId")
        }
    }
    
    /**
     * Stops all real-time synchronization and cleans up resources.
     * 
     * This method should be called when ending active workout sessions to prevent
     * memory leaks and unnecessary network usage.
     */
    fun stopRealtimeSync() {
        // Remove all active listeners
        activeListeners.forEach { (workoutId, listener) ->
            listener.remove()
            Timber.d("RealtimeSyncService: Removed listener for workout $workoutId")
        }
        activeListeners.clear()
        
        val userId = currentUserId
        currentUserId = null
        
        Timber.d("RealtimeSyncService: Stopped all real-time sync for user $userId")
    }
    
    /**
     * Enables real-time synchronization for a user session.
     * This method can be used to initialize real-time capabilities without
     * targeting a specific workout.
     * 
     * @param userId The user ID to enable real-time sync for
     */
    fun enableRealtimeSync(userId: String) {
        Timber.d("RealtimeSyncService: Enabling real-time sync capabilities for user $userId")
        currentUserId = userId
        // Additional setup for user-level real-time sync can be added here
    }
    
    /**
     * Disables real-time synchronization to conserve resources.
     * Should be called when the user is no longer in an active session.
     */
    fun disableRealtimeSync() {
        Timber.d("RealtimeSyncService: Disabling real-time sync")
        stopRealtimeSync()
    }
    
    /**
     * IDEMPOTENT: Processes workout updates from REMOTE origin (Firestore listener).
     * Uses upsertFromRemote() with timestamp deduplication, sets isDirty=false.
     * Feature-flag gated for rollback support.
     *
     * @param workoutId The ID of the workout being updated
     * @param remoteData The workout data from Firestore
     */
    private suspend fun processWorkoutUpdate(workoutId: String, remoteData: Map<String, Any>) {
        try {
            if (OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS) {
                // NEW: Room-first idempotent pattern
                val lastModified = toEpochMillis(remoteData["lastModified"]) ?: System.currentTimeMillis()
                val syncVersion = (remoteData["syncVersion"] as? Number)?.toLong()
                    ?: (remoteData["version"] as? Number)?.toLong()
                    ?: 0L
                val userId = remoteData["userId"] as? String ?: currentUserId ?: return
                val beforeCount = workoutDao.getWorkoutCountForUser(userId)
                val remoteStatusValue = remoteData["status"]
                val remoteEndTimePresent = remoteData["endTime"] != null

                Timber.d("RealtimeSyncService: Processing update for workout $workoutId (version: $syncVersion, lastModified: $lastModified)")
                Timber.tag("WorkoutSyncDebug").d(
                    "[DATABASE-DEBUG] operation=REALTIME_FIREBASE_LOADED source=Firebase userId=$userId workoutId=$workoutId timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount remoteStatus=$remoteStatusValue remoteLastModified=$lastModified remoteEndTimePresent=$remoteEndTimePresent"
                )

                // Parse Firestore data to WorkoutEntity
                val workoutEntity = WorkoutEntity(
                    id = workoutId,
                    userId = userId,
                    name = remoteData["name"] as? String ?: "",
                    date = toLocalDate(remoteData["date"]) ?: LocalDate.now(),
                    exercisesJson = buildExercisesJson(remoteData),
                    status = parseStatus(remoteData["status"]),
                    startTime = toInstant(remoteData["startTime"]),
                    endTime = toInstant(remoteData["endTime"]),
                    notes = remoteData["notes"] as? String,
                    templateId = remoteData["templateId"] as? String,
                    createdAt = toInstant(remoteData["createdAt"]) ?: Instant.now(),
                    updatedAt = toInstant(remoteData["updatedAt"])
                        ?: toInstant(remoteData["createdAt"])
                        ?: Instant.now(),
                    isDirty = false,  // From Firestore, don't sync back
                    isSynced = true,
                    syncVersion = syncVersion,
                    lastModified = lastModified
                )

                // IDEMPOTENT: Only applies if remote is newer (timestamp check in DAO)
                workoutDao.upsertFromRemote(workoutEntity)
                Timber.tag("WorkoutSyncDebug").d(
                    "[DATABASE-DEBUG] operation=REALTIME_FIREBASE_APPLIED source=Firebase userId=$userId workoutId=$workoutId timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount afterCount=${workoutDao.getWorkoutCountForUser(userId)} parsedStatus=${workoutEntity.status} parsedEndTimePresent=${workoutEntity.endTime != null} parsedLastModified=${workoutEntity.lastModified}"
                )
                // NO SYNC TRIGGER - already from Firestore

                Timber.d("✅ IDEMPOTENT: Updated workout from real-time listener: $workoutId (Room-first)")
            } else {
                // LEGACY: Stub implementation (feedback loop risk)
                val lastModified = remoteData["lastModified"] as? Long ?: System.currentTimeMillis()
                val syncVersion = remoteData["syncVersion"] as? Long ?: 0L

                Timber.w("⚠️ LEGACY: Workout update received but not processed (stub implementation): $workoutId (version: $syncVersion)")
            }

        } catch (e: Exception) {
            Timber.e(e, "RealtimeSyncService: Error processing workout update for $workoutId")
        }
    }

    private fun parseStatus(value: Any?): WorkoutStatus {
        val statusString = value as? String
        return if (statusString.isNullOrBlank()) {
            WorkoutStatus.PLANNED
        } else {
            try {
                WorkoutStatus.valueOf(statusString)
            } catch (_: IllegalArgumentException) {
                WorkoutStatus.PLANNED
            }
        }
    }

    private fun buildExercisesJson(remoteData: Map<String, Any>): String {
        val exercisesJson = remoteData["exercisesJson"] as? String
        if (!exercisesJson.isNullOrBlank()) {
            return exercisesJson
        }

        val exercises = remoteData["exercises"]
        val payload = mutableMapOf<String, Any>(
            "exercises" to when (exercises) {
                is List<*> -> exercises
                is Map<*, *> -> listOf(exercises)
                is String -> return exercises
                else -> emptyList<Any>()
            }
        )

        (remoteData["totalVolume"] as? Number)?.toDouble()?.let { payload["totalVolume"] = it }
        (remoteData["duration"] as? Number)?.toLong()?.let { payload["duration"] = it }

        return gson.toJson(payload)
    }

    private fun toInstant(value: Any?): Instant? {
        return when (value) {
            is Timestamp -> value.toDate().toInstant()
            is Date -> value.toInstant()
            is Instant -> value
            is Long -> Instant.ofEpochMilli(value)
            is Number -> Instant.ofEpochMilli(value.toLong())
            else -> null
        }
    }

    private fun toLocalDate(value: Any?): LocalDate? {
        return when (value) {
            is LocalDate -> value
            is Timestamp -> value.toDate().toInstant().atZone(ZoneOffset.UTC).toLocalDate()
            is Date -> value.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
            is Instant -> value.atZone(ZoneOffset.UTC).toLocalDate()
            is Long -> Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()
            is Number -> Instant.ofEpochMilli(value.toLong()).atZone(ZoneOffset.UTC).toLocalDate()
            else -> null
        }
    }

    private fun toEpochMillis(value: Any?): Long? {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Date -> value.time
            is Instant -> value.toEpochMilli()
            is Long -> value
            is Number -> value.toLong()
            else -> null
        }
    }
    
    /**
     * Handles errors from Firestore listeners with appropriate fallback strategies.
     * 
     * @param workoutId The workout ID where the error occurred
     * @param error The error that occurred
     */
    private fun handleListenerError(workoutId: String, error: Throwable) {
        when {
            error.message?.contains("PERMISSION_DENIED") == true -> {
                Timber.w("RealtimeSyncService: Permission denied for workout $workoutId - stopping listener")
                stopRealtimeSync()
            }
            error.message?.contains("UNAUTHENTICATED") == true -> {
                Timber.w("RealtimeSyncService: Authentication expired for workout $workoutId - stopping listener")
                stopRealtimeSync()
            }
            else -> {
                Timber.e(error, "RealtimeSyncService: Listener error for workout $workoutId")
                // For other errors, keep the listener active but log the issue
            }
        }
    }
    
    // Lifecycle management methods
    
    /**
     * Called when the app comes to foreground.
     * Re-enables real-time sync if there was an active user.
     * Should be called from Application or Activity lifecycle.
     */
    fun onAppForegrounded() {
        currentUserId?.let { userId ->
            Timber.d("RealtimeSyncService: App resumed, re-enabling real-time sync for user $userId")
            enableRealtimeSync(userId)
        }
    }
    
    /**
     * Called when the app goes to background.
     * Stops all listeners to prevent battery drain and memory leaks.
     * Should be called from Application or Activity lifecycle.
     */
    fun onAppBackgrounded() {
        Timber.d("RealtimeSyncService: App backgrounded, pausing real-time sync")
        // Stop all listeners but keep user context for resume
        activeListeners.forEach { (workoutId, listener) ->
            listener.remove()
            Timber.d("RealtimeSyncService: Paused listener for workout $workoutId")
        }
        activeListeners.clear()
    }
    
    /**
     * Called when the service is being destroyed.
     * Performs complete cleanup of all resources.
     * Should be called when the app is terminating.
     */
    fun cleanup() {
        Timber.d("RealtimeSyncService: Cleaning up all resources")
        stopRealtimeSync()
        // Cancel the coroutine scope to prevent leaks
        scope.cancel("Service cleanup")
    }
}
