package com.example.liftrix.domain.service

import android.net.Uri
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.support.AddSupportTicketReplyRequest
import com.example.liftrix.domain.model.support.CreateSupportTicketRequest
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.domain.model.support.SupportStatus
import com.example.liftrix.domain.model.support.SupportTicket
import com.example.liftrix.domain.model.support.SupportTicketMessage

/**
 * Service interface for support ticket functionality
 * Provides operations for creating and managing support requests
 */
interface SupportService {
    
    /**
     * Creates a new support ticket
     * @param userId User identifier for the ticket owner
     * @param category Support category for routing
     * @param subject Brief description of the issue
     * @param description Detailed description of the issue
     * @param attachments Optional list of file URIs to attach
     * @return LiftrixResult containing the created ticket ID
     */
    suspend fun createTicket(
        userId: String,
        category: SupportCategory,
        subject: String,
        description: String,
        attachments: List<Uri> = emptyList()
    ): LiftrixResult<String>
    
    /**
     * Creates a support ticket from a request object
     * @param request Complete support ticket creation request
     * @return LiftrixResult containing the created ticket ID
     */
    suspend fun createTicket(request: CreateSupportTicketRequest): LiftrixResult<String>
    
    /**
     * Retrieves a specific support ticket by ID
     * @param ticketId Unique identifier for the ticket
     * @param userId User identifier for security validation
     * @return LiftrixResult containing the support ticket or null if not found
     */
    suspend fun getTicket(ticketId: String, userId: String): LiftrixResult<SupportTicket?>
    
    /**
     * Retrieves all support tickets for a user
     * @param userId User identifier
     * @return LiftrixResult containing list of user's support tickets
     */
    suspend fun getUserTickets(userId: String): LiftrixResult<List<SupportTicket>>
    
    /**
     * Retrieves active support tickets for a user
     * @param userId User identifier
     * @return LiftrixResult containing list of active tickets
     */
    suspend fun getActiveTickets(userId: String): LiftrixResult<List<SupportTicket>>
    
    /**
     * Retrieves support tickets by status for a user
     * @param userId User identifier
     * @param status Ticket status to filter by
     * @return LiftrixResult containing list of tickets with the specified status
     */
    suspend fun getTicketsByStatus(userId: String, status: SupportStatus): LiftrixResult<List<SupportTicket>>
    
    /**
     * Retrieves support tickets by category for a user
     * @param userId User identifier
     * @param category Support category to filter by
     * @return LiftrixResult containing list of tickets in the specified category
     */
    suspend fun getTicketsByCategory(userId: String, category: SupportCategory): LiftrixResult<List<SupportTicket>>
    
    /**
     * Gets the current status of a support ticket
     * @param ticketId Unique identifier for the ticket
     * @param userId User identifier for security validation
     * @return LiftrixResult containing the current ticket status
     */
    suspend fun getTicketStatus(ticketId: String, userId: String): LiftrixResult<SupportStatus>
    
    /**
     * Updates the status of a support ticket (admin function)
     * @param ticketId Unique identifier for the ticket
     * @param userId User identifier for security validation
     * @param newStatus New status to set
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateTicketStatus(
        ticketId: String, 
        userId: String, 
        newStatus: SupportStatus
    ): LiftrixResult<Unit>
    
    /**
     * Adds a comment or update to an existing ticket
     * @param ticketId Unique identifier for the ticket
     * @param userId User identifier for security validation
     * @param comment Additional information or update from user
     * @return LiftrixResult indicating success or failure
     */
    suspend fun addTicketComment(
        ticketId: String,
        userId: String,
        comment: String
    ): LiftrixResult<Unit>
    
    /**
     * Adds a reply to an existing support ticket
     * @param request Complete reply request with validation
     * @return LiftrixResult indicating success or failure
     */
    suspend fun addTicketReply(request: AddSupportTicketReplyRequest): LiftrixResult<Unit>
    
    /**
     * Gets all messages for a specific support ticket
     * @param ticketId Unique identifier for the ticket
     * @param userId User identifier for security validation
     * @return LiftrixResult containing list of ticket messages
     */
    suspend fun getTicketMessages(
        ticketId: String,
        userId: String
    ): LiftrixResult<List<SupportTicketMessage>>
    
    /**
     * Uploads attachments for a support ticket
     * @param ticketId Unique identifier for the ticket
     * @param userId User identifier for security validation
     * @param attachments List of file URIs to upload
     * @return LiftrixResult containing list of uploaded file URLs
     */
    suspend fun uploadAttachments(
        ticketId: String,
        userId: String,
        attachments: List<Uri>
    ): LiftrixResult<List<String>>
    
    /**
     * Syncs unsynced tickets to remote storage
     * @param userId User identifier
     * @return LiftrixResult indicating success or failure of sync operation
     */
    suspend fun syncTickets(userId: String): LiftrixResult<Unit>
    
    /**
     * Gets support ticket statistics for a user
     * @param userId User identifier
     * @return LiftrixResult containing ticket statistics
     */
    suspend fun getTicketStatistics(userId: String): LiftrixResult<SupportTicketStatistics>
    
    /**
     * Validates a support ticket request
     * @param request Support ticket creation request
     * @return LiftrixResult containing validation results
     */
    suspend fun validateTicketRequest(request: CreateSupportTicketRequest): LiftrixResult<List<String>>
}

/**
 * Statistics about user's support ticket history
 */
data class SupportTicketStatistics(
    val totalTickets: Int,
    val activeTickets: Int,
    val resolvedTickets: Int,
    val averageResolutionDays: Double,
    val ticketsByCategory: Map<SupportCategory, Int>,
    val ticketsByStatus: Map<SupportStatus, Int>,
    val mostRecentTicket: SupportTicket?,
    val oldestActiveTicket: SupportTicket?
) {
    /**
     * Calculates resolution rate as percentage
     */
    fun getResolutionRate(): Double {
        return if (totalTickets > 0) {
            (resolvedTickets.toDouble() / totalTickets.toDouble()) * 100
        } else {
            0.0
        }
    }
    
    /**
     * Checks if user has any pending tickets requiring attention
     */
    fun hasPendingTickets(): Boolean = activeTickets > 0
    
    /**
     * Gets the most common category for user's tickets
     */
    fun getMostCommonCategory(): SupportCategory? {
        return ticketsByCategory.maxByOrNull { it.value }?.key
    }
}