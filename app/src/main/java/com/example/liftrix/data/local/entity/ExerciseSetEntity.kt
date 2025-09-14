package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters

/**
 * Room entity representing an exercise set in local database
 * Supports flexible metrics (weight, time, distance, RPE) for enhanced set tracking
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
        Index(value = ["exercise_id"], name = "index_exercise_sets_exercise_id"),
        Index(value = ["set_number"], name = "index_exercise_sets_set_number"),
        Index(value = ["completed_at"], name = "index_exercise_sets_completed_at"),
        Index(value = ["exercise_id", "set_number"], name = "idx_exercise_sets_exercise_set"),
        Index(value = ["exercise_id", "completed_at"], name = "idx_exercise_sets_exercise_completed"),
        Index(value = ["weight_kg", "completed_at"], name = "idx_exercise_sets_performance")
    ]
)
@TypeConverters(DateTimeConverters::class)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    
    @ColumnInfo(name = "set_number")
    val setNumber: Int,
    
    @ColumnInfo(name = "reps")
    val reps: Int? = null,
    
    @ColumnInfo(name = "weight_kg")
    val weightKg: Float? = null,
    
    @ColumnInfo(name = "time_seconds")
    val timeSeconds: Int? = null,
    
    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Float? = null,
    
    @ColumnInfo(name = "rpe")
    val rpe: Int? = null,
    
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
) 