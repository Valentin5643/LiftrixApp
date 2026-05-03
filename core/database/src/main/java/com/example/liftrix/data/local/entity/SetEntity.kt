package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a set within an exercise
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exercise_id"], name = "index_sets_exercise_id")
    ]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    
    @ColumnInfo(name = "set_number")
    val setNumber: Int,
    
    @ColumnInfo(name = "reps")
    val reps: Int? = null,
    
    @ColumnInfo(name = "weight")
    val weight: Float? = null,
    
    @ColumnInfo(name = "distance")
    val distance: Float? = null,
    
    @ColumnInfo(name = "duration")
    val duration: Int? = null, // in seconds
    
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    
    @ColumnInfo(name = "notes")
    val notes: String? = null
)