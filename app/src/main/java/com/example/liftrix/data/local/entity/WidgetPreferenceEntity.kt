package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.LocalDateTime

/**
 * Room entity representing user preferences for individual dashboard widgets.
 * 
 * Uses composite primary key (user_id, widget_type) to ensure unique widget
 * preferences per user while supporting multiple users on device.
 * Includes sync metadata for Firebase synchronization.
 */
@Entity(
    tableName = "widget_preferences",
    primaryKeys = ["user_id", "widget_type"]
)
@TypeConverters(DateTimeConverters::class)
data class WidgetPreferenceEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "widget_type")
    val widgetType: String,

    @ColumnInfo(name = "is_enabled", defaultValue = "1")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "position")
    val position: Int = 0,

    @ColumnInfo(name = "size_mode", defaultValue = "'STANDARD'")
    val sizeMode: String = "STANDARD",

    @ColumnInfo(name = "refresh_rate_minutes", defaultValue = "30")
    val refreshRateMinutes: Int = 30,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L
)