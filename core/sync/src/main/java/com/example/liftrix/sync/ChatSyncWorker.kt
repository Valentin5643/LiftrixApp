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
import com.example.liftrix.config.OfflineArchitectureFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Date
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
        private const val MONTHLY_USAGE_TAG = "MonthlyUsageDebug"
        
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
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        
        try {
            Timber.tag(MONTHLY_USAGE_TAG).d(
                "Chat sync started userId=%s source=Room.chat_history+Room.chat_preferences target=Firebase now=%s dirtyFlagGating=%s",
                userId,
                Date(System.currentTimeMillis()).toString(),
                useDirtyFlagGating
            )
            
            var syncedCount = 0
            
            // 1. Sync chat preferences
            val preferencesSynced = syncPreferences(userId, useDirtyFlagGating)
            if (preferencesSynced) syncedCount++
            
            // 2. Sync chat history in batches
            val messagesSynced = syncChatHistory(userId, useDirtyFlagGating)
            syncedCount += messagesSynced
            
            Timber.tag(MONTHLY_USAGE_TAG).d(
                "Chat sync completed userId=%s syncedItems=%d source=Room/Firebase now=%s",
                userId,
                syncedCount,
                Date(System.currentTimeMillis()).toString()
            )
            
            Result.success(
                workDataOf(KEY_SYNC_COUNT to syncedCount)
            )
        } catch (e: Exception) {
            Timber.tag(MONTHLY_USAGE_TAG).e(e, "Chat sync failed userId=%s attempt=%d", userId, runAttemptCount + 1)
            
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
    private suspend fun syncPreferences(userId: String, useDirtyFlagGating: Boolean): Boolean {
        try {
            val preferences = if (useDirtyFlagGating) {
                chatPreferencesDao.getDirtyChatPreferences(userId).firstOrNull()
            } else {
                chatPreferencesDao.getUnsyncedPreferences(userId)
            }
            
            if (preferences == null) {
                Timber.tag(MONTHLY_USAGE_TAG).d("No chat preference sync needed userId=%s source=Room.chat_preferences", userId)
                return false
            }
            Timber.tag(MONTHLY_USAGE_TAG).d(
                "Chat preference sync candidate userId=%s localMonthlyLimit=%d localDailyLimit=%d localLastModified=%s localSynced=%s localDirty=%s source=Room.chat_preferences",
                userId,
                preferences.maxTokensPerMonth,
                preferences.maxMessagesPerDay,
                Date(preferences.lastModified).toString(),
                preferences.isSynced,
                preferences.isDirty
            )

            val docRef = firestore.collection("users")
                .document(userId)
                .collection("chat_preferences")
                .document("settings")
            val remoteDoc = docRef.get().await()
            if (remoteDoc.exists()) {
                val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                Timber.tag(MONTHLY_USAGE_TAG).d(
                    "Remote chat preferences read userId=%s remoteMonthlyLimit=%s remoteDailyLimit=%s remoteLastModified=%s localLastModified=%s source=Firebase.chat_preferences",
                    userId,
                    remoteDoc.getLong("maxTokensPerMonth")?.toString() ?: "missing",
                    remoteDoc.getLong("maxMessagesPerDay")?.toString() ?: "missing",
                    Date(remoteLastModified).toString(),
                    Date(preferences.lastModified).toString()
                )
                if (remoteLastModified > preferences.lastModified) {
                    val remoteEntity = preferences.copy(
                        preferredLanguage = remoteDoc.getString("preferredLanguage")
                            ?: preferences.preferredLanguage,
                        autoDetectLanguage = remoteDoc.getBoolean("autoDetectLanguage")
                            ?: preferences.autoDetectLanguage,
                        chatNotificationsEnabled = remoteDoc.getBoolean("chatNotificationsEnabled")
                            ?: preferences.chatNotificationsEnabled,
                        conversationHistoryEnabled = remoteDoc.getBoolean("conversationHistoryEnabled")
                            ?: preferences.conversationHistoryEnabled,
                        workoutContextSharing = remoteDoc.getBoolean("workoutContextSharing")
                            ?: preferences.workoutContextSharing,
                        maxMessagesPerDay = remoteDoc.getLong("maxMessagesPerDay")?.toInt()
                            ?: preferences.maxMessagesPerDay,
                        maxTokensPerMonth = remoteDoc.getLong("maxTokensPerMonth")?.toInt()
                            ?: preferences.maxTokensPerMonth,
                        autoClearDays = remoteDoc.getLong("autoClearDays")?.toInt()
                            ?: preferences.autoClearDays,
                        userContextPrompt = remoteDoc.getString("userContextPrompt")
                            ?: preferences.userContextPrompt,
                        updatedAt = remoteDoc.getLong("updatedAt") ?: preferences.updatedAt,
                        isDirty = false,
                        isSynced = true,
                        syncVersion = remoteDoc.getLong("syncVersion")
                            ?: preferences.syncVersion,
                        lastModified = remoteLastModified
                    )
                    chatPreferencesDao.upsertFromRemote(remoteEntity)
                    Timber.tag(MONTHLY_USAGE_TAG).w(
                        "Remote chat preferences overwrote local userId=%s monthlyLimit=%d dailyLimit=%d remoteLastModified=%s source=Firebase.chat_preferences target=Room.chat_preferences",
                        userId,
                        remoteEntity.maxTokensPerMonth,
                        remoteEntity.maxMessagesPerDay,
                        Date(remoteLastModified).toString()
                    )
                    return false
                }
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
                "syncVersion" to 1L,
                "lastModified" to preferences.lastModified,
                "lastSyncedAt" to FieldValue.serverTimestamp()
            )
            
            // Upload to Firebase
            docRef.set(preferencesData, SetOptions.merge()).await()
            Timber.tag(MONTHLY_USAGE_TAG).d(
                "Uploaded chat preferences userId=%s monthlyLimit=%d dailyLimit=%d localLastModified=%s source=Room.chat_preferences target=Firebase.chat_preferences",
                userId,
                preferences.maxTokensPerMonth,
                preferences.maxMessagesPerDay,
                Date(preferences.lastModified).toString()
            )
            
            // Mark as synced in local database
            chatPreferencesDao.markAsClean(
                ids = listOf(userId),
                userId = userId,
                syncVersion = System.currentTimeMillis()
            )
            
            Timber.tag(MONTHLY_USAGE_TAG).d("Marked chat preferences synced userId=%s source=Room.chat_preferences", userId)
            return true
            
        } catch (e: Exception) {
            Timber.tag(MONTHLY_USAGE_TAG).e(e, "Failed to sync preferences userId=%s source=Room.chat_preferences target=Firebase.chat_preferences", userId)
            throw e
        }
    }
    
    /**
     * Syncs chat history to Firebase in batches.
     */
    private suspend fun syncChatHistory(userId: String, useDirtyFlagGating: Boolean): Int {
        try {
            val unsyncedMessages = if (useDirtyFlagGating) {
                chatHistoryDao.getDirtyChatHistories(userId)
            } else {
                chatHistoryDao.getUnsyncedMessages(userId)
            }
            
            if (unsyncedMessages.isEmpty()) {
                Timber.tag(MONTHLY_USAGE_TAG).d("No chat history sync needed userId=%s source=Room.chat_history", userId)
                return 0
            }
            
            Timber.tag(MONTHLY_USAGE_TAG).d(
                "Chat history sync candidates userId=%s count=%d tokenSum=%d source=Room.chat_history",
                userId,
                unsyncedMessages.size,
                unsyncedMessages.sumOf { it.tokenCount ?: 0 }
            )
            
            var totalSynced = 0
            
            val collectionRef = firestore.collection("users")
                .document(userId)
                .collection("chat_history")

            // Process messages in batches
            unsyncedMessages.chunked(BATCH_SIZE).forEach { batch ->
                val messagesToUpload = mutableListOf<ChatHistoryEntity>()
                val remoteDocs = FirestorePrefetcher.prefetchByIds(
                    collection = collectionRef,
                    ids = batch.map { it.id }
                )

                for (message in batch) {
                    val remoteDoc = remoteDocs[message.id]
                    if (remoteDoc?.exists() == true) {
                        val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                            is com.google.firebase.Timestamp -> remoteValue.toDate().time
                            is Number -> remoteValue.toLong()
                            else -> 0L
                        }
                        if (remoteLastModified > message.lastModified) {
                            val remoteEntity = message.copy(
                                conversationId = remoteDoc.getString("conversationId")
                                    ?: message.conversationId,
                                messageType = remoteDoc.getString("messageType")
                                    ?: message.messageType,
                                language = remoteDoc.getString("language")
                                    ?: message.language,
                                content = remoteDoc.getString("content")
                                    ?: message.content,
                                workoutContext = remoteDoc.getString("workoutContext")
                                    ?: message.workoutContext,
                                tokenCount = remoteDoc.getLong("tokenCount")?.toInt()
                                    ?: message.tokenCount,
                                processingTimeMs = remoteDoc.getLong("processingTimeMs")
                                    ?: message.processingTimeMs,
                                createdAt = remoteDoc.getLong("createdAt") ?: message.createdAt,
                                isDirty = false,
                                isSynced = true,
                                syncVersion = remoteDoc.getLong("syncVersion")
                                    ?: message.syncVersion,
                                lastModified = remoteLastModified
                            )
                            chatHistoryDao.upsertFromRemote(remoteEntity)
                            Timber.tag(MONTHLY_USAGE_TAG).w(
                                "Remote chat message overwrote local userId=%s messageId=%s messageType=%s tokenCount=%s createdAt=%s remoteLastModified=%s source=Firebase.chat_history target=Room.chat_history",
                                userId,
                                message.id,
                                remoteEntity.messageType,
                                remoteEntity.tokenCount?.toString() ?: "null",
                                Date(remoteEntity.createdAt).toString(),
                                Date(remoteLastModified).toString()
                            )
                            continue
                        }
                    }

                    messagesToUpload.add(message)
                }

                if (messagesToUpload.isEmpty()) {
                    return@forEach
                }

                val firestoreBatch = firestore.batch()
                
                messagesToUpload.forEach { message ->
                    val messageData = mapOf(
                        "conversationId" to message.conversationId,
                        "messageType" to message.messageType,
                        "language" to message.language,
                        "content" to message.content,
                        "workoutContext" to message.workoutContext,
                        "tokenCount" to message.tokenCount,
                        "processingTimeMs" to message.processingTimeMs,
                        "createdAt" to message.createdAt,
                        "syncVersion" to 1L,
                        "lastModified" to message.lastModified,
                        "lastSyncedAt" to FieldValue.serverTimestamp()
                    )
                    
                    val docRef = collectionRef.document(message.id)
                    
                    firestoreBatch.set(docRef, messageData, SetOptions.merge())
                }
                
                // Commit the batch
                firestoreBatch.commit().await()
                Timber.tag(MONTHLY_USAGE_TAG).d(
                    "Uploaded chat history batch userId=%s count=%d tokenSum=%d source=Room.chat_history target=Firebase.chat_history",
                    userId,
                    messagesToUpload.size,
                    messagesToUpload.sumOf { it.tokenCount ?: 0 }
                )
                
                // Mark messages as synced in local database
                val messageIds = messagesToUpload.map { it.id }
                chatHistoryDao.markAsClean(
                    ids = messageIds,
                    userId = userId,
                    syncVersion = System.currentTimeMillis()
                )
                
                totalSynced += messagesToUpload.size
                Timber.tag(MONTHLY_USAGE_TAG).d("Marked chat history batch synced userId=%s count=%d source=Room.chat_history", userId, messagesToUpload.size)
            }
            
            return totalSynced
            
        } catch (e: Exception) {
            Timber.tag(MONTHLY_USAGE_TAG).e(e, "Failed to sync chat history userId=%s source=Room.chat_history target=Firebase.chat_history", userId)
            throw e
        }
    }
    
    /**
     * Downloads chat data from Firebase (for conflict resolution or initial sync).
     * This is typically called when a user signs in on a new device.
     */
    suspend fun downloadChatData(userId: String): Boolean {
        return try {
            Timber.tag(MONTHLY_USAGE_TAG).d("Downloading chat data userId=%s source=Firebase target=Room now=%s", userId, Date(System.currentTimeMillis()).toString())
            
            // Download preferences
            val preferencesDoc = firestore.collection("users")
                .document(userId)
                .collection("chat_preferences")
                .document("settings")
                .get()
                .await()
            
            if (preferencesDoc.exists()) {
                val remoteLastModified = when (val remoteValue = preferencesDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
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
                    isDirty = false,
                    isSynced = true,
                    syncVersion = preferencesDoc.getLong("syncVersion") ?: 1L,
                    lastModified = remoteLastModified,
                    updatedAt = preferencesDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                
                chatPreferencesDao.upsertFromRemote(preferences)
                Timber.tag(MONTHLY_USAGE_TAG).w(
                    "Downloaded chat preferences userId=%s monthlyLimit=%d dailyLimit=%d remoteLastModified=%s source=Firebase.chat_preferences target=Room.chat_preferences",
                    userId,
                    preferences.maxTokensPerMonth,
                    preferences.maxMessagesPerDay,
                    Date(remoteLastModified).toString()
                )
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
                    val remoteLastModified = when (val remoteValue = doc.get("lastModified")) {
                        is com.google.firebase.Timestamp -> remoteValue.toDate().time
                        is Number -> remoteValue.toLong()
                        else -> 0L
                    }
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
                        isDirty = false,
                        isSynced = true,
                        syncVersion = doc.getLong("syncVersion") ?: 1L,
                        lastModified = remoteLastModified
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing message document: ${doc.id}")
                    null
                }
            }
            
            if (messages.isNotEmpty()) {
                messages.forEach { message ->
                    chatHistoryDao.upsertFromRemote(message)
                }
                Timber.tag(MONTHLY_USAGE_TAG).w(
                    "Downloaded chat history userId=%s count=%d tokenSum=%d source=Firebase.chat_history target=Room.chat_history",
                    userId,
                    messages.size,
                    messages.sumOf { it.tokenCount ?: 0 }
                )
            }
            
            true
        } catch (e: Exception) {
            Timber.tag(MONTHLY_USAGE_TAG).e(e, "Failed to download chat data userId=%s source=Firebase target=Room", userId)
            false
        }
    }
}
