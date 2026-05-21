package com.example.liftrix.domain.interactor.support

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.support.AddReplyToSupportTicketUseCase
import javax.inject.Inject

class SupportInteractor @Inject constructor(
    private val addReplyToSupportTicketUseCase: AddReplyToSupportTicketUseCase
) {
    suspend fun addReply(
        ticketId: String,
        content: String,
        attachments: List<String> = emptyList()
    ): LiftrixResult<Unit> = addReplyToSupportTicketUseCase(
        ticketId = ticketId,
        content = content,
        attachments = attachments
    )
}
