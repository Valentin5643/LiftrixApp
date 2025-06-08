package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.ExerciseConverters
import com.example.liftrix.domain.model.ExerciseCategory
import java.time.Instant

/**
 * Room entity representing an exercise in local database
 */
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workout_id"]),
        Index(value = ["name"]),
        Index(value = ["category"])
    ]
)
@TypeConverters(DateTimeConverters::class, ExerciseConverters::class)
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "category")
    val category: ExerciseCategory,
    
    @ColumnInfo(name = "sets_json")
    val setsJson: String, // JSON serialized list of sets
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "target_sets")
    val targetSets: Int?,
    
    @ColumnInfo(name = "target_reps")
    val targetReps: Int?,
    
    @ColumnInfo(name = "target_weight_kg")
    val targetWeightKg: Double?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0
) 