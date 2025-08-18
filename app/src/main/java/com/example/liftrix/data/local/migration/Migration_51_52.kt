package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 51 to 52 of the Liftrix database.
 * 
 * Changes:
 * - Adds data_exports table for export tracking and management
 * - Adds data_imports table for import tracking and validation
 * 
 * This migration adds the data portability system as specified in 
 * SPEC-20250116-data-portability, enabling users to export/import workout data.
 */
val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create data_exports table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `data_exports` (
                `export_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `export_type` TEXT NOT NULL,
                `data_types` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'REQUESTED',
                `file_uri` TEXT,
                `file_size_bytes` INTEGER,
                `record_count` INTEGER,
                `date_range_start` INTEGER,
                `date_range_end` INTEGER,
                `requested_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                `expires_at` INTEGER,
                `error_message` TEXT,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `sync_version` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`export_id`)
            )
        """.trimIndent())
        
        // Create data_imports table  
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `data_imports` (
                `import_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `source_format` TEXT NOT NULL,
                `source_app` TEXT,
                `status` TEXT NOT NULL DEFAULT 'VALIDATING',
                `total_records` INTEGER,
                `imported_records` INTEGER,
                `skipped_records` INTEGER,
                `conflict_resolution` TEXT,
                `validation_errors` TEXT,
                `started_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                `rollback_available` INTEGER DEFAULT 1,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`import_id`)
            )
        """.trimIndent())
        
        // Create indices for data_exports table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_data_exports_user_id_status` 
            ON `data_exports` (`user_id`, `status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_data_exports_requested_at` 
            ON `data_exports` (`requested_at`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_data_exports_expires_at` 
            ON `data_exports` (`expires_at`)
        """.trimIndent())
        
        // Create indices for data_imports table
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_data_imports_user_id_status` 
            ON `data_imports` (`user_id`, `status`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_data_imports_started_at` 
            ON `data_imports` (`started_at`)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_data_imports_rollback_available` 
            ON `data_imports` (`rollback_available`)
        """.trimIndent())
    }
}