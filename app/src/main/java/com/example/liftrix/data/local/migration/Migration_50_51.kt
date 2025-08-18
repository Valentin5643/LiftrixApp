package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 50 to 51 of the Liftrix database.
 * 
 * Changes:
 * - Adds media_items table for content sharing and media management
 * 
 * This migration adds the media management system as specified in 
 * SPEC-20250113-content-sharing-media, including photos, videos, and media processing.
 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create media_items table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `media_items` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `post_id` TEXT,
                `type` TEXT NOT NULL,
                `original_url` TEXT NOT NULL,
                `cdn_url` TEXT,
                `thumbnail_url` TEXT,
                `blurhash` TEXT,
                `width` INTEGER,
                `height` INTEGER,
                `size_bytes` INTEGER,
                `duration_seconds` INTEGER,
                `mime_type` TEXT,
                `processing_status` TEXT NOT NULL DEFAULT 'PENDING',
                `processed_at` INTEGER,
                `compression_ratio` REAL,
                `is_public` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `expires_at` INTEGER,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`user_id`) REFERENCES `social_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`post_id`) REFERENCES `workout_posts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        
        // Create indices for media_items table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `idx_media_items_user_created` 
            ON `media_items` (`user_id`, `created_at`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_media_items_post_id` 
            ON `media_items` (`post_id`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_media_items_processing_status` 
            ON `media_items` (`processing_status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_media_items_is_public` 
            ON `media_items` (`is_public`)
        """.trimIndent())
    }
}