package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
}