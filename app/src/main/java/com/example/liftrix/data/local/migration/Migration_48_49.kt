package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 48 to 49 of the Liftrix database.
 * 
 * Changes:
 * - Adds user_accounts table for account management functionality
 * 
 * This migration adds the account management system as specified in 
 * SPEC-20250116-account-management, including email, password, and username management.
 */
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create user_accounts table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_accounts` (
                `user_id` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `username` TEXT UNIQUE,
                `email_verified` INTEGER NOT NULL DEFAULT 0,
                `display_name` TEXT,
                `last_password_change` INTEGER,
                `account_created_at` INTEGER NOT NULL,
                `last_email_update` INTEGER,
                `deletion_requested_at` INTEGER,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`user_id`)
            )
        """.trimIndent())
        
        // Create unique index for username
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS `index_user_accounts_username` 
            ON `user_accounts` (`username`)
        """.trimIndent())
        
        // Create index for email lookups
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_user_accounts_email` 
            ON `user_accounts` (`email`)
        """.trimIndent())
        
        // Create index for syncing operations
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_user_accounts_is_synced` 
            ON `user_accounts` (`is_synced`)
        """.trimIndent())
        
        // Create index for deletion operations
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_user_accounts_deletion_requested_at` 
            ON `user_accounts` (`deletion_requested_at`)
        """.trimIndent())
    }
}