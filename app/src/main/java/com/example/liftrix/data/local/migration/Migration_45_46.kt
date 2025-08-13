package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 45 to 46 of the Liftrix database.
 * 
 * Changes:
 * - Adds follow_requests table for managing pending follow requests
 * 
 * This migration is necessary to support the social follow request system
 * where users can send follow requests to private profiles.
 */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create follow_requests table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `follow_requests` (
                `id` TEXT NOT NULL,
                `requester_id` TEXT NOT NULL,
                `target_id` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `message` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                `expires_at` INTEGER,
                `rejection_reason` TEXT,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 1,
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