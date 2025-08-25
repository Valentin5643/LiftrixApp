package com.example.liftrix.domain.usecase.support

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.support.AddSupportTicketReplyRequest
import com.example.liftrix.domain.service.SupportService
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for adding a reply to an existing support ticket
 * 
 * Handles validation, user authentication, and business logic for ticket replies
 * Follows the LiftrixResult error handling pattern and ensures proper user scoping
 */
class AddReplyToSupportTicketUseCase @Inject constructor(
    private val supportService: SupportService,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    /**
     * Adds a reply to a support ticket
     * 
     * @param ticketId The ID of the ticket to reply to
     * @param content The reply content from the user
     * @param attachments Optional list of attachment URLs
     * @return LiftrixResult with success or error details
     */
    suspend operator fun invoke(
        ticketId: String,
        content: String,
        attachments: List<String> = emptyList()
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "REPLY_SUBMISSION_FAILED",
                    errorMessage = "Failed to add reply to support ticket",
                    analyticsContext = mapOf(
                        "operation" to "ADD_SUPPORT_TICKET_REPLY",
                        "ticket_id" to ticketId,
                        "content_length" to content.length.toString(),
                        "attachment_count" to attachments.size.toString()
                    )
                )
            }
        }
    ) {
        // Get current user ID for authentication and scoping
        val userId = getCurrentUserIdUseCase() ?: throw LiftrixError.AuthenticationError(
            "User not authenticated"
        )
        
        // Create and validate the reply request
        val request = AddSupportTicketReplyRequest(
            ticketId = ticketId,
            userId = userId,
            content = content.trim(),
            attachments = attachments
        )
        
        // Validate the request
        val validationErrors = request.validate()
        if (validationErrors.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "reply",
                violations = validationErrors,
                analyticsContext = mapOf(
                    "operation" to "VALIDATE_SUPPORT_TICKET_REPLY",
                    "ticket_id" to ticketId
                )
            )
        }
        
        // Check if ticket exists and user has permission to reply
        val existingTicket = supportService.getTicket(ticketId, userId).fold(
            onSuccess = { ticket ->
                ticket ?: throw LiftrixError.BusinessLogicError(
                    code = "TICKET_NOT_FOUND",
                    errorMessage = "Support ticket not found",
                    analyticsContext = mapOf(
                        "operation" to "GET_SUPPORT_TICKET",
                        "ticket_id" to ticketId
                    )
                )
            },
            onFailure = { error -> throw error }
        )
        
        // Check if ticket is in a state that allows replies
        if (!existingTicket.isActive()) {
            throw LiftrixError.BusinessLogicError(
                code = "TICKET_NOT_ACTIVE",
                errorMessage = "Cannot reply to a closed or resolved ticket",
                analyticsContext = mapOf(
                    "operation" to "CHECK_TICKET_STATUS",
                    "ticket_id" to ticketId,
                    "ticket_status" to existingTicket.status.name
                )
            )
        }
        
        // Add the reply through the support service
        supportService.addTicketReply(request).fold(
            onSuccess = { /* Success */ },
            onFailure = { error -> throw error }
        )
    }
}