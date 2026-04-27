package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.TemplateShareEventEntity

@Dao
interface TemplateShareEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: TemplateShareEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<TemplateShareEventEntity>)

    @Query(
        """
        SELECT * FROM template_share_events
        WHERE sender_id = :senderId
        AND status = 'PENDING'
        AND expires_at > :now
        AND (receiver_id = :receiverId OR receiver_id IS NULL)
        ORDER BY created_at DESC
        """
    )
    suspend fun getPendingSharesFromBuddy(
        senderId: String,
        receiverId: String,
        now: Long
    ): List<TemplateShareEventEntity>

    @Query(
        """
        SELECT * FROM template_share_events
        WHERE id = :shareId
        AND status = 'PENDING'
        AND expires_at > :now
        AND (receiver_id = :receiverId OR receiver_id IS NULL)
        LIMIT 1
        """
    )
    suspend fun getPendingShareForReceiver(
        shareId: String,
        receiverId: String,
        now: Long
    ): TemplateShareEventEntity?

    @Query(
        """
        UPDATE template_share_events
        SET status = 'ACCEPTED',
            receiver_id = :receiverId,
            accepted_at = :acceptedAt,
            is_dirty = 1,
            last_modified = :acceptedAt
        WHERE id = :shareId
        AND status = 'PENDING'
        AND (receiver_id = :receiverId OR receiver_id IS NULL)
        """
    )
    suspend fun markAccepted(shareId: String, receiverId: String, acceptedAt: Long): Int
}

