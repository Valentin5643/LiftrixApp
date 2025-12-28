package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.SupportTicketEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Data Access Object for support ticket operations
 * 
 * Provides methods to retrieve, insert, and update support tickets
 * Includes user scoping to prevent data leakage between users
 */
@Dao
interface SupportTicketDao {
    
    /**
     * Retrieves all support tickets for a specific user
     * @param userId The user's unique identifier
     * @return Flow of user's support tickets ordered by creation date (newest first)
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId ORDER BY created_at DESC")
    fun getUserTickets(userId: String): Flow<List<SupportTicketEntity>>
    
    /**
     * Retrieves support tickets for a user synchronously
     * @param userId The user's unique identifier
     * @return List of user's support tickets
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getUserTicketsSync(userId: String): List<SupportTicketEntity>
    
    /**
     * Retrieves a specific support ticket by ID and user
     * @param ticketId The ticket identifier
     * @param userId The user's unique identifier (for security)
     * @return Flow of SupportTicketEntity or null if not found or not owned by user
     */
    @Query("SELECT * FROM support_tickets WHERE ticket_id = :ticketId AND user_id = :userId")
    fun getTicket(ticketId: String, userId: String): Flow<SupportTicketEntity?>
    
    /**
     * Retrieves a specific support ticket synchronously
     * @param ticketId The ticket identifier
     * @param userId The user's unique identifier (for security)
     * @return SupportTicketEntity or null if not found or not owned by user
     */
    @Query("SELECT * FROM support_tickets WHERE ticket_id = :ticketId AND user_id = :userId")
    suspend fun getTicketSync(ticketId: String, userId: String): SupportTicketEntity?
    
    /**
     * Retrieves support tickets by status for a user
     * @param userId The user's unique identifier
     * @param status The ticket status to filter by
     * @return Flow of tickets matching the status
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId AND status = :status ORDER BY created_at DESC")
    fun getTicketsByStatus(userId: String, status: String): Flow<List<SupportTicketEntity>>
    
    /**
     * Retrieves active (open or in progress) tickets for a user
     * @param userId The user's unique identifier
     * @return Flow of active tickets
     */
    @Query("""
        SELECT * FROM support_tickets 
        WHERE user_id = :userId AND status IN ('OPEN', 'IN_PROGRESS') 
        ORDER BY created_at DESC
    """)
    fun getActiveTickets(userId: String): Flow<List<SupportTicketEntity>>
    
    /**
     * Retrieves tickets by category for a user
     * @param userId The user's unique identifier
     * @param category The ticket category
     * @return Flow of tickets in the specified category
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId AND category = :category ORDER BY created_at DESC")
    fun getTicketsByCategory(userId: String, category: String): Flow<List<SupportTicketEntity>>
    
    /**
     * Retrieves recent tickets for a user (last 30 days)
     * @param userId The user's unique identifier
     * @param sinceTimestamp Timestamp for filtering recent tickets
     * @return Flow of recent tickets
     */
    @Query("""
        SELECT * FROM support_tickets 
        WHERE user_id = :userId AND created_at >= :sinceTimestamp 
        ORDER BY created_at DESC
    """)
    fun getRecentTickets(userId: String, sinceTimestamp: Instant): Flow<List<SupportTicketEntity>>
    
    /**
     * Retrieves unsynced tickets for a user
     * @param userId The user's unique identifier
     * @return Flow of tickets that need to be synced to remote storage
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId AND is_synced = 0 ORDER BY created_at ASC")
    fun getUnsyncedTickets(userId: String): Flow<List<SupportTicketEntity>>
    
    /**
     * Gets unsynced tickets synchronously for sync operations
     * @param userId The user's unique identifier
     * @return List of unsynced tickets
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId AND is_synced = 0 ORDER BY created_at ASC")
    suspend fun getUnsyncedTicketsSync(userId: String): List<SupportTicketEntity>
    
    /**
     * Inserts a new support ticket
     * @param ticket The ticket entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: SupportTicketEntity)
    
    /**
     * Inserts multiple support tickets
     * @param tickets List of ticket entities to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTickets(tickets: List<SupportTicketEntity>)
    
    /**
     * Updates an existing support ticket
     * @param ticket The ticket entity to update
     */
    @Update
    suspend fun updateTicket(ticket: SupportTicketEntity)
    
