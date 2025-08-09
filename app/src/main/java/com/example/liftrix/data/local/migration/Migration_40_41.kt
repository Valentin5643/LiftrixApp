package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.sql.SQLException

/**
 * Migration from database version 40 to 41
 * Modern Chart Components Integration: Add modern charts configuration flag
 * 
 * Background:
 * - Analytics system has new modern chart components (ModernVolumeChart, GlobalTimeRangeSelector)
 * - Need configuration flag to enable/disable modern charts vs legacy charts
 * - Modern charts offer better UX with bezier curves, gradients, and haptic feedback
 * - 2-column mobile layout requires chart sizing optimization
 * 
 * Changes:
 * - Add use_modern_charts column to dashboard_configurations table
 * - Default to true (enabled) for new and existing users
 * - Update existing configurations to enable modern charts
 * 
 * Modern Chart Components:
 * - ModernVolumeChart: Replaces WorkoutVolumeChart with bezier curves and gradients
 * - GlobalTimeRangeSelector: Replaces TimePeriodSelector with Material 3 design
 * - MuscleGroupPieChart: Interactive pie chart for muscle group distribution
 * 
 * Layout Integration:
 * - 2-column mobile layout activated (ResponsiveDashboardLayout)
 * - Modern charts sized appropriately for compact mobile layout
 * - Maintains 60fps performance target with optimized rendering
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            // ========================================
            // Add Modern Charts Configuration Column
            // ========================================
            
            // Add use_modern_charts column with default value of true
            // This enables modern chart components by default for all users
            database.execSQL("""
                ALTER TABLE dashboard_configurations 
                ADD COLUMN use_modern_charts INTEGER NOT NULL DEFAULT 1
            """.trimIndent())
            
            // ========================================
            // Update Existing Configurations
            // ========================================
            
            // Enable modern charts for all existing dashboard configurations
            // This ensures smooth transition for current users
            database.execSQL("""
                UPDATE dashboard_configurations 
                SET use_modern_charts = 1,
                    updated_at = datetime('now'),
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
                    'migration_40_41_modern_charts', 
                    'system', 
                    'modern_chart_integration', 
                    '{"migration": "40_41", "action": "modern_chart_activation", "feature": "use_modern_charts", "enabled": true, "timestamp": ' || (strftime('%s', 'now') * 1000) || '}',
                    strftime('%s', 'now') * 1000
                )
            """.trimIndent())
            
            database.setTransactionSuccessful()
        } catch (e: SQLException) {
            throw RuntimeException("Migration 40->41 failed: Modern chart integration", e)
        } finally {
            database.endTransaction()
        }
    }
}