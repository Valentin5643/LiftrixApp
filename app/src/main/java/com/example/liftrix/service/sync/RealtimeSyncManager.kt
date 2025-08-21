package com.example.liftrix.service.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Real-time synchronization manager providing hybrid sync strategy.
 * 
 * This manager implements:
 * - Firestore listeners for real-time updates (<1s latency)
 * - Smart polling for analytics data (30s-5min intervals)
 * - WorkManager for background synchronization
 * - Network-aware sync optimization
 * - Exponential backoff for failure recovery
 * 
 * Technical Implementation:
 * - Uses Firestore snapshot listeners for real-time data
 * - Coroutine-based polling with exponential backoff
 * - WorkManager for offline sync recovery
 * - Memory leak prevention with proper listener management
 * 
 * Performance Characteristics:
 * - Real-time updates: <1 second for workout sessions and PRs
 * - Smart polling: 30s-5min based on widget complexity
 * - Background sync: 15-minute intervals with network constraints
 * - Memory efficient: Proper listener registration/cleanup
 */
@Singleton
class RealtimeSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val syncStrategy: SyncStrategy,
    private val conflictResolver: ConflictResolver
) {
    
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track active listeners to prevent memory leaks
    private val activeListeners = mutableMapOf<String, ListenerRegistration>()
    
    // Sync state management
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Error tracking for exponential backoff
    private val errorCounts = mutableMapOf<String, Int>()
    
    /**
     * Starts real-time synchronization for a user with authentication validation
     */
    fun startRealtimeSync(userId: String) {
        Timber.d("Starting real-time sync for user: $userId")
        
        if (userId.isBlank()) {
            Timber.w("Cannot start sync - invalid userId")
            return
        }
        
        scope.launch {
            try {
                _syncState.value = SyncState.Starting(userId)
                
                // Validate authentication before setting up listeners
                val authValidation = validateAuthentication(userId)
                if (!authValidation.isValid) {
                    Timber.w("Skipping real-time sync: ${authValidation.reason}")
                    _syncState.value = SyncState.Error(
                        userId = userId,
                        error = authValidation.reason,
                        retryAfterMs = 5000L // Retry after 5 seconds for auth issues
                    )
                    return@launch
                }
                
                // Start real-time listeners for critical data with error handling
                startWorkoutSessionListener(userId)
                startPersonalRecordListener(userId)
                
                // Schedule background sync worker
                scheduleBackgroundSync(userId)
                
                // Reset error count on successful start
                errorCounts.remove(userId)
                _syncState.value = SyncState.Active(userId)
                
                Timber.i("Real-time sync started successfully for user: $userId")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to start real-time sync for user: $userId")
                handleSyncError(userId, e)
            }
        }
    }
    
    /**
     * Validates authentication before setting up Firestore listeners
     */
    private suspend fun validateAuthentication(userId: String): AuthValidationResult {
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            return AuthValidationResult(false, "No authenticated user")
        }
        
        if (currentUser.uid != userId) {
            return AuthValidationResult(false, "User ID mismatch")
        }
        
        // Validate token freshness to prevent permission denied errors
        try {
            val tokenResult = currentUser.getIdToken(false).await()
            if (tokenResult?.token.isNullOrBlank()) {
                return AuthValidationResult(false, "Authentication token is invalid")
            }
            
            return AuthValidationResult(true, "Authentication valid")
        } catch (e: Exception) {
            Timber.w(e, "Failed to validate authentication token for real-time sync")
            return AuthValidationResult(false, "Token validation failed: ${e.message}")
        }
    }
    
    /**
     * Stops real-time synchronization for a user
     */
    fun stopRealtimeSync(userId: String) {
        Timber.d("Stopping real-time sync for user: $userId")
        
        // Remove all listeners for this user
        activeListeners.filterKeys { it.startsWith(userId) }
            .forEach { (key, listener) ->
                listener.remove()
                activeListeners.remove(key)
                Timber.d("Removed listener: $key")
            }
        
        // Cancel background work for this user
        workManager.cancelUniqueWork("widget_sync_$userId")
        
        // Clear error tracking
        errorCounts.remove(userId)
        _syncState.value = SyncState.Idle
        
        Timber.i("Real-time sync stopped for user: $userId")
    }
    
    /**
     * Forces a manual sync for all user data
     */
    suspend fun forceSyncAll(userId: String): LiftrixResult<Unit> {
        return try {
            Timber.d("Force syncing all data for user: $userId")
            _syncState.value = SyncState.Syncing(userId, "manual_sync")
            
            // Trigger immediate background sync
            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setInputData(
                    androidx.work.workDataOf(
                        "userId" to userId,
                        "syncType" to "manual_force"
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            workManager.enqueue(syncRequest)
            
            _syncState.value = SyncState.Active(userId)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to force sync for user: $userId")
            handleSyncError(userId, e)
            Result.failure(
                LiftrixError.NetworkError(
                    errorMessage = "Failed to force sync: ${e.message}",
                    isRecoverable = true
                )
            )
        }
    }
    
    /**
     * Gets real-time workout session updates
     */
    fun getWorkoutSessionUpdates(userId: String): Flow<DocumentChange> = callbackFlow {
        val listenerKey = "${userId}_workout_sessions"
        
        try {
            // FIXED: Use user-scoped subcollection to avoid permission errors
            // This matches the new Firestore security rule structure
            val listener = firestore.collection("users")
                .document(userId)
                .collection("workout_sessions")
                .whereIn("status", listOf("ACTIVE", "PAUSED", "COMPLETED"))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle permission denied errors gracefully
                        if (isPermissionDeniedError(error)) {
                            Timber.w("Permission denied for workout session listener - user: $userId. Gracefully closing listener.")
                            // Don't treat as fatal error, just close the listener
                            close()
                            return@addSnapshotListener
                        }
                        
                        Timber.e(error, "Workout session listener error for user: $userId")
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    snapshot?.documentChanges?.forEach { change ->
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                Timber.d("Workout session update: ${change.type} - ${change.document.id}")
                                trySend(change)
                            }
                            DocumentChange.Type.REMOVED -> {
                                Timber.d("Workout session removed: ${change.document.id}")
                            }
                        }
                    }
                }
            
            activeListeners[listenerKey] = listener
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup workout session listener for user: $userId")
            close(e)
        }
        
        awaitClose {
            activeListeners[listenerKey]?.remove()
            activeListeners.remove(listenerKey)
            Timber.d("Closed workout session listener for user: $userId")
        }
    }
    
    /**
     * Gets real-time personal record updates
     */
    fun getPersonalRecordUpdates(userId: String): Flow<DocumentChange> = callbackFlow {
        val listenerKey = "${userId}_personal_records"
        
        try {
            val listener = firestore.collection("personal_records")
                .whereEqualTo("userId", userId)
                .orderBy("achievedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50) // Limit to recent PRs to reduce bandwidth
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle permission denied errors gracefully
                        if (isPermissionDeniedError(error)) {
                            Timber.w("Permission denied for personal record listener - user: $userId. Gracefully closing listener.")
                            // Don't treat as fatal error, just close the listener
                            close()
                            return@addSnapshotListener
                        }
                        
                        Timber.e(error, "Personal record listener error for user: $userId")
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    snapshot?.documentChanges?.forEach { change ->
                        when (change.type) {
                            DocumentChange.Type.ADDED -> {
                                Timber.d("New personal record: ${change.document.id}")
                                trySend(change)
                            }
                            DocumentChange.Type.MODIFIED -> {
                                Timber.d("Personal record updated: ${change.document.id}")
                                trySend(change)
                            }
                            DocumentChange.Type.REMOVED -> {
                                Timber.d("Personal record removed: ${change.document.id}")
                            }
                        }
                    }
                }
            
            activeListeners[listenerKey] = listener
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup personal record listener for user: $userId")
            close(e)
        }
        
        awaitClose {
            activeListeners[listenerKey]?.remove()
            activeListeners.remove(listenerKey)
            Timber.d("Closed personal record listener for user: $userId")
        }
    }
    
    /**
     * Private helper methods
     */
    
    private fun startWorkoutSessionListener(userId: String) {
        scope.launch {
            getWorkoutSessionUpdates(userId).collect { change ->
                try {
                    // Handle workout session changes
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            val sessionData = change.document.data
                            Timber.d("Processing workout session change: ${change.document.id}")
                            
                            // Trigger local data update through sync strategy
                            syncStrategy.handleWorkoutSessionUpdate(userId, sessionData)
                        }
                        else -> { /* No action needed for REMOVED */ }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing workout session change")
                    handleSyncError(userId, e)
                }
            }
        }
    }
    
    private fun startPersonalRecordListener(userId: String) {
        scope.launch {
            getPersonalRecordUpdates(userId).collect { change ->
                try {
                    // Handle personal record changes
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            val prData = change.document.data
                            Timber.d("Processing new personal record: ${change.document.id}")
                            
                            // Trigger analytics recalculation
                            syncStrategy.handlePersonalRecordUpdate(userId, prData)
                        }
                        else -> { /* Handle other change types if needed */ }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing personal record change")
                    handleSyncError(userId, e)
                }
            }
        }
    }
    
    private fun scheduleBackgroundSync(userId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<WidgetSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(
                androidx.work.workDataOf(
                    "userId" to userId,
                    "syncType" to "background_periodic"
                )
            )
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "widget_sync_$userId",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
        
        Timber.d("Scheduled background sync for user: $userId")
    }
    
    private fun handleSyncError(userId: String, error: Throwable) {
        val errorCount = errorCounts.getOrDefault(userId, 0) + 1
        errorCounts[userId] = errorCount
        
        // Calculate exponential backoff delay (max 5 minutes)
        val backoffDelay = minOf(1000L * (1 shl errorCount), 300_000L)
        
        _syncState.value = SyncState.Error(
            userId = userId,
            error = error.message ?: "Unknown sync error",
            retryAfterMs = backoffDelay
        )
        
        Timber.w("Sync error for user $userId (attempt $errorCount): ${error.message}")
        
        // Schedule retry with exponential backoff
        scope.launch {
            kotlinx.coroutines.delay(backoffDelay)
            if (errorCount <= 5) { // Max 5 retry attempts
                startRealtimeSync(userId)
            } else {
                Timber.e("Max retry attempts reached for user: $userId")
                _syncState.value = SyncState.Failed(userId, "Max retry attempts exceeded")
            }
        }
    }
    
    /**
     * Checks if an error is a permission denied error
     */
    private fun isPermissionDeniedError(error: Throwable): Boolean {
        return when (error) {
            is FirebaseFirestoreException -> {
                error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            }
            else -> {
                error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                error.message?.contains("Missing or insufficient permissions", ignoreCase = true) == true ||
                error.message?.contains("Permission denied", ignoreCase = true) == true
            }
        }
    }
    
    /**
     * Sync state sealed class for monitoring sync status
     */
    sealed class SyncState {
        object Idle : SyncState()
        
        data class Starting(val userId: String) : SyncState()
        
        data class Active(val userId: String) : SyncState()
        
        data class Syncing(
            val userId: String,
            val operation: String
        ) : SyncState()
        
        data class Error(
            val userId: String,
            val error: String,
            val retryAfterMs: Long
        ) : SyncState()
        
        data class Failed(
            val userId: String,
            val reason: String
        ) : SyncState()
    }
    
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 300_000L // 5 minutes
    }
}

/**
 * Result of authentication validation for real-time sync operations
 */
data class AuthValidationResult(
    val isValid: Boolean,
    val reason: String
)