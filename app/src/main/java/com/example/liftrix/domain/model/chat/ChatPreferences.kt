package com.example.liftrix.domain.model.chat

/**
 * Domain model representing user's chat preferences.
 */
data class ChatPreferences(
    val userId: String,
    val preferredLanguage: String = "en",
    val autoDetectLanguage: Boolean = true,
    val chatNotificationsEnabled: Boolean = true,
    val conversationHistoryEnabled: Boolean = true,
    val workoutContextSharing: Boolean = true,
    val maxMessagesPerDay: Int = 100,
    val maxTokensPerMonth: Int = 10000,
    val autoClearDays: Int = 30,
    val userContextPrompt: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)