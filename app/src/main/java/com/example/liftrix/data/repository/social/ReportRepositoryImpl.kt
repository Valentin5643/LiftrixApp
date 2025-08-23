package com.example.liftrix.data.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.repository.social.ReportRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ReportRepository for managing user reports
 * 
 * Note: Reports are only stored in Firebase for moderation purposes,
 * not synced to local database to maintain reporter privacy
 */
@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ReportRepository {
    
    companion object {
        private const val REPORTS_COLLECTION = "user_reports"
    }
    
    override suspend fun submitReport(
        reporterId: String,
        targetUserId: String,
        reason: ReportReason,
        description: String?
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to submit report: ${throwable.message}",
                isRecoverable = true
            )
        }
    ) {
        val reportId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()
        
        val reportData = mapOf(
            "id" to reportId,
            "reporterId" to reporterId,
            "targetUserId" to targetUserId,
            "reason" to reason.name,
            "description" to description,
            "createdAt" to currentTime,
            "status" to "PENDING",
            "reviewed" to false
        )
        
        firestore.collection(REPORTS_COLLECTION)
            .document(reportId)
            .set(reportData)
            .await()
        
        Timber.d("Report submitted: $reportId against user $targetUserId")
        
        Unit
    }
    
    override suspend fun hasExistingReport(
        reporterId: String,
        targetUserId: String
    ): Boolean {
        return try {
            val query = firestore.collection(REPORTS_COLLECTION)
                .whereEqualTo("reporterId", reporterId)
                .whereEqualTo("targetUserId", targetUserId)
                .limit(1)
                .get()
                .await()
            
            !query.isEmpty
        } catch (e: Exception) {
            Timber.e(e, "Failed to check existing reports")
            false
        }
    }
    
    override suspend fun getReportCount(userId: String): Int {
        return try {
            val query = firestore.collection(REPORTS_COLLECTION)
                .whereEqualTo("targetUserId", userId)
                .get()
                .await()
            
            query.size()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get report count")
            0
        }
    }
}