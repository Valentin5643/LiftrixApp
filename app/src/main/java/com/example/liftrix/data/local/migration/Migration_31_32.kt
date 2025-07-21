package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.liftrix.domain.model.WeightUnit
import java.util.Locale

/**
 * Migration from database version 31 to 32
 * Adds weight_unit column to user_settings table with proper default values
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add weight_unit column to user_settings table
        // Default to system locale preference (kg for most countries, lbs for US/Myanmar/Liberia)
        val defaultWeightUnit = when (Locale.getDefault().country) {
            "US", "MM", "LR" -> "lbs"
            else -> "kg"
        }
        
        database.execSQL(
            "ALTER TABLE user_settings ADD COLUMN weight_unit TEXT NOT NULL DEFAULT '$defaultWeightUnit'"
        )
    }
}