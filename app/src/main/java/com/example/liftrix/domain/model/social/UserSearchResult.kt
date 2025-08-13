package com.example.liftrix.domain.model.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.FitnessLevel
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
 * Connection status between users for social interactions
 */
enum class ConnectionStatus {
    NONE,              // No existing connection
    PENDING_SENT,      // Current user sent request
    PENDING_RECEIVED,  // Received request from this user
    CONNECTED,         // Already connected/friends
    MUTUAL_FOLLOW,     // Both users follow each other
    GYM_BUDDY,         // Inner circle gym buddy relationship
    BLOCKED            // User is blocked
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

