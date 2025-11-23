package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity representing a workout in local database
 */
@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["user_id", "date", "status"], name = "idx_workout_analytics"),
        Index(value = ["user_id", "created_at"], name = "idx_workout_user_created"),
        Index(value = ["user_id", "status"], name = "idx_workout_user_status"),
        Index(value = ["status", "date"], name = "idx_workout_status_date"),
        Index(value = ["is_synced", "sync_version"], name = "idx_workout_sync"),
        Index(value = ["user_id", "is_synced", "updated_at"], name = "idx_workout_sync_operations"),
        Index(value = ["id", "updated_at"], name = "idx_workout_conflict_detection"),
        // Performance optimization indexes (added based on PERF-SPEC analysis)
        Index(value = ["user_id", "updated_at", "date", "created_at"], name = "idx_workout_history_optimized"),
        Index(value = ["user_id", "date"], name = "idx_workout_date_range_fast"),
        Index(value = ["template_id", "user_id", "created_at"], name = "idx_workout_template_history")
    ]
)
@TypeConverters(DateTimeConverters::class, WorkoutConverters::class)
data class WorkoutEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "date")
    val date: LocalDate,
    
    @ColumnInfo(name = "exercises_json")
    val exercisesJson: String, // JSON serialized list of exercises
    
    @ColumnInfo(name = "status")
    val status: WorkoutStatus,
    
    @ColumnInfo(name = "start_time")
    val startTime: Instant?,
    
    @ColumnInfo(name = "end_time")
    val endTime: Instant?,
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "template_id")
    val templateId: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Long = 0L
) 