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
    
    // Enhanced AI Settings
    val aiResponseStyle: String = "balanced", // "concise", "balanced", "detailed"
    val includeWorkoutHistory: Boolean = true,
    val includeExerciseFormTips: Boolean = true,
    val usageNotificationsThreshold: Int = 80, // Percentage threshold
    val conversationSaveEnabled: Boolean = true,
    
    val updatedAt: Long = System.currentTimeMillis()
)