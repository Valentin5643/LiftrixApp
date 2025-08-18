package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 49 to 50 of the Liftrix database.
 * 
 * Changes:
 * - Adds follow_requests table for follow request management
 * 
 * This migration adds the follow request system for social profiles
 * as specified in SPEC-20250113-user-profiles-follow.
 */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create follow_requests table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `follow_requests` (
                `id` TEXT NOT NULL,
                `requester_id` TEXT NOT NULL,
                `target_id` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `request_message` TEXT,
                `created_at` INTEGER NOT NULL,
                `processed_at` INTEGER,
                `expires_at` INTEGER NOT NULL,
                `request_source` TEXT NOT NULL,
                `notification_sent` INTEGER NOT NULL DEFAULT 0,
                `reminder_count` INTEGER NOT NULL DEFAULT 0,
                `last_reminder_at` INTEGER,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 0,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`requester_id`) REFERENCES `social_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_id`) REFERENCES `social_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create indices for follow_requests table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_follow_requests_requester_id_status` 
            ON `follow_requests` (`requester_id`, `status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_follow_requests_target_id_status` 
            ON `follow_requests` (`target_id`, `status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS `index_follow_requests_requester_id_target_id` 
            ON `follow_requests` (`requester_id`, `target_id`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_follow_requests_created_at` 
            ON `follow_requests` (`created_at`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_follow_requests_expires_at` 
            ON `follow_requests` (`expires_at`)
        """.trimIndent())
    }
}