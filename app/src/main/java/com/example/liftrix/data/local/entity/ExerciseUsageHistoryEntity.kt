package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "exercise_usage_history",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["user_id", "exercise_id"]),
        Index(value = ["user_id", "used_at"])
    ]
)
data class ExerciseUsageHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,
    
    @ColumnInfo(name = "weight_used")
    val weightUsed: Float,
    
    @ColumnInfo(name = "reps_performed")
    val repsPerformed: Int,
    
    @ColumnInfo(name = "sets_performed")
    val setsPerformed: Int,
    
    @ColumnInfo(name = "used_at")
    val usedAt: LocalDateTime,
    
    @ColumnInfo(name = "workout_id")
    val workoutId: String? = null
) 