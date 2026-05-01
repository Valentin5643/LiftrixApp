package com.example.liftrix.domain.model.sharing

import java.util.UUID

data class TemplateShareEvent(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val receiverId: String?,
    val templateId: String,
    val deliveryMode: TemplateShareDeliveryMode,
    val status: TemplateShareStatus = TemplateShareStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + DEFAULT_EXPIRATION_MS,
    val acceptedAt: Long? = null
) {
    fun isPendingAt(now: Long = System.currentTimeMillis()): Boolean {
        return status == TemplateShareStatus.PENDING && expiresAt > now
    }

    companion object {
        const val DEFAULT_EXPIRATION_MS: Long = 30L * 60L * 1000L
    }
}

enum class TemplateShareDeliveryMode {
    DIRECT,
    QR
}

enum class TemplateShareStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    CANCELLED
}
