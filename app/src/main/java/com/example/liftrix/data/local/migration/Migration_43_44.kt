package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.sql.SQLException

/**
 * Migration from database version 43 to 44
 * Social Infrastructure Foundation: Create social tables with privacy controls
 * 
 * Background:
 * - Enable social connectivity driving 60% user engagement increase and 40% retention improvement
 * - Complete privacy control with user data isolation at DAO level
 * - Foundation for follow relationships, gym buddies, and social sharing
 * - Supports requirements from SPEC-20250113-social-infrastructure
 * 
 * Changes:
 * - Add social_profiles table with enhanced user profile fields and social stats
 * - Add follow_relationships table with status tracking (PENDING, ACCEPTED, BLOCKED)
 * - Add gym_buddies table for inner circle relationships via QR code pairing
 * - Add privacy_settings table with granular controls for social features
 * - Add blocked_users table for user blocking functionality
 * - Performance indexes for social queries targeting <200ms response time
 * 
 * Security Impact:
 * - All social queries MUST include user_id filtering to prevent data leakage
 * - Privacy-first defaults (private profiles, social disabled by default)
 * - FOREIGN KEY constraints ensure data integrity on user deletion
 */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            // ========================================
            // Social Infrastructure Tables
            // ========================================
            
            // Social profiles with enhanced fields
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS social_profiles (
                    user_id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    display_name TEXT,
                    bio TEXT,
                    profile_photo_url TEXT,
                    cover_photo_url TEXT,
                    
                    -- Social stats
                    workout_count INTEGER DEFAULT 0,
                    follower_count INTEGER DEFAULT 0,
                    following_count INTEGER DEFAULT 0,
                    
                    -- Profile metadata
                    member_since INTEGER NOT NULL,
                    last_active INTEGER,
                    is_verified BOOLEAN DEFAULT FALSE,
                    
                    -- Privacy settings
                    is_private BOOLEAN DEFAULT TRUE,
                    hide_from_suggestions BOOLEAN DEFAULT FALSE,
                    allow_friend_requests BOOLEAN DEFAULT TRUE,
                    
                    -- External links
                    instagram_handle TEXT,
                    youtube_channel TEXT,
                    personal_website TEXT,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    sync_version INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    
                    FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Follow relationships with status tracking
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS follow_relationships (
                    id TEXT PRIMARY KEY,
                    follower_id TEXT NOT NULL,
                    following_id TEXT NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('PENDING', 'ACCEPTED', 'BLOCKED')),
                    
                    -- Relationship metadata
                    created_at INTEGER NOT NULL,
                    accepted_at INTEGER,
                    blocked_at INTEGER,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    sync_version INTEGER DEFAULT 0,
                    
                    UNIQUE(follower_id, following_id),
                    FOREIGN KEY (follower_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (following_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Gym buddy relationships (inner circle)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS gym_buddies (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    buddy_id TEXT NOT NULL,
                    
                    -- Buddy metadata
                    buddy_nickname TEXT,
                    created_at INTEGER NOT NULL,
                    last_pr_notification_sent INTEGER,
                    notification_cooldown_hours INTEGER DEFAULT 24,
                    
                    -- QR code pairing
                    paired_via_qr BOOLEAN DEFAULT TRUE,
                    pairing_location TEXT,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    sync_version INTEGER DEFAULT 0,
                    
                    UNIQUE(user_id, buddy_id),
                    CHECK(user_id != buddy_id),
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (buddy_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Privacy settings with granular controls
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS privacy_settings (
                    user_id TEXT PRIMARY KEY,
                    
                    -- Master controls
                    social_enabled BOOLEAN DEFAULT FALSE,
                    profile_visibility TEXT DEFAULT 'PRIVATE' CHECK(profile_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
                    
                    -- Feature toggles
                    allow_follow_requests BOOLEAN DEFAULT FALSE,
                    workout_sharing_enabled BOOLEAN DEFAULT FALSE,
                    gym_buddies_enabled BOOLEAN DEFAULT FALSE,
                    community_participation BOOLEAN DEFAULT FALSE,
                    challenge_participation BOOLEAN DEFAULT FALSE,
                    routine_sharing_enabled BOOLEAN DEFAULT FALSE,
                    
                    -- Content visibility
                    default_workout_visibility TEXT DEFAULT 'PRIVATE' CHECK(default_workout_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
                    show_workout_stats BOOLEAN DEFAULT TRUE,
                    show_achievements BOOLEAN DEFAULT TRUE,
                    show_workout_streak BOOLEAN DEFAULT TRUE,
                    
                    -- Discovery controls
                    hide_from_suggestions BOOLEAN DEFAULT TRUE,
                    hide_from_search BOOLEAN DEFAULT FALSE,
                    
                    -- Notification preferences (JSON)
                    notification_settings TEXT DEFAULT '{}',
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    sync_version INTEGER DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    
                    FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Blocked users list
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS blocked_users (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    blocked_user_id TEXT NOT NULL,
                    reason TEXT,
                    blocked_at INTEGER NOT NULL,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    
                    UNIQUE(user_id, blocked_user_id),
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (blocked_user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // ========================================
            // Performance Indexes for Social Queries
            // ========================================
            
            // Index for social profiles username lookups and search
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_social_profiles_username 
                ON social_profiles(username)
            """.trimIndent())
            
            // Index for social profiles member_since for discovery ranking
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_social_profiles_member_since 
                ON social_profiles(member_since)
            """.trimIndent())
            
            // Index for follow relationships by follower with status filtering
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_follow_relationships_follower 
                ON follow_relationships(follower_id, status)
            """.trimIndent())
            
            // Index for follow relationships by following with status filtering
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_follow_relationships_following 
                ON follow_relationships(following_id, status)
            """.trimIndent())
            
            // Index for gym buddies by user for quick lookup
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_gym_buddies_user 
                ON gym_buddies(user_id)
            """.trimIndent())
            
            // Index for blocked users by user for privacy enforcement
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_blocked_users_user 
                ON blocked_users(user_id)
            """.trimIndent())
            
            // ========================================
            // Default Privacy Settings for Existing Users
            // ========================================
            
            // Seed privacy settings for existing users with maximum privacy defaults
            database.execSQL("""
                INSERT INTO privacy_settings (
                    user_id, 
                    social_enabled, 
                    profile_visibility, 
                    allow_follow_requests, 
                    workout_sharing_enabled, 
                    gym_buddies_enabled, 
                    community_participation, 
                    challenge_participation, 
                    routine_sharing_enabled, 
                    default_workout_visibility, 
                    show_workout_stats, 
                    show_achievements, 
                    show_workout_streak, 
                    hide_from_suggestions, 
                    hide_from_search, 
                    notification_settings, 
                    updated_at
                )
                SELECT 
                    user_id,
                    FALSE,                    -- social_enabled: OFF by default
                    'PRIVATE',                -- profile_visibility: PRIVATE by default
                    FALSE,                    -- allow_follow_requests: OFF by default
                    FALSE,                    -- workout_sharing_enabled: OFF by default
                    FALSE,                    -- gym_buddies_enabled: OFF by default
                    FALSE,                    -- community_participation: OFF by default
                    FALSE,                    -- challenge_participation: OFF by default
                    FALSE,                    -- routine_sharing_enabled: OFF by default
                    'PRIVATE',                -- default_workout_visibility: PRIVATE by default
                    TRUE,                     -- show_workout_stats: ON by default (for when they enable social)
                    TRUE,                     -- show_achievements: ON by default
                    TRUE,                     -- show_workout_streak: ON by default
                    TRUE,                     -- hide_from_suggestions: ON by default (maximum privacy)
                    FALSE,                    -- hide_from_search: OFF by default
                    '{}',                     -- notification_settings: empty JSON
                    strftime('%s', 'now') * 1000  -- updated_at: current timestamp
                FROM user_profiles
                WHERE user_profiles.user_id NOT IN (SELECT user_id FROM privacy_settings)
            """.trimIndent())
            
            // ========================================
            // Migration Analytics Logging
            // ========================================
            
            // Log migration completion for monitoring
            database.execSQL("""
                INSERT OR REPLACE INTO analytics_cache (
                    id, user_id, calculation_type, result, timestamp
                ) VALUES (
                    'migration_43_44_social_infrastructure', 
                    'system', 
                    'social_migration', 
                    '{"migration": "43_44", "action": "create_social_tables", "tables": ["social_profiles", "follow_relationships", "gym_buddies", "privacy_settings", "blocked_users"], "privacy_defaults": "maximum_privacy", "target_performance": {"social_queries": "<200ms", "feed_generation": "<2s"}, "timestamp": ' || (strftime('%s', 'now') * 1000) || '}',
                    strftime('%s', 'now') * 1000
                )
            """.trimIndent())
            
            database.setTransactionSuccessful()
        } catch (e: SQLException) {
            throw RuntimeException("Migration 43->44 failed: Social infrastructure tables", e)
        } finally {
            database.endTransaction()
        }
    }
}