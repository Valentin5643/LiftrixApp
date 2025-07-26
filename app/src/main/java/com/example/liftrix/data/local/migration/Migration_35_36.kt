package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 35 to 36
 * Enhances user profile system with social features, achievements, and privacy controls
 * 
 * Changes:
 * - Adds bio, privacy, activity tracking fields to user_profiles table
 * - Adds search and social interaction fields (search_keywords, profile views)
 * - Creates user_achievements table for fitness milestone tracking
 * - Creates user_search_cache table for performance optimization
 * - Creates qr_code_mappings table for profile sharing
 * - Adds performance indexes for social queries and search functionality
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Enhance user_profiles table with social and achievement fields
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN bio TEXT
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN is_public INTEGER NOT NULL DEFAULT 1
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN last_active_at TEXT
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN total_workouts INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN current_streak INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN longest_streak INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN member_since TEXT
        """.trimIndent())
        
        // Update existing rows with current timestamp
        database.execSQL("""
            UPDATE user_profiles 
            SET member_since = datetime('now') 
            WHERE member_since IS NULL
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN profile_completion_percentage INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        // Add social search and tracking fields to user_profiles
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN search_keywords TEXT
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN last_profile_view_at TEXT
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN profile_views_count INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        // Create user_achievements table for fitness milestone tracking
        database.execSQL("""
            CREATE TABLE user_achievements (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                achievement_type TEXT NOT NULL,
                achievement_title TEXT NOT NULL,
                achievement_description TEXT NOT NULL,
                unlocked_at TEXT NOT NULL,
                is_displayed INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create user search cache table for performance optimization
        database.execSQL("""
            CREATE TABLE user_search_cache (
                id TEXT PRIMARY KEY NOT NULL,
                viewer_user_id TEXT NOT NULL,
                search_query TEXT NOT NULL,
                search_results TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                FOREIGN KEY(viewer_user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create QR code mapping table for profile sharing
        database.execSQL("""
            CREATE TABLE qr_code_mappings (
                qr_code_id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT,
                is_active INTEGER NOT NULL DEFAULT 1,
                usage_count INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Initialize timestamps for new tables (will be set when records are actually created)
        // These tables start empty, so no initial UPDATE needed
        
        // Create performance indexes for social and achievement queries
        database.execSQL("""
            CREATE INDEX index_user_profiles_is_public ON user_profiles(is_public)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_user_profiles_total_workouts ON user_profiles(total_workouts)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_user_achievements_user_id ON user_achievements(user_id)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_user_achievements_type ON user_achievements(achievement_type)
        """.trimIndent())
        
        // Create indexes for search performance
        database.execSQL("""
            CREATE INDEX index_user_search_cache_viewer ON user_search_cache(viewer_user_id)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_user_search_cache_query ON user_search_cache(search_query)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_qr_codes_user_id ON qr_code_mappings(user_id)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_user_profiles_search_keywords ON user_profiles(search_keywords)
        """.trimIndent())
    }
}