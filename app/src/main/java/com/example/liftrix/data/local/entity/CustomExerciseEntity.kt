package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.ExerciseConverters
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import java.time.Instant

/**
 * Room entity representing a user-created custom exercise in local database
 */
@Entity(
    tableName = "custom_exercises",
    indices = [
        Index(value = ["user_id"], name = "index_custom_exercises_user_id"),
        Index(value = ["name", "user_id"], unique = true, name = "index_custom_exercises_name_user_id"),
        Index(value = ["primary_muscle_group"], name = "index_custom_exercises_primary_muscle_group"),
        Index(value = ["equipment"], name = "index_custom_exercises_equipment")
    ]
)
@TypeConverters(DateTimeConverters::class, ExerciseConverters::class)
data class CustomExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "primary_muscle_group")
    val primaryMuscleGroup: ExerciseCategory,
    
    @ColumnInfo(name = "equipment")
    val equipment: Equipment,
    
    @ColumnInfo(name = "secondary_muscle_groups")
    val secondaryMuscleGroups: List<ExerciseCategory>?,
    
    @ColumnInfo(name = "difficulty")
    val difficulty: Int?,
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Int = 1
) 