package com.example.liftrix.data.local.dto

import androidx.room.ColumnInfo

data class ChatConversationSummaryRow(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
    @ColumnInfo(name = "message_count") val messageCount: Int,
    @ColumnInfo(name = "is_title_custom") val isTitleCustom: Boolean
)
