package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters

/**
 * Room entity for caching expensive analytics calculations
 * 
 * Stores pre-calculated analytics results to avoid repeated complex queries
 * and improve dashboard performance. Cache entries are scoped by user and
 * calculation type for efficient retrieval.
 */
@Entity(tableName = "analytics_cache")
@TypeConverters(DateTimeConverters::class)
data class AnalyticsCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "calculation_type")
    val calculationType: String,
    
    @ColumnInfo(name = "result")
    val result: String, // JSON serialized calculation result
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long // Unix timestamp when calculation was performed
)