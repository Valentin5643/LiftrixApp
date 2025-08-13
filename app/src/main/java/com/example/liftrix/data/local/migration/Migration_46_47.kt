package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 46 to 47 of the Liftrix database.
 * 
 * Changes:
 * - Adds fcm_tokens table for FCM token management
 * - Adds notification_preferences table for user notification preferences
 * - Adds notification_queue table for notification batching and scheduling
 * - Adds notification_mutes table for user notification muting
 * - Adds notification_history table for notification history tracking
 * 
 * This migration is necessary to support the comprehensive notification system
 * with intelligent routing, privacy controls, and batching capabilities.
 */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create fcm_tokens table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `fcm_tokens` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `token` TEXT NOT NULL,
                `device_id` TEXT NOT NULL,
                `device_name` TEXT,
                `platform` TEXT NOT NULL,
                `app_version` TEXT,
                `is_active` INTEGER NOT NULL DEFAULT 1,
                `last_used` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create indices for fcm_tokens table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_fcm_tokens_user_id_is_active` 
            ON `fcm_tokens` (`user_id`, `is_active`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS `index_fcm_tokens_user_id_device_id` 
            ON `fcm_tokens` (`user_id`, `device_id`)
        """.trimIndent())
        
        // Create notification_preferences table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `notification_preferences` (
                `user_id` TEXT NOT NULL,
                `notifications_enabled` INTEGER NOT NULL DEFAULT 1,
                `workout_notifications` INTEGER NOT NULL DEFAULT 1,
                `social_notifications` INTEGER NOT NULL DEFAULT 1,
                `achievement_notifications` INTEGER NOT NULL DEFAULT 1,
                `reminder_notifications` INTEGER NOT NULL DEFAULT 1,
                `gym_buddy_prs` INTEGER NOT NULL DEFAULT 1,
                `follow_requests` INTEGER NOT NULL DEFAULT 1,
                `post_likes` INTEGER NOT NULL DEFAULT 1,
                `post_comments` INTEGER NOT NULL DEFAULT 1,
                `mentions` INTEGER NOT NULL DEFAULT 1,
                `delivery_frequency` TEXT NOT NULL DEFAULT 'IMMEDIATE',
                `quiet_hours_enabled` INTEGER NOT NULL DEFAULT 1,
                `quiet_hours_start` INTEGER NOT NULL DEFAULT 22,
                `quiet_hours_end` INTEGER NOT NULL DEFAULT 8,
                `batch_social_notifications` INTEGER NOT NULL DEFAULT 1,
                `batch_window_minutes` INTEGER NOT NULL DEFAULT 60,
                `notification_sound` INTEGER NOT NULL DEFAULT 1,
                `notification_vibration` INTEGER NOT NULL DEFAULT 1,
                `show_in_app_notifications` INTEGER NOT NULL DEFAULT 1,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`),
                FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create notification_queue table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `notification_queue` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `data` TEXT,
                `priority` TEXT NOT NULL DEFAULT 'NORMAL',
                `channel_id` TEXT NOT NULL,
                `batch_key` TEXT,
                `can_batch` INTEGER NOT NULL DEFAULT 1,
                `scheduled_for` INTEGER,
                `expires_at` INTEGER,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `sent_at` INTEGER,
                `failure_reason` TEXT,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create indices for notification_queue table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_queue_user_id_status_scheduled_for` 
            ON `notification_queue` (`user_id`, `status`, `scheduled_for`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_queue_batch_key` 
            ON `notification_queue` (`batch_key`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_queue_expires_at` 
            ON `notification_queue` (`expires_at`)
        """.trimIndent())
        
        // Create notification_mutes table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `notification_mutes` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `mute_type` TEXT NOT NULL,
                `muted_user_id` TEXT,
                `muted_category` TEXT,
                `muted_until` INTEGER,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`muted_user_id`) REFERENCES `social_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create indices for notification_mutes table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_mutes_user_id_mute_type` 
            ON `notification_mutes` (`user_id`, `mute_type`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_mutes_muted_until` 
            ON `notification_mutes` (`muted_until`)
        """.trimIndent())
        
        // Create notification_history table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `notification_history` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `data` TEXT,
                `is_read` INTEGER NOT NULL DEFAULT 0,
                `read_at` INTEGER,
                `action_taken` TEXT,
                `received_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create indices for notification_history table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_history_user_id_received_at` 
            ON `notification_history` (`user_id`, `received_at`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_notification_history_is_read` 
            ON `notification_history` (`is_read`)
        """.trimIndent())
    }
}