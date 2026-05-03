package com.example.liftrix.domain.service

interface AIMessageReportService {
    suspend fun reportMessage(
        userId: String,
        messageId: String,
        messageContent: String,
        reason: String,
        reasonDescription: String,
        notes: String?
    ): Result<Unit>
}
