package com.example.liftrix.data.local.dto

/**
 * Data transfer object for post save statistics.
 * Used for efficient aggregation queries in DAOs.
 */
data class PostSaveStats(
    val postId: String,
    val saveCount: Int
)