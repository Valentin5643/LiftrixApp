package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters

/**
 * Room entity representing exercise weight memory in local database
 * Tracks user's last used weight per exercise for faster input in future workouts
 */
@Entity(
    tableName = "exercise_weight_memory",
    primaryKeys = ["user_id", "exercise_library_id"]
)
@TypeConverters(DateTimeConverters::class)
data class ExerciseWeightMemoryEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "exercise_library_id")
    val exerciseLibraryId: String,
    
    @ColumnInfo(name = "last_weight_kg")
    val lastWeightKg: Float,
    
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long,
    
    @ColumnInfo(name = "usage_count", defaultValue = "1")
    val usageCount: Int = 1
)