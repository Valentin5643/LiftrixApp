package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.sql.SQLException

/**
 * Migration from database version 41 to 42
 * Widget Migration Notice Support: Add column to track if user has seen widget migration notice
 * 
 * Background:
 * - Widget system refactoring removes deprecated widgets (Analytics Dashboard System)
 * - Need to show one-time notification to users explaining changes  
 * - Track notification state to prevent showing multiple times
 * - Supports UX requirement from SPEC-20250205-widget-system-refactoring
 * 
 * Changes:
 * - Add has_seen_widget_migration_notice column to dashboard_configurations table
 * - Default to false (0) for all users so notification is shown initially
 * - Integrates with user notification system in ProgressDashboardScreen
 * 
 * Integration Points:
 * - ProgressDashboardScreen checks this field to show/hide notification dialog
 * - UserPreferencesViewModel provides method to mark notice as seen
 * - DashboardConfigurationEntity updated with new field
 */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            // ========================================
            // Add Widget Migration Notice Column
            // ========================================
            
            // Add has_seen_widget_migration_notice column with default value of false
            // This enables notification display for all users initially
            database.execSQL("""
                ALTER TABLE dashboard_configurations 
                ADD COLUMN has_seen_widget_migration_notice INTEGER NOT NULL DEFAULT 0
            """.trimIndent())
            
            // ========================================
            // Update Sync Metadata
            // ========================================
            
            // Mark all configurations as requiring sync due to schema change
            database.execSQL("""
                UPDATE dashboard_configurations 
                SET updated_at = datetime('now'),
                    is_synced = 0,
                    sync_version = sync_version + 1
            """.trimIndent())
            
            // ========================================
            // Migration Logging
            // ========================================
            
            // Log the migration for monitoring and debugging purposes
            database.execSQL("""
                INSERT OR REPLACE INTO analytics_cache (
                    id, user_id, calculation_type, result, timestamp
                ) VALUES (
                    'migration_41_42_widget_migration_notice', 
                    'system', 
                    'widget_migration_notice_support', 
                    '{"migration": "41_42", "action": "add_migration_notice_field", "feature": "has_seen_widget_migration_notice", "default": false, "timestamp": ' || (strftime('%s', 'now') * 1000) || '}',
                    strftime('%s', 'now') * 1000
                )
            """.trimIndent())
            
            database.setTransactionSuccessful()
        } catch (e: SQLException) {
            throw RuntimeException("Migration 41->42 failed: Widget migration notice support", e)
        } finally {
            database.endTransaction()
        }
    }
}