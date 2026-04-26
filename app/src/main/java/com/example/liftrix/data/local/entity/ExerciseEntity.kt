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
 * Room entity representing an exercise in local database
 * Supports flexible metrics (weight, time, distance) for enhanced exercise tracking
 */
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id", "user_id"],
            childColumns = ["workout_id", "user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id", "user_id"], unique = true, name = "idx_exercises_id_user"),
        Index(value = ["user_id", "workout_id"], name = "idx_exercises_user_workout"),
        Index(value = ["workout_id", "user_id"], name = "idx_exercises_workout_user"),
        Index(value = ["user_id", "exercise_library_id"], name = "idx_exercises_user_library"),
        Index(value = ["user_id", "order_index"], name = "idx_exercises_user_order")
    ]
)
@TypeConverters(DateTimeConverters::class)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    
    @ColumnInfo(name = "exercise_library_id")
    val exerciseLibraryId: String,
    
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
    
    @ColumnInfo(name = "target_sets")
    val targetSets: Int? = null,
    
    @ColumnInfo(name = "target_reps")
    val targetReps: Int? = null,
    
    @ColumnInfo(name = "target_weight_kg")
    val targetWeightKg: Float? = null,
    
    @ColumnInfo(name = "target_time_seconds")
    val targetTimeSeconds: Int? = null,
    
    @ColumnInfo(name = "target_distance_meters")
    val targetDistanceMeters: Float? = null,
    
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    
    @ColumnInfo(name = "last_used_weight_kg")
    val lastUsedWeightKg: Float? = null,
    
    @ColumnInfo(name = "weight_memory_updated_at")
    val weightMemoryUpdatedAt: Long? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) 
