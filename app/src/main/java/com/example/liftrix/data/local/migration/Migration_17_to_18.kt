package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Migration from version 17 to 18
 * 
 * DEFINITIVE FIX: Ensures friends table has correct schema
 * 
 * This migration takes a simple, reliable approach:
 * 1. Back up any existing friends data
 * 2. Drop and recreate friends table with correct schema
 * 3. Restore the data
 * 
 * This guarantees the table matches Room's expected schema regardless of previous state.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Log.d("Migration_17_18", "Starting definitive friends table schema fix")
        
        try {
            // Fix friends table schema definitively
            fixFriendsTableSchema(database)
            
            Log.d("Migration_17_18", "Migration completed successfully")
        } catch (e: Exception) {
            Log.e("Migration_17_18", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Definitively fixes friends table schema by recreating it
     */
    private fun fixFriendsTableSchema(database: SupportSQLiteDatabase) {
        Log.d("Migration_17_18", "Applying definitive friends table schema fix")
        
        // Check if friends table exists
        val friendsTableExists = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='friends'"
        ).use { cursor -> cursor.moveToFirst() }
        
        if (friendsTableExists) {
            // Backup existing data
            database.execSQL("""
                CREATE TEMPORARY TABLE friends_backup AS 
                SELECT user_id, friend_user_id, status, created_at, updated_at, 
                       COALESCE(is_synced, 0) as is_synced 
                FROM friends
            """.trimIndent())
            
            // Drop existing table
            database.execSQL("DROP TABLE friends")
        }
        
        // Create friends table with definitive correct schema
        database.execSQL("""
            CREATE TABLE friends (
                user_id TEXT NOT NULL,
                friend_user_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (user_id, friend_user_id)
            )
        """.trimIndent())
        
        // Restore data if we backed it up
        if (friendsTableExists) {
            database.execSQL("""
                INSERT INTO friends (user_id, friend_user_id, status, created_at, updated_at, is_synced)
                SELECT user_id, friend_user_id, status, 
                       CASE 
                           WHEN created_at LIKE '%-%-%T%:%:%Z' OR created_at LIKE '%-%-%T%:%:%.%Z' 
                           THEN created_at 
                           ELSE datetime(CAST(created_at AS INTEGER), 'unixepoch') || 'Z'
                       END as created_at,
                       CASE 
                           WHEN updated_at LIKE '%-%-%T%:%:%Z' OR updated_at LIKE '%-%-%T%:%:%.%Z' 
                           THEN updated_at 
                           ELSE datetime(CAST(updated_at AS INTEGER), 'unixepoch') || 'Z'
                       END as updated_at,
                       is_synced
                FROM friends_backup
            """.trimIndent())
            
            // Drop backup table
            database.execSQL("DROP TABLE friends_backup")
            Log.d("Migration_17_18", "Restored existing friends data")
        }
        
        // Create indexes
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_user_id ON friends(user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_friend_user_id ON friends(friend_user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_status ON friends(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_created_at ON friends(created_at)")
        
        Log.d("Migration_17_18", "Friends table schema definitively fixed")
    }
} 