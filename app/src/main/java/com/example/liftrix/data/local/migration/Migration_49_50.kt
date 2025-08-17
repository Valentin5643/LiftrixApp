package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 49 to 50 of the Liftrix database.
 * 
 * Changes:
 * - Adds content_reports table for content moderation and reporting
 * 
 * This migration adds the content reporting system for social privacy
 * and moderation as specified in SPEC-20250116-social-privacy-moderation.
 */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create content_reports table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `content_reports` (
                `id` TEXT NOT NULL,
                `reporter_user_id` TEXT NOT NULL,
                `content_type` TEXT NOT NULL,
                `content_id` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `description` TEXT,
                `reported_at` INTEGER NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `reviewed_by` TEXT,
                `reviewed_at` INTEGER,
                `action_taken` TEXT,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`reporter_user_id`) REFERENCES `social_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create indices for content_reports table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_content_reports_status` 
            ON `content_reports` (`status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_content_reports_content_type_content_id` 
            ON `content_reports` (`content_type`, `content_id`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_content_reports_reporter_user_id` 
            ON `content_reports` (`reporter_user_id`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_content_reports_reported_at` 
            ON `content_reports` (`reported_at`)
        """.trimIndent())
    }
}