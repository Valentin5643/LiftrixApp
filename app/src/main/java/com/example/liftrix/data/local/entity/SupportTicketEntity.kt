package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing support tickets in local database
 * Stores user support requests with device information and sync capabilities
 * 
 * Uses snake_case column names following existing database conventions
 * and includes mandatory user scoping and sync fields
 */
@Entity(tableName = "support_tickets")
@TypeConverters(DateTimeConverters::class)
data class SupportTicketEntity(
    @PrimaryKey
    @ColumnInfo(name = "ticket_id")
    val ticketId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "category")
    val category: String,
    
    @ColumnInfo(name = "subject")
    val subject: String,
    
    @ColumnInfo(name = "description")
    val description: String,
    
    @ColumnInfo(name = "device_info")
    val deviceInfo: String? = null, // JSON string with device details
    
    @ColumnInfo(name = "app_version")
    val appVersion: String? = null,
    
    @ColumnInfo(name = "status")
    val status: String = "OPEN", // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    
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
         * Creates a new support ticket entity
         * 
         * @param userId The user submitting the ticket
         * @param category Support category (e.g., "Bug Report", "Feature Request")
         * @param subject Brief description of the issue
         * @param description Detailed description of the issue
         * @param deviceInfo JSON string with device information
         * @param appVersion Current app version
         * @return SupportTicketEntity with generated ID and timestamps
         */
        fun create(
            userId: String,
            category: String,
            subject: String,
            description: String,
            deviceInfo: String? = null,
            appVersion: String? = null
        ): SupportTicketEntity = SupportTicketEntity(
            ticketId = UUID.randomUUID().toString(),
            userId = userId,
            category = category,
            subject = subject,
            description = description,
            deviceInfo = deviceInfo,
            appVersion = appVersion,
            status = "OPEN",
            createdAt = Instant.now(),
            updatedAt = null,
            isSynced = false,
            syncVersion = 1
        )
        
        /**
         * Available support ticket categories
         */
        object Categories {
            const val BUG_REPORT = "Bug Report"
            const val FEATURE_REQUEST = "Feature Request"
            const val ACCOUNT_ISSUE = "Account Issue"
            const val SYNC_PROBLEM = "Sync Problem"
            const val PERFORMANCE_ISSUE = "Performance Issue"
            const val GENERAL_QUESTION = "General Question"
            const val OTHER = "Other"
        }
        
        /**
         * Available support ticket statuses
         */
        object Statuses {
            const val OPEN = "OPEN"
            const val IN_PROGRESS = "IN_PROGRESS"
            const val RESOLVED = "RESOLVED"
            const val CLOSED = "CLOSED"
        }
    }
    
    /**
     * Marks the ticket as updated with current timestamp
     * @param newStatus Optional new status for the ticket
     * @return Updated entity with new timestamp and status
     */
    fun markUpdated(newStatus: String? = null): SupportTicketEntity = copy(
        status = newStatus ?: status,
        updatedAt = Instant.now(),
        isSynced = false,
        syncVersion = syncVersion + 1
    )
    
    /**
     * Marks the ticket as synced to remote storage
     * @return Updated entity with synced flag set
     */
    fun markSynced(): SupportTicketEntity = copy(
        isSynced = true
    )
    
    /**
     * Checks if the ticket is in an active state
     * @return True if ticket is open or in progress
     */
    fun isActive(): Boolean = status in listOf(Statuses.OPEN, Statuses.IN_PROGRESS)
    
    /**
     * Checks if the ticket is resolved or closed
     * @return True if ticket is resolved or closed
     */
    fun isResolved(): Boolean = status in listOf(Statuses.RESOLVED, Statuses.CLOSED)
}
