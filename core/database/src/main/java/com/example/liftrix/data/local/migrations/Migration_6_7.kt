package com.example.liftrix.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 6 to 7.
 *
 * This migration adds:
 * - user_consents table for GDPR/CCPA compliance
 * - Moderation fields to workout_posts (is_hidden, hidden_reason, etc.)
 * - Chat history expiration tracking (expires_at column)
 * - account_restrictions table for user moderation
 * - moderation_actions audit log table
 *
 * IMPORTANT: Existing users are NOT given implicit consent.
 * They must explicitly consent on next login before health/AI features are enabled.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Create user_consents table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS user_consents (
                user_id TEXT NOT NULL PRIMARY KEY,
                privacy_policy_version TEXT,
                privacy_policy_accepted_at INTEGER,
                health_data_consent INTEGER NOT NULL DEFAULT 0,
                health_data_consent_at INTEGER,
                ai_chat_consent INTEGER NOT NULL DEFAULT 0,
                ai_chat_consent_at INTEGER,
                analytics_consent INTEGER NOT NULL DEFAULT 0,
                analytics_consent_at INTEGER,
                marketing_consent INTEGER NOT NULL DEFAULT 0,
                marketing_consent_at INTEGER,
                last_updated INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES user_profiles(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // 2. Seed existing users with NO implicit consent (all flags = 0)
        //    Users must explicitly consent on next login
        database.execSQL("""
            INSERT INTO user_consents (
                user_id,
                privacy_policy_version,
                privacy_policy_accepted_at,
                health_data_consent,
                health_data_consent_at,
                ai_chat_consent,
                ai_chat_consent_at,
                analytics_consent,
                analytics_consent_at,
                marketing_consent,
                marketing_consent_at,
                last_updated
            )
            SELECT
                id,
                NULL,
                NULL,
                0,
                NULL,
                0,
                NULL,
                0,
                NULL,
                0,
                NULL,
                ${System.currentTimeMillis()}
            FROM user_profiles
        """.trimIndent())

        // 3. Add moderation fields to workout_posts
        database.execSQL("""
            ALTER TABLE workout_posts ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0
        """.trimIndent())

        database.execSQL("""
            ALTER TABLE workout_posts ADD COLUMN hidden_reason TEXT
        """.trimIndent())

        database.execSQL("""
            ALTER TABLE workout_posts ADD COLUMN hidden_at INTEGER
        """.trimIndent())

        database.execSQL("""
            ALTER TABLE workout_posts ADD COLUMN hidden_by_user_id TEXT
        """.trimIndent())

        // 4. Add expiration tracking to chat_history
        database.execSQL("""
            ALTER TABLE chat_history ADD COLUMN expires_at INTEGER
        """.trimIndent())

        // Set expires_at for existing messages (default 30 days retention)
        database.execSQL("""
            UPDATE chat_history
            SET expires_at = created_at + (30 * 24 * 60 * 60 * 1000)
            WHERE expires_at IS NULL
        """.trimIndent())

        // Create index for efficient expiration queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_chat_history_expires
            ON chat_history(expires_at)
        """.trimIndent())

        // 5. Create account_restrictions table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS account_restrictions (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                restriction_type TEXT NOT NULL,
                reason TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                created_by TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES user_profiles(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Index for looking up active restrictions
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_account_restrictions_user
            ON account_restrictions(user_id, restriction_type)
        """.trimIndent())

        // 6. Create moderation_actions audit log table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS moderation_actions (
                id TEXT NOT NULL PRIMARY KEY,
                admin_user_id TEXT NOT NULL,
                action_type TEXT NOT NULL,
                target_type TEXT NOT NULL,
                target_id TEXT NOT NULL,
                report_id TEXT,
                reason TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        // Index for admin audit queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_moderation_actions_admin
            ON moderation_actions(admin_user_id, created_at DESC)
        """.trimIndent())

        // Index for target lookups
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_moderation_actions_target
            ON moderation_actions(target_type, target_id)
        """.trimIndent())
    }
}
