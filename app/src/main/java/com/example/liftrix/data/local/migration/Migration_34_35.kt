package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 34 to 35
 * Adds terminology migration tracking columns to user_settings table
 * 
 * This migration supports the terminology migration system to track user preferences
 * when transitioning from 'template' terminology to workflow-based language.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add terminology migration columns to user_settings table
        database.execSQL("""
            ALTER TABLE user_settings 
            ADD COLUMN terminology_preference TEXT NOT NULL DEFAULT 'NEW'
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_settings 
            ADD COLUMN migration_completed INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE user_settings 
            ADD COLUMN migration_explanation_seen INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        // For existing users, set migration as pending (completed = false)
        // New users will get default values through SettingsEntity.createDefault()
        database.execSQL("""
            UPDATE user_settings 
            SET migration_completed = 0, 
                migration_explanation_seen = 0,
                terminology_preference = 'NEW'
            WHERE migration_completed IS NULL OR migration_completed = 0
        """.trimIndent())
    }
}