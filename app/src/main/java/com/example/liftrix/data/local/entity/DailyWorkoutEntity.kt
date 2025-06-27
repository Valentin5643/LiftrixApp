package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity representing a daily workout instance in local database
 * This is distinct from templates and represents actual workout sessions
 */
@Entity(
    tableName = "daily_workouts",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["user_id"], name = "index_daily_workouts_user_id"),
        Index(value = ["date", "user_id"], name = "index_daily_workouts_date_user_id"),
        Index(value = ["template_id"], name = "index_daily_workouts_template_id"),
        Index(value = ["status"], name = "index_daily_workouts_status"),
        Index(value = ["created_at"], name = "index_daily_workouts_created_at")
    ]
)
@TypeConverters(DateTimeConverters::class, WorkoutConverters::class)
data class DailyWorkoutEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "date")
    val date: LocalDate,
    
    @ColumnInfo(name = "template_id")
    val templateId: String?, // Reference to WorkoutTemplateEntity
    
    @ColumnInfo(name = "exercises_json")
    val exercisesJson: String, // JSON serialized list of actual exercises with performance data
    
    @ColumnInfo(name = "status")
    val status: WorkoutStatus,
    
    @ColumnInfo(name = "start_time")
    val startTime: Instant?,
    
    @ColumnInfo(name = "end_time")
    val endTime: Instant?,
    
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int?,
    
    @ColumnInfo(name = "total_volume_kg")
    val totalVolumeKg: Double?,
    
    @ColumnInfo(name = "total_sets")
    val totalSets: Int?,
    
    @ColumnInfo(name = "total_reps")
    val totalReps: Int?,
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "rating")
    val rating: Int?, // 1-5 stars for workout satisfaction
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Int = 1
) 