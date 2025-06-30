package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Migration from version 18 to 19
 * 
 * FOREIGN KEY CONSTRAINT REMOVAL: Fixes schema mismatch
 * 
 * Room doesn't expect FOREIGN KEY constraints in the schema definition.
 * This migration removes them to match Room's expected schema exactly.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Log.d("Migration_18_19", "Starting FOREIGN KEY constraint removal")
        
        try {
            // Fix friends table - remove FOREIGN KEY constraint
            fixFriendsTable(database)
            
            // Fix user_privacy_settings table - remove FOREIGN KEY constraint
            fixPrivacySettingsTable(database)
            
            Log.d("Migration_18_19", "Migration completed successfully")
        } catch (e: Exception) {
            Log.e("Migration_18_19", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    private fun fixFriendsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_18_19", "Fixing friends table schema")
        
        // Check if friends table exists
        val friendsTableExists = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='friends'"
        ).use { cursor -> cursor.moveToFirst() }
        
        if (friendsTableExists) {
            // Backup existing data
            database.execSQL("""
                CREATE TEMPORARY TABLE friends_backup AS 
                SELECT user_id, friend_user_id, status, created_at, updated_at, is_synced 
                FROM friends
            """.trimIndent())
            
            // Drop existing table
            database.execSQL("DROP TABLE friends")
        }
        
        // Create friends table without FOREIGN KEY constraint
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
                SELECT user_id, friend_user_id, status, created_at, updated_at, is_synced
                FROM friends_backup
            """.trimIndent())
            
            // Drop backup table
            database.execSQL("DROP TABLE friends_backup")
            Log.d("Migration_18_19", "Restored friends data")
        }
        
        // Recreate indexes
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_user_id ON friends(user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_friend_user_id ON friends(friend_user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_status ON friends(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_created_at ON friends(created_at)")
    }
    
    private fun fixPrivacySettingsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_18_19", "Fixing user_privacy_settings table schema")
        
        // Check if table exists
        val tableExists = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='user_privacy_settings'"
        ).use { cursor -> cursor.moveToFirst() }
        
        if (tableExists) {
            // Backup existing data
            database.execSQL("""
                CREATE TEMPORARY TABLE user_privacy_settings_backup AS 
                SELECT user_id, online_status_visibility, workout_sharing_default, allow_friend_requests, updated_at
                FROM user_privacy_settings
            """.trimIndent())
            
            // Drop existing table
            database.execSQL("DROP TABLE user_privacy_settings")
        }
        
        // Create table without FOREIGN KEY constraint
        database.execSQL("""
            CREATE TABLE user_privacy_settings (
                user_id TEXT PRIMARY KEY NOT NULL,
                online_status_visibility TEXT NOT NULL DEFAULT 'ALL_FRIENDS',
                workout_sharing_default TEXT NOT NULL DEFAULT 'ASK_EACH_TIME',
                allow_friend_requests INTEGER NOT NULL DEFAULT 1,
                updated_at TEXT NOT NULL
            )
        """.trimIndent())
        
        // Restore data if we backed it up
        if (tableExists) {
            database.execSQL("""
                INSERT INTO user_privacy_settings (user_id, online_status_visibility, workout_sharing_default, allow_friend_requests, updated_at)
                SELECT user_id, online_status_visibility, workout_sharing_default, allow_friend_requests, updated_at
                FROM user_privacy_settings_backup
            """.trimIndent())
            
            // Drop backup table
            database.execSQL("DROP TABLE user_privacy_settings_backup")
            Log.d("Migration_18_19", "Restored privacy settings data")
        }
    }
} 