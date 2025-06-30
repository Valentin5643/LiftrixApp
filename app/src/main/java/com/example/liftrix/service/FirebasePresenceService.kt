package com.example.liftrix.service

import com.example.liftrix.domain.model.PresenceStatus
import com.example.liftrix.domain.model.UserPresence
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase service for managing real-time user presence tracking and friend status updates.
 * Handles online/offline detection, workout status updates, and real-time presence observation.
 */
@Singleton
class FirebasePresenceService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    companion object {
        private const val PRESENCE_COLLECTION = "user_presence"
        private const val PRESENCE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        
        // Firebase field names
        private const val FIELD_STATUS = "status"
        private const val FIELD_LAST_ACTIVE = "last_active"
        private const val FIELD_CURRENT_WORKOUT_ID = "current_workout_id"
        private const val FIELD_USER_ID = "user_id"
    }
    
    /**
     * Starts presence tracking for the current user.
     * Sets user as online and configures offline detection.
     * 
     * @return Result indicating success or failure
     */
    suspend fun startPresenceTracking(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )
            
            // Verify user is properly authenticated before attempting Firestore operations
            if (!auth.currentUser?.isEmailVerified.let { it == true || auth.currentUser?.isAnonymous == true }) {
                Timber.w("User email not verified, skipping presence tracking")
                return Result.success(Unit) // Don't fail the app, just skip presence
            }
            
            val presenceRef = firestore.collection(PRESENCE_COLLECTION).document(userId)
            
            // Set user as online with server timestamp
            val presenceData = mapOf(
                FIELD_STATUS to PresenceStatus.ONLINE.name,
                FIELD_LAST_ACTIVE to FieldValue.serverTimestamp(),
                FIELD_USER_ID to userId,
                FIELD_CURRENT_WORKOUT_ID to null
            )
            
            presenceRef.set(presenceData).await()
            
            Timber.d("Presence tracking started for user: $userId")
            Result.success(Unit)
            
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Timber.w(e, "Permission denied for presence tracking - check Firestore rules")
                    // Don't fail the app, presence is not critical
                    Result.success(Unit)
                }
                else -> {
                    Timber.e(e, "Firestore error during presence tracking")
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start presence tracking")
            Result.failure(e)
        }
    }
    
    /**
     * Updates the user's workout status.
     * Sets presence to WORKING_OUT when workout starts, ONLINE when workout ends.
     * 
     * @param workoutId The ID of the active workout, null if ending workout
     * @return Result indicating success or failure
     */
    suspend fun updateWorkoutStatus(workoutId: String?): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )
            
            val status = if (workoutId != null) PresenceStatus.WORKING_OUT else PresenceStatus.ONLINE
            
            val updateData = mapOf(
                FIELD_STATUS to status.name,
                FIELD_LAST_ACTIVE to FieldValue.serverTimestamp(),
                FIELD_CURRENT_WORKOUT_ID to workoutId
            )
            
            firestore.collection(PRESENCE_COLLECTION)
                .document(userId)
                .update(updateData)
                .await()
            
            Timber.d("Workout status updated: status=$status, workoutId=$workoutId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update workout status")
            Result.failure(e)
        }
    }
    
    /**
     * Observes the presence status of multiple friends in real-time.
     * 
     * @param friendIds List of friend user IDs to observe
     * @return Flow emitting a map of user ID to UserPresence
     */
    fun observeFriendsPresence(friendIds: List<String>): Flow<Map<String, UserPresence>> = callbackFlow {
        if (friendIds.isEmpty()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        
        var listenerRegistration: ListenerRegistration? = null
        
        try {
            listenerRegistration = firestore.collection(PRESENCE_COLLECTION)
                .whereIn(FIELD_USER_ID, friendIds)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "Error observing friends presence")
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        val presenceMap = snapshot.documents.mapNotNull { document ->
                            try {
                                val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                                val presence = mapFirebaseDataToUserPresence(document.data)
                                userId to presence
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to parse presence data for document: ${document.id}")
                                null
                            }
                        }.toMap()
                        
                        trySend(presenceMap)
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set up friends presence listener")
            close(e)
        }
        
        awaitClose {
            listenerRegistration?.remove()
            Timber.d("Friends presence listener removed")
        }
    }
    
    /**
     * Stops presence tracking for the current user.
     * Sets user as offline before disconnecting.
     * 
     * @return Result indicating success or failure
     */
    suspend fun stopPresenceTracking(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )
            
            val updateData = mapOf(
                FIELD_STATUS to PresenceStatus.OFFLINE.name,
                FIELD_LAST_ACTIVE to FieldValue.serverTimestamp(),
                FIELD_CURRENT_WORKOUT_ID to null
            )
            
            firestore.collection(PRESENCE_COLLECTION)
                .document(userId)
                .update(updateData)
                .await()
            
            Timber.d("Presence tracking stopped for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop presence tracking")
            Result.failure(e)
        }
    }
    
    /**
     * Updates the user's presence status.
     * 
     * @param status The new presence status
     * @param workoutId Optional workout ID if status is WORKING_OUT
     * @return Result indicating success or failure
     */
    suspend fun updatePresenceStatus(status: PresenceStatus, workoutId: String? = null): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )
            
            val updateData = mapOf(
                FIELD_STATUS to status.name,
                FIELD_LAST_ACTIVE to FieldValue.serverTimestamp(),
                FIELD_CURRENT_WORKOUT_ID to if (status == PresenceStatus.WORKING_OUT) workoutId else null
            )
            
            firestore.collection(PRESENCE_COLLECTION)
                .document(userId)
                .update(updateData)
                .await()
            
            Timber.d("Presence status updated: $status")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update presence status")
            Result.failure(e)
        }
    }
    
    /**
     * Maps Firebase document data to UserPresence domain model.
     * 
     * @param data Firebase document data
     * @return UserPresence instance or null if data is invalid
     */
    private fun mapFirebaseDataToUserPresence(data: Map<String, Any?>?): UserPresence {
        if (data == null) {
            return UserPresence.offline()
        }
        
        return try {
            val statusString = data[FIELD_STATUS] as? String ?: PresenceStatus.OFFLINE.name
            val status = try {
                PresenceStatus.valueOf(statusString)
            } catch (e: IllegalArgumentException) {
                Timber.w("Unknown presence status: $statusString, defaulting to OFFLINE")
                PresenceStatus.OFFLINE
            }
            
            val lastActiveTimestamp = data[FIELD_LAST_ACTIVE] as? com.google.firebase.Timestamp
            val lastActive = lastActiveTimestamp?.toDate()?.toInstant() ?: Instant.now()
            
            val currentWorkoutId = data[FIELD_CURRENT_WORKOUT_ID] as? String
            
            UserPresence(
                status = status,
                lastActive = lastActive,
                currentWorkoutId = currentWorkoutId
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to map Firebase data to UserPresence, returning offline")
            UserPresence.offline()
        }
    }
}