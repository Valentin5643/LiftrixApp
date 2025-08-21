package com.example.liftrix.data.service

import android.net.Uri
import com.example.liftrix.data.local.dao.SupportTicketDao
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.liftrix.data.mapper.SupportTicketMapper.toDomainModel
import com.example.liftrix.data.mapper.SupportTicketMapper.toDomainModels
import com.example.liftrix.data.mapper.SupportTicketMapper.toEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.support.CreateSupportTicketRequest
import com.example.liftrix.domain.model.support.DeviceInfo
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.domain.model.support.SupportStatus
import com.example.liftrix.domain.model.support.SupportTicket
import com.example.liftrix.domain.service.AppInfoService
import com.example.liftrix.domain.service.SupportService
import com.example.liftrix.domain.service.SupportTicketStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SupportService providing support ticket management
 * 
 * Features:
 * - Local ticket storage with user scoping
 * - Device information collection for support context
 * - Ticket validation and status management
 * - File attachment support
 * - Sync capabilities for offline-first operation
 */
@Singleton
class SupportServiceImpl @Inject constructor(
    private val supportTicketDao: SupportTicketDao,
    private val appInfoService: AppInfoService
) : SupportService {
    
    override suspend fun createTicket(
        userId: String,
        category: SupportCategory,
        subject: String,
        description: String,
        attachments: List<Uri>
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SUPPORT_TICKET_CREATION_FAILED",
                errorMessage = "Failed to create support ticket",
                analyticsContext = mapOf(
                    "operation" to "CREATE_TICKET",
                    "user_id" to userId,
                    "category" to category.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Collect device information for support context
            val deviceInfo = appInfoService.getDeviceInfo()
            val appVersion = "${appInfoService.getAppVersion()} (${appInfoService.getBuildNumber()})"
            
            // Create ticket request for validation
            val request = CreateSupportTicketRequest(
                userId = userId,
                category = category,
                subject = subject,
                description = description,
                deviceInfo = deviceInfo,
                attachments = attachments.map { it.toString() }
            )
            
            // Validate the request
            val validationErrors = request.validate()
            if (validationErrors.isNotEmpty()) {
                throw LiftrixError.ValidationError(
                    field = "ticket_request",
                    violations = validationErrors,
                    analyticsContext = mapOf(
                        "operation" to "CREATE_TICKET_VALIDATION",
                        "user_id" to userId,
                        "category" to category.name
                    )
                )
            }
            
            // Create the ticket
            val ticket = SupportTicket(
                id = UUID.randomUUID().toString(),
                userId = userId,
                category = category,
                subject = subject,
                description = description,
                deviceInfo = deviceInfo,
                appVersion = appVersion,
                status = SupportStatus.OPEN,
                createdAt = Instant.now(),
                updatedAt = null,
                isSynced = false
            )
            
            // Save to database
            supportTicketDao.insertTicket(ticket.toEntity())
            
            Timber.d("Created support ticket ${ticket.id} for user $userId in category ${category.name}")
            
            ticket.id
        }
    }
    
    override suspend fun createTicket(request: CreateSupportTicketRequest): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SUPPORT_TICKET_CREATION_FAILED",
                errorMessage = "Failed to create support ticket from request",
                analyticsContext = mapOf(
                    "operation" to "CREATE_TICKET_FROM_REQUEST",
                    "user_id" to request.userId,
                    "category" to request.category.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        createTicket(
            userId = request.userId,
            category = request.category,
            subject = request.subject,
            description = request.description,
            attachments = request.attachments.map { Uri.parse(it) }
        ).getOrThrow()
    }
    
    override suspend fun getTicket(ticketId: String, userId: String): LiftrixResult<SupportTicket?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SUPPORT_TICKET_FETCH_FAILED",
                errorMessage = "Failed to fetch support ticket",
                analyticsContext = mapOf(
                    "operation" to "GET_TICKET",
                    "ticket_id" to ticketId,
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val ticketEntity = supportTicketDao.getTicketSync(ticketId, userId)
            ticketEntity?.toDomainModel()
        }
    }
    
    override suspend fun getUserTickets(userId: String): LiftrixResult<List<SupportTicket>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "USER_TICKETS_FETCH_FAILED",
                errorMessage = "Failed to fetch user tickets",
                analyticsContext = mapOf(
                    "operation" to "GET_USER_TICKETS",
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val tickets = supportTicketDao.getUserTicketsSync(userId)
            tickets.toDomainModels()
        }
    }
    
    override suspend fun getActiveTickets(userId: String): LiftrixResult<List<SupportTicket>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ACTIVE_TICKETS_FETCH_FAILED",
                errorMessage = "Failed to fetch active tickets",
                analyticsContext = mapOf(
                    "operation" to "GET_ACTIVE_TICKETS",
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val tickets = supportTicketDao.getUserTicketsSync(userId)
                .filter { it.status in listOf("OPEN", "IN_PROGRESS", "WAITING_FOR_USER") }
            tickets.toDomainModels()
        }
    }
    
    override suspend fun getTicketsByStatus(userId: String, status: SupportStatus): LiftrixResult<List<SupportTicket>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKETS_BY_STATUS_FETCH_FAILED",
                errorMessage = "Failed to fetch tickets by status",
                analyticsContext = mapOf(
                    "operation" to "GET_TICKETS_BY_STATUS",
                    "user_id" to userId,
                    "status" to status.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val tickets = supportTicketDao.getUserTicketsSync(userId)
                .filter { it.status == status.name }
            tickets.toDomainModels()
        }
    }
    
    override suspend fun getTicketsByCategory(userId: String, category: SupportCategory): LiftrixResult<List<SupportTicket>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKETS_BY_CATEGORY_FETCH_FAILED",
                errorMessage = "Failed to fetch tickets by category",
                analyticsContext = mapOf(
                    "operation" to "GET_TICKETS_BY_CATEGORY",
                    "user_id" to userId,
                    "category" to category.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val tickets = supportTicketDao.getUserTicketsSync(userId)
                .filter { it.category == category.name }
            tickets.toDomainModels()
        }
    }
    
    override suspend fun getTicketStatus(ticketId: String, userId: String): LiftrixResult<SupportStatus> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKET_STATUS_FETCH_FAILED",
                errorMessage = "Failed to fetch ticket status",
                analyticsContext = mapOf(
                    "operation" to "GET_TICKET_STATUS",
                    "ticket_id" to ticketId,
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val ticket = supportTicketDao.getTicketSync(ticketId, userId)
                ?: throw LiftrixError.BusinessLogicError(
                    code = "TICKET_NOT_FOUND",
                    errorMessage = "Support ticket not found",
                    analyticsContext = mapOf(
                        "ticket_id" to ticketId,
                        "user_id" to userId
                    )
                )
            
            SupportStatus.valueOf(ticket.status)
        }
    }
    
    override suspend fun updateTicketStatus(
        ticketId: String,
        userId: String,
        newStatus: SupportStatus
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKET_STATUS_UPDATE_FAILED",
                errorMessage = "Failed to update ticket status",
                analyticsContext = mapOf(
                    "operation" to "UPDATE_TICKET_STATUS",
                    "ticket_id" to ticketId,
                    "user_id" to userId,
                    "new_status" to newStatus.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            supportTicketDao.updateTicketStatus(ticketId, userId, newStatus.name, Instant.now())
            Timber.d("Updated ticket $ticketId status to ${newStatus.name}")
        }
    }
    
    override suspend fun addTicketComment(
        ticketId: String,
        userId: String,
        comment: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKET_COMMENT_ADD_FAILED",
                errorMessage = "Failed to add ticket comment",
                analyticsContext = mapOf(
                    "operation" to "ADD_TICKET_COMMENT",
                    "ticket_id" to ticketId,
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            if (comment.isBlank()) {
                throw LiftrixError.ValidationError(
                    field = "comment",
                    violations = listOf("Comment cannot be empty"),
                    analyticsContext = mapOf(
                        "ticket_id" to ticketId,
                        "user_id" to userId
                    )
                )
            }
            
            // Get existing ticket
            val existingTicket = supportTicketDao.getTicketSync(ticketId, userId)
                ?: throw LiftrixError.BusinessLogicError(
                    code = "TICKET_NOT_FOUND",
                    errorMessage = "Support ticket not found",
                    analyticsContext = mapOf(
                        "ticket_id" to ticketId,
                        "user_id" to userId
                    )
                )
            
            // Update description with comment (in a real implementation, this might be a separate comments table)
            val updatedDescription = "${existingTicket.description}\n\n[${Instant.now()}] User update: $comment"
            val updatedTicket = existingTicket.copy(
                description = updatedDescription,
                updatedAt = Instant.now(),
                isSynced = false,
                syncVersion = existingTicket.syncVersion + 1
            )
            
            supportTicketDao.updateTicket(updatedTicket)
            Timber.d("Added comment to ticket $ticketId")
        }
    }
    
    override suspend fun uploadAttachments(
        ticketId: String,
        userId: String,
        attachments: List<Uri>
    ): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ATTACHMENT_UPLOAD_FAILED",
                errorMessage = "Failed to upload ticket attachments",
                analyticsContext = mapOf(
                    "operation" to "UPLOAD_ATTACHMENTS",
                    "ticket_id" to ticketId,
                    "user_id" to userId,
                    "attachment_count" to attachments.size.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            if (attachments.size > 5) {
                throw LiftrixError.ValidationError(
                    field = "attachments",
                    violations = listOf("Maximum 5 attachments allowed"),
                    analyticsContext = mapOf(
                        "ticket_id" to ticketId,
                        "user_id" to userId,
                        "attachment_count" to attachments.size.toString()
                    )
                )
            }
            
            // Verify ticket exists and belongs to user
            supportTicketDao.getTicketSync(ticketId, userId)
                ?: throw LiftrixError.BusinessLogicError(
                    code = "TICKET_NOT_FOUND",
                    errorMessage = "Support ticket not found",
                    analyticsContext = mapOf(
                        "ticket_id" to ticketId,
                        "user_id" to userId
                    )
                )
            
            // In a real implementation, this would upload files to cloud storage
            // For now, we'll just return the URI strings as placeholders
            val uploadedUrls = attachments.map { uri ->
                "https://storage.example.com/support-attachments/$ticketId/${System.currentTimeMillis()}_${uri.lastPathSegment}"
            }
            
            Timber.d("Uploaded ${attachments.size} attachments for ticket $ticketId")
            uploadedUrls
        }
    }
    
    override suspend fun syncTickets(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to sync support tickets",
                analyticsContext = mapOf(
                    "operation" to "SYNC_TICKETS",
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val unsyncedTickets = supportTicketDao.getUnsyncedTicketsSync(userId)
            
            if (unsyncedTickets.isEmpty()) {
                Timber.d("No unsynced tickets for user $userId")
                return@withContext
            }
            
            // Sync tickets to Firestore which will trigger email sending
            val firestore = FirebaseFirestore.getInstance()
            val batch = firestore.batch()
            
            for (ticket in unsyncedTickets) {
                val firestoreTicket = mapOf(
                    "user_id" to ticket.userId,
                    "ticket_id" to ticket.ticketId,
                    "category" to ticket.category,
                    "subject" to ticket.subject,
                    "description" to ticket.description,
                    "device_info" to ticket.deviceInfo,
                    "app_version" to ticket.appVersion,
                    "status" to ticket.status,
                    "created_at" to Timestamp(ticket.createdAt),
                    "updated_at" to ticket.updatedAt?.let { Timestamp(it) },
                    "email_sent" to false,
                    "sync_version" to ticket.syncVersion
                )
                
                val docRef = firestore.collection("support_tickets").document(ticket.ticketId)
                batch.set(docRef, firestoreTicket)
            }
            
            // Commit batch to Firestore
            batch.commit().await()
            
            // Mark tickets as synced in local database
            val ticketIds = unsyncedTickets.map { it.ticketId }
            supportTicketDao.markTicketsSynced(ticketIds, userId)
            
            Timber.d("Synced ${unsyncedTickets.size} tickets to Firestore for user $userId")
        }
    }
    
    override suspend fun getTicketStatistics(userId: String): LiftrixResult<SupportTicketStatistics> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKET_STATS_FETCH_FAILED",
                errorMessage = "Failed to fetch ticket statistics",
                analyticsContext = mapOf(
                    "operation" to "GET_TICKET_STATISTICS",
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val tickets = supportTicketDao.getUserTicketsSync(userId).toDomainModels()
            
            val activeTickets = tickets.filter { it.isActive() }
            val resolvedTickets = tickets.filter { it.isResolved() }
            
            // Calculate average resolution time
            val avgResolutionDays = if (resolvedTickets.isNotEmpty()) {
                resolvedTickets.mapNotNull { ticket ->
                    ticket.updatedAt?.let { updated ->
                        ChronoUnit.DAYS.between(ticket.createdAt, updated).toDouble()
                    }
                }.average()
            } else {
                0.0
            }
            
            val ticketsByCategory = tickets.groupBy { it.category }
                .mapValues { it.value.size }
            
            val ticketsByStatus = tickets.groupBy { it.status }
                .mapValues { it.value.size }
            
            val mostRecentTicket = tickets.maxByOrNull { it.createdAt }
            val oldestActiveTicket = activeTickets.minByOrNull { it.createdAt }
            
            SupportTicketStatistics(
                totalTickets = tickets.size,
                activeTickets = activeTickets.size,
                resolvedTickets = resolvedTickets.size,
                averageResolutionDays = avgResolutionDays,
                ticketsByCategory = ticketsByCategory,
                ticketsByStatus = ticketsByStatus,
                mostRecentTicket = mostRecentTicket,
                oldestActiveTicket = oldestActiveTicket
            )
        }
    }
    
    override suspend fun validateTicketRequest(request: CreateSupportTicketRequest): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TICKET_VALIDATION_FAILED",
                errorMessage = "Failed to validate ticket request",
                analyticsContext = mapOf(
                    "operation" to "VALIDATE_TICKET_REQUEST",
                    "user_id" to request.userId,
                    "category" to request.category.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            request.validate()
        }
    }
}