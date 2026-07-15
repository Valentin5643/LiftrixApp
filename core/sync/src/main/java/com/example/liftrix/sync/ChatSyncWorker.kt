package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.dao.ChatPreferencesDao
import com.example.liftrix.data.local.entity.ChatConversationEntity
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.data.local.entity.ChatPreferencesEntity
import com.example.liftrix.domain.usecase.chat.ChatConversationDeletionPolicy
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Per-user synchronization boundary for AI conversation metadata, messages,
 * preferences, tombstones, and retention.
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
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        fun createWorkRequest(userId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ChatSyncWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .addTag("user_$userId")
                .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(KEY_USER_ID)
        if (userId.isNullOrBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Missing userId"))
        }
        if (auth.currentUser?.uid != userId) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "User not authenticated")
            )
        }

        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

        try {
            Timber.tag(MONTHLY_USAGE_TAG).d(
                "Chat sync started userId=%s dirtyFlagGating=%s now=%s",
                userId,
                useDirtyFlagGating,
                Date(System.currentTimeMillis()).toString()
            )

            var syncedCount = syncConversationMetadata(userId)
            if (syncPreferences(userId, useDirtyFlagGating)) syncedCount++
            syncedCount += cleanupExpiredMessages(userId)
            syncedCount += syncChatHistory(userId, useDirtyFlagGating)
            check(downloadChatData(userId)) { "Chat restore failed" }

            Result.success(workDataOf(KEY_SYNC_COUNT to syncedCount))
        } catch (exception: Exception) {
            Timber.tag(MONTHLY_USAGE_TAG).e(
                exception,
                "Chat sync failed userId=%s attempt=%d",
                userId,
                runAttemptCount + 1
            )
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to (exception.message ?: "Unknown error"))
                )
            }
        }
    }

    /**
     * Reconciles all remote metadata into Room before uploading messages. Tombstones
     * are made remote-visible before their messages are deleted, and the final write
     * records completion without requiring a Room schema change.
     */
    private suspend fun syncConversationMetadata(userId: String): Int {
        val collection = firestore.collection("users")
            .document(userId)
            .collection("chat_conversations")
        val remoteDocuments = collection.get().await().documents.associateBy { it.id }

        remoteDocuments.values.forEach { document ->
            chatHistoryDao.upsertConversationFromRemote(document.toConversationEntity(userId))
        }

        var syncedCount = 0
        chatHistoryDao.getConversationMetadataForSync(userId).forEach { conversation ->
            val remote = remoteDocuments[conversation.conversationId]
            val remoteUpdatedAt = remote?.readMillis("updatedAt") ?: Long.MIN_VALUE
            if (remoteUpdatedAt > conversation.updatedAt) return@forEach

            val document = collection.document(conversation.conversationId)
            val data = conversation.toRemoteData()
            if (conversation.deletedAt != null) {
                document.set(data, SetOptions.merge()).await()
                deleteRemoteSuppressedMessages(userId, conversation)
            }
            document.set(
                data + ("lastSyncedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
            syncedCount++
        }
        return syncedCount
    }

    private suspend fun deleteRemoteSuppressedMessages(
        userId: String,
        conversation: ChatConversationEntity
    ) {
        val history = firestore.collection("users")
            .document(userId)
            .collection("chat_history")
        val query = if (ChatConversationDeletionPolicy.isReservedConversationId(
                conversation.conversationId
            )
        ) {
            history.whereLessThanOrEqualTo("createdAt", requireNotNull(conversation.deletedAt))
        } else {
            history.whereEqualTo("conversationId", conversation.conversationId)
        }
        deleteRemoteQueryInBatches(query)
    }

    private suspend fun deleteRemoteQueryInBatches(query: Query) {
        val documents = query.get().await().documents
        documents.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private suspend fun syncPreferences(userId: String, useDirtyFlagGating: Boolean): Boolean {
        val preferences = if (useDirtyFlagGating) {
            chatPreferencesDao.getDirtyChatPreferences(userId).firstOrNull()
        } else {
            chatPreferencesDao.getUnsyncedPreferences(userId)
        } ?: return false

        val document = firestore.collection("users")
            .document(userId)
            .collection("chat_preferences")
            .document("settings")
        val remote = document.get().await()
        val remoteLastModified = remote.readMillis("lastModified")
        if (remote.exists() && remoteLastModified > preferences.lastModified) {
            chatPreferencesDao.upsertFromRemote(remote.toPreferencesEntity(userId, preferences))
            return false
        }

        document.set(preferences.toRemoteData(), SetOptions.merge()).await()
        chatPreferencesDao.markAsClean(userId, System.currentTimeMillis())
        return true
    }

    private suspend fun syncChatHistory(userId: String, useDirtyFlagGating: Boolean): Int {
        val candidates = if (useDirtyFlagGating) {
            chatHistoryDao.getDirtyChatHistories(userId)
        } else {
            chatHistoryDao.getUnsyncedMessages(userId)
        }
        if (candidates.isEmpty()) return 0

        val collection = firestore.collection("users")
            .document(userId)
            .collection("chat_history")
        var totalSynced = 0

        candidates.chunked(BATCH_SIZE).forEach { localBatch ->
            val messagesToUpload = mutableListOf<ChatHistoryEntity>()
            localBatch.forEach messageLoop@{ message ->
                val conversation = chatHistoryDao.getConversationMetadata(
                    userId,
                    message.conversationId
                )
                val clearAll = chatHistoryDao.getClearAllTombstone(userId)
                if (ChatConversationDeletionPolicy.shouldSuppressMessage(
                        conversationId = message.conversationId,
                        messageCreatedAt = message.createdAt,
                        conversationDeletedAt = conversation?.deletedAt,
                        allHistoryDeletedAt = clearAll?.deletedAt
                    )
                ) {
                    chatHistoryDao.deleteMessagesByIds(userId, listOf(message.id))
                    return@messageLoop
                }

                val remote = collection.document(message.id).get().await()
                val remoteLastModified = remote.readMillis("lastModified")
                if (remote.exists() && remoteLastModified > message.lastModified) {
                    chatHistoryDao.upsertFromRemote(remote.toMessageEntity(userId))
                } else {
                    messagesToUpload += message
                }
            }

            if (messagesToUpload.isNotEmpty()) {
                val batch = firestore.batch()
                messagesToUpload.forEach { message ->
                    batch.set(
                        collection.document(message.id),
                        message.toRemoteData(),
                        SetOptions.merge()
                    )
                }
                batch.commit().await()
                chatHistoryDao.markAsClean(
                    ids = messagesToUpload.map { it.id },
                    userId = userId,
                    syncVersion = System.currentTimeMillis()
                )
                totalSynced += messagesToUpload.size
            }
        }
        return totalSynced
    }

    /** Deletes remote expired rows first; any remote failure leaves Room untouched. */
    private suspend fun cleanupExpiredMessages(userId: String): Int {
        var totalDeleted = 0
        while (true) {
            val expired = chatHistoryDao.getExpiredMessages(
                userId = userId,
                currentTime = System.currentTimeMillis(),
                limit = BATCH_SIZE
            )
            if (expired.isEmpty()) return totalDeleted

            val batch = firestore.batch()
            val history = firestore.collection("users")
                .document(userId)
                .collection("chat_history")
            expired.forEach { batch.delete(history.document(it.id)) }
            batch.commit().await()

            val deleted = chatHistoryDao.deleteMessagesByIds(userId, expired.map { it.id })
            check(deleted > 0) { "Expired chat rows could not be removed locally" }
            totalDeleted += deleted
        }
    }

    /** Metadata/tombstones are always applied before any remote message. */
    suspend fun downloadChatData(userId: String): Boolean = try {
        val preferencesDocument = firestore.collection("users")
            .document(userId)
            .collection("chat_preferences")
            .document("settings")
            .get()
            .await()
        if (preferencesDocument.exists()) {
            val fallback = chatPreferencesDao.getChatPreferencesForSync(userId)
                ?: ChatPreferencesEntity(userId = userId)
            chatPreferencesDao.upsertFromRemote(
                preferencesDocument.toPreferencesEntity(userId, fallback)
            )
        }

        val metadataDocuments = firestore.collection("users")
            .document(userId)
            .collection("chat_conversations")
            .get()
            .await()
        metadataDocuments.documents.forEach { document ->
            chatHistoryDao.upsertConversationFromRemote(document.toConversationEntity(userId))
        }

        val preferences = chatPreferencesDao.getChatPreferencesForSync(userId)
        val retentionDays = preferences?.autoClearDays ?: DEFAULT_RETENTION_DAYS
        val messages = firestore.collection("users")
            .document(userId)
            .collection("chat_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
        messages.documents.forEach { document ->
            val entity = document.toMessageEntity(userId, retentionDays)
            chatHistoryDao.upsertFromRemote(entity)
        }
        true
    } catch (exception: Exception) {
        Timber.tag(MONTHLY_USAGE_TAG).e(
            exception,
            "Failed to download chat data userId=%s",
            userId
        )
        false
    }

    private fun ChatConversationEntity.toRemoteData(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "conversationId" to conversationId,
        "title" to title,
        "isTitleCustom" to isTitleCustom,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "deletedAt" to deletedAt,
        "syncVersion" to 1L,
        "lastModified" to updatedAt
    )

    private fun ChatPreferencesEntity.toRemoteData(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "preferredLanguage" to preferredLanguage,
        "autoDetectLanguage" to autoDetectLanguage,
        "chatNotificationsEnabled" to chatNotificationsEnabled,
        "conversationHistoryEnabled" to conversationHistoryEnabled,
        "workoutContextSharing" to workoutContextSharing,
        "maxMessagesPerDay" to maxMessagesPerDay,
        "maxTokensPerMonth" to maxTokensPerMonth,
        "autoClearDays" to autoClearDays,
        "userContextPrompt" to userContextPrompt,
        "aiResponseStyle" to aiResponseStyle,
        "includeWorkoutHistory" to includeWorkoutHistory,
        "includeExerciseFormTips" to includeExerciseFormTips,
        "usageNotificationsThreshold" to usageNotificationsThreshold,
        "conversationSaveEnabled" to conversationSaveEnabled,
        "updatedAt" to updatedAt,
        "syncVersion" to 1L,
        "lastModified" to lastModified,
        "lastSyncedAt" to FieldValue.serverTimestamp()
    )

    private fun ChatHistoryEntity.toRemoteData(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "conversationId" to conversationId,
        "messageType" to messageType.toRemoteMessageType(),
        "language" to language,
        "content" to content,
        "workoutContext" to workoutContext,
        "tokenCount" to tokenCount,
        "processingTimeMs" to processingTimeMs,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt,
        "syncVersion" to 1L,
        "lastModified" to lastModified,
        "lastSyncedAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toConversationEntity(userId: String): ChatConversationEntity {
        val documentUserId = getString("userId") ?: userId
        require(documentUserId == userId) { "Conversation metadata owner mismatch" }
        val conversationId = getString("conversationId") ?: id
        require(conversationId == id) { "Conversation metadata ID mismatch" }
        val createdAt = readMillis("createdAt")
        val updatedAt = readMillis("updatedAt")
        require(createdAt > 0L && updatedAt > 0L) { "Invalid conversation metadata timestamp" }
        return ChatConversationEntity(
            userId = userId,
            conversationId = conversationId,
            title = getString("title") ?: "New chat",
            isTitleCustom = getBoolean("isTitleCustom") ?: false,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = get("deletedAt")?.let { readMillis("deletedAt") }
        )
    }

    private fun DocumentSnapshot.toPreferencesEntity(
        userId: String,
        fallback: ChatPreferencesEntity
    ): ChatPreferencesEntity {
        require((getString("userId") ?: userId) == userId) { "Preference owner mismatch" }
        return fallback.copy(
            userId = userId,
            preferredLanguage = getString("preferredLanguage") ?: fallback.preferredLanguage,
            autoDetectLanguage = getBoolean("autoDetectLanguage") ?: fallback.autoDetectLanguage,
            chatNotificationsEnabled = getBoolean("chatNotificationsEnabled")
                ?: fallback.chatNotificationsEnabled,
            conversationHistoryEnabled = getBoolean("conversationHistoryEnabled")
                ?: fallback.conversationHistoryEnabled,
            workoutContextSharing = getBoolean("workoutContextSharing")
                ?: fallback.workoutContextSharing,
            maxMessagesPerDay = getLong("maxMessagesPerDay")?.toInt()
                ?: fallback.maxMessagesPerDay,
            maxTokensPerMonth = getLong("maxTokensPerMonth")?.toInt()
                ?: fallback.maxTokensPerMonth,
            autoClearDays = getLong("autoClearDays")?.toInt() ?: fallback.autoClearDays,
            userContextPrompt = if (contains("userContextPrompt")) {
                getString("userContextPrompt")
            } else {
                fallback.userContextPrompt
            },
            aiResponseStyle = getString("aiResponseStyle") ?: fallback.aiResponseStyle,
            includeWorkoutHistory = getBoolean("includeWorkoutHistory")
                ?: fallback.includeWorkoutHistory,
            includeExerciseFormTips = getBoolean("includeExerciseFormTips")
                ?: fallback.includeExerciseFormTips,
            usageNotificationsThreshold = getLong("usageNotificationsThreshold")?.toInt()
                ?: fallback.usageNotificationsThreshold,
            conversationSaveEnabled = getBoolean("conversationSaveEnabled")
                ?: fallback.conversationSaveEnabled,
            isDirty = false,
            isSynced = true,
            syncVersion = getLong("syncVersion") ?: 1L,
            lastModified = readMillis("lastModified"),
            updatedAt = readMillis("updatedAt").takeIf { it > 0L } ?: fallback.updatedAt
        )
    }

    private fun DocumentSnapshot.toMessageEntity(
        userId: String,
        retentionDays: Int = DEFAULT_RETENTION_DAYS
    ): ChatHistoryEntity {
        require((getString("userId") ?: userId) == userId) { "Chat message owner mismatch" }
        val conversationId = requireNotNull(getString("conversationId")) {
            "Missing conversationId for message $id"
        }
        ChatConversationDeletionPolicy.requireRealConversationId(conversationId)
        val createdAt = readMillis("createdAt")
        require(createdAt > 0L) { "Invalid createdAt for message $id" }
        return ChatHistoryEntity(
            id = id,
            userId = userId,
            conversationId = conversationId,
            messageType = requireNotNull(getString("messageType")).toLocalMessageType(),
            language = getString("language") ?: "en",
            content = requireNotNull(getString("content")),
            workoutContext = getString("workoutContext"),
            tokenCount = getLong("tokenCount")?.toInt(),
            processingTimeMs = getLong("processingTimeMs"),
            createdAt = createdAt,
            isDirty = false,
            isSynced = true,
            syncVersion = getLong("syncVersion") ?: 1L,
            lastModified = readMillis("lastModified"),
            expiresAt = get("expiresAt")?.let { readMillis("expiresAt") }
                ?: createdAt + retentionDays.coerceAtLeast(1) * MILLIS_PER_DAY
        )
    }

    private fun DocumentSnapshot.readMillis(field: String): Long = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> 0L
    }

    private fun String.toRemoteMessageType(): String = when (this) {
        "USER" -> "USER"
        "AI_RESPONSE" -> "ASSISTANT"
        "SYSTEM" -> "SYSTEM"
        else -> error("Unknown local chat message type: $this")
    }

    private fun String.toLocalMessageType(): String = when (this) {
        "USER" -> "USER"
        "ASSISTANT" -> "AI_RESPONSE"
        "SYSTEM" -> "SYSTEM"
        else -> error("Unknown remote chat message type: $this")
    }
}
