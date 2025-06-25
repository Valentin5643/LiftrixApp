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
 * Room entity representing a reusable workout template in local database
 */
@Entity(
    tableName = "workout_templates",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["name", "user_id"], unique = true),
        Index(value = ["created_at"])
    ]
)
@TypeConverters(DateTimeConverters::class, WorkoutConverters::class)
data class WorkoutTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String?,
    
    @ColumnInfo(name = "template_exercises_json")
    val templateExercisesJson: String, // JSON serialized list of template exercises
    
    @ColumnInfo(name = "estimated_duration_minutes")
    val estimatedDurationMinutes: Int?,
    
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: Int?, // 1-10 scale
    
    @ColumnInfo(name = "tags")
    val tags: List<String>?, // e.g., ["strength", "upper-body", "beginner"]
    
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,
    
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Instant?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    val syncVersion: Int = 1
) 