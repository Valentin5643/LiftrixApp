package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_imports")
data class DataImportEntity(
    @PrimaryKey
    @ColumnInfo(name = "import_id")
    val importId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "source_format")
    val sourceFormat: String, // JSON, CSV, FIT, TCX, GPX
    
    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null,
    
    @ColumnInfo(name = "status")
    val status: String, // VALIDATING, IN_PROGRESS, COMPLETED, FAILED
    
    @ColumnInfo(name = "total_records")
    val totalRecords: Int? = null,
    
    @ColumnInfo(name = "imported_records")
    val importedRecords: Int? = null,
    
    @ColumnInfo(name = "skipped_records")
    val skippedRecords: Int? = null,
    
    @ColumnInfo(name = "conflict_resolution")
    val conflictResolution: String? = null, // SKIP, REPLACE, MERGE
    
    @ColumnInfo(name = "validation_errors")
    val validationErrors: String? = null, // JSON array
    
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    
    @ColumnInfo(name = "rollback_available")
    val rollbackAvailable: Boolean = true,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
)
