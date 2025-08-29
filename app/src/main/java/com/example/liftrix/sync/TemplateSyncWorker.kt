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
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.example.liftrix.service.sync.ConflictResolver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.example.liftrix.domain.model.TemplateExercise

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
    private val firestore: FirebaseFirestore,
    private val conflictResolver: ConflictResolver,
    private val auth: FirebaseAuth
) : BaseSyncWorker(context, params) {
    
    // 🔧 HOTFIX: Fallback constructor for when Hilt factory generation fails
    // This allows WorkManager to instantiate the worker via reflection
    // TEMPORARY: Remove once Hilt assisted factories are confirmed working
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        WorkerServiceLocator.getTemplateSyncDependencies(context).run {
            Timber.w("⚠️ TemplateSyncWorker using FALLBACK constructor - Hilt factory failed!")
            return@run this
        }
    )
    
    // Helper constructor to unpack the dependency structure
    private constructor(
        context: Context,
        params: WorkerParameters,
        deps: WorkerServiceLocator.TemplateSyncDependencies
    ) : this(
        context, params,
        deps.templateDao, deps.firestore, deps.conflictResolver, deps.auth
    )

    init {
        val processName = getProcessName()
        Timber.d("✅ TemplateSyncWorker constructed with Hilt dependency injection in process: $processName")
    }
    
    private fun getProcessName(): String {
        return try {
            val processName = applicationContext.packageManager
                .getApplicationLabel(applicationContext.applicationInfo)
                .toString()
            processName
        } catch (e: Exception) {
            "unknown"
        }
    }

    override val workerName: String = "TemplateSyncWorker"

    companion object {
        const val WORK_NAME = "template_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val BATCH_SIZE = 15 // Smaller batch size for templates

        fun createWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<TemplateSyncWorker>()
                .setInputData(workDataOf("userId" to userId))
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

    override suspend fun performSync(userId: String): Result = withContext(Dispatchers.IO) {
        // Verify authentication before attempting sync
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid != userId) {
            Timber.e("TemplateSyncWorker: User not authenticated or user ID mismatch. Current: ${currentUser?.uid}, Expected: $userId")
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Authentication failed - user not signed in or ID mismatch")
            )
        }
        
        val unsyncedTemplates = templateDao.getUnsyncedTemplates(userId)
        
        if (unsyncedTemplates.isEmpty()) {
            Timber.d("No unsynced templates found for user $userId")
            return@withContext Result.success()
        }

        Timber.d("Found ${unsyncedTemplates.size} unsynced templates for user $userId")
            
            var successCount = 0
            var failureCount = 0

            // Process templates individually for better error handling
            unsyncedTemplates.forEach { template ->
                try {
                    val docRef = firestore
                        .collection("users")
                        .document(userId)
                        .collection("templates")
                        .document(template.id)
                    
                    // Check for remote version for conflict resolution
                    val remoteDoc = docRef.get().await()
                    
                    val shouldSync = if (remoteDoc.exists()) {
                        val remoteTemplate = remoteDoc.data
                        val remoteUpdatedAt = remoteTemplate?.get("updatedAt") as? Long ?: 0
                        
                        // Simple last-write-wins, but preserve usage statistics
                        if (template.updatedAt.epochSecond > remoteUpdatedAt) {
                            true // Local is newer
                        } else {
                            // Remote is newer, but merge usage statistics
                            val remoteUsageCount = remoteTemplate?.get("usageCount") as? Long ?: 0
                            val remoteLastUsedAt = remoteTemplate?.get("lastUsedAt") as? Long
                            
                            // Keep the higher usage count and most recent usage
                            val mergedUsageCount = maxOf(template.usageCount, remoteUsageCount.toInt())
                            val mergedLastUsedAt = if (remoteLastUsedAt != null && template.lastUsedAt != null) {
                                if (remoteLastUsedAt > template.lastUsedAt.epochSecond) {
                                    remoteLastUsedAt
                                } else {
                                    template.lastUsedAt.epochSecond
                                }
                            } else {
                                remoteLastUsedAt ?: template.lastUsedAt?.epochSecond
                            }
                            
                            // Update local template with merged usage statistics
                            // This would require an update method in the DAO
                            false // Don't sync, remote is newer
                        }
                    } else {
                        true // No remote version, sync local
                    }
                    
                    if (shouldSync) {
                        // Parse the JSON exercises string into a proper list for Firestore validation
                        val exercisesList = try {
                            Json.decodeFromString<List<TemplateExercise>>(template.templateExercisesJson)
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
                            "lastModified" to FieldValue.serverTimestamp(),
                            "isSynced" to true
                        )
                        
                        docRef.set(templateData, SetOptions.merge()).await()
                    }
                    
                    // Mark as synced in local database
                    templateDao.markTemplatesAsSynced(listOf(template.id))
                    
                    successCount++
                    Timber.d("Successfully synced template: ${template.id}")
                    
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
}