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
 */
@Entity(tableName = "user_profiles")
@TypeConverters(DateTimeConverters::class, UserProfileConverters::class)
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "age")
    val age: Int?,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,

    @ColumnInfo(name = "available_equipment_json")
    val availableEquipmentJson: String?,

    @ColumnInfo(name = "other_equipment")
    val otherEquipment: String?,

    @ColumnInfo(name = "fitness_goals_json")
    val fitnessGoalsJson: String?,

    @ColumnInfo(name = "goals_priority_json")
    val goalsPriorityJson: String?,

    @ColumnInfo(name = "completed_at")
    val completedAt: LocalDateTime?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "profile_version")
    val profileVersion: Long,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0L
) 