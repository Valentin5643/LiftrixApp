package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import androidx.room.Index
import com.example.liftrix.data.local.converter.DateTimeConverters

/**
 * Room entity for QR code mapping to user profiles
 * 
 * Maps QR code IDs to user profiles for secure profile sharing.
 * Supports expiration and usage tracking for security and analytics.
 */
@Entity(
    tableName = "qr_code_mappings",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"]) // Index for foreign key
    ]
)
@TypeConverters(DateTimeConverters::class)
data class QRCodeMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "qr_code_id")
    val qrCodeId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: String? = null,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0
)