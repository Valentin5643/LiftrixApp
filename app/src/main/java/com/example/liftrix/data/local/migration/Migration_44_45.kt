package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.sql.SQLException

/**
 * Migration from database version 44 to 45
 * Social Feed and Engagement System: Create feed tables with engagement tracking
 * 
 * Background:
 * - Enable social feed with workout sharing, likes, comments, and saves
 * - Support real-time engagement with optimistic updates
 * - Feed caching mechanism for performance optimization (< 2s load time)
 * - Implements requirements from SPEC-20250113-social-feed-engagement
 * 
 * Changes:
 * - Add workout_posts table with media support and engagement metrics
 * - Add post_likes table for tracking post likes with user-scoped filtering
 * - Add post_comments table with nested replies support
 * - Add feed_cache table for personalized feed performance optimization
 * - Add saved_posts table for users to bookmark workouts
 * - Performance indexes for feed queries targeting <500ms pagination
 * 
 * Security Impact:
 * - All feed queries MUST include user_id filtering to prevent data leakage
 * - Privacy controls enforced through visibility settings (PUBLIC, FOLLOWERS, PRIVATE)
 * - FOREIGN KEY constraints ensure data integrity with cascade deletes
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            // ========================================
            // Social Feed and Engagement Tables
            // ========================================
            
            // Workout posts with media support and engagement metrics
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS workout_posts (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    workout_id TEXT NOT NULL,
                    
                    -- Content
                    caption TEXT,
                    media_urls TEXT, -- JSON array of photo/video URLs
                    media_thumbnails TEXT, -- JSON array of thumbnail URLs
                    
                    -- Metadata
                    workout_duration INTEGER,
                    total_volume REAL,
                    exercises_count INTEGER,
                    prs_count INTEGER DEFAULT 0,
                    
                    -- Engagement metrics
                    like_count INTEGER DEFAULT 0,
                    comment_count INTEGER DEFAULT 0,
                    share_count INTEGER DEFAULT 0,
                    save_count INTEGER DEFAULT 0,
                    
                    -- Visibility
                    visibility TEXT DEFAULT 'FOLLOWERS' CHECK(visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
                    
                    -- Timestamps
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    sync_version INTEGER DEFAULT 0,
                    
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (workout_id) REFERENCES workouts(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Post interactions - likes
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS post_likes (
                    id TEXT PRIMARY KEY,
                    post_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    
                    UNIQUE(post_id, user_id),
                    FOREIGN KEY (post_id) REFERENCES workout_posts(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Comments with nesting support
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS post_comments (
                    id TEXT PRIMARY KEY,
                    post_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    
                    -- Content
                    content TEXT NOT NULL,
                    reply_to_comment_id TEXT, -- For nested replies
                    
                    -- Metadata
                    like_count INTEGER DEFAULT 0,
                    is_edited BOOLEAN DEFAULT FALSE,
                    
                    -- Timestamps
                    created_at INTEGER NOT NULL,
                    edited_at INTEGER,
                    updated_at INTEGER NOT NULL,
                    
                    -- Sync metadata
                    is_synced BOOLEAN DEFAULT FALSE,
                    sync_version INTEGER DEFAULT 0,
                    
                    FOREIGN KEY (post_id) REFERENCES workout_posts(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (reply_to_comment_id) REFERENCES post_comments(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Feed cache for performance optimization
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS feed_cache (
                    user_id TEXT NOT NULL,
                    post_id TEXT NOT NULL,
                    score REAL NOT NULL, -- Relevance score for ordering
                    fetched_at INTEGER NOT NULL,
                    
                    PRIMARY KEY (user_id, post_id),
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (post_id) REFERENCES workout_posts(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Saved workouts for later reference
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS saved_posts (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    post_id TEXT NOT NULL,
                    saved_at INTEGER NOT NULL,
                    
                    UNIQUE(user_id, post_id),
                    FOREIGN KEY (user_id) REFERENCES social_profiles(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (post_id) REFERENCES workout_posts(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // ========================================
            // Performance Indexes for Feed Queries
            // ========================================
            
            // Index for workout posts by user and creation time (user profile feeds)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workout_posts_user_created 
                ON workout_posts(user_id, created_at DESC)
            """.trimIndent())
            
            // Index for workout posts by visibility and creation time (public/discovery feeds)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workout_posts_visibility 
                ON workout_posts(visibility, created_at DESC)
            """.trimIndent())
            
            // Index for post likes by post (engagement display)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_post_likes_post 
                ON post_likes(post_id)
            """.trimIndent())
            
            // Index for post comments by post and creation time (comment threads)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_post_comments_post 
                ON post_comments(post_id, created_at)
            """.trimIndent())
            
            // Index for feed cache by user and relevance score (personalized feeds)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_feed_cache_user_score 
                ON feed_cache(user_id, score DESC)
            """.trimIndent())
            
            // Index for saved posts by user and save time (saved workouts view)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_saved_posts_user_date 
                ON saved_posts(user_id, saved_at DESC)
            """.trimIndent())
            
            // ========================================
            // Migration Analytics Logging
            // ========================================
            
            // Log migration completion for monitoring
            database.execSQL("""
                INSERT OR REPLACE INTO analytics_cache (
                    id, user_id, calculation_type, result, timestamp
                ) VALUES (
                    'migration_44_45_social_feed_engagement', 
                    'system', 
                    'feed_migration', 
                    '{"migration": "44_45", "action": "create_feed_tables", "tables": ["workout_posts", "post_likes", "post_comments", "feed_cache", "saved_posts"], "performance_targets": {"feed_load": "<2s", "pagination": "<500ms", "engagement_updates": "<100ms"}, "timestamp": ' || (strftime('%s', 'now') * 1000) || '}',
                    strftime('%s', 'now') * 1000
                )
            """.trimIndent())
            
            database.setTransactionSuccessful()
        } catch (e: SQLException) {
            throw RuntimeException("Migration 44->45 failed: Social feed and engagement tables", e)
        } finally {
            database.endTransaction()
        }
    }
}