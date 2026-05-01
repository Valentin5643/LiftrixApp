package com.example.liftrix.domain.model.notifications

/**
 * Domain model representing a notification in the queue system
 */
data class NotificationQueue(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val data: String,
    val priority: String = "NORMAL",
    val channelId: String,
    val batchKey: String? = null,
    val canBatch: Boolean = false,
    val scheduledFor: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val status: String = "PENDING",
    val sentAt: Long? = null,
    val failureReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)