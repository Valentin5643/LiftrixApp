package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 52 to 53 of the Liftrix database.
 * 
 * Changes:
 * - Adds help_articles table for in-app help content management
 * - Adds support_tickets table for user support requests
 * - Adds app_config table for application configuration storage
 * 
 * This migration adds the help and support system as specified in 
 * SPEC-20250116-app-information, enabling users to access help content
 * and submit support requests.
 */
val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create help_articles table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `help_articles` (
                `article_id` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `keywords` TEXT,
                `view_count` INTEGER NOT NULL DEFAULT 0,
                `helpful_count` INTEGER NOT NULL DEFAULT 0,
                `not_helpful_count` INTEGER NOT NULL DEFAULT 0,
                `last_updated` INTEGER NOT NULL,
                `version` INTEGER NOT NULL DEFAULT 1,
                `is_featured` INTEGER NOT NULL DEFAULT 0,
                `sort_order` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`article_id`)
            )
        """.trimIndent())
        
        // Create support_tickets table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `support_tickets` (
                `ticket_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `subject` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `device_info` TEXT,
                `app_version` TEXT,
                `status` TEXT NOT NULL DEFAULT 'OPEN',
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`ticket_id`)
            )
        """.trimIndent())
        
        // Create app_config table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `app_config` (
                `config_key` TEXT NOT NULL,
                `config_value` TEXT NOT NULL,
                `last_updated` INTEGER NOT NULL,
                `config_type` TEXT NOT NULL DEFAULT 'STRING',
                `is_cached` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`config_key`)
            )
        """.trimIndent())
        
        // Create indices for help_articles table for search performance
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_help_articles_keywords` 
            ON `help_articles` (`keywords`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_help_articles_category` 
            ON `help_articles` (`category`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_help_articles_featured_sort` 
            ON `help_articles` (`is_featured`, `sort_order`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_help_articles_title_content` 
            ON `help_articles` (`title`, `content`)
        """.trimIndent())
        
        // Create indices for support_tickets table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_support_tickets_user_id_status` 
            ON `support_tickets` (`user_id`, `status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_support_tickets_created_at` 
            ON `support_tickets` (`created_at`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_support_tickets_sync_status` 
            ON `support_tickets` (`is_synced`, `sync_version`)
        """.trimIndent())
        
        // Create indices for app_config table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_app_config_type_cached` 
            ON `app_config` (`config_type`, `is_cached`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_app_config_last_updated` 
            ON `app_config` (`last_updated`)
        """.trimIndent())
    }
}