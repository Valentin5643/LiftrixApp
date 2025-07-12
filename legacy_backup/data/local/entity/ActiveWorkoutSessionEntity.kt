package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import java.time.Instant

/**
 * Room entity for persisting active workout sessions that are currently in progress.
 * This enables background persistence and recovery of ongoing workouts.
 */
@Entity(
    tableName = "active_workout_sessions",
    indices = [
        Index(value = ["user_id"], name = "index_active_sessions_user_id"),
        Index(value = ["started_at"], name = "index_active_sessions_started_at"),
        Index(value = ["template_id"], name = "index_active_sessions_template_id"),
        Index(value = ["session_state"], name = "index_active_sessions_state")
    ]
)
@TypeConverters(DateTimeConverters::class, WorkoutConverters::class)
data class ActiveWorkoutSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "template_id")
    val templateId: String? = null, // Reference to original template
    
    @ColumnInfo(name = "exercises_json")
    val exercisesJson: String, // JSON serialized list of SessionExercise
    
    @ColumnInfo(name = "current_exercise_index", defaultValue = "0")
    val currentExerciseIndex: Int = 0,
    
    @ColumnInfo(name = "session_state")
    val sessionState: String, // ACTIVE, PAUSED, REST
    
    @ColumnInfo(name = "started_at")
    val startedAt: Instant,
    
    @ColumnInfo(name = "paused_at")
    val pausedAt: Instant? = null,
    
    @ColumnInfo(name = "resumed_at")
    val resumedAt: Instant? = null,
    
    @ColumnInfo(name = "total_paused_duration", defaultValue = "0")
    val totalPausedDuration: Long = 0, // seconds
    
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    
    @ColumnInfo(name = "last_modified")
    val lastModified: Instant = Instant.now(),
    
    // Rest timer state
    @ColumnInfo(name = "rest_timer_start_time")
    val restTimerStartTime: Instant? = null,
    
    @ColumnInfo(name = "rest_timer_duration_seconds")
    val restTimerDurationSeconds: Int? = null,
    
    @ColumnInfo(name = "rest_timer_paused_at")
    val restTimerPausedAt: Instant? = null,
    
    // Background persistence metadata
    @ColumnInfo(name = "auto_save_enabled", defaultValue = "1")
    val autoSaveEnabled: Boolean = true,
    
    @ColumnInfo(name = "last_auto_save")
    val lastAutoSave: Instant? = null,
    
    @ColumnInfo(name = "recovery_data_json")
    val recoveryDataJson: String? = null, // Additional recovery metadata
    
    // Sync fields
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Int = 1
) {
    companion object {
        // Session state constants
        const val STATE_ACTIVE = "ACTIVE"
        const val STATE_PAUSED = "PAUSED" 
        const val STATE_REST = "REST"
        
        // Recovery data keys
        const val RECOVERY_DEVICE_INFO = "device_info"
        const val RECOVERY_APP_VERSION = "app_version"
        const val RECOVERY_LAST_INTERACTION = "last_interaction"
        const val RECOVERY_EXERCISE_HISTORY = "exercise_history"
    }
}

/**
 * Recovery data structure for additional session metadata
 */
data class SessionRecoveryData(
    val deviceInfo: String? = null,
    val appVersion: String? = null,
    val lastInteraction: Instant? = null,
    val exerciseHistory: List<String> = emptyList(), // Exercise IDs in order of completion
    val completionPercentage: Float = 0f,
    val totalVolume: Double = 0.0,
    val averageRestTime: Long = 0,
    val sessionNotes: List<String> = emptyList()
)