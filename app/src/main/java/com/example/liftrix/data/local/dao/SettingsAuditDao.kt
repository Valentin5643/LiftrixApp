package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.SettingsAuditEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for settings audit operations
 * 
 * Provides database access for tracking settings changes
 * for debugging persistence issues. Critical for maintaining
 * user experience when settings don't persist correctly.
 */
@Dao
interface SettingsAuditDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: SettingsAuditEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audits: List<SettingsAuditEntity>)
    
    /**
     * Get audit trail for a specific user, ordered by timestamp descending
     */
    @Query("""
        SELECT * FROM settings_audit 
        WHERE user_id = :userId 
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getAuditTrailForUser(userId: String, limit: Int = 100): List<SettingsAuditEntity>
    
    /**
     * Get audit trail for a specific setting key
     */
    @Query("""
        SELECT * FROM settings_audit 
        WHERE user_id = :userId AND setting_key = :settingKey 
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getAuditTrailForSetting(
        userId: String, 
        settingKey: String, 
        limit: Int = 50
    ): List<SettingsAuditEntity>
    
    /**
     * Get recent audit entries (for debugging current issues)
     */
    @Query("""
        SELECT * FROM settings_audit 
        WHERE timestamp > :sinceTimestamp
        ORDER BY timestamp DESC
    """)
    fun getRecentAudits(sinceTimestamp: Long): Flow<List<SettingsAuditEntity>>
    
    /**
     * Get audit entries by source (USER, SYNC, MIGRATION)
     */
    @Query("""
        SELECT * FROM settings_audit 
        WHERE user_id = :userId AND source = :source 
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getAuditsBySource(
        userId: String, 
        source: String, 
        limit: Int = 50
    ): List<SettingsAuditEntity>
    
    /**
     * Clean old audit entries to prevent table growth
     * Keep only last 1000 entries per user
     */
    @Query("""
        DELETE FROM settings_audit 
        WHERE user_id = :userId 
        AND audit_id NOT IN (
            SELECT audit_id FROM settings_audit 
            WHERE user_id = :userId 
            ORDER BY timestamp DESC 
            LIMIT 1000
        )
    """)
    suspend fun cleanOldAudits(userId: String)
    
    /**
     * Delete all audit entries for a user (for data cleanup)
     */
    @Query("DELETE FROM settings_audit WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}