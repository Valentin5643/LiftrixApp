package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.LocalDateTime

/**
 * Room entity representing a user's account information in the local database.
 * 
 * Stores account-level data including email, username, and account management metadata.
 * Created as part of SPEC-20250116-account-management migration 50->51.
 */
@Entity(
    tableName = "user_accounts",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["email"]),
        Index(value = ["is_synced"]),
        Index(value = ["deletion_requested_at"])
    ]
)
@TypeConverters(DateTimeConverters::class)
data class UserAccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "username")
    val username: String? = null,

    @ColumnInfo(name = "email_verified", defaultValue = "0")
    val emailVerified: Boolean = false,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "last_password_change")
    val lastPasswordChange: LocalDateTime? = null,

    @ColumnInfo(name = "account_created_at")
    val accountCreatedAt: LocalDateTime,

    @ColumnInfo(name = "last_email_update")
    val lastEmailUpdate: LocalDateTime? = null,

    @ColumnInfo(name = "deletion_requested_at")
    val deletionRequestedAt: LocalDateTime? = null,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
)
