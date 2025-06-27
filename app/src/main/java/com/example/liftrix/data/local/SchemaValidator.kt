package com.example.liftrix.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Schema validation utility to prevent database migration failures.
 */
object SchemaValidator {
    
    private const val TAG = "SchemaValidator"
    
    fun validateSchema(database: SupportSQLiteDatabase): List<SchemaIssue> {
        val issues = mutableListOf<SchemaIssue>()
        
        try {
            Log.d(TAG, "Starting comprehensive schema validation")
            
            // Validate all critical tables
            issues.addAll(validateWorkoutsTable(database))
            
            Log.d(TAG, "Schema validation completed. Found ${issues.size} issues")
            
        } catch (e: Exception) {
            Log.e(TAG, "Schema validation failed: ${e.message}", e)
            issues.add(SchemaIssue.ValidationError("Schema validation failed: ${e.message}"))
        }
        
        return issues
    }
    
    private fun validateWorkoutsTable(database: SupportSQLiteDatabase): List<SchemaIssue> {
        val issues = mutableListOf<SchemaIssue>()
        val tableName = "workouts"
        
        if (!tableExists(database, tableName)) {
            issues.add(SchemaIssue.MissingTable(tableName))
            return issues
        }
        
        val columns = getTableColumns(database, tableName)
        val columnTypes = getTableColumnTypes(database, tableName)
        
        val expectedColumns = mapOf(
            "id" to "TEXT",
            "user_id" to "TEXT",
            "name" to "TEXT",
            "date" to "TEXT",
            "exercises_json" to "TEXT",
            "status" to "TEXT",
            "created_at" to "TEXT",
            "updated_at" to "TEXT"
        )
        
        expectedColumns.keys.forEach { expectedColumn ->
            if (!columns.contains(expectedColumn)) {
                issues.add(SchemaIssue.MissingColumn(tableName, expectedColumn))
            }
        }
        
        expectedColumns.forEach { (columnName, expectedType) ->
            val actualType = columnTypes[columnName]
            if (actualType != null && !actualType.contains(expectedType)) {
                issues.add(SchemaIssue.TypeMismatch(tableName, columnName, expectedType, actualType))
            }
        }
        
        return issues
    }
    
    
    private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean {
        return try {
            database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
                cursor.moveToFirst()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getTableColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
        return try {
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(nameIndex))
                }
                columns
            }
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    private fun getTableColumnTypes(database: SupportSQLiteDatabase, tableName: String): Map<String, String> {
        return try {
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                val columnTypes = mutableMapOf<String, String>()
                while (cursor.moveToNext()) {
                    columnTypes[cursor.getString(nameIndex)] = cursor.getString(typeIndex)
                }
                columnTypes
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

sealed class SchemaIssue {
    data class MissingTable(val tableName: String) : SchemaIssue()
    data class MissingColumn(val tableName: String, val columnName: String) : SchemaIssue()
    data class TypeMismatch(val tableName: String, val columnName: String, val expectedType: String, val actualType: String) : SchemaIssue()
    data class ValidationError(val message: String) : SchemaIssue()
    
    override fun toString(): String {
        return when (this) {
            is MissingTable -> "Missing table: $tableName"
            is MissingColumn -> "Missing column: $tableName.$columnName"
            is TypeMismatch -> "Type mismatch: $tableName.$columnName (expected: $expectedType, actual: $actualType)"
            is ValidationError -> "Validation error: $message"
        }
    }
}
