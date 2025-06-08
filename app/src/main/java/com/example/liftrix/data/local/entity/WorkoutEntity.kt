package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity representing a workout in local database
 */
@Entity(tableName = "workouts")
@TypeConverters(DateTimeConverters::class, WorkoutConverters::class)
data class WorkoutEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "date")
    val date: LocalDate,
    
    @ColumnInfo(name = "exercises_json")
    val exercisesJson: String, // JSON serialized list of exercises
    
    @ColumnInfo(name = "status")
    val status: WorkoutStatus,
    
    @ColumnInfo(name = "start_time")
    val startTime: Instant?,
    
    @ColumnInfo(name = "end_time")
    val endTime: Instant?,
    
    @ColumnInfo(name = "notes")
    val notes: String?,
    
    @ColumnInfo(name = "template_id")
    val templateId: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0L
) 