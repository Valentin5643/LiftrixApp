package com.example.liftrix.domain.model.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserAchievement
import java.time.LocalDateTime

/**
 * Domain model representing a user search result for social discovery
 * 
 * Contains user information visible in search results with privacy filtering applied.
 * Includes social context like connection status and mutual connections.
 */
data class UserSearchResult(
    val userId: String,
    val displayName: String,
    val profileImageUrl: String?,
    val bio: String?,
    val fitnessLevel: FitnessLevel?,
    val totalWorkouts: Int,
    val memberSince: LocalDateTime,
    val sharedEquipment: List<Equipment>,
    val sharedGoals: List<FitnessGoal>,
    val connectionStatus: ConnectionStatus,
    val mutualConnections: Int = 0
)

/**
 * User fitness level enum for search filtering and display
 */
enum class FitnessLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}

/**
 * Connection status between users for social interactions
 */
enum class ConnectionStatus {
    NONE,              // No existing connection
    PENDING_SENT,      // Current user sent request
    PENDING_RECEIVED,  // Received request from this user
    CONNECTED          // Already connected/friends
}

/**
 * Search filters for user discovery
 */
data class SearchFilters(
    val fitnessLevel: FitnessLevel? = null,
    val equipment: Set<Equipment> = emptySet(),
    val goals: Set<FitnessGoal> = emptySet(),
    val location: String? = null,
    val minWorkouts: Int? = null,
    val maxWorkouts: Int? = null
)

/**
 * Privacy-aware public user profile for detailed viewing
 */
data class PublicUserProfile(
    val userId: String,
    val displayName: String,
    val profileImageUrl: String?,
    val bio: String?,
    val memberSince: LocalDateTime,
    val fitnessLevel: FitnessLevel?,
    val isOnline: Boolean,
    val lastActiveAt: LocalDateTime?,
    val connectionStatus: ConnectionStatus,
    val mutualConnections: Int = 0,
    // Only included if user's privacy allows
    val publicAchievements: List<UserAchievement>?,
    val publicWorkoutStats: PublicWorkoutStats?,
    val publicFitnessGoals: List<FitnessGoal>?,
    val availableEquipment: List<Equipment>?
)

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