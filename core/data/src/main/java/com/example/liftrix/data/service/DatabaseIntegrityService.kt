package com.example.liftrix.data.service

import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.domain.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseIntegrityService @Inject constructor(
    private val database: LiftrixDatabase,
    private val analyticsService: AnalyticsService
) {

    data class IntegrityCheckResult(
        val status: String,
        val durationMs: Long,
        val isOk: Boolean
    )

    suspend fun runIntegrityCheck(userId: String?): IntegrityCheckResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val status = try {
            database.openHelper.readableDatabase.query("PRAGMA integrity_check;").use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
            }
        } catch (e: Exception) {
            Timber.e(e, "Database integrity check failed to execute")
            "error:${e.message ?: "unknown"}"
        }
        val durationMs = System.currentTimeMillis() - startTime
        val isOk = status == "ok"

        analyticsService.logEvent(
            "database_integrity_check",
            mapOf(
                "status" to status,
                "duration_ms" to durationMs,
                "user_id" to (userId ?: "unknown")
            )
        )

        IntegrityCheckResult(status = status, durationMs = durationMs, isOk = isOk)
    }
}
