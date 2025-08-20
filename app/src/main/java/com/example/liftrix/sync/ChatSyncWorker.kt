package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.dao.ChatPreferencesDao
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.data.local.entity.ChatPreferencesEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for syncing chat data to Firebase.
 * Handles both chat history and preferences sync with batch processing.
 */
@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatHistoryDao: ChatHistoryDao,
    private val chatPreferencesDao: ChatPreferencesDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "chat_sync_work"
        const val KEY_USER_ID = "userId"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val BATCH_SIZE = 20
        
        /**
         * Creates a work request for chat sync.
         */
        fun createWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<ChatSyncWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(WORK_NAME)
                .addTag("user_$userId")
                .build()
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(KEY_USER_ID)
        
        if (userId.isNullOrEmpty()) {
            Timber.e("ChatSyncWorker: userId is null or empty")
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Missing userId")
            )
        }
        
        // Check if user is authenticated
        if (auth.currentUser == null || auth.currentUser?.uid != userId) {
            Timber.w("ChatSyncWorker: User not authenticated or userId mismatch")
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "User not authenticated")
            )
        }
        
        try {
            Timber.d("Starting chat sync for user: $userId")
            
            var syncedCount = 0
            
            // 1. Sync chat preferences
            val preferencesSynced = syncPreferences(userId)
            if (preferencesSynced) syncedCount++
            
            // 2. Sync chat history in batches
            val messagesSynced = syncChatHistory(userId)
            syncedCount += messagesSynced
            
            Timber.d("Chat sync completed for user $userId. Synced $syncedCount items")
            
            Result.success(
                workDataOf(KEY_SYNC_COUNT to syncedCount)
            )
        } catch (e: Exception) {
            Timber.e(e, "Chat sync failed for user: $userId")
            
            // Check retry count
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Timber.d("Retrying chat sync (attempt ${runAttemptCount + 1}/$MAX_RETRY_COUNT)")
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error"))
                )
            }
        }
    }
    
    /**
     * Syncs chat preferences to Firebase.
     */
    private suspend fun syncPreferences(userId: String): Boolean {
        try {
            val preferences = chatPreferencesDao.getUnsyncedPreferences(userId)
            
            if (preferences == null) {
                Timber.d("No unsynced preferences for user $userId")
                return false
            }
            
            val preferencesData = mapOf(
                "preferredLanguage" to preferences.preferredLanguage,
                "autoDetectLanguage" to preferences.autoDetectLanguage,
                "chatNotificationsEnabled" to preferences.chatNotificationsEnabled,
                "conversationHistoryEnabled" to preferences.conversationHistoryEnabled,
                "workoutContextSharing" to preferences.workoutContextSharing,
                "maxMessagesPerDay" to preferences.maxMessagesPerDay,
                "maxTokensPerMonth" to preferences.maxTokensPerMonth,
                "autoClearDays" to preferences.autoClearDays,
                "userContextPrompt" to preferences.userContextPrompt,
                "updatedAt" to preferences.updatedAt,
                "syncVersion" to 1,
                "lastSyncedAt" to FieldValue.serverTimestamp()
            )
            
            // Upload to Firebase
            firestore.collection("users")
                .document(userId)
                .collection("chat_preferences")
                .document("settings")
                .set(preferencesData, SetOptions.merge())
                .await()
            
            // Mark as synced in local database
            chatPreferencesDao.markAsSynced(userId, version = 1)
            
            Timber.d("Synced chat preferences for user $userId")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync preferences for user $userId")
            throw e
        }
    }
    
    /**
     * Syncs chat history to Firebase in batches.
     */
    private suspend fun syncChatHistory(userId: String): Int {
        try {
            val unsyncedMessages = chatHistoryDao.getUnsyncedMessages(userId)
            
            if (unsyncedMessages.isEmpty()) {
                Timber.d("No unsynced messages for user $userId")
                return 0
            }
            
            Timber.d("Syncing ${unsyncedMessages.size} messages for user $userId")
            
            var totalSynced = 0
            
            // Process messages in batches
            unsyncedMessages.chunked(BATCH_SIZE).forEach { batch ->
                val firestoreBatch = firestore.batch()
                
                batch.forEach { message ->
                    val messageData = mapOf(
                        "conversationId" to message.conversationId,
                        "messageType" to message.messageType,
                        "language" to message.language,
                        "content" to message.content,
                        "workoutContext" to message.workoutContext,
                        "tokenCount" to message.tokenCount,
                        "processingTimeMs" to message.processingTimeMs,
                        "createdAt" to message.createdAt,
                        "syncVersion" to 1,
                        "lastSyncedAt" to FieldValue.serverTimestamp()
                    )
                    
                    val docRef = firestore.collection("users")
                        .document(userId)
                        .collection("chat_history")
                        .document(message.id)
                    
                    firestoreBatch.set(docRef, messageData, SetOptions.merge())
                }
                
                // Commit the batch
                firestoreBatch.commit().await()
                
                // Mark messages as synced in local database
                val messageIds = batch.map { it.id }
                chatHistoryDao.markMessagesAsSynced(userId, messageIds, version = 1)
                
                totalSynced += batch.size
                Timber.d("Synced batch of ${batch.size} messages")
            }
            
            return totalSynced
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync chat history for user $userId")
            throw e
        }
    }
    
    /**
     * Downloads chat data from Firebase (for conflict resolution or initial sync).
     * This is typically called when a user signs in on a new device.
     */
    suspend fun downloadChatData(userId: String): Boolean {
        return try {
            Timber.d("Downloading chat data for user $userId")
            
            // Download preferences
            val preferencesDoc = firestore.collection("users")
                .document(userId)
                .collection("chat_preferences")
                .document("settings")
                .get()
                .await()
            
            if (preferencesDoc.exists()) {
                val preferences = ChatPreferencesEntity(
                    userId = userId,
                    preferredLanguage = preferencesDoc.getString("preferredLanguage") ?: "en",
                    autoDetectLanguage = preferencesDoc.getBoolean("autoDetectLanguage") ?: true,
                    chatNotificationsEnabled = preferencesDoc.getBoolean("chatNotificationsEnabled") ?: true,
                    conversationHistoryEnabled = preferencesDoc.getBoolean("conversationHistoryEnabled") ?: true,
                    workoutContextSharing = preferencesDoc.getBoolean("workoutContextSharing") ?: true,
                    maxMessagesPerDay = preferencesDoc.getLong("maxMessagesPerDay")?.toInt() ?: 50,
                    maxTokensPerMonth = preferencesDoc.getLong("maxTokensPerMonth")?.toInt() ?: 100000,
                    autoClearDays = preferencesDoc.getLong("autoClearDays")?.toInt() ?: 30,
                    userContextPrompt = preferencesDoc.getString("userContextPrompt"),
                    isSynced = true,
                    syncVersion = (preferencesDoc.getLong("syncVersion") ?: 1).toInt(),
                    updatedAt = preferencesDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                
                chatPreferencesDao.insertOrUpdatePreferences(preferences)
            }
            
            // Download recent chat history (last 100 messages)
            val messagesSnapshot = firestore.collection("users")
                .document(userId)
                .collection("chat_history")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            
            val messages = messagesSnapshot.documents.mapNotNull { doc ->
                try {
                    ChatHistoryEntity(
                        id = doc.id,
                        userId = userId,
                        conversationId = doc.getString("conversationId") ?: "",
                        messageType = doc.getString("messageType") ?: "USER",
                        language = doc.getString("language") ?: "en",
                        content = doc.getString("content") ?: "",
                        workoutContext = doc.getString("workoutContext"),
                        tokenCount = doc.getLong("tokenCount")?.toInt(),
                        processingTimeMs = doc.getLong("processingTimeMs"),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        isSynced = true,
                        syncVersion = (doc.getLong("syncVersion") ?: 1).toInt()
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing message document: ${doc.id}")
                    null
                }
            }
            
            if (messages.isNotEmpty()) {
                messages.forEach { message ->
                    chatHistoryDao.insertMessage(message)
                }
                Timber.d("Downloaded ${messages.size} messages for user $userId")
            }
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to download chat data for user $userId")
            false
        }
    }
}