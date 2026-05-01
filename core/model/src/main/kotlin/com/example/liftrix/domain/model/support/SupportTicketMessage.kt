package com.example.liftrix.domain.model.support

import java.time.Instant

/**
 * Domain model representing a message/reply in a support ticket conversation
 * Contains the content and metadata for each message in the ticket thread
 */
data class SupportTicketMessage(
    val id: String,
    val ticketId: String,
    val userId: String,
    val content: String,
    val isFromSupport: Boolean = false,
    val authorName: String? = null,
    val attachments: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val isSynced: Boolean = false
) {
    /**
     * Checks if the message has been modified since creation
     */
    fun isEdited(): Boolean = updatedAt != null && updatedAt != createdAt
    
    /**
     * Gets the display name for the message author
     */
    fun getAuthorDisplayName(): String = when {
        isFromSupport -> authorName ?: "Support Team"
        else -> "You"
    }
    
    /**
     * Checks if the message has attachments
     */
    fun hasAttachments(): Boolean = attachments.isNotEmpty()
    
    /**
     * Gets the age of the message in hours
     */
    fun getAgeInHours(): Long {
        val now = Instant.now()
        return java.time.Duration.between(createdAt, now).toHours()
    }
    
    /**
     * Marks the message as updated
     */
    fun markUpdated(newContent: String? = null): SupportTicketMessage = copy(
        content = newContent ?: content,
        updatedAt = Instant.now(),
        isSynced = false
    )
    
    /**
     * Marks the message as synced
     */
    fun markSynced(): SupportTicketMessage = copy(isSynced = true)
}

/**
 * Request model for adding a reply to a support ticket
 */
data class AddSupportTicketReplyRequest(
    val ticketId: String,
    val userId: String,
    val content: String,
    val attachments: List<String> = emptyList()
) {
    /**
     * Validates the reply request
     */
    fun validate(): List<String> = buildList {
        if (ticketId.isBlank()) add("Ticket ID is required")
        if (userId.isBlank()) add("User ID is required")
        if (content.isBlank()) add("Reply content is required")
        if (content.length < 5) add("Reply must be at least 5 characters")
        if (content.length > 5000) add("Reply must be less than 5000 characters")
        if (attachments.size > 5) add("Maximum 5 attachments allowed per reply")
    }
    
    /**
     * Checks if the request is valid
     */
    fun isValid(): Boolean = validate().isEmpty()
}