package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create friends table with composite primary key
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS friends (
                user_id TEXT NOT NULL,
                friend_user_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (user_id, friend_user_id)
            )
        """.trimIndent())
        
        // Create user privacy settings table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS user_privacy_settings (
                user_id TEXT PRIMARY KEY NOT NULL,
                online_status_visibility TEXT NOT NULL DEFAULT 'ALL_FRIENDS',
                workout_sharing_default TEXT NOT NULL DEFAULT 'ASK_EACH_TIME',
                allow_friend_requests INTEGER NOT NULL DEFAULT 1,
                updated_at TEXT NOT NULL
            )
        """.trimIndent())
        
        // Create indexes for performance
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_user_id ON friends(user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_friend_user_id ON friends(friend_user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_status ON friends(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_created_at ON friends(created_at)")
    }
}