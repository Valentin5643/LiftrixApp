package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 53 to 54 of the Liftrix database.
 * 
 * Changes:
 * - Adds settings_version and last_sync_timestamp columns to user_settings table
 * - Creates settings_audit table for debugging settings persistence issues
 * - Fixes weight unit storage to use consistent enum values (KILOGRAMS/POUNDS)
 * - Adds indices for performance optimization
 * 
 * This migration enhances settings persistence reliability as specified in 
 * SPEC-20250116-settings-persistence, ensuring settings changes are properly
 * tracked and can be debugged when persistence issues occur.
 */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Add settings versioning and sync timestamp to user_settings table
        database.execSQL("""
            ALTER TABLE `user_settings` 
            ADD COLUMN `settings_version` INTEGER DEFAULT 1
        """.trimIndent())

        database.execSQL("""
            ALTER TABLE `user_settings`
            ADD COLUMN `last_sync_timestamp` INTEGER
        """.trimIndent())

        // Create settings audit table for debugging persistence issues
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `settings_audit` (
                `audit_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `setting_key` TEXT NOT NULL,
                `old_value` TEXT,
                `new_value` TEXT,
                `source` TEXT NOT NULL, -- 'USER', 'SYNC', 'MIGRATION'
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`audit_id`)
            )
        """.trimIndent())

        // Fix weight unit storage to use consistent enum values
        database.execSQL("""
            UPDATE `user_settings` 
            SET `weight_unit` = CASE 
                WHEN `weight_unit` = 'kg' THEN 'KILOGRAMS'
                WHEN `weight_unit` = 'lbs' THEN 'POUNDS'
                WHEN `weight_unit` = 'KG' THEN 'KILOGRAMS'
                WHEN `weight_unit` = 'LBS' THEN 'POUNDS'
                WHEN `weight_unit` = 'kilogram' THEN 'KILOGRAMS'
                WHEN `weight_unit` = 'pound' THEN 'POUNDS'
                ELSE `weight_unit`
            END
        """.trimIndent())

        // Create indices for settings_audit table for efficient querying
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_settings_audit_user_id_timestamp` 
            ON `settings_audit` (`user_id`, `timestamp`)
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_settings_audit_setting_key_timestamp` 
            ON `settings_audit` (`setting_key`, `timestamp`)
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_settings_audit_source` 
            ON `settings_audit` (`source`)
        """.trimIndent())

        // Create index on user_settings for sync performance
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_user_settings_sync_timestamp` 
            ON `user_settings` (`last_sync_timestamp`)
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_user_settings_version` 
            ON `user_settings` (`settings_version`)
        """.trimIndent())
    }
}