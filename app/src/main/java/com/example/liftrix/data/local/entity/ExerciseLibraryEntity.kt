package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.ExerciseConverters
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory

/**
 * Room entity representing an exercise in the exercise library
 */
@Entity(
    tableName = "exercise_library",
    indices = [
        Index(value = ["name"]),
        Index(value = ["primary_muscle_group"]),
        Index(value = ["equipment"]),
        Index(value = ["movement_pattern"])
    ]
)
@TypeConverters(ExerciseConverters::class)
data class ExerciseLibraryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "primary_muscle_group")
    val primaryMuscleGroup: ExerciseCategory,
    
    @ColumnInfo(name = "equipment")
    val equipment: Equipment,
    
    @ColumnInfo(name = "secondary_muscle_groups")
    val secondaryMuscleGroups: List<ExerciseCategory>,
    
    @ColumnInfo(name = "movement_pattern")
    val movementPattern: String, // e.g., "press", "row", "squat"
    
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: Int, // 1-10 scale
    
    @ColumnInfo(name = "instructions")
    val instructions: String?,
    
    @ColumnInfo(name = "is_compound")
    val isCompound: Boolean, // true for compound movements, false for isolation
    
    @ColumnInfo(name = "searchable_terms")
    val searchableTerms: List<String> // Alternative names and variations for search
) 