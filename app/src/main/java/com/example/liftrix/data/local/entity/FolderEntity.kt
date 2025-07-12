package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant

/**
 * Room entity representing a folder for organizing workout templates.
 * 
 * Each folder belongs to a specific user and contains a collection of workout templates.
 * Folders provide hierarchical organization to replace the previous tag-based system.
 * 
 * Schema includes foreign key constraint to UserProfileEntity for data integrity
 * and proper cascade deletion when users are removed.
 */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"], name = "index_folders_user_id"),
        Index(value = ["user_id", "name"], unique = true, name = "index_folders_user_id_name"),
        Index(value = ["created_at"], name = "index_folders_created_at")
    ]
)
@TypeConverters(DateTimeConverters::class)
data class FolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "template_count", defaultValue = "0")
    val templateCount: Int = 0,
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L
)