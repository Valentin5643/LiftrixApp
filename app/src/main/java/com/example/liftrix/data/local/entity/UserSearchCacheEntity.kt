package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import androidx.room.Index
import com.example.liftrix.data.local.converter.DateTimeConverters

/**
 * Room entity for caching user search results
 * 
 * Stores search results to improve performance by avoiding repeated Firebase queries.
 * Cache entries are scoped by viewer and search query with expiration handling.
 */
@Entity(
    tableName = "user_search_cache",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["viewer_user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["viewer_user_id"]) // Index for foreign key
    ]
)
@TypeConverters(DateTimeConverters::class)
data class UserSearchCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "viewer_user_id")
    val viewerUserId: String,
    
    @ColumnInfo(name = "search_query")
    val searchQuery: String,
    
    @ColumnInfo(name = "search_results")
    val searchResults: String, // JSON serialized List<UserSearchResult>
    
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: String
)