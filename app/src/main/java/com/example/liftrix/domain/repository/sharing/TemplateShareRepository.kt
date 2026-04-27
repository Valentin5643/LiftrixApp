package com.example.liftrix.domain.repository.sharing

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.sharing.TemplateShareEvent

interface TemplateShareRepository {
    suspend fun createShare(event: TemplateShareEvent): LiftrixResult<TemplateShareEvent>

    suspend fun getPendingSharesFromBuddy(
        senderId: String,
        receiverId: String
    ): LiftrixResult<List<TemplateShareEvent>>

    suspend fun getPendingShareForReceiver(
        shareId: String,
        receiverId: String
    ): LiftrixResult<TemplateShareEvent?>

    suspend fun markAccepted(
        shareId: String,
        receiverId: String,
        acceptedAt: Long = System.currentTimeMillis()
    ): LiftrixResult<Unit>
}

