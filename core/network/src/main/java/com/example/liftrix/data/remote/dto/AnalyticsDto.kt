package com.example.liftrix.data.remote.dto

import com.google.firebase.firestore.PropertyName

/**
 * Data Transfer Object for analytics data in Firebase Firestore
 * 
 * Represents analytics calculations and cached results for cloud synchronization:
 * - Volume calendar data and daily aggregations
 * - Progress metrics and dashboard calculations
 * - Workout statistics and trend analysis
 * - Goal tracking and achievement data
 * 
 * Firebase Mapping:
 * - Uses @PropertyName annotations for consistent field naming
 * - Supports automatic serialization/deserialization with Firestore
 * - Includes sync metadata for conflict resolution
 * 
 * Data Structure:
 * - User-scoped with userId filtering for multi-tenancy
 * - Calculation type categorization for efficient queries
 * - JSON serialized results for flexible data storage
 * - Timestamp-based conflict resolution support
 */
data class AnalyticsDto(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("user_id")
    val userId: String = "",
    
    @PropertyName("calculation_type")
    val calculationType: String = "",
    
    @PropertyName("result")
    val result: String = "", // JSON serialized calculation result
    
    @PropertyName("timestamp")
    val timestamp: Long = 0L, // Unix timestamp when calculation was performed
    
    @PropertyName("metadata")
    val metadata: Map<String, String> = emptyMap(), // Additional calculation context
    
    @PropertyName("sync_version")
    val syncVersion: Long = 0L, // Version for optimistic concurrency control
    
    @PropertyName("last_modified")
    val lastModified: Long = 0L, // Last modification timestamp for conflict resolution
    
    @PropertyName("created_at")
    val createdAt: Long = System.currentTimeMillis(), // Creation timestamp
    
    @PropertyName("is_deleted")
    val isDeleted: Boolean = false // Soft delete flag for data cleanup
) {
    
    companion object {
        // Calculation types for analytics categorization
        const val TYPE_VOLUME_CALENDAR = "volume_calendar"
        const val TYPE_WORKOUT_METRICS = "workout_metrics"
        const val TYPE_PROGRESS_DASHBOARD = "progress_dashboard"
        const val TYPE_FREQUENCY_ANALYSIS = "frequency_analysis"
        const val TYPE_STRENGTH_PROGRESSION = "strength_progression"
        const val TYPE_MUSCLE_GROUP_DISTRIBUTION = "muscle_group_distribution"
        const val TYPE_CONSISTENCY_STREAK = "consistency_streak"
        const val TYPE_CALORIE_SUMMARY = "calorie_summary"
        const val TYPE_GOAL_PROGRESS = "goal_progress"
        const val TYPE_ACHIEVEMENT_DATA = "achievement_data"
        
        // Metadata keys for additional context
        const val METADATA_DATE_RANGE = "date_range"
        const val METADATA_EXERCISE_COUNT = "exercise_count"
        const val METADATA_WORKOUT_COUNT = "workout_count"
        const val METADATA_CONFIGURATION = "configuration"
        const val METADATA_VERSION = "version"
        const val METADATA_DEVICE_ID = "device_id"
        
        // Firebase collection and document naming conventions
        const val COLLECTION_ANALYTICS = "analytics"
        const val COLLECTION_ANALYTICS_CACHE = "analytics_cache"
        
        fun createDocumentId(userId: String, calculationType: String, timestamp: Long): String {
            return "${userId}_${calculationType}_${timestamp}"
        }
        
        fun createCacheDocumentId(userId: String, calculationType: String): String {
            return "${userId}_${calculationType}_cache"
        }
        
        fun fromFirebaseMap(data: Map<String, Any>): AnalyticsDto {
            return AnalyticsDto(
                id = data["id"] as? String ?: "",
                userId = data["user_id"] as? String ?: "",
                calculationType = data["calculation_type"] as? String ?: "",
                result = data["result"] as? String ?: "",
                timestamp = data["timestamp"] as? Long ?: 0L,
                metadata = (data["metadata"] as? Map<String, String>) ?: emptyMap(),
                syncVersion = data["sync_version"] as? Long ?: 0L,
                lastModified = data["last_modified"] as? Long ?: 0L,
                createdAt = data["created_at"] as? Long ?: System.currentTimeMillis(),
                isDeleted = data["is_deleted"] as? Boolean ?: false
            )
        }
    }
    
    /**
     * No-argument constructor required by Firebase Firestore
     */
    constructor() : this(
        id = "",
        userId = "",
        calculationType = "",
        result = "",
        timestamp = 0L,
        metadata = emptyMap(),
        syncVersion = 0L,
        lastModified = 0L,
        createdAt = System.currentTimeMillis(),
        isDeleted = false
    )
    
    /**
     * Validates the analytics DTO for Firebase sync
     */
    fun isValid(): Boolean {
        return id.isNotBlank() &&
               userId.isNotBlank() &&
               calculationType.isNotBlank() &&
               result.isNotBlank() &&
               timestamp > 0L &&
               syncVersion >= 0L &&
               lastModified > 0L
    }
    
    /**
     * Creates a copy with updated sync metadata
     */
    fun withSyncMetadata(newSyncVersion: Long, modifiedTime: Long): AnalyticsDto {
        return copy(
            syncVersion = newSyncVersion,
            lastModified = modifiedTime
        )
    }
    
    /**
     * Creates a soft-deleted version of this analytics entry
     */
    fun markAsDeleted(): AnalyticsDto {
        return copy(
            isDeleted = true,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Checks if this analytics entry is more recent than another
     */
    fun isNewerThan(other: AnalyticsDto): Boolean {
        return when {
            this.lastModified > other.lastModified -> true
            this.lastModified < other.lastModified -> false
            else -> this.syncVersion > other.syncVersion
        }
    }
    
    /**
     * Gets user-friendly display name for calculation type
     */
    fun getDisplayName(): String = when (calculationType) {
        TYPE_VOLUME_CALENDAR -> "Volume Calendar"
        TYPE_WORKOUT_METRICS -> "Workout Metrics"
        TYPE_PROGRESS_DASHBOARD -> "Progress Dashboard"
        TYPE_FREQUENCY_ANALYSIS -> "Frequency Analysis"
        TYPE_STRENGTH_PROGRESSION -> "Strength Progression"
        TYPE_MUSCLE_GROUP_DISTRIBUTION -> "Muscle Group Distribution"
        TYPE_CONSISTENCY_STREAK -> "Consistency Streak"
        TYPE_CALORIE_SUMMARY -> "Calorie Summary"
        TYPE_GOAL_PROGRESS -> "Goal Progress"
        TYPE_ACHIEVEMENT_DATA -> "Achievement Data"
        else -> calculationType.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
    }
    
    /**
     * Converts to Firebase document data map
     */
    fun toFirebaseMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "user_id" to userId,
            "calculation_type" to calculationType,
            "result" to result,
            "timestamp" to timestamp,
            "metadata" to metadata,
            "sync_version" to syncVersion,
            "last_modified" to lastModified,
            "created_at" to createdAt,
            "is_deleted" to isDeleted
        )
    }
}