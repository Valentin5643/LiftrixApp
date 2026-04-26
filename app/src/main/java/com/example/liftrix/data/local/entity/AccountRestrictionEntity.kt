package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing account restrictions for user moderation.
 *
 * Used to track:
 * - SUSPENDED: Account temporarily or permanently suspended
 * - WARNED: User has been warned about policy violations
 * - RESTRICTED: Limited functionality (e.g., can't post/comment)
 *
 * Restrictions can be temporary (with end_time) or permanent (end_time = null).
 */
@Entity(
    tableName = "account_restrictions",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "restriction_type"])
    ]
)
data class AccountRestrictionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    /**
     * Type of restriction: SUSPENDED, WARNED, RESTRICTED
     */
    @ColumnInfo(name = "restriction_type")
    val restrictionType: String,

    @ColumnInfo(name = "reason")
    val reason: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    /**
     * End time of restriction. Null = permanent restriction.
     */
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    /**
     * Admin user ID who created the restriction.
     */
    @ColumnInfo(name = "created_by")
    val createdBy: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
