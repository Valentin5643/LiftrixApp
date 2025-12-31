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
            parentColumns = ["id", "user_id"],
            childColumns = ["exercise_id", "user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id", "user_id"], unique = true, name = "idx_exercise_sets_id_user"),
        Index(value = ["user_id", "exercise_id"], name = "idx_exercise_sets_user_exercise"),
        Index(value = ["exercise_id", "user_id"], name = "idx_exercise_sets_exercise_user"),
        Index(value = ["user_id", "set_number"], name = "idx_exercise_sets_user_set_number"),
        Index(value = ["user_id", "completed_at"], name = "idx_exercise_sets_user_completed"),
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

    @ColumnInfo(name = "user_id")
    val userId: String,
    
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
