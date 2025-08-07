package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 38 to 39
 * Fixes critical foreign key constraint issue causing template deletion when folders are deleted
 * 
 * Root Cause:
 * - WorkoutTemplateEntity has CASCADE DELETE constraint on folder_id
 * - DeleteFolderUseCase tries to move templates but CASCADE DELETE removes them instead
 * - Results in "Failed to load workouts" error after folder deletion
 * 
 * Changes:
 * - Removes CASCADE DELETE foreign key constraint between workout_templates and folders
 * - Allows templates to exist with NULL folder_id (orphaned templates go to default folder)
 * - Preserves data integrity while allowing business logic to handle template preservation
 * 
 * Business Impact:
 * - Fixes folder deletion causing complete template loss
 * - Templates are properly moved to default folder instead of being deleted
 * - Eliminates "Failed to load workouts" error after folder operations
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // ========================================
        // Fix Foreign Key Constraint Issue
        // ========================================
        
        // Step 1: Create new workout_templates table without CASCADE DELETE constraint
        database.execSQL("""
            CREATE TABLE workout_templates_new (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                template_exercises_json TEXT NOT NULL,
                estimated_duration_minutes INTEGER,
                difficulty_level INTEGER,
                folder_id TEXT,
                usage_count INTEGER NOT NULL DEFAULT 0,
                last_used_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Step 2: Copy all data from old table to new table
        database.execSQL("""
            INSERT INTO workout_templates_new 
            SELECT id, user_id, name, description, template_exercises_json, 
                   estimated_duration_minutes, difficulty_level, folder_id, 
                   usage_count, last_used_at, created_at, updated_at, 
                   is_synced, sync_version
            FROM workout_templates
        """.trimIndent())
        
        // Step 3: Drop the old table
        database.execSQL("DROP TABLE workout_templates")
        
        // Step 4: Rename new table to original name
        database.execSQL("ALTER TABLE workout_templates_new RENAME TO workout_templates")
        
        // Step 5: Recreate indexes for workout_templates table
        database.execSQL("""
            CREATE UNIQUE INDEX index_workout_templates_name_user_id 
            ON workout_templates(name, user_id)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_workout_templates_user_id 
            ON workout_templates(user_id)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_workout_templates_created_at 
            ON workout_templates(created_at)
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX index_workout_templates_folder_id 
            ON workout_templates(folder_id)
        """.trimIndent())
        
        // Step 6: Add index for usage queries
        database.execSQL("""
            CREATE INDEX index_workout_templates_usage 
            ON workout_templates(user_id, usage_count DESC, last_used_at DESC)
        """.trimIndent())
        
        // Step 7: Add index for folder filtering queries (performance optimization)
        database.execSQL("""
            CREATE INDEX index_workout_templates_user_folder 
            ON workout_templates(user_id, folder_id)
        """.trimIndent())
        
        // ========================================
        // Handle Orphaned Templates (Optional Cleanup)
        // ========================================
        
        // Find templates that reference non-existent folders and set them to NULL
        // These will be handled by business logic to move to default folder
        database.execSQL("""
            UPDATE workout_templates 
            SET folder_id = NULL 
            WHERE folder_id IS NOT NULL 
            AND folder_id NOT IN (SELECT id FROM folders)
        """.trimIndent())
        
        // Log the fix for debugging
        database.execSQL("""
            INSERT OR IGNORE INTO analytics_cache (
                id, user_id, cache_key, cache_data, expires_at, created_at
            ) VALUES (
                'migration_38_39_fix', 
                'system', 
                'folder_constraint_fix', 
                '{"migration": "38_39", "fix": "removed_cascade_delete", "timestamp": ' || (strftime('%s', 'now') * 1000) || '}',
                (strftime('%s', 'now') + 86400) * 1000,
                strftime('%s', 'now') * 1000
            )
        """.trimIndent())
    }
}