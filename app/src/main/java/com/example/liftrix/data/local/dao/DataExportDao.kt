package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.DataExportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataExportDao {
    
    @Query("SELECT * FROM data_exports WHERE user_id = :userId ORDER BY requested_at DESC")
    fun getAllExportsForUser(userId: String): Flow<List<DataExportEntity>>
    
    @Query("SELECT * FROM data_exports WHERE export_id = :exportId AND user_id = :userId")
    suspend fun getExportByIdForUser(exportId: String, userId: String): DataExportEntity?
    
    @Query("SELECT * FROM data_exports WHERE user_id = :userId AND status = :status ORDER BY requested_at DESC")
    fun getExportsByStatusForUser(userId: String, status: String): Flow<List<DataExportEntity>>
    
    @Query("SELECT * FROM data_exports WHERE user_id = :userId AND status IN ('REQUESTED', 'IN_PROGRESS') ORDER BY requested_at ASC")
    suspend fun getActiveExportsForUser(userId: String): List<DataExportEntity>
    
    @Query("SELECT * FROM data_exports WHERE user_id = :userId AND status = 'COMPLETED' AND expires_at > :currentTime ORDER BY completed_at DESC")
    suspend fun getValidCompletedExportsForUser(userId: String, currentTime: Long): List<DataExportEntity>
    
    @Query("SELECT * FROM data_exports WHERE expires_at <= :currentTime AND status = 'COMPLETED'")
    suspend fun getExpiredExports(currentTime: Long): List<DataExportEntity>
    
    @Query("SELECT * FROM data_exports WHERE is_synced = 0 AND user_id = :userId ORDER BY requested_at ASC")
    suspend fun getUnsyncedExportsForUser(userId: String): List<DataExportEntity>
    
    @Query("DELETE FROM data_exports WHERE export_id = :exportId AND user_id = :userId")
    suspend fun deleteExportForUser(exportId: String, userId: String)
    
    @Query("DELETE FROM data_exports WHERE expires_at <= :currentTime AND status = 'COMPLETED'")
    suspend fun deleteExpiredExports(currentTime: Long)
    
    @Query("UPDATE data_exports SET status = :status, error_message = :errorMessage WHERE export_id = :exportId AND user_id = :userId")
    suspend fun updateExportStatus(exportId: String, userId: String, status: String, errorMessage: String? = null)
    
    @Query("UPDATE data_exports SET status = 'COMPLETED', file_uri = :fileUri, file_size_bytes = :fileSizeBytes, record_count = :recordCount, completed_at = :completedAt WHERE export_id = :exportId AND user_id = :userId")
    suspend fun markExportCompleted(
        exportId: String,
        userId: String,
        fileUri: String,
        fileSizeBytes: Long,
        recordCount: Int,
        completedAt: Long
    )
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: DataExportEntity)
    
    @Update
    suspend fun updateExport(export: DataExportEntity)

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert dataexport from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(dataExport: DataExportEntity) {
        val entity = dataExport.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert dataexport from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(dataExport: DataExportEntity) {
        val local = getDataExportForSync(dataExport.exportId, dataExport.userId)
        if (local == null || dataExport.lastModified > local.lastModified) {
            val entity = dataExport.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis().toInt()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: DataExportEntity)

    /**
     * Get dirty dataexport that need upload to Firestore.
     */
    @Query("SELECT * FROM data_exports WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyDataExports(userId: String): List<DataExportEntity>

    /**
     * Mark dataexport as clean after successful Firestore upload.
     */
    @Query("UPDATE data_exports SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE export_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local dataexport for remote deduplication.
     */
    @Query("SELECT * FROM data_exports WHERE export_id = :id AND user_id = :userId LIMIT 1")
    suspend fun getDataExportForSync(id: String, userId: String): DataExportEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
