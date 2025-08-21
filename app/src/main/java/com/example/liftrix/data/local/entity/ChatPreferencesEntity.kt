package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing user's AI chatbot preferences.
 * Stores language settings, usage limits, and notification preferences.
 */
@Entity(tableName = "chat_preferences")
data class ChatPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    // AI Preferences
    @ColumnInfo(name = "preferred_language", defaultValue = "'en'")
    val preferredLanguage: String = "en", // "en" or "ro"
    
    @ColumnInfo(name = "auto_detect_language", defaultValue = "1")
    val autoDetectLanguage: Boolean = true,
    
    @ColumnInfo(name = "chat_notifications_enabled", defaultValue = "1")
    val chatNotificationsEnabled: Boolean = true,
    
    @ColumnInfo(name = "conversation_history_enabled", defaultValue = "1")
    val conversationHistoryEnabled: Boolean = true,
    
    @ColumnInfo(name = "workout_context_sharing", defaultValue = "1")
    val workoutContextSharing: Boolean = true,
    
    @ColumnInfo(name = "max_messages_per_day", defaultValue = "100")
    val maxMessagesPerDay: Int = 100,
    
    @ColumnInfo(name = "max_tokens_per_month", defaultValue = "10000")
    val maxTokensPerMonth: Int = 10000,
    
    @ColumnInfo(name = "auto_clear_days", defaultValue = "30")
    val autoClearDays: Int = 30,
    
    @ColumnInfo(name = "user_context_prompt")
    val userContextPrompt: String? = null, // Free-form preferences
    
    // Enhanced AI Settings
    @ColumnInfo(name = "ai_response_style", defaultValue = "'balanced'")
    val aiResponseStyle: String = "balanced", // "concise", "balanced", "detailed"
    
    @ColumnInfo(name = "include_workout_history", defaultValue = "1")
    val includeWorkoutHistory: Boolean = true,
    
    @ColumnInfo(name = "include_exercise_form_tips", defaultValue = "1")
    val includeExerciseFormTips: Boolean = true,
    
    @ColumnInfo(name = "usage_notifications_threshold", defaultValue = "80")
    val usageNotificationsThreshold: Int = 80, // Percentage threshold for usage alerts
    
    @ColumnInfo(name = "conversation_save_enabled", defaultValue = "1")
    val conversationSaveEnabled: Boolean = true,
    
    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)