package com.example.liftrix.domain.service

interface WorkoutRemoteNotificationSender {
    suspend fun sendDataMessage(
        token: String,
        data: Map<String, String>,
        messageId: String,
        ttlMs: Int
    )
}
