package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for storing guest session data
 */
@Entity(
    tableName = "guest_sessions",
    indices = [
        Index(value = ["user_id"], unique = true),
        Index(value = ["is_limit_reached"]),
        Index(value = ["last_activity_at"])
    ]
)
data class GuestSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "workout_count")
    val workoutCount: Int,

    @ColumnInfo(name = "max_workouts")
    val maxWorkouts: Int,

    @ColumnInfo(name = "last_nudge_shown")
    val lastNudgeShown: Instant? = null,

    @ColumnInfo(name = "nudge_count")
    val nudgeCount: Int,

    @ColumnInfo(name = "significant_interaction_count")
    val significantInteractionCount: Int,

    @ColumnInfo(name = "session_started_at")
    val sessionStartedAt: Instant,

    @ColumnInfo(name = "last_activity_at")
    val lastActivityAt: Instant,

    @ColumnInfo(name = "has_seen_limit_warning")
    val hasSeenLimitWarning: Boolean,

    @ColumnInfo(name = "is_limit_reached")
    val isLimitReached: Boolean
)