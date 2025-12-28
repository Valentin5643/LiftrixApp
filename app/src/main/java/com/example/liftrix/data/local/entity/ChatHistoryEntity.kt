package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing chat message history with the AI chatbot.
 * Stores messages, context, and token usage for tracking and sync.
 */
@Entity(
    tableName = "chat_history",
    indices = [
        Index(value = ["user_id", "created_at"]),
        Index(value = ["conversation_id", "created_at"]),
        Index(value = ["user_id", "is_synced"])
    ]
)
data class ChatHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    
    @ColumnInfo(name = "message_type")
    val messageType: String, // USER, AI_RESPONSE, SYSTEM
    
    @ColumnInfo(name = "language")
    val language: String = "en", // "en" or "ro"
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "workout_context")
    val workoutContext: String? = null, // JSON serialized
    
    @ColumnInfo(name = "token_count")
    val tokenCount: Int? = null,
    
    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
)
