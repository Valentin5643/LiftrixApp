package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.TemplateShareEventEntity
import com.example.liftrix.domain.model.sharing.TemplateShareDeliveryMode
import com.example.liftrix.domain.model.sharing.TemplateShareEvent
import com.example.liftrix.domain.model.sharing.TemplateShareStatus

fun TemplateShareEvent.toEntity(isSynced: Boolean = false, isDirty: Boolean = true): TemplateShareEventEntity {
    return TemplateShareEventEntity(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        templateId = templateId,
        deliveryMode = deliveryMode.name,
        status = status.name,
        createdAt = createdAt,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        isSynced = isSynced,
        isDirty = isDirty,
        lastModified = System.currentTimeMillis()
    )
}

fun TemplateShareEventEntity.toDomain(): TemplateShareEvent {
    return TemplateShareEvent(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        templateId = templateId,
        deliveryMode = TemplateShareDeliveryMode.valueOf(deliveryMode),
        status = TemplateShareStatus.valueOf(status),
        createdAt = createdAt,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt
    )
}

