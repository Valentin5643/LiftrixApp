package com.example.liftrix.domain.model.social

import com.example.liftrix.domain.model.FitnessLevel
import java.time.LocalDateTime

/**
 * PublicUserProfile - Domain model representing a user's public profile information
 * 
 * Contains filtered profile data based on privacy settings and viewer relationship.
 * Privacy-aware fields are only populated when viewer has appropriate access.
 * 
 * Used in: UserProfileScreen, profile discovery, social feeds
 */
data class PublicUserProfile(
    val userId: String,
    val username: String,
    val displayName: String?,
    val profileImageUrl: String?,
    val coverImageUrl: String?,
    
    // Basic info (respects privacy settings)
    val bio: String?,
    val age: Int?,
    val location: String?,
    val fitnessLevel: FitnessLevel?,
    
    // Social stats
    val followersCount: Int,
    val followingCount: Int,
    val mutualConnectionsCount: Int = 0,
    
    // Workout stats (privacy-aware)
    val totalWorkouts: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    
    // Profile metadata
    val memberSince: LocalDateTime,
    val lastActive: LocalDateTime?,
    val isVerified: Boolean = false,
    
    // Privacy and relationship status
    val isPrivate: Boolean,
    val followStatus: FollowStatus,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NONE,
    val canViewDetails: Boolean, // Based on privacy settings and follow status
    
    // External links (privacy-aware)
    val instagramHandle: String? = null,
    val youtubeChannel: String? = null,
    val personalWebsite: String? = null,
    
    // Recent content (privacy-aware)
    val recentWorkouts: List<RecentWorkout> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    
    // Workout statistics (privacy-aware)
    val publicWorkoutStats: PublicWorkoutStats? = null
) {
    /**
     * Whether this is the viewer's own profile
     */
    fun isOwnProfile(viewerId: String?): Boolean = userId == viewerId
}

/**
 * Recent workout summary for profile display
 */
data class RecentWorkout(
    val id: String,
    val name: String,
    val date: String,
    val exerciseCount: Int,
    val duration: String
)

/**
 * Achievement summary for profile display
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val earnedAt: LocalDateTime,
    val iconUrl: String? = null,
    val category: String
)

/**
 * Follow status between viewer and profile owner
 */
enum class FollowStatus {
    NONE,              // Not following
    FOLLOWING,         // Currently following
    PENDING_SENT,      // Follow request sent, awaiting approval
    PENDING_RECEIVED,  // Follow request received, awaiting acceptance
    BLOCKED            // User is blocked
}

/**
 * Public workout statistics for profile viewing
 */
data class PublicWorkoutStats(
    val totalWorkouts: Int,
    val totalWorkoutTime: Long, // in minutes
    val averageWorkoutTime: Long, // in minutes
    val currentStreak: Int,
    val longestStreak: Int,
    val favoriteExercises: List<String> = emptyList()
)

