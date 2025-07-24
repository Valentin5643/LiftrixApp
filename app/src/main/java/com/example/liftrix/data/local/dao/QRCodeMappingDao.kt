package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.liftrix.data.local.entity.QRCodeMappingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for QR code mapping operations
 * 
 * Provides methods to store, retrieve, and manage QR code mappings to user profiles.
 * All operations maintain user scoping and support expiration handling.
 */
@Dao
interface QRCodeMappingDao {
    
    /**
     * Insert a new QR code mapping
     * @param mapping The QR code mapping to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(mapping: QRCodeMappingEntity)
    
    /**
     * Insert multiple QR code mappings
     * @param mappings List of QR code mappings to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(mappings: List<QRCodeMappingEntity>)
    
    /**
     * Update an existing QR code mapping
     * @param mapping The QR code mapping to update
     */
    @Update
    suspend fun update(mapping: QRCodeMappingEntity)
    
    /**
     * Get QR code mapping by QR code ID
     * @param qrCodeId The QR code ID to look up
     * @return QR code mapping or null if not found or expired
     */
    @Query("SELECT * FROM qr_code_mappings WHERE qr_code_id = :qrCodeId AND is_active = 1 AND (expires_at IS NULL OR expires_at > datetime('now')) LIMIT 1")
    suspend fun getMapping(qrCodeId: String): QRCodeMappingEntity?
    
    /**
     * Get QR code mapping by QR code ID as Flow
     * @param qrCodeId The QR code ID to look up
     * @return Flow of QR code mapping or null if not found or expired
     */
    @Query("SELECT * FROM qr_code_mappings WHERE qr_code_id = :qrCodeId AND is_active = 1 AND (expires_at IS NULL OR expires_at > datetime('now')) LIMIT 1")
    fun getMappingFlow(qrCodeId: String): Flow<QRCodeMappingEntity?>
    
    /**
     * Get all QR code mappings for a user
     * @param userId User ID to filter by
     * @return Flow of all QR code mappings for the user
     */
    @Query("SELECT * FROM qr_code_mappings WHERE user_id = :userId ORDER BY created_at DESC")
    fun getAllMappingsForUser(userId: String): Flow<List<QRCodeMappingEntity>>
    
    /**
     * Get all active QR code mappings for a user
     * @param userId User ID to filter by
     * @return Flow of all active QR code mappings for the user
     */
    @Query("SELECT * FROM qr_code_mappings WHERE user_id = :userId AND is_active = 1 AND (expires_at IS NULL OR expires_at > datetime('now')) ORDER BY created_at DESC")
    fun getActiveMappingsForUser(userId: String): Flow<List<QRCodeMappingEntity>>
    
    /**
     * Get user ID by QR code ID (for resolving scanned codes)
     * @param qrCodeId The QR code ID to resolve
     * @return User ID or null if mapping not found or expired
     */
    @Query("SELECT user_id FROM qr_code_mappings WHERE qr_code_id = :qrCodeId AND is_active = 1 AND (expires_at IS NULL OR expires_at > datetime('now')) LIMIT 1")
    suspend fun getUserIdByQRCode(qrCodeId: String): String?
    
    /**
     * Increment usage count for a QR code
     * @param qrCodeId The QR code ID to update
     * @return Number of updated rows
     */
    @Query("UPDATE qr_code_mappings SET usage_count = usage_count + 1 WHERE qr_code_id = :qrCodeId")
    suspend fun incrementUsageCount(qrCodeId: String): Int
    
    /**
     * Deactivate a QR code mapping
     * @param qrCodeId The QR code ID to deactivate
     * @return Number of updated rows
     */
    @Query("UPDATE qr_code_mappings SET is_active = 0 WHERE qr_code_id = :qrCodeId")
    suspend fun deactivateMapping(qrCodeId: String): Int
    
    /**
     * Reactivate a QR code mapping
     * @param qrCodeId The QR code ID to reactivate
     * @return Number of updated rows
     */
    @Query("UPDATE qr_code_mappings SET is_active = 1 WHERE qr_code_id = :qrCodeId")
    suspend fun reactivateMapping(qrCodeId: String): Int
    
    /**
     * Delete a specific QR code mapping
     * @param mapping The QR code mapping to delete
     */
    @Delete
    suspend fun delete(mapping: QRCodeMappingEntity)
    
    /**
     * Delete QR code mapping by ID
     * @param qrCodeId The QR code ID to delete
     * @return Number of deleted rows
     */
    @Query("DELETE FROM qr_code_mappings WHERE qr_code_id = :qrCodeId")
    suspend fun deleteById(qrCodeId: String): Int
    
    /**
     * Delete all QR code mappings for a user
     * @param userId User ID to filter by
     * @return Number of deleted rows
     */
    @Query("DELETE FROM qr_code_mappings WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String): Int
    
    /**
     * Delete expired QR code mappings for all users
     * @return Number of deleted rows
     */
    @Query("DELETE FROM qr_code_mappings WHERE expires_at IS NOT NULL AND expires_at <= datetime('now')")
    suspend fun deleteExpiredMappings(): Int
    
    /**
     * Delete expired QR code mappings for a specific user
     * @param userId User ID to filter by
     * @return Number of deleted rows
     */
    @Query("DELETE FROM qr_code_mappings WHERE user_id = :userId AND expires_at IS NOT NULL AND expires_at <= datetime('now')")
    suspend fun deleteExpiredMappingsForUser(userId: String): Int
    
    /**
     * Delete old QR code mappings (keeping only the most recent N per user)
     * @param userId User ID to filter by
     * @param maxMappings Maximum number of mappings to keep per user
     * @return Number of deleted rows
     */
    @Query("""
        DELETE FROM qr_code_mappings 
        WHERE user_id = :userId 
        AND qr_code_id NOT IN (
            SELECT qr_code_id FROM qr_code_mappings 
            WHERE user_id = :userId 
            ORDER BY created_at DESC 
            LIMIT :maxMappings
        )
    """)
    suspend fun cleanupOldMappings(userId: String, maxMappings: Int): Int
    
    /**
     * Get count of QR code mappings for a user
     * @param userId User ID to filter by
     * @return Flow of count of QR code mappings
     */
    @Query("SELECT COUNT(*) FROM qr_code_mappings WHERE user_id = :userId")
    fun getMappingCount(userId: String): Flow<Int>
    
    /**
     * Get count of active QR code mappings for a user
     * @param userId User ID to filter by
     * @return Flow of count of active QR code mappings
     */
    @Query("SELECT COUNT(*) FROM qr_code_mappings WHERE user_id = :userId AND is_active = 1 AND (expires_at IS NULL OR expires_at > datetime('now'))")
    fun getActiveMappingCount(userId: String): Flow<Int>
    
    /**
     * Check if a QR code mapping exists and is active
     * @param qrCodeId The QR code ID to check
     * @return True if mapping exists and is active, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM qr_code_mappings WHERE qr_code_id = :qrCodeId AND is_active = 1 AND (expires_at IS NULL OR expires_at > datetime('now'))")
    suspend fun isValidMapping(qrCodeId: String): Boolean
}