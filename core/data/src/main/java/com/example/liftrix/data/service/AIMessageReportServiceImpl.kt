package com.example.liftrix.data.service

import com.example.liftrix.domain.service.AIMessageReportService
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIMessageReportServiceImpl @Inject constructor(
    private val firebaseFunctions: FirebaseFunctions
) : AIMessageReportService {

    override suspend fun reportMessage(
        userId: String,
        messageId: String,
        messageContent: String,
        reason: String,
        reasonDescription: String,
        notes: String?
    ): Result<Unit> = runCatching {
        val reportData = hashMapOf(
            "messageId" to messageId,
            "messageContent" to messageContent,
            "reason" to reason,
            "reasonDescription" to reasonDescription,
            "notes" to (notes ?: ""),
            "userId" to userId,
            "timestamp" to System.currentTimeMillis()
        )

        firebaseFunctions
            .getHttpsCallable("aiReport")
            .call(reportData)
            .await()
    }.map { Unit }
}
