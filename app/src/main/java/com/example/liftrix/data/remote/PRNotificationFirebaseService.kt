package com.example.liftrix.data.remote

import com.example.liftrix.data.remote.ProcessResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase service for PR notification and reaction operations.
 * 
 * Handles:
 * - PR reaction synchronization between devices
 * - PR notification preferences sync
 * - Real-time reaction updates
 * - Cross-device notification state management
 * 
 * Collection Structure:
 * /social_profiles/{userId}/pr_reactions/{reactionId}
 * /social_profiles/{userId}/pr_notification_preferences
 */
@Singleton
class PRNotificationFirebaseService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val json: Json
) {
    
    companion object {
        private const val SOCIAL_PROFILES_COLLECTION = "social_profiles"
        private const val PR_REACTIONS_SUBCOLLECTION = "pr_reactions"
        private const val PR_PREFERENCES_DOCUMENT = "pr_notification_preferences"
    }
    
    /**
     * Syncs a PR reaction to Firebase
     */
    suspend fun syncReaction(
        userId: String,
        reactionId: String,
        reactionData: String
    ): ProcessResult {
        return try {
            Timber.d("Syncing PR reaction $reactionId for user $userId")
            
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated"))
            }
            
            val document = firestore
                .collection(SOCIAL_PROFILES_COLLECTION)
                .document(userId)
                .collection(PR_REACTIONS_SUBCOLLECTION)
                .document(reactionId)
            
            document.set(mapOf("data" to reactionData)).await()
            
            Timber.d("Successfully synced PR reaction $reactionId")
            ProcessResult.Success
            
        } catch (e: FirebaseFirestoreException) {
            Timber.e(e, "Firebase error syncing PR reaction")
            ProcessResult.Failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error syncing PR reaction")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Removes a PR reaction from Firebase
     */
    suspend fun deleteReaction(
        userId: String,
        reactionId: String
    ): ProcessResult {
        return try {
            Timber.d("Deleting PR reaction $reactionId for user $userId")
            
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated"))
            }
            
            firestore
                .collection(SOCIAL_PROFILES_COLLECTION)
                .document(userId)
                .collection(PR_REACTIONS_SUBCOLLECTION)
                .document(reactionId)
                .delete()
                .await()
                
            ProcessResult.Success
            
        } catch (e: Exception) {
            Timber.e(e, "Error deleting PR reaction")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Fetches PR reactions from Firebase
     */
    suspend fun fetchReactions(
        userId: String,
        prId: String
    ): ProcessResult {
        return try {
            val snapshot = firestore
                .collection(SOCIAL_PROFILES_COLLECTION)
                .document(userId)
                .collection(PR_REACTIONS_SUBCOLLECTION)
                .whereEqualTo("pr_id", prId)
                .get()
                .await()
            
            val reactions = snapshot.documents.mapNotNull { doc ->
                doc.data?.get("data")?.toString()
            }
            
            ProcessResult.DataList(reactions)
            
        } catch (e: Exception) {
            Timber.e(e, "Error fetching PR reactions")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Syncs PR notification preferences to Firebase
     */
    suspend fun syncPreferences(
        userId: String,
        preferencesData: String
    ): ProcessResult {
        return try {
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated"))
            }
            
            firestore
                .collection(SOCIAL_PROFILES_COLLECTION)
                .document(userId)
                .collection("preferences")
                .document(PR_PREFERENCES_DOCUMENT)
                .set(mapOf("data" to preferencesData))
                .await()
                
            ProcessResult.Success
            
        } catch (e: Exception) {
            Timber.e(e, "Error syncing PR preferences")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Fetches PR notification preferences from Firebase
     */
    suspend fun fetchPreferences(userId: String): ProcessResult {
        return try {
            val snapshot = firestore
                .collection(SOCIAL_PROFILES_COLLECTION)
                .document(userId)
                .collection("preferences")
                .document(PR_PREFERENCES_DOCUMENT)
                .get()
                .await()
            
            if (snapshot.exists()) {
                val data = snapshot.data?.get("data")?.toString() ?: ""
                ProcessResult.Data(data)
            } else {
                ProcessResult.Failure(Exception("Preferences not found"))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error fetching PR preferences")
            ProcessResult.Failure(e)
        }
    }
    
    private fun isUserAuthenticated(userId: String): Boolean {
        val currentUser = auth.currentUser
        return currentUser != null && currentUser.uid == userId
    }
}