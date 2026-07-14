package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "chat_conversations",
    primaryKeys = ["user_id", "conversation_id"],
    indices = [Index(value = ["user_id", "updated_at"])]
)
data class ChatConversationEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "is_title_custom", defaultValue = "0") val isTitleCustom: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)
