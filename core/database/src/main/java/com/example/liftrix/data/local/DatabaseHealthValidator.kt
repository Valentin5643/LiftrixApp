package com.example.liftrix.data.local

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates database health and logs any unexpected migration activity during runtime
 */
@Singleton
class DatabaseHealthValidator @Inject constructor(
    private val database: LiftrixDatabase
) {
    
    private var isInitialized = false
    private var initialVersion: Int = -1
    
    /**
     * Call this once after database initialization to establish baseline
     */
    suspend fun initialize() {
        if (isInitialized) return
        
        try {
            initialVersion = database.openHelper.readableDatabase.version
            Timber.i("🩺 Database health baseline established at version: $initialVersion")
            isInitialized = true
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to establish database health baseline")
        }
    }
    
    /**
     * Validates that database version hasn't changed unexpectedly during runtime
     */
    suspend fun validateStability() {
        if (!isInitialized) {
            Timber.w("⚠️ Database health validator not initialized")
            return
        }
        
        try {
            val currentVersion = database.openHelper.readableDatabase.version
            if (currentVersion != initialVersion) {
                Timber.e("🚨 UNEXPECTED DATABASE VERSION CHANGE: $initialVersion → $currentVersion")
                Timber.e("This indicates unwanted migration activity during runtime!")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Database health validation failed")
        }
    }
    
    /**
     * Logs database connection activity (for debugging only)
     */
    fun logConnectionActivity(operation: String) {
        if (isInitialized) {
            Timber.v("🔗 DB Connection: $operation (version should remain $initialVersion)")
        }
    }
}