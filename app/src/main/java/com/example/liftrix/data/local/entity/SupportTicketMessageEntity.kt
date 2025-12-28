package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing support ticket messages in local database
 * Stores individual messages/replies in support ticket conversations
 * 
 * Uses snake_case column names following existing database conventions
 * and includes mandatory user scoping and sync fields
 */
@Entity(
    tableName = "support_ticket_messages",
    foreignKeys = [
        ForeignKey(
            entity = SupportTicketEntity::class,
            parentColumns = ["ticket_id"],
            childColumns = ["ticket_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ticket_id"]),
        Index(value = ["user_id"]),
        Index(value = ["created_at"]),
        Index(value = ["is_synced"])
    ]
)
@TypeConverters(DateTimeConverters::class, WorkoutConverters::class)
data class SupportTicketMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,
    
    @ColumnInfo(name = "ticket_id")
    val ticketId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "is_from_support")
    val isFromSupport: Boolean = false,
    
    @ColumnInfo(name = "author_name")
    val authorName: String? = null,
    
    @ColumnInfo(name = "attachments")
    val attachments: List<String> = emptyList(),
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant? = null,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    val syncVersion: Int = 1,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
) {
    companion object {
        /**
         * Creates a new support ticket message entity from user reply
         * 
         * @param ticketId The ID of the support ticket this message belongs to
         * @param userId The user sending the message
         * @param content The message content
         * @param attachments Optional list of attachment URLs
         * @return SupportTicketMessageEntity with generated ID and timestamps
         */
        fun createUserReply(
            ticketId: String,
            userId: String,
            content: String,
            attachments: List<String> = emptyList()
        ): SupportTicketMessageEntity = SupportTicketMessageEntity(
            messageId = UUID.randomUUID().toString(),
            ticketId = ticketId,
            userId = userId,
            content = content,
            isFromSupport = false,
            authorName = null,
            attachments = attachments,
            createdAt = Instant.now(),
            updatedAt = null,
            isSynced = false,
            syncVersion = 1
        )
        
        /**
         * Creates a new support ticket message entity from support team
         * 
         * @param ticketId The ID of the support ticket this message belongs to
         * @param userId The user who owns the ticket (for scoping)
         * @param content The message content from support
         * @param authorName Optional name of the support agent
         * @param attachments Optional list of attachment URLs
         * @return SupportTicketMessageEntity with generated ID and timestamps
         */
        fun createSupportReply(
            ticketId: String,
            userId: String,
            content: String,
            authorName: String? = null,
            attachments: List<String> = emptyList()
        ): SupportTicketMessageEntity = SupportTicketMessageEntity(
            messageId = UUID.randomUUID().toString(),
            ticketId = ticketId,
            userId = userId,
            content = content,
            isFromSupport = true,
            authorName = authorName,
            attachments = attachments,
            createdAt = Instant.now(),
            updatedAt = null,
            isSynced = false,
            syncVersion = 1
        )
    }
    
    /**
     * Marks the message as updated with current timestamp
     * @param newContent Optional new content for the message
     * @return Updated entity with new timestamp and content
     */
    fun markUpdated(newContent: String? = null): SupportTicketMessageEntity = copy(
        content = newContent ?: content,
        updatedAt = Instant.now(),
        isSynced = false,
        syncVersion = syncVersion + 1
    )
    
    /**
     * Marks the message as synced to remote storage
     * @return Updated entity with synced flag set
     */
    fun markSynced(): SupportTicketMessageEntity = copy(
        isSynced = true
    )
    
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
}