    /**
     * Updates the status of a support ticket
     * @param ticketId The ticket identifier
     * @param userId The user's unique identifier (for security)
     * @param status The new status
     * @param updatedAt The timestamp of the update
     */
    @Query("""
        UPDATE support_tickets 
        SET status = :status, updated_at = :updatedAt, is_synced = 0, sync_version = sync_version + 1
        WHERE ticket_id = :ticketId AND user_id = :userId
    """)
    suspend fun updateTicketStatus(ticketId: String, userId: String, status: String, updatedAt: Instant)
    
    /**
     * Marks a ticket as synced
     * @param ticketId The ticket identifier
     * @param userId The user's unique identifier (for security)
     */
    @Query("""
        UPDATE support_tickets 
        SET is_synced = 1 
        WHERE ticket_id = :ticketId AND user_id = :userId
    """)
    suspend fun markTicketSynced(ticketId: String, userId: String)
    
    /**
     * Marks multiple tickets as synced
     * @param ticketIds List of ticket identifiers
     * @param userId The user's unique identifier (for security)
     */
    @Query("""
        UPDATE support_tickets 
        SET is_synced = 1 
        WHERE ticket_id IN (:ticketIds) AND user_id = :userId
    """)
    suspend fun markTicketsSynced(ticketIds: List<String>, userId: String)
    
    /**
     * Deletes a specific support ticket
     * @param ticketId The ticket identifier
     * @param userId The user's unique identifier (for security)
     */
    @Query("DELETE FROM support_tickets WHERE ticket_id = :ticketId AND user_id = :userId")
    suspend fun deleteTicket(ticketId: String, userId: String)
    
    /**
     * Deletes all support tickets for a user (for data cleanup)
     * @param userId The user's unique identifier
     */
    @Query("DELETE FROM support_tickets WHERE user_id = :userId")
    suspend fun deleteAllUserTickets(userId: String)
    
    /**
     * Gets the total number of tickets for a user
     * @param userId The user's unique identifier
     * @return Total count of user's tickets
     */
    @Query("SELECT COUNT(*) FROM support_tickets WHERE user_id = :userId")
    suspend fun getUserTicketCount(userId: String): Int
    
    /**
     * Gets the count of active tickets for a user
     * @param userId The user's unique identifier
     * @return Count of active (open or in progress) tickets
     */
    @Query("SELECT COUNT(*) FROM support_tickets WHERE user_id = :userId AND status IN ('OPEN', 'IN_PROGRESS')")
    suspend fun getActiveTicketCount(userId: String): Int
    
    /**
     * Checks if a user has any unsynced tickets
     * @param userId The user's unique identifier
     * @return True if user has unsynced tickets
     */
    @Query("SELECT COUNT(*) > 0 FROM support_tickets WHERE user_id = :userId AND is_synced = 0")
    suspend fun hasUnsyncedTickets(userId: String): Boolean
    
    /**
     * Gets the most recent ticket for a user
     * @param userId The user's unique identifier
     * @return The most recently created ticket or null
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId ORDER BY created_at DESC LIMIT 1")
    suspend fun getMostRecentTicket(userId: String): SupportTicketEntity?
    
    /**
     * Gets ticket statistics for a user
     * @param userId The user's unique identifier
     * @return Map of status to count
     */
    @Query("SELECT status, COUNT(*) as count FROM support_tickets WHERE user_id = :userId GROUP BY status")
    suspend fun getTicketStatistics(userId: String): Map<@MapColumn(columnName = "status") String, @MapColumn(columnName = "count") Int>

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert supportticket from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(supportTicket: SupportTicketEntity) {
        val entity = supportTicket.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert supportticket from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(supportTicket: SupportTicketEntity) {
        val local = getSupportTicketForSync(supportTicket.ticketId, supportTicket.userId)
        if (local == null || supportTicket.lastModified > local.lastModified) {
            val entity = supportTicket.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis().toInt()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: SupportTicketEntity)

    /**
     * Get dirty supportticket that need upload to Firestore.
     */
    @Query("SELECT * FROM support_tickets WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtySupportTickets(userId: String): List<SupportTicketEntity>

    /**
     * Mark supportticket as clean after successful Firestore upload.
     */
    @Query("UPDATE support_tickets SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE ticket_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local supportticket for remote deduplication.
     */
    @Query("SELECT * FROM support_tickets WHERE ticket_id = :id AND user_id = :userId LIMIT 1")
    suspend fun getSupportTicketForSync(id: String, userId: String): SupportTicketEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
