package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.UserProfileConverters
import java.time.LocalDateTime

/**
 * Room entity representing a user's fitness profile in the local database.
 * Schema matches Migration_9_to_10 user_profiles table creation with completion tracking.
 */
@Entity(tableName = "user_profiles")
@TypeConverters(DateTimeConverters::class, UserProfileConverters::class)
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "age")
    val age: Int?,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,

    @ColumnInfo(name = "height_cm")
    val heightCm: Double?,

    @ColumnInfo(name = "fitness_level")
    val fitnessLevel: String?,

    @ColumnInfo(name = "goals")
    val goals: String?,

    @ColumnInfo(name = "available_equipment")
    val availableEquipment: String?,

    @ColumnInfo(name = "workout_frequency")
    val workoutFrequency: Int?,

    @ColumnInfo(name = "preferred_workout_duration")
    val preferredWorkoutDuration: Int?,

    @ColumnInfo(name = "completed_at")
    val completedAt: LocalDateTime?,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L
) 