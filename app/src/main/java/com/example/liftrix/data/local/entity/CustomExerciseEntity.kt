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
import com.example.liftrix.domain.model.ExerciseType
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
        Index(value = ["equipment"], name = "index_custom_exercises_equipment"),
        Index(value = ["exercise_type"], name = "index_custom_exercises_exercise_type"),
        Index(value = ["tags"], name = "index_custom_exercises_tags")
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
    
    @ColumnInfo(name = "exercise_type")
    val exerciseType: ExerciseType,
    
    @ColumnInfo(name = "description")
    val description: String?,
    
    @ColumnInfo(name = "secondary_muscle_groups")
    val secondaryMuscleGroups: List<ExerciseCategory>?,
    
    @ColumnInfo(name = "difficulty")
    val difficulty: Int?,
    
    @ColumnInfo(name = "instructions")
    val instructions: List<String>?,
    
    @ColumnInfo(name = "main_image_url")
    val mainImageUrl: String?,
    
    @ColumnInfo(name = "additional_image_urls")
    val additionalImageUrls: List<String>?,
    
    @ColumnInfo(name = "video_url")
    val videoUrl: String?,
    
    @ColumnInfo(name = "tags")
    val tags: List<String>?,
    
    @ColumnInfo(name = "categories")
    val categories: List<ExerciseCategory>?,
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = System.currentTimeMillis()
) 