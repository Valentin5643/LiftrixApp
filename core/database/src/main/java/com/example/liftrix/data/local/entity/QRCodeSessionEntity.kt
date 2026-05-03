package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * QR Code Session Entity for tracking QR code generation and usage
 * Supports time-limited QR codes for gym buddy pairing with location context
 */
@Entity(
    tableName = "qr_code_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"], 
            childColumns = ["used_by_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["code_token"], unique = true),
        Index(value = ["expires_at"]),
        Index(value = ["user_id"]),
        Index(value = ["used_by_user_id"])
    ]
)
data class QRCodeSessionEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    // QR data
    @ColumnInfo(name = "code_token")
    val codeToken: String,
    
    @ColumnInfo(name = "code_data")
    val codeData: String, // Encrypted payload
    
    // Validity
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    
    @ColumnInfo(name = "is_used")
    val isUsed: Boolean = false,
    
    @ColumnInfo(name = "used_by_user_id")
    val usedByUserId: String? = null,
    
    // Location context
    @ColumnInfo(name = "generation_lat")
    val generationLat: Double? = null,
    
    @ColumnInfo(name = "generation_lng")
    val generationLng: Double? = null,
    
    @ColumnInfo(name = "gym_name")
    val gymName: String? = null
)