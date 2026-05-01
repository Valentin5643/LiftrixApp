package com.example.liftrix.data.remote.legacy

import com.example.liftrix.domain.model.social.ReportReason
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyReportFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val REPORTS_COLLECTION = "user_reports"
    }

    suspend fun submitReport(
        reportId: String,
        reporterId: String,
        targetUserId: String,
        reason: ReportReason,
        description: String?,
        createdAt: Long
    ) {
        val reportData = mapOf(
            "id" to reportId,
            "reporterId" to reporterId,
            "targetUserId" to targetUserId,
            "reason" to reason.name,
            "description" to description,
            "createdAt" to createdAt,
            "status" to "PENDING",
            "reviewed" to false
        )

        firestore.collection(REPORTS_COLLECTION)
            .document(reportId)
            .set(reportData)
            .await()
    }

    suspend fun hasExistingReport(reporterId: String, targetUserId: String): Boolean {
        val query = firestore.collection(REPORTS_COLLECTION)
            .whereEqualTo("reporterId", reporterId)
            .whereEqualTo("targetUserId", targetUserId)
            .limit(1)
            .get()
            .await()
        return !query.isEmpty
    }

    suspend fun getReportCount(targetUserId: String): Int {
        val query = firestore.collection(REPORTS_COLLECTION)
            .whereEqualTo("targetUserId", targetUserId)
            .get()
            .await()
        return query.size()
    }
}
