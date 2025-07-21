package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 30 to 31
 * 
 * Adds support for dynamic customizable analytics dashboard with widget preferences
 * and dashboard configuration settings.
 * 
 * Changes:
 * - Creates widget_preferences table for storing user widget visibility, order, size, and refresh rates
 * - Creates dashboard_configurations table for layout modes and display preferences
 * - Adds proper indices for performance optimization and user scoping
 * - Supports all 25+ widget types defined in AnalyticsWidget enum
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create widget_preferences table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS widget_preferences (
                user_id TEXT NOT NULL,
                widget_type TEXT NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                position INTEGER NOT NULL,
                size_mode TEXT NOT NULL DEFAULT 'STANDARD',
                refresh_rate_minutes INTEGER NOT NULL DEFAULT 30,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(user_id, widget_type)
            )
        """)
        
        // Create indices for widget_preferences
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_widget_preferences_user_id 
            ON widget_preferences(user_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_widget_preferences_is_enabled 
            ON widget_preferences(user_id, is_enabled)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_widget_preferences_position 
            ON widget_preferences(user_id, position)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_widget_preferences_sync_status 
            ON widget_preferences(is_synced, sync_version)
        """)
        
        // Create dashboard_configurations table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS dashboard_configurations (
                user_id TEXT NOT NULL PRIMARY KEY,
                layout_mode TEXT NOT NULL DEFAULT 'GRID',
                columns_portrait INTEGER NOT NULL DEFAULT 1,
                columns_landscape INTEGER NOT NULL DEFAULT 2,
                auto_refresh_enabled INTEGER NOT NULL DEFAULT 1,
                theme_variant TEXT NOT NULL DEFAULT 'DEFAULT',
                show_widget_headers INTEGER NOT NULL DEFAULT 1,
                animate_transitions INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1
            )
        """)
        
        // Create indices for dashboard_configurations
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_dashboard_configurations_user_id 
            ON dashboard_configurations(user_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_dashboard_configurations_sync_status 
            ON dashboard_configurations(is_synced, sync_version)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_dashboard_configurations_layout_mode 
            ON dashboard_configurations(layout_mode)
        """)
    }
}