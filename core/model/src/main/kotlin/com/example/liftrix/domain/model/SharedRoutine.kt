package com.example.liftrix.domain.model

/**
 * Domain model representing a shared workout routine.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
data class SharedRoutine(
    val id: String,
    val userId: String,
    val routineId: String,
    val shareToken: String,
    val shareUrl: String,
    val routineName: String,
    val description: String? = null,
    val routineData: String, // JSON structure
    val previewImageUrl: String? = null,
    val exerciseCount: Int? = null,
    val estimatedDuration: Int? = null,
    val difficultyLevel: DifficultyLevel? = null,
    val equipmentNeeded: List<String> = emptyList(),
    val viewCount: Int = 0,
    val importCount: Int = 0,
    val likeCount: Int = 0,
    val version: Int = 1,
    val parentRoutineId: String? = null,
    val isActive: Boolean = true,
    val isFeatured: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Difficulty level enumeration for shared routines.
 */
enum class DifficultyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}

/**
 * Request for creating a shareable routine.
 */
data class ShareRoutineRequest(
    val routineId: String,
    val userId: String,
    val customDescription: String? = null,
    val generatePreview: Boolean = true,
    val includeBranding: Boolean = true
)

/**
 * Analytics data for shared routine performance.
 */
data class SharedRoutineAnalytics(
    val routineId: String,
    val viewCount: Int,
    val importCount: Int,
    val likeCount: Int,
    val shareCount: Int,
    val conversionRate: Float, // imports/views
    val popularityScore: Float,
    val lastActivity: Long
)