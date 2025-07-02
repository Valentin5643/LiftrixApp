package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 19 to 20.
 * 
 * This is a no-op migration since the database schemas for version 19 and 20 
 * are identical (same identityHash: 921a6f6dbdf9fbee72284fec739dd315).
 * No actual schema changes are required.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No-op migration - schemas are identical between version 19 and 20
        // Room requires this migration object even when no changes are needed
    }
} 