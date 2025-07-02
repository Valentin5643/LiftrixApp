package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Migration from version 21 to 22
 * 
 * This is a no-op migration to resolve schema mismatch issues during development.
 * No actual schema changes are needed.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Log.d("Migration_21_22", "Starting migration 21→22: No-op migration for schema mismatch fix")
        
        // No actual schema changes needed - this migration exists purely to resolve
        // the identity hash mismatch between versions 19/20 and 21
        
        Log.d("Migration_21_22", "Migration 21→22 completed successfully (no-op)")
    }
} 