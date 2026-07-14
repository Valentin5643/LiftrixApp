package com.example.liftrix.domain.model.chat

data class ChatConversation(
    val id: String,
    val title: String,
    val lastMessagePreview: String,
    val lastUpdatedAt: Long,
    val messageCount: Int,
    val isTitleCustom: Boolean
)
