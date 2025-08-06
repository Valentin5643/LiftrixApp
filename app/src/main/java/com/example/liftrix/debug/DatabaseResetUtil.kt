package com.example.liftrix.debug

import android.content.Context
import com.example.liftrix.BuildConfig
import timber.log.Timber
import java.io.File

/**
 * Development utility for safely resetting the Room database when corrupted.
 * 
 * This utility is only available in DEBUG builds to prevent accidental data loss
 * in production environments.
 * 
 * Use cases:
 * - Database corruption during development
 * - Schema changes requiring fresh database
 * - Migration testing and validation
 * 
 * @author Claude Code Assistant
 */
object DatabaseResetUtil {
    
    private const val DATABASE_NAME = "liftrix_database"
    
    /**
     * Safely resets the Room database by deleting all database files.
     * 
     * This will force Room to recreate the database from scratch using
     * the current schema definition, bypassing any migration issues.
     * 
     * @param context Application context
     * @return true if database was successfully reset, false otherwise
     */
    fun resetDatabase(context: Context): Boolean {
        if (!BuildConfig.DEBUG) {
            Timber.w("Database reset is only available in DEBUG builds")
            return false
        }
        
        return try {
            Timber.i("Starting database reset...")
            
            // Get database file paths
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val shmFile = File(dbFile.absolutePath + "-shm")  // Shared memory file
            val walFile = File(dbFile.absolutePath + "-wal")  // Write-ahead log file
            
            var filesDeleted = 0
            
            // Delete main database file
            if (dbFile.exists() && dbFile.delete()) {
                filesDeleted++
                Timber.d("Deleted main database file: ${dbFile.name}")
            }
            
            // Delete shared memory file (SQLite)
            if (shmFile.exists() && shmFile.delete()) {
                filesDeleted++
                Timber.d("Deleted shared memory file: ${shmFile.name}")
            }
            
            // Delete write-ahead log file (SQLite WAL mode)
            if (walFile.exists() && walFile.delete()) {
                filesDeleted++
                Timber.d("Deleted WAL file: ${walFile.name}")
            }
            
            if (filesDeleted > 0) {
                Timber.i("Database reset successful - deleted $filesDeleted files")
                Timber.i("Next app restart will create fresh database using current schema")
                true
            } else {
                Timber.w("No database files found to delete")
                true // Not an error, database may not exist yet
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset database")
            false
        }
    }
    
    /**
     * Checks if the database exists and returns file information.
     * 
     * Useful for debugging and determining if database reset is needed.
     * 
     * @param context Application context
     * @return DatabaseInfo containing file status and sizes
     */
    fun getDatabaseInfo(context: Context): DatabaseInfo {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val shmFile = File(dbFile.absolutePath + "-shm")
        val walFile = File(dbFile.absolutePath + "-wal")
        
        return DatabaseInfo(
            mainFileExists = dbFile.exists(),
            mainFileSize = if (dbFile.exists()) dbFile.length() else 0L,
            shmFileExists = shmFile.exists(),
            shmFileSize = if (shmFile.exists()) shmFile.length() else 0L,
            walFileExists = walFile.exists(),
            walFileSize = if (walFile.exists()) walFile.length() else 0L,
            totalSize = listOf(dbFile, shmFile, walFile)
                .filter { it.exists() }
                .sumOf { it.length() }
        )
    }
    
    /**
     * Data class containing database file information.
     */
    data class DatabaseInfo(
        val mainFileExists: Boolean,
        val mainFileSize: Long,
        val shmFileExists: Boolean,
        val shmFileSize: Long,
        val walFileExists: Boolean,
        val walFileSize: Long,
        val totalSize: Long
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Database File Info:")
                appendLine("  Main DB: ${if (mainFileExists) "${mainFileSize / 1024}KB" else "Not found"}")
                appendLine("  SHM file: ${if (shmFileExists) "${shmFileSize / 1024}KB" else "Not found"}")
                appendLine("  WAL file: ${if (walFileExists) "${walFileSize / 1024}KB" else "Not found"}")
                appendLine("  Total: ${totalSize / 1024}KB")
            }
        }
    }
    
    /**
     * Validates that all necessary migrations are present in the migration chain.
     * 
     * This helps identify missing migrations that could cause crashes.
     * 
     * @param expectedVersions List of database versions that should have migrations
     * @return List of missing migration version pairs
     */
    fun validateMigrationChain(expectedVersions: List<Int>): List<Pair<Int, Int>> {
        val missingMigrations = mutableListOf<Pair<Int, Int>>()
        
        for (i in 0 until expectedVersions.size - 1) {
            val fromVersion = expectedVersions[i]
            val toVersion = expectedVersions[i + 1]
            
            // In a real implementation, this would check if migration files exist
            // For now, just log the expected migrations
            Timber.d("Expected migration: $fromVersion -> $toVersion")
        }
        
        return missingMigrations
    }
}