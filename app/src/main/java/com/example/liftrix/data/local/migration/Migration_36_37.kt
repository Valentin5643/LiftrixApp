package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 36 to 37
 * Adds profile image management support to user profiles
 * 
 * Changes:
 * - Adds profile_image_url field for Firebase Storage URLs
 * - Adds profile_image_updated_at field for cache invalidation
 * - Adds has_custom_profile_image field to track custom vs default images
 * - Creates performance index for custom image queries
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add profile image URL field for Firebase Storage integration
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN profile_image_url TEXT
        """.trimIndent())
        
        // Add profile image timestamp for cache invalidation
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN profile_image_updated_at TEXT
        """.trimIndent())
        
        // Add flag to track users with custom profile images
        database.execSQL("""
            ALTER TABLE user_profiles 
            ADD COLUMN has_custom_profile_image INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        // Create performance index for custom image queries (as specified in spec)
        database.execSQL("""
            CREATE INDEX index_user_profiles_has_custom_image ON user_profiles(has_custom_profile_image)
        """.trimIndent())
    }
}