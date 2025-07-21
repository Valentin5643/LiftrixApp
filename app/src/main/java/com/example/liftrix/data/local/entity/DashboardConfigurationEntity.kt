package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.LocalDateTime

/**
 * Room entity representing user dashboard configuration and layout preferences.
 * 
 * Stores global dashboard settings like layout mode, column preferences,
 * and display options. User-scoped for multi-user device support with
 * sync metadata for Firebase synchronization.
 */
@Entity(tableName = "dashboard_configurations")
@TypeConverters(DateTimeConverters::class)
data class DashboardConfigurationEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "layout_mode", defaultValue = "'GRID'")
    val layoutMode: String = "GRID", // GRID, SECTIONS, LIST, CUSTOM

    @ColumnInfo(name = "columns_portrait", defaultValue = "1")
    val columnsPortrait: Int = 1,

    @ColumnInfo(name = "columns_landscape", defaultValue = "2")
    val columnsLandscape: Int = 2,

    @ColumnInfo(name = "auto_refresh_enabled", defaultValue = "1")
    val autoRefreshEnabled: Boolean = true,

    @ColumnInfo(name = "theme_variant", defaultValue = "'DEFAULT'")
    val themeVariant: String = "DEFAULT", // DEFAULT, COMPACT, EXPANDED

    @ColumnInfo(name = "show_widget_headers", defaultValue = "1")
    val showWidgetHeaders: Boolean = true,

    @ColumnInfo(name = "animate_transitions", defaultValue = "1")
    val animateTransitions: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L
)