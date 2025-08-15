package com.example.liftrix.domain.model.social

/**
 * Domain model representing a workout post in the social feed.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
data class WorkoutPost(
    val id: String,
    val userId: String,
    val workoutId: String,
    
    // Content
    val caption: String = "",
    val mediaUrls: List<String> = emptyList(),
    val mediaThumbnails: List<String> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    
    // Metadata
    val workoutDuration: Int? = null,
    val totalVolume: Double? = null,
    val exercisesCount: Int? = null,
    val prsCount: Int = 0,
    val achievements: List<WorkoutAchievement> = emptyList(),
    val workoutSummary: WorkoutSummary? = null,
    
    // Engagement metrics
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val saveCount: Int = 0,
    
    // Visibility
    val visibility: PostVisibility = PostVisibility.FOLLOWERS,
    
    // User interactions (from viewer's perspective)
    val isLikedByViewer: Boolean = false,
    val isSavedByViewer: Boolean = false,
    
    // Timestamps
    val createdAt: Long,
    val updatedAt: Long,
    
    // Feed-specific data
    val relevanceScore: Double = 0.0,
    
    // Author information (denormalized for feed performance)
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorProfilePhotoUrl: String? = null
)

/**
 * Post visibility levels for social feed
 */
enum class PostVisibility {
    PUBLIC,     // Visible to everyone
    FOLLOWERS,  // Visible to followers only
    PRIVATE     // Visible to author only
}

/**
 * Request for creating a new workout post
 */
data class CreateWorkoutPostRequest(
    val workoutId: String,
    val caption: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val visibility: PostVisibility = PostVisibility.FOLLOWERS
)

/**
 * Workout summary for posts
 */
data class WorkoutSummary(
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val totalVolume: Double = 0.0,
    val exerciseCount: Int = 0,
    val duration: Int? = null
)

/**
 * Achievement for workout posts
 */
data class WorkoutAchievement(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val value: Double? = null
)

/**
 * Feed type enumeration for different feed contexts
 */
enum class FeedType {
    HOME,       // Posts from followed users
    DISCOVERY,  // Public posts for discovery
    USER        // Posts from a specific user
}