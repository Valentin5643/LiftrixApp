package com.example.liftrix.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.FolderDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.entity.FolderEntity
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.example.liftrix.service.sync.ConflictResolver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.example.liftrix.config.OfflineArchitectureFlags
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.example.liftrix.domain.model.TemplateExercise
import java.time.Instant

/**
 * WorkManager worker for syncing workout templates to Firebase Firestore.
 * 
 * This worker handles:
 * - Batch processing of unsynced templates for efficiency
 * - Template version tracking and conflict resolution
 * - User-scoped template synchronization
 * - Template usage statistics preservation
 * 
 * Templates are synced with the following strategy:
 * - Local-first: Templates are saved locally first for offline access
 * - Conflict resolution: Last-write-wins with usage statistics merging
 * - Batch processing: Templates are processed in smaller batches for reliability
 * - Version tracking: Each template has a sync version for conflict detection
 */
@HiltWorker
class TemplateSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val templateDao: WorkoutTemplateDao,
    private val folderDao: FolderDao,
    private val firestore: FirebaseFirestore,
    private val conflictResolver: ConflictResolver,
    private val auth: FirebaseAuth,
    private val startupRestoreGate: StartupRestoreGate,
    private val templateRestoreNotifier: TemplateRestoreNotifier
) : BaseSyncWorker(context, params) {

    init {
        Timber.d("✅ TemplateSyncWorker constructed with Hilt dependency injection")
    }

    override val workerName: String = "TemplateSyncWorker"

    private val templateJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    companion object {
        const val WORK_NAME = "template_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val BATCH_SIZE = 15 // Smaller batch size for templates
        private const val UNCATEGORIZED_FOLDER_NAME = "Uncategorized"

        fun createWorkRequest(userId: String, startupSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<TemplateSyncWorker>()
                .setInputData(workDataOf("userId" to userId, "startupSync" to startupSync))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("template_sync")
                .build()
        }
    }

    private data class TemplateRestoreStats(
        var inserted: Int = 0,
        var updated: Int = 0,
        var unchanged: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0,
        val skipReasons: MutableMap<String, Int> = mutableMapOf()
    )

    private data class ReceivedTemplate(
        val documentId: String,
        val fieldTemplateId: String?,
        val templateId: String?,
        val name: String,
        val remoteUserId: String?,
        val folderId: String?,
        val deleted: Boolean,
        val archived: Boolean
    )

    private data class TemplateValidation(
        val result: String,
        val reason: String? = null
    )

    private object TemplateValidationResult {
        const val VALID = "valid"
        const val SKIP = "skip"
    }

    override suspend fun performSync(userId: String): Result = withContext(Dispatchers.IO) {
        val startupSync = inputData.getBoolean("startupSync", false)
        // Verify authentication before attempting sync
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid != userId) {
            if (startupSync) {
                startupRestoreGate.transition(userId, StartupRestoreState.RESTORE_FAILED, "template_auth_failed")
            }
            Timber.e("TemplateSyncWorker: User not authenticated or user ID mismatch. Current: ${currentUser?.uid}, Expected: $userId")
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Authentication failed - user not signed in or ID mismatch")
            )
        }
        
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

        val collectionRef = firestore
            .collection("users")
            .document(userId)
            .collection("templates")
        val localBeforeRestore = templateDao.getTemplateCount(userId)
        val folderCountBeforeRestore = folderDao.getFolderCount(userId)
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_RESTORE_START userId=$userId startupSync=$startupSync path=users/$userId/templates roomBeforeCount=$localBeforeRestore folderCountBeforeRestore=$folderCountBeforeRestore timestamp=${System.currentTimeMillis()}"
        )
        val defaultFolderId = ensureDefaultFolder(userId)
        Timber.tag("StartupRestoreFix").i(
            "operation=FOLDER_RESTORE_PHASE_COMPLETE userId=$userId folderCountBefore=$folderCountBeforeRestore folderCountAfter=${folderDao.getFolderCount(userId)} defaultFolderId=${defaultFolderId ?: "null"} timestamp=${System.currentTimeMillis()}"
        )
        val remoteTemplates = collectionRef.get().await()
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_RESTORE_REMOTE_RESULT userId=$userId startupSync=$startupSync path=users/$userId/templates remoteCount=${remoteTemplates.size()} timestamp=${System.currentTimeMillis()}"
        )
        val restoreStats = TemplateRestoreStats()
        val seenTemplateIds = mutableSetOf<String>()
        remoteTemplates.documents.forEach { doc ->
            val received = doc.toReceivedTemplate(userId)
            Timber.tag("StartupRestoreFix").i(
                "operation=TEMPLATE_REMOTE_ITEM_RECEIVED userId=$userId documentId=${doc.id} templateId=${received.templateId ?: "null"} fieldTemplateId=${received.fieldTemplateId ?: "null"} name=\"${received.name}\" remoteUserId=${received.remoteUserId ?: "null"} folderId=${received.folderId ?: "null"} deleted=${received.deleted} archived=${received.archived} timestamp=${System.currentTimeMillis()}"
            )
            try {
                val validation = validateRemoteTemplate(received, userId, seenTemplateIds)
                Timber.tag("StartupRestoreFix").i(
                    "operation=TEMPLATE_RESTORE_VALIDATE_RESULT userId=$userId documentId=${doc.id} templateId=${received.templateId ?: "null"} result=${validation.result} reason=${validation.reason ?: "none"} timestamp=${System.currentTimeMillis()}"
                )
                if (validation.result == TemplateValidationResult.SKIP) {
                    restoreStats.skipped++
                    val skipReason = validation.reason ?: "unknown"
                    restoreStats.skipReasons[skipReason] = (restoreStats.skipReasons[skipReason] ?: 0) + 1
                    Timber.tag("StartupRestoreFix").w(
                        "operation=TEMPLATE_RESTORE_SKIPPED userId=$userId documentId=${doc.id} templateId=${received.templateId ?: "null"} reason=$skipReason timestamp=${System.currentTimeMillis()}"
                    )
                    return@forEach
                }

                val remoteEntity = doc.toTemplateEntity(userId, defaultFolderId)
                seenTemplateIds.add(remoteEntity.id)
                Timber.tag("StartupRestoreFix").d(
                    "operation=TEMPLATE_RESTORE_UPSERT_START userId=$userId templateId=${remoteEntity.id} name=\"${remoteEntity.name}\" folderId=${remoteEntity.folderId ?: "null"} roomBeforeVisibleCount=${templateDao.getTemplateCount(userId)} timestamp=${System.currentTimeMillis()}"
                )
                val action = templateDao.upsertFromRemote(remoteEntity)
                when (action) {
                    "inserted" -> restoreStats.inserted++
                    "updated" -> restoreStats.updated++
                    "unchanged" -> restoreStats.unchanged++
                    else -> restoreStats.unchanged++
                }
                val restoredEntity = templateDao.getTemplateById(remoteEntity.id, userId)
                if (restoredEntity == null) {
                    restoreStats.failed++
                    Timber.tag("StartupRestoreFix").e(
                        "operation=TEMPLATE_RESTORE_UPSERT_FAILED userId=$userId templateId=${remoteEntity.id} exception=upsert_returned_without_queryable_row timestamp=${System.currentTimeMillis()}"
                    )
                    return@forEach
                }
                Timber.tag("StartupRestoreFix").d(
                    "operation=TEMPLATE_RESTORE_UPSERT_SUCCESS userId=$userId templateId=${remoteEntity.id} action=$action localRowId=primaryKeyIsStableId syncId=${remoteEntity.id} folderId=${restoredEntity.folderId ?: "null"} timestamp=${System.currentTimeMillis()}"
                )
            } catch (e: Exception) {
                restoreStats.failed++
                Timber.tag("StartupRestoreFix").e(
                    e,
                    "operation=TEMPLATE_RESTORE_UPSERT_FAILED userId=$userId documentId=${doc.id} templateId=${received.templateId ?: "null"} exception=${e.javaClass.simpleName} timestamp=${System.currentTimeMillis()}"
                )
            }
        }
        val roomTotalAllUsers = templateDao.getTotalTemplateCountAllUsers()
        val roomVisible = templateDao.getTemplateCount(userId)
        val remoteTemplateIds = seenTemplateIds.toList()
        val wrongUser = if (remoteTemplateIds.isEmpty()) {
            0
        } else {
            templateDao.getTemplateCountForWrongUser(remoteTemplateIds, userId)
        }
        val missingFolder = templateDao.getTemplateCountWithMissingFolder(userId)
        val folderCounts = templateDao.getTemplateCountByFolder(userId)
        val restoredIds = if (remoteTemplateIds.isEmpty()) {
            emptySet()
        } else {
            templateDao.getRestoredTemplateIds(remoteTemplateIds, userId).toSet()
        }
        val missingIds = seenTemplateIds.filterNot { it in restoredIds }
        val accounted = restoreStats.inserted + restoreStats.updated + restoreStats.unchanged + restoreStats.skipped + restoreStats.failed
        val reconciled = accounted == remoteTemplates.size() && restoreStats.failed == 0 && missingIds.isEmpty()

        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_ROOM_COUNT_TOTAL userId=$userId allUsersCount=$roomTotalAllUsers userScopedCount=$roomVisible timestamp=${System.currentTimeMillis()}"
        )
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_ROOM_COUNT_VISIBLE userId=$userId visibleCount=$roomVisible visibilityRule=user_id_only timestamp=${System.currentTimeMillis()}"
        )
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_ROOM_COUNT_BY_FOLDER userId=$userId counts=${folderCounts.joinToString(separator = "|") { "${it.folderId ?: "null"}:${it.count}" }} timestamp=${System.currentTimeMillis()}"
        )
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_ROOM_COUNT_WRONG_USER userId=$userId wrongUserCount=$wrongUser checkedRemoteIds=${seenTemplateIds.size} timestamp=${System.currentTimeMillis()}"
        )
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_ROOM_COUNT_DELETED_OR_ARCHIVED userId=$userId deletedOrArchivedCount=${restoreStats.skipReasons["remote_deleted_or_archived"] ?: 0} missingFolderCount=$missingFolder timestamp=${System.currentTimeMillis()}"
        )
        if (missingIds.isNotEmpty()) {
            missingIds.forEach { missingId ->
                val anyUserEntity = templateDao.getTemplateByIdAnyUser(missingId)
                Timber.tag("StartupRestoreFix").e(
                    "operation=TEMPLATE_RESTORE_MISSING_AFTER_UPSERT userId=$userId templateId=$missingId foundAnyUser=${anyUserEntity != null} foundUserId=${anyUserEntity?.userId ?: "null"} reason=not_queryable_by_user_scope timestamp=${System.currentTimeMillis()}"
                )
            }
        }
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_RESTORE_RECONCILE userId=$userId remoteCount=${remoteTemplates.size()} inserted=${restoreStats.inserted} updated=${restoreStats.updated} unchanged=${restoreStats.unchanged} skipped=${restoreStats.skipped} failed=${restoreStats.failed} accounted=$accounted roomTotal=$roomTotalAllUsers roomVisible=$roomVisible missingQueryable=${missingIds.size} skipReasons=${restoreStats.skipReasons} reconciled=$reconciled timestamp=${System.currentTimeMillis()}"
        )
        Timber.tag("StartupRestoreFix").i(
            "operation=TEMPLATE_RESTORE_FINISH userId=$userId startupSync=$startupSync roomBeforeCount=$localBeforeRestore roomAfterCount=$roomVisible remoteCount=${remoteTemplates.size()} reconciled=$reconciled timestamp=${System.currentTimeMillis()}"
        )
        if (!reconciled) {
            if (startupSync) {
                startupRestoreGate.transition(userId, StartupRestoreState.RESTORE_FAILED, "template_restore_reconcile_failed")
            }
            Timber.tag("StartupRestoreFix").e(
                "operation=RESTORE_GATE_BLOCKED userId=$userId reason=template_restore_reconcile_failed remoteCount=${remoteTemplates.size()} accounted=$accounted failed=${restoreStats.failed} missingQueryable=${missingIds.size} roomVisible=$roomVisible timestamp=${System.currentTimeMillis()}"
            )
            return@withContext Result.retry()
        }
        val restoreFinishedAt = System.currentTimeMillis()
        Timber.tag("StartupRestoreFix").i(
            "[TEMPLATE-LOAD] operation=TEMPLATE_ROOM_WRITE_COMMITTED userId=$userId roomVisible=$roomVisible remoteCount=${remoteTemplates.size()} reconciled=$reconciled timestamp=$restoreFinishedAt"
        )
        templateRestoreNotifier.notifyRestoreCompleted(userId, roomVisible, restoreFinishedAt)
        if (startupSync) {
            startupRestoreGate.transition(userId, StartupRestoreState.RESTORE_COMPLETE, "all_restore_phases_complete")
        }

        val unsyncedTemplates = if (useDirtyFlagGating) {
            templateDao.getDirtyWorkoutTemplates(userId)
        } else {
            templateDao.getUnsyncedTemplates(userId)
        }
        
        if (unsyncedTemplates.isEmpty()) {
            Timber.d("No unsynced templates found for user $userId")
            return@withContext Result.success()
        }

        Timber.d("Found ${unsyncedTemplates.size} templates to sync for user $userId (dirty gating: $useDirtyFlagGating)")
            
            var successCount = 0
            var failureCount = 0
            // Process templates in batches for efficient prefetch and writes
            unsyncedTemplates.chunked(BATCH_SIZE).forEach { batch ->
                val remoteDocs = FirestorePrefetcher.prefetchByIds(
                    collection = collectionRef,
                    ids = batch.map { it.id }
                )
                val firestoreBatch = firestore.batch()
                val templatesToMarkClean = mutableListOf<String>()
                var batchHasWrites = false

                batch.forEach { template ->
                    try {
                        if (useDirtyFlagGating && !template.isDirty) {
                            return@forEach
                        }

                        val docRef = collectionRef.document(template.id)
                        
                        // Check for remote version for conflict resolution
                        val remoteDoc = remoteDocs[template.id]

                        if (remoteDoc?.exists() == true) {
                            val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                                is com.google.firebase.Timestamp -> remoteValue.toDate().time
                                is Number -> remoteValue.toLong()
                                else -> 0L
                            }
                            if (remoteLastModified > template.lastModified) {
                                val gson = Gson()
                                val exercisesJson = when (val remoteExercises = remoteDoc.get("exercises")) {
                                    is List<*> -> gson.toJson(remoteExercises)
                                    is String -> remoteExercises
                                    else -> template.templateExercisesJson
                                }
                                val updatedAt = when (val remoteUpdatedAt = remoteDoc.get("updatedAt")) {
                                    is com.google.firebase.Timestamp -> remoteUpdatedAt.toDate().toInstant()
                                    is Number -> java.time.Instant.ofEpochSecond(remoteUpdatedAt.toLong())
                                    else -> template.updatedAt
                                }
                                val createdAt = when (val remoteCreatedAt = remoteDoc.get("createdAt")) {
                                    is com.google.firebase.Timestamp -> remoteCreatedAt.toDate().toInstant()
                                    is Number -> java.time.Instant.ofEpochSecond(remoteCreatedAt.toLong())
                                    else -> template.createdAt
                                }
                                val remoteEntity = template.copy(
                                    templateExercisesJson = exercisesJson,
                                    estimatedDurationMinutes = (remoteDoc.getLong("estimatedDurationMinutes")
                                        ?: template.estimatedDurationMinutes?.toLong())?.toInt(),
                                    difficultyLevel = (remoteDoc.getLong("difficultyLevel")
                                        ?: template.difficultyLevel?.toLong())?.toInt(),
                                    folderId = remoteDoc.getString("folderId") ?: template.folderId,
                                    usageCount = (remoteDoc.getLong("usageCount") ?: template.usageCount.toLong()).toInt(),
                                    lastUsedAt = (remoteDoc.getLong("lastUsedAt"))?.let {
                                        java.time.Instant.ofEpochSecond(it)
                                    } ?: template.lastUsedAt,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    isDirty = false,
                                    isSynced = true,
                                    syncVersion = System.currentTimeMillis(),
                                    lastModified = remoteLastModified
                                )
                                templateDao.upsertFromRemote(remoteEntity)
                                return@forEach
                            }
                        }
                        
                        // Parse the JSON exercises string into a proper list for Firestore validation
                        val exercisesList = try {
                            templateJson.decodeFromString<List<TemplateExercise>>(template.templateExercisesJson)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse template exercises JSON, using empty list")
                            emptyList<TemplateExercise>()
                        }
                        
                        // Create template data that complies with Firestore security rules
                        val templateData = mapOf(
                            "id" to template.id,
                            "userId" to userId,
                            "name" to template.name,
                            "description" to template.description,
                            "exercises" to exercisesList, // Security rule expects this as 'exercises' list
                            "estimatedDurationMinutes" to template.estimatedDurationMinutes,
                            "difficultyLevel" to template.difficultyLevel,
                            // "category" field not available in entity
                            "usageCount" to template.usageCount,
                            "lastUsedAt" to template.lastUsedAt?.epochSecond,
                            "folderId" to template.folderId,
                            "createdAt" to template.createdAt.epochSecond,
                            // Add all required sync metadata fields
                            "syncVersion" to template.syncVersion,
                            "lastModified" to template.lastModified,
                            "isSynced" to true
                        )
                        
                        firestoreBatch.set(docRef, templateData, SetOptions.merge())
                        templatesToMarkClean.add(template.id)
                        batchHasWrites = true
                        
                    } catch (e: Exception) {
                        when {
                            e.message?.contains("PERMISSION_DENIED") == true -> {
                                Timber.e(e, "Permission denied syncing template: ${template.id}. Check Firestore security rules.")
                            }
                            e.message?.contains("INVALID_ARGUMENT") == true -> {
                                Timber.e(e, "Invalid data syncing template: ${template.id}. Check field types and validation.")
                            }
                            else -> {
                                Timber.e(e, "Failed to sync template: ${template.id}")
                            }
                        }
                        failureCount++
                    }
                }

                if (!batchHasWrites) {
                    return@forEach
                }

                try {
                    firestoreBatch.commit().await()
                    templateDao.markAsClean(
                        ids = templatesToMarkClean,
                        userId = userId,
                        syncVersion = System.currentTimeMillis()
                    )
                    successCount += templatesToMarkClean.size
                    Timber.d("Successfully synced batch of ${templatesToMarkClean.size} templates")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to commit template batch of ${templatesToMarkClean.size}")
                    failureCount += templatesToMarkClean.size
                }
            }
            
            Timber.d("Template sync complete - Success: $successCount, Failed: $failureCount")
            
        return@withContext when {
            failureCount > 0 && successCount == 0 -> {
                // All failed - let BaseSyncWorker handle retry logic
                throw Exception("All template syncs failed. Success: $successCount, Failed: $failureCount")
            }
            else -> {
                // Some or all succeeded
                Result.success(
                    workDataOf(
                        KEY_SYNC_COUNT to successCount,
                        "failed_count" to failureCount
                    )
                )
            }
        }
    }

    private fun DocumentSnapshot.toReceivedTemplate(pathUserId: String): ReceivedTemplate {
        val fieldTemplateId = getString("id")?.takeIf { it.isNotBlank() }
        val canonicalTemplateId = when {
            fieldTemplateId.isNullOrBlank() -> id
            fieldTemplateId != id -> id
            else -> fieldTemplateId
        }
        return ReceivedTemplate(
            documentId = id,
            fieldTemplateId = fieldTemplateId,
            templateId = canonicalTemplateId,
            name = getString("name")?.takeIf { it.isNotBlank() } ?: "Untitled Template",
            remoteUserId = getString("userId") ?: pathUserId,
            folderId = getString("folderId"),
            deleted = getBooleanFlag("deleted") || getBooleanFlag("isDeleted"),
            archived = getBooleanFlag("archived") || getBooleanFlag("isArchived")
        )
    }

    private fun validateRemoteTemplate(
        received: ReceivedTemplate,
        pathUserId: String,
        seenTemplateIds: Set<String>
    ): TemplateValidation {
        val templateId = received.templateId
        return when {
            templateId.isNullOrBlank() -> TemplateValidation(
                result = TemplateValidationResult.SKIP,
                reason = "missing_template_id"
            )
            received.deleted || received.archived -> TemplateValidation(
                result = TemplateValidationResult.SKIP,
                reason = "remote_deleted_or_archived"
            )
            templateId in seenTemplateIds -> TemplateValidation(
                result = TemplateValidationResult.SKIP,
                reason = "duplicate_template_id_in_remote_result"
            )
            received.remoteUserId != pathUserId -> TemplateValidation(
                result = TemplateValidationResult.VALID,
                reason = "remote_user_id_repaired_to_path_user"
            )
            received.fieldTemplateId != null && received.fieldTemplateId != received.documentId -> TemplateValidation(
                result = TemplateValidationResult.VALID,
                reason = "field_id_mismatch_using_document_id"
            )
            else -> TemplateValidation(result = TemplateValidationResult.VALID)
        }
    }

    private suspend fun DocumentSnapshot.toTemplateEntity(
        pathUserId: String,
        defaultFolderId: String?
    ): WorkoutTemplateEntity {
        val gson = Gson()
        val received = toReceivedTemplate(pathUserId)
        val templateId = received.templateId ?: id
        val exercisesJson = when (val remoteExercises = get("exercises") ?: get("templateExercisesJson")) {
            is List<*> -> gson.toJson(remoteExercises)
            is String -> remoteExercises
            else -> ""
        }
        val createdAt = instantFromFirestoreValue(get("createdAt")) ?: Instant.now()
        val updatedAt = instantFromFirestoreValue(get("updatedAt")) ?: createdAt
        val lastModified = millisFromFirestoreValue(get("lastModified"))
            ?: updatedAt.toEpochMilli()
        val folderId = resolveTemplateFolderId(
            requestedFolderId = received.folderId,
            userId = pathUserId,
            defaultFolderId = defaultFolderId,
            templateId = templateId
        )

        return WorkoutTemplateEntity(
            id = templateId,
            userId = pathUserId,
            name = received.name,
            description = getString("description"),
            templateExercisesJson = exercisesJson,
            estimatedDurationMinutes = getLong("estimatedDurationMinutes")?.toInt(),
            difficultyLevel = getLong("difficultyLevel")?.toInt(),
            folderId = folderId,
            usageCount = getLong("usageCount")?.toInt() ?: 0,
            lastUsedAt = instantFromFirestoreValue(get("lastUsedAt")),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isSynced = true,
            syncVersion = getLong("syncVersion") ?: System.currentTimeMillis(),
            isDirty = false,
            lastModified = lastModified
        )
    }

    private suspend fun ensureDefaultFolder(userId: String): String? {
        val defaultFolderId = "uncategorized_$userId"
        val existing = folderDao.getFolderById(defaultFolderId, userId)
        if (existing != null) {
            return existing.id
        }

        return try {
            val now = Instant.now()
            folderDao.insertFolder(
                FolderEntity(
                    id = defaultFolderId,
                    userId = userId,
                    name = UNCATEGORIZED_FOLDER_NAME,
                    createdAt = now,
                    updatedAt = now,
                    templateCount = 0,
                    isSynced = false,
                    syncVersion = 1L,
                    isDirty = true,
                    lastModified = System.currentTimeMillis()
                )
            )
            Timber.tag("StartupRestoreFix").i(
                "operation=FOLDER_RESTORE_DEFAULT_CREATED userId=$userId folderId=$defaultFolderId timestamp=${System.currentTimeMillis()}"
            )
            defaultFolderId
        } catch (e: Exception) {
            Timber.tag("StartupRestoreFix").w(
                e,
                "operation=FOLDER_RESTORE_DEFAULT_CREATE_FAILED userId=$userId folderId=$defaultFolderId action=templates_with_missing_folders_restore_with_null_folder timestamp=${System.currentTimeMillis()}"
            )
            null
        }
    }

    private suspend fun resolveTemplateFolderId(
        requestedFolderId: String?,
        userId: String,
        defaultFolderId: String?,
        templateId: String
    ): String? {
        if (requestedFolderId.isNullOrBlank()) {
            return defaultFolderId
        }

        val localFolder = folderDao.getFolderById(requestedFolderId, userId)
        if (localFolder != null) {
            return requestedFolderId
        }

        Timber.tag("StartupRestoreFix").w(
            "operation=TEMPLATE_RESTORE_VALIDATE_RESULT userId=$userId templateId=$templateId result=valid reason=missing_folder_moved_to_default missingFolderId=$requestedFolderId defaultFolderId=${defaultFolderId ?: "null"} timestamp=${System.currentTimeMillis()}"
        )
        return defaultFolderId
    }

    private fun DocumentSnapshot.getBooleanFlag(field: String): Boolean {
        return when (val value = get(field)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }
    }

    private fun instantFromFirestoreValue(value: Any?): Instant? {
        return when (value) {
            is com.google.firebase.Timestamp -> value.toDate().toInstant()
            is Long -> Instant.ofEpochSecond(value)
            is Int -> Instant.ofEpochSecond(value.toLong())
            is Number -> Instant.ofEpochSecond(value.toLong())
            else -> null
        }
    }

    private fun millisFromFirestoreValue(value: Any?): Long? {
        return when (value) {
            is com.google.firebase.Timestamp -> value.toDate().time
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }
    }
}
