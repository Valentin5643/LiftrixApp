package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant

/**
 * Room entity representing an exercise set in local database
 */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exercise_id"]),
        Index(value = ["set_number"]),
        Index(value = ["is_completed"])
    ]
)
@TypeConverters(DateTimeConverters::class)
data class ExerciseSetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,
    
    @ColumnInfo(name = "set_number")
    val setNumber: Int,
    
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,
    
    @ColumnInfo(name = "reps")
    val reps: Int,
    
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    
    @ColumnInfo(name = "rest_time_seconds")
    val restTimeSeconds: Int?,
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Instant?
) 