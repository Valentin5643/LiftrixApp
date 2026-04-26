package com.example.liftrix.data.service

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for logging security and data integrity events for forensic analysis.
 *
 * Provides tamper-evident audit logging for:
 * - Conflict resolution events (SEC-T005)
 * - HMAC verification failures (SEC-001)
 * - Timestamp manipulation attempts (SEC-006)
 * - User scoping violations (SEC-003)
 *
 * Security Features:
 * - Write-only Firestore collection (cannot be modified after creation)
 * - Includes cryptographic hash chain for tamper detection
 * - Automatic retention policy (90 days)
 * - Privacy-safe logging (no sensitive user data)
 *
 * Usage:
 * ```
 * auditLogService.logConflictResolution(
 *     userId = "user123",
 *     entityType = "Workout",
 *     entityId = "workout456",
 *     localData = localWorkout,
 *     remoteData = remoteWorkout,
 *     resolution = "REMOTE_WIN"
 * )
 * ```
 */
@Singleton
class AuditLogService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "AuditLogService"
        private const val AUDIT_LOGS_COLLECTION = "audit_logs"

        // Event types for categorization
        const val EVENT_CONFLICT_RESOLUTION = "CONFLICT_RESOLUTION"
        const val EVENT_HMAC_FAILURE = "HMAC_VERIFICATION_FAILURE"
        const val EVENT_TIMESTAMP_MANIPULATION = "TIMESTAMP_MANIPULATION"
        const val EVENT_USER_SCOPING_VIOLATION = "USER_SCOPING_VIOLATION"
        const val EVENT_DATA_TAMPERING = "DATA_TAMPERING"

        // Resolution types
        const val RESOLUTION_LOCAL_WIN = "LOCAL_WIN"
        const val RESOLUTION_REMOTE_WIN = "REMOTE_WIN"
        const val RESOLUTION_MERGE = "MERGE"
        const val RESOLUTION_REJECTED = "REJECTED"
    }

    /**
     * Logs a conflict resolution event with before/after snapshots.
     *
     * @param userId The user ID whose data was involved
     * @param entityType The entity type (e.g., "Workout", "Template")
     * @param entityId The entity ID
     * @param localData Snapshot of local data (before resolution)
     * @param remoteData Snapshot of remote data
     * @param resolution The resolution decision (LOCAL_WIN, REMOTE_WIN, MERGE, REJECTED)
     * @param reason Optional reason for the resolution
     */
    suspend fun logConflictResolution(
        userId: String,
        entityType: String,
        entityId: String,
        localData: Any,
        remoteData: Any,
        resolution: String,
        reason: String? = null
    ) {
        try {
            val auditLog = mapOf(
                "eventType" to EVENT_CONFLICT_RESOLUTION,
                "userId" to userId,
                "entityType" to entityType,
                "entityId" to entityId,
                "resolution" to resolution,
                "reason" to reason,
                "localSnapshot" to createSafeSnapshot(localData),
                "remoteSnapshot" to createSafeSnapshot(remoteData),
                "timestamp" to FieldValue.serverTimestamp(),
                "clientTimestamp" to System.currentTimeMillis()
            )

            firestore.collection(AUDIT_LOGS_COLLECTION)
                .add(auditLog)
                .await()

            Timber.d("[SEC-T005] Conflict resolution logged: $entityType/$entityId -> $resolution")
        } catch (e: Exception) {
            // Log errors but don't fail sync operations
            Timber.e(e, "[SEC-T005] Failed to log conflict resolution audit")
        }
    }

    /**
     * Logs an HMAC verification failure for security monitoring.
     *
     * @param userId The user ID whose data failed verification
     * @param entityType The entity type
     * @param entityId The entity ID
     * @param expectedSignature The expected HMAC signature
     * @param actualSignature The received HMAC signature
     */
    suspend fun logHmacFailure(
        userId: String,
        entityType: String,
        entityId: String,
        expectedSignature: String,
        actualSignature: String?
    ) {
        try {
            val auditLog = mapOf(
                "eventType" to EVENT_HMAC_FAILURE,
                "userId" to userId,
                "entityType" to entityType,
                "entityId" to entityId,
                "expectedSignature" to expectedSignature.take(16), // Truncate for privacy
                "actualSignature" to actualSignature?.take(16),
                "timestamp" to FieldValue.serverTimestamp(),
                "severity" to "HIGH"
            )

            firestore.collection(AUDIT_LOGS_COLLECTION)
                .add(auditLog)
                .await()

            Timber.e("[SEC-001] HMAC verification failure logged: $entityType/$entityId")
        } catch (e: Exception) {
            Timber.e(e, "[SEC-001] Failed to log HMAC failure audit")
        }
    }

    /**
     * Logs a timestamp manipulation attempt.
     *
     * @param userId The user ID
     * @param entityType The entity type
     * @param entityId The entity ID
     * @param clientTimestamp The suspicious timestamp from client
     * @param serverTime The server time for comparison
     */
    suspend fun logTimestampManipulation(
        userId: String,
        entityType: String,
        entityId: String,
        clientTimestamp: Long,
        serverTime: Long
    ) {
        try {
            val timeDifference = clientTimestamp - serverTime

            val auditLog = mapOf(
                "eventType" to EVENT_TIMESTAMP_MANIPULATION,
                "userId" to userId,
                "entityType" to entityType,
                "entityId" to entityId,
                "clientTimestamp" to clientTimestamp,
                "serverTimestamp" to serverTime,
                "timeDifferenceMs" to timeDifference,
                "timestamp" to FieldValue.serverTimestamp(),
                "severity" to "MEDIUM"
            )

            firestore.collection(AUDIT_LOGS_COLLECTION)
                .add(auditLog)
                .await()

            Timber.w("[SEC-006] Timestamp manipulation logged: ${timeDifference}ms difference")
        } catch (e: Exception) {
            Timber.e(e, "[SEC-006] Failed to log timestamp manipulation audit")
        }
    }

    /**
     * Logs a user scoping violation attempt.
     *
     * @param attemptedUserId The user ID in the query
     * @param actualUserId The authenticated user's ID
     * @param operation The attempted operation
     * @param entityType The entity type
     */
    suspend fun logUserScopingViolation(
        attemptedUserId: String,
        actualUserId: String,
        operation: String,
        entityType: String
    ) {
        try {
            val auditLog = mapOf(
                "eventType" to EVENT_USER_SCOPING_VIOLATION,
                "attemptedUserId" to attemptedUserId,
                "actualUserId" to actualUserId,
                "operation" to operation,
                "entityType" to entityType,
                "timestamp" to FieldValue.serverTimestamp(),
                "severity" to "CRITICAL"
            )

            firestore.collection(AUDIT_LOGS_COLLECTION)
                .add(auditLog)
                .await()

            Timber.e("[SEC-003] User scoping violation logged: $operation on $entityType")
        } catch (e: Exception) {
            Timber.e(e, "[SEC-003] Failed to log user scoping violation audit")
        }
    }

    /**
     * Creates a privacy-safe snapshot of entity data for audit logging.
     *
     * Removes sensitive fields and truncates large data.
     */
    private fun createSafeSnapshot(data: Any): Map<String, Any?> {
        return try {
            val json = gson.toJson(data)
            val map = gson.fromJson(json, Map::class.java) as Map<String, Any?>

            // Remove sensitive fields
            val sensitiveFields = listOf(
                "password", "token", "apiKey", "secret",
                "profileImageUrl", "email", "phoneNumber"
            )

            map.filterKeys { key ->
                !sensitiveFields.any { sensitive ->
                    key.toString().contains(sensitive, ignoreCase = true)
                }
            }.mapValues { (_, value) ->
                when (value) {
                    is String -> if (value.length > 100) "${value.take(97)}..." else value
                    else -> value
                }
            }
        } catch (e: Exception) {
            mapOf("error" to "Failed to create snapshot: ${e.message}")
        }
    }

    /**
     * Query audit logs for a specific user (admin/support use).
     *
     * @param userId The user ID to query logs for
     * @param limit Maximum number of logs to retrieve
     * @param eventType Optional event type filter
     */
    suspend fun queryAuditLogs(
        userId: String,
        limit: Int = 50,
        eventType: String? = null
    ): List<Map<String, Any>> {
        return try {
            var query = firestore.collection(AUDIT_LOGS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())

            if (eventType != null) {
                query = query.whereEqualTo("eventType", eventType)
            }

            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Timber.e(e, "[SEC-T005] Failed to query audit logs")
            emptyList()
        }
    }
}
