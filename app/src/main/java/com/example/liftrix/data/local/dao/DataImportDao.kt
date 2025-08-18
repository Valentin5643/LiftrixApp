package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.DataImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataImportDao {
    
    @Query("SELECT * FROM data_imports WHERE user_id = :userId ORDER BY started_at DESC")
    fun getAllImportsForUser(userId: String): Flow<List<DataImportEntity>>
    
    @Query("SELECT * FROM data_imports WHERE import_id = :importId AND user_id = :userId")
    suspend fun getImportByIdForUser(importId: String, userId: String): DataImportEntity?
    
    @Query("SELECT * FROM data_imports WHERE user_id = :userId AND status = :status ORDER BY started_at DESC")
    fun getImportsByStatusForUser(userId: String, status: String): Flow<List<DataImportEntity>>
    
    @Query("SELECT * FROM data_imports WHERE user_id = :userId AND status IN ('VALIDATING', 'IN_PROGRESS') ORDER BY started_at ASC")
    suspend fun getActiveImportsForUser(userId: String): List<DataImportEntity>
    
    @Query("SELECT * FROM data_imports WHERE user_id = :userId AND rollback_available = 1 AND status = 'COMPLETED' ORDER BY completed_at DESC")
    suspend fun getRollbackableImportsForUser(userId: String): List<DataImportEntity>
    
    @Query("SELECT * FROM data_imports WHERE user_id = :userId AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT :limit")
    suspend fun getRecentCompletedImportsForUser(userId: String, limit: Int = 10): List<DataImportEntity>
    
    @Query("SELECT * FROM data_imports WHERE is_synced = 0 AND user_id = :userId ORDER BY started_at ASC")
    suspend fun getUnsyncedImportsForUser(userId: String): List<DataImportEntity>
    
    @Query("DELETE FROM data_imports WHERE import_id = :importId AND user_id = :userId")
    suspend fun deleteImportForUser(importId: String, userId: String)
    
    @Query("UPDATE data_imports SET status = :status, validation_errors = :validationErrors WHERE import_id = :importId AND user_id = :userId")
    suspend fun updateImportStatus(importId: String, userId: String, status: String, validationErrors: String? = null)
    
    @Query("UPDATE data_imports SET status = 'COMPLETED', imported_records = :importedRecords, skipped_records = :skippedRecords, completed_at = :completedAt WHERE import_id = :importId AND user_id = :userId")
    suspend fun markImportCompleted(
        importId: String,
        userId: String,
        importedRecords: Int,
        skippedRecords: Int,
        completedAt: Long
    )
    
    @Query("UPDATE data_imports SET rollback_available = 0 WHERE import_id = :importId AND user_id = :userId")
    suspend fun disableRollbackForImport(importId: String, userId: String)
    
    @Query("UPDATE data_imports SET total_records = :totalRecords, status = 'IN_PROGRESS' WHERE import_id = :importId AND user_id = :userId")
    suspend fun startImportProcessing(importId: String, userId: String, totalRecords: Int)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImport(import: DataImportEntity)
    
    @Update
    suspend fun updateImport(import: DataImportEntity)
}