package com.example.liftrix.domain.model.chat

/**
 * Domain model representing a chat message.
 */
data class ChatMessage(
    val id: String,
    val userId: String,
    val conversationId: String,
    val type: MessageType,
    val language: String,
    val content: String,
    val workoutContext: WorkoutContext? = null,
    val tokenCount: Int? = null,
    val processingTimeMs: Long? = null,
    val createdAt: Long,
    val isSynced: Boolean = false
)