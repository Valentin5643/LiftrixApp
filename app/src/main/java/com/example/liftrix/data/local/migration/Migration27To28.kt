package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 27 to 28
 * 
 * Adds analytics_cache table for performance optimization of analytics calculations
 * and creates composite indexes for efficient analytics queries.
 * 
 * Changes:
 * - Creates analytics_cache table with columns: id, user_id, calculation_type, result, timestamp
 * - Adds composite index on (user_id, calculation_type) for cache lookups
 * - Adds composite index on (user_id, timestamp) for cache expiration queries
 * - Adds composite index on workout table (user_id, date, status) for analytics queries
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create analytics_cache table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS analytics_cache (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                calculation_type TEXT NOT NULL,
                result TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        
        // Create composite index for analytics cache lookups by user and calculation type
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_analytics_cache_user_type 
            ON analytics_cache(user_id, calculation_type)
        """)
        
        // Create composite index for analytics cache expiration queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_analytics_cache_user_timestamp 
            ON analytics_cache(user_id, timestamp)
        """)
        
        // Create composite index on workout table for analytics queries
        // This optimizes queries that filter by user, date range, and status
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_workout_analytics 
            ON workouts(user_id, date, status)
        """)
    }
}