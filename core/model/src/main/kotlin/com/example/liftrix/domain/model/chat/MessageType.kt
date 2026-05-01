package com.example.liftrix.domain.model.chat

/**
 * Types of messages in the chat system.
 */
enum class MessageType {
    USER,        // User-sent message
    AI_RESPONSE, // AI assistant response
    SYSTEM       // System notification or error message
}