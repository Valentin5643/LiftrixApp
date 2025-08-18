package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_exports")
data class DataExportEntity(
    @PrimaryKey
    @ColumnInfo(name = "export_id")
    val exportId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "export_type")
    val exportType: String,
    
    @ColumnInfo(name = "data_types") 
    val dataTypes: String, // JSON array of selected types
    
    @ColumnInfo(name = "status")
    val status: String, // REQUESTED, IN_PROGRESS, COMPLETED, FAILED
    
    @ColumnInfo(name = "file_uri")
    val fileUri: String? = null,
    
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,
    
    @ColumnInfo(name = "record_count")
    val recordCount: Int? = null,
    
    @ColumnInfo(name = "date_range_start")
    val dateRangeStart: Long? = null,
    
    @ColumnInfo(name = "date_range_end")
    val dateRangeEnd: Long? = null,
    
    @ColumnInfo(name = "requested_at")
    val requestedAt: Long,
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    val syncVersion: Int = 1
)