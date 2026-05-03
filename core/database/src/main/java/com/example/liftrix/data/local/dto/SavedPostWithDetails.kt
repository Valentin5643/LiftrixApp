package com.example.liftrix.data.local.dto

/**
 * Data transfer object for saved posts with additional details.
 * Used for efficient JOIN queries in DAOs.
 */
data class SavedPostWithDetails(
    val id: String,
    val userId: String,
    val postId: String,
    val savedAt: Long,
    val caption: String?,
    val authorUsername: String?,
    val authorDisplayName: String?,
    val authorProfilePhotoUrl: String?,
    val workoutDuration: Int?,
    val exercisesCount: Int?,
    val prsCount: Int,
    val createdAt: Long
)