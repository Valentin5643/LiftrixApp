package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for settings audit tracking
 * 
 * This table provides debugging capabilities for settings persistence issues
 * by tracking all changes to user settings with timestamps and sources.
 * 
 * Essential for diagnosing why settings don't persist correctly across
 * app sessions, especially weight unit preferences.
 */
@Entity(tableName = "settings_audit")
data class SettingsAuditEntity(
    @PrimaryKey
    @ColumnInfo(name = "audit_id")
    val auditId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "setting_key")
    val settingKey: String,
    
    @ColumnInfo(name = "old_value")
    val oldValue: String?,
    
    @ColumnInfo(name = "new_value")
    val newValue: String,
    
    @ColumnInfo(name = "source")
    val source: String, // USER, SYNC, MIGRATION
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)