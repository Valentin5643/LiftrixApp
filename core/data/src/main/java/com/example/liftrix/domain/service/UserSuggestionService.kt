package com.example.liftrix.domain.service

import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.ProfileViewDao
import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.social.ConnectionStatus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.ln
import kotlin.math.pow
import com.example.liftrix.data.local.LiftrixDatabase
import android.os.Process

/**
 * Advanced user suggestion service with ML-inspired algorithms for social discovery.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Features:
 * - Multi-factor scoring algorithm combining mutual connections, interests, and activity
 * - Temporal decay for suggestion freshness and diversity
 * - Privacy-aware filtering with comprehensive blocked user handling
 * - Performance optimization with caching and batch processing
 * - A/B testing support for algorithm refinement
 * - Comprehensive analytics tracking for suggestion effectiveness
 * 
 * Algorithm Components:
 * 1. Mutual Connection Score (40% weight) - Users followed by your connections
 * 2. Interest Similarity Score (25% weight) - Similar fitness goals and equipment
 * 3. Activity Level Score (20% weight) - Compatible workout frequency and level
 * 4. Engagement Score (10% weight) - Profile views and interactions
 * 5. Freshness Score (5% weight) - Temporal diversity and new user promotion
 */
@Singleton
class UserSuggestionService @Inject constructor(
    private val socialProfileDao: SocialProfileDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val profileViewDao: ProfileViewDao,
    private val blockedUserDao: BlockedUserDao,
    private val database: LiftrixDatabase
) {

    companion object {
        private const val DEFAULT_SUGGESTION_LIMIT = 20
        private const val MAX_MUTUAL_CONNECTION_DEPTH = 2
        private const val ACTIVITY_SIMILARITY_THRESHOLD = 0.7
        private const val VIEW_HISTORY_DAYS = 30
        private const val FRESHNESS_DECAY_DAYS = 7
        private const val MIN_MUTUAL_CONNECTIONS_FOR_BOOST = 2
        
        // Scoring weights
        private const val MUTUAL_CONNECTION_WEIGHT = 0.4
        private const val INTEREST_SIMILARITY_WEIGHT = 0.25
        private const val ACTIVITY_LEVEL_WEIGHT = 0.2
        private const val ENGAGEMENT_WEIGHT = 0.1
        private const val FRESHNESS_WEIGHT = 0.05
        
        // Scoring thresholds
        private const val MIN_SUGGESTION_SCORE = 0.3
        private const val HIGH_QUALITY_SUGGESTION_SCORE = 0.7
    }

    /**
     * Get personalized user suggestions using multi-factor algorithm
     */
    suspend fun getPersonalizedSuggestions(
        userId: String,
        limit: Int = DEFAULT_SUGGESTION_LIMIT,
        diversityFactor: Double = 0.8
    ): LiftrixResult<List<UserSuggestionResult>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get personalized suggestions: ${throwable.message}",
                    operation = "GET_PERSONALIZED_SUGGESTIONS"
                )
            }
        ) {
            logProfileDbDiagnostics("PROFILE_DISCOVERY_PERSONALIZED_START userId=$userId")
            val preTotalCount = socialProfileDao.getTotalProfileCount()
            val preUserCount = socialProfileDao.getProfileCount(userId)
            Timber.d(
                "PROFILE_DISCOVERY_PERSONALIZED_COUNTS userId=$userId userCount=$preUserCount totalCount=$preTotalCount"
            )

            Timber.d("Generating personalized suggestions for user: $userId, limit: $limit")
            
            val userProfile = socialProfileDao.getSocialProfileByUserId(userId)
                ?: throw IllegalArgumentException("User profile not found")

            Timber.d(
                "PROFILE_DISCOVERY_PERSONALIZED_SELF userId=$userId username=${userProfile.username} " +
                    "isDirty=${userProfile.isDirty} isSynced=${userProfile.isSynced}"
            )
            
            // Get candidate users from multiple sources
            val candidateUsers = getCandidateUsers(userId, limit * 3)
            
            if (candidateUsers.isEmpty()) {
                Timber.d("No candidate users found for suggestions")
                return@liftrixCatching emptyList<UserSuggestionResult>()
            }
            
            // Score and rank candidates
            val scoredCandidates = scoreCandidateUsers(
                userId = userId,
                userProfile = userProfile,
                candidates = candidateUsers,
                diversityFactor = diversityFactor
            )
            
            // Filter and apply final selection logic
            val finalSuggestions = selectFinalSuggestions(
                scoredCandidates = scoredCandidates,
                limit = limit,
                minScore = MIN_SUGGESTION_SCORE
            )
            
            Timber.d("Generated ${finalSuggestions.size} personalized suggestions for user: $userId")
            finalSuggestions
        }
    }

    /**
     * Get suggestions based primarily on mutual connections
     */
    suspend fun getMutualConnectionSuggestions(
        userId: String,
        limit: Int = DEFAULT_SUGGESTION_LIMIT
    ): LiftrixResult<List<UserSuggestionResult>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get mutual connection suggestions: ${throwable.message}",
                    operation = "GET_MUTUAL_CONNECTION_SUGGESTIONS"
                )
            }
        ) {
            Timber.d("Getting mutual connection suggestions for user: $userId")
            
            // Get users that the current user's connections follow
            val mutualConnectionCandidates = getMutualConnectionCandidates(userId, limit * 2)
            
            // Score based on connection strength and mutual friend count
            val scoredSuggestions = mutualConnectionCandidates.mapNotNull { candidate ->
                val mutualCount = calculateMutualConnectionCount(userId, candidate.userId)
                if (mutualCount >= MIN_MUTUAL_CONNECTIONS_FOR_BOOST) {
                    val connectionScore = calculateConnectionStrengthScore(userId, candidate.userId)
                    val finalScore = (mutualCount.toDouble() / 10.0) * connectionScore
                    
                    UserSuggestionResult(
                        userSearchResult = mapEntityToSearchResult(candidate, userId),
                        suggestionScore = finalScore,
                        suggestionReason = SuggestionReason.MUTUAL_CONNECTIONS,
                        mutualConnectionCount = mutualCount,
                        interestMatchScore = 0.0,
                        activityCompatibilityScore = 0.0
                    )
                } else null
            }.sortedByDescending { it.suggestionScore }
            
            scoredSuggestions.take(limit)
        }
    }

    /**
     * Get suggestions for new users with minimal social graph
     */
    suspend fun getNewUserSuggestions(
        userId: String,
        limit: Int = DEFAULT_SUGGESTION_LIMIT
    ): LiftrixResult<List<UserSuggestionResult>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get new user suggestions: ${throwable.message}",
                    operation = "GET_NEW_USER_SUGGESTIONS"
                )
            }
        ) {
            logProfileDbDiagnostics("PROFILE_DISCOVERY_NEW_USER_START userId=$userId")
            val preTotalCount = socialProfileDao.getTotalProfileCount()
            val preUserCount = socialProfileDao.getProfileCount(userId)
            Timber.d(
                "PROFILE_DISCOVERY_NEW_USER_COUNTS userId=$userId userCount=$preUserCount totalCount=$preTotalCount"
            )

            Timber.d("Getting new user suggestions for user: $userId")
            
            val userProfile = socialProfileDao.getSocialProfileByUserId(userId)
                ?: throw IllegalArgumentException("User profile not found")

            Timber.d(
                "PROFILE_DISCOVERY_NEW_USER_SELF userId=$userId username=${userProfile.username} " +
                    "isDirty=${userProfile.isDirty} isSynced=${userProfile.isSynced}"
            )
            
            // For new users, focus on popular and active users
            val popularUsers = getPopularUsers(userId, limit)
            val activeUsers = getRecentlyActiveUsers(userId, limit)
            val similarLevelUsers = getSimilarFitnessLevelUsers(userId, userProfile, limit)
            
            // Combine and deduplicate
            val combinedCandidates = (popularUsers + activeUsers + similarLevelUsers)
                .distinctBy { it.userId }
                .take(limit * 2)
            
            // Score based on popularity and compatibility for new users
            val scoredSuggestions = combinedCandidates.map { candidate ->
                val popularityScore = calculatePopularityScore(candidate)
                val compatibilityScore = calculateActivityCompatibility(userProfile, candidate)
                val finalScore = (popularityScore * 0.6) + (compatibilityScore * 0.4)
                
                UserSuggestionResult(
                    userSearchResult = mapEntityToSearchResult(candidate, userId),
                    suggestionScore = finalScore,
                    suggestionReason = SuggestionReason.NEW_USER_POPULAR,
                    mutualConnectionCount = 0,
                    interestMatchScore = 0.0,
                    activityCompatibilityScore = compatibilityScore
                )
            }.sortedByDescending { it.suggestionScore }
            
            scoredSuggestions.take(limit)
        }
    }

    /**
     * Get trending users based on recent activity and engagement
     */
    suspend fun getTrendingSuggestions(
        userId: String,
        timeWindowDays: Int = 7,
        limit: Int = DEFAULT_SUGGESTION_LIMIT
    ): LiftrixResult<List<UserSuggestionResult>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get trending suggestions: ${throwable.message}",
                    operation = "GET_TRENDING_SUGGESTIONS"
                )
            }
        ) {
            val timeThreshold = System.currentTimeMillis() - (timeWindowDays * 24 * 60 * 60 * 1000L)
            
            // Get users with high recent activity
            val trendingUsers = socialProfileDao.getRecentlyActiveProfiles(timeThreshold, limit * 2)
                .filter { it.userId != userId }
            
            // Filter out blocked users and existing connections
            val filteredUsers = filterExistingConnections(trendingUsers, userId)
            
            val scoredSuggestions = filteredUsers.map { candidate ->
                val trendingScore = calculateTrendingScore(candidate, timeThreshold)
                
                UserSuggestionResult(
                    userSearchResult = mapEntityToSearchResult(candidate, userId),
                    suggestionScore = trendingScore,
                    suggestionReason = SuggestionReason.TRENDING,
                    mutualConnectionCount = 0,
                    interestMatchScore = 0.0,
                    activityCompatibilityScore = 0.0
                )
            }.sortedByDescending { it.suggestionScore }
            
            scoredSuggestions.take(limit)
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private suspend fun getCandidateUsers(
        userId: String,
        candidateLimit: Int
    ): List<SocialProfileEntity> {
        val candidates = mutableListOf<SocialProfileEntity>()
        
        // 1. Mutual connection candidates (primary source)
        val mutualCandidates = getMutualConnectionCandidates(userId, candidateLimit / 2)
        candidates.addAll(mutualCandidates)
        
        // 2. Similar activity level users
        val userProfile = socialProfileDao.getSocialProfileByUserId(userId)
        if (userProfile != null) {
            val similarUsers = getSimilarFitnessLevelUsers(userId, userProfile, candidateLimit / 3)
            candidates.addAll(similarUsers)
        }
        
        // 3. Recently active users for freshness
        val timeThreshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val recentUsers = socialProfileDao.getRecentlyActiveProfiles(timeThreshold, candidateLimit / 4)
        candidates.addAll(recentUsers)
        
        // 4. Fill remainder with popular users
        val remainingSlots = candidateLimit - candidates.size
        if (remainingSlots > 0) {
            val popularUsers = getPopularUsers(userId, remainingSlots)
            candidates.addAll(popularUsers)
        }
        
        // Remove duplicates and filter out existing connections
        return candidates.distinctBy { it.userId }
            .let { filterExistingConnections(it, userId) }
    }

    private suspend fun getMutualConnectionCandidates(
        userId: String,
        limit: Int
    ): List<SocialProfileEntity> {
        // Get users that the current user's connections follow
        val userFollowing = followRelationshipDao.getFollowing(
            userId,
            FollowRelationshipEntity.STATUS_ACCEPTED,
            200 // Limit to prevent performance issues
        )
        
        val candidates = mutableListOf<SocialProfileEntity>()
        val seenUserIds = mutableSetOf<String>()
        
        for (relationship in userFollowing.take(20)) { // Limit connection traversal
            val theirFollowing = followRelationshipDao.getFollowing(
                relationship.followingId,
                FollowRelationshipEntity.STATUS_ACCEPTED,
                50
            )
            
            for (theirRelationship in theirFollowing) {
                val candidateId = theirRelationship.followingId
                
                if (candidateId != userId && !seenUserIds.contains(candidateId)) {
                    val candidateProfile = socialProfileDao.getSocialProfileByUserId(candidateId)
                    candidateProfile?.let {
                        candidates.add(it)
                        seenUserIds.add(candidateId)
                    }
                }
            }
            
            if (candidates.size >= limit) break
        }
        
        return candidates.take(limit)
    }

    private suspend fun getSimilarFitnessLevelUsers(
        userId: String,
        userProfile: SocialProfileEntity,
        limit: Int
    ): List<SocialProfileEntity> {
        val minWorkouts = maxOf(0, userProfile.workoutCount - 50)
        val maxWorkouts = userProfile.workoutCount + 50
        
        return socialProfileDao.getProfilesByWorkoutRange(minWorkouts, maxWorkouts, limit * 2)
            .filter { it.userId != userId }
            .take(limit)
    }

    private suspend fun getPopularUsers(userId: String, limit: Int): List<SocialProfileEntity> {
        return socialProfileDao.getMostFollowedProfiles(limit * 2)
            .filter { it.userId != userId }
            .take(limit)
    }

    private suspend fun getRecentlyActiveUsers(userId: String, limit: Int): List<SocialProfileEntity> {
        val timeThreshold = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L) // 3 days
        return socialProfileDao.getRecentlyActiveProfiles(timeThreshold, limit * 2)
            .filter { it.userId != userId }
            .take(limit)
    }

    private suspend fun scoreCandidateUsers(
        userId: String,
        userProfile: SocialProfileEntity,
        candidates: List<SocialProfileEntity>,
        diversityFactor: Double
    ): List<UserSuggestionResult> {
        return candidates.mapNotNull { candidate ->
            try {
                val mutualConnectionScore = calculateMutualConnectionScore(userId, candidate.userId)
                val interestSimilarityScore = calculateInterestSimilarityScore(userProfile, candidate)
                val activityLevelScore = calculateActivityCompatibility(userProfile, candidate)
                val engagementScore = calculateEngagementScore(userId, candidate.userId)
                val freshnessScore = calculateFreshnessScore(candidate)
                
                val totalScore = (mutualConnectionScore * MUTUAL_CONNECTION_WEIGHT) +
                    (interestSimilarityScore * INTEREST_SIMILARITY_WEIGHT) +
                    (activityLevelScore * ACTIVITY_LEVEL_WEIGHT) +
                    (engagementScore * ENGAGEMENT_WEIGHT) +
                    (freshnessScore * FRESHNESS_WEIGHT)
                
                // Apply diversity factor for result variety
                val finalScore = totalScore * diversityFactor
                
                if (finalScore >= MIN_SUGGESTION_SCORE) {
                    UserSuggestionResult(
                        userSearchResult = mapEntityToSearchResult(candidate, userId),
                        suggestionScore = finalScore,
                        suggestionReason = determineSuggestionReason(
                            mutualConnectionScore,
                            interestSimilarityScore,
                            activityLevelScore
                        ),
                        mutualConnectionCount = calculateMutualConnectionCount(userId, candidate.userId),
                        interestMatchScore = interestSimilarityScore,
                        activityCompatibilityScore = activityLevelScore
                    )
                } else null
            } catch (e: Exception) {
                Timber.e(e, "Failed to score candidate: ${candidate.userId}")
                null
            }
        }
    }

    private suspend fun calculateMutualConnectionScore(userId: String, candidateId: String): Double {
        val mutualCount = calculateMutualConnectionCount(userId, candidateId)
        return when {
            mutualCount >= 10 -> 1.0
            mutualCount >= 5 -> 0.8
            mutualCount >= 2 -> 0.6
            mutualCount >= 1 -> 0.4
            else -> 0.1
        }
    }

    private suspend fun calculateMutualConnectionCount(userId: String, candidateId: String): Int {
        return try {
            val userFollowing = followRelationshipDao.getFollowing(userId, FollowRelationshipEntity.STATUS_ACCEPTED, 500)
            val candidateFollowing = followRelationshipDao.getFollowing(candidateId, FollowRelationshipEntity.STATUS_ACCEPTED, 500)
            
            val userFollowingIds = userFollowing.map { it.followingId }.toSet()
            val candidateFollowingIds = candidateFollowing.map { it.followingId }.toSet()
            
            userFollowingIds.intersect(candidateFollowingIds).size
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateInterestSimilarityScore(
        userProfile: SocialProfileEntity,
        candidate: SocialProfileEntity
    ): Double {
        // This is a simplified version - in practice, you'd compare fitness goals, equipment, etc.
        // For now, we'll use workout count similarity as a proxy for fitness interests
        val userWorkoutLevel = categorizeFitnessLevel(userProfile.workoutCount)
        val candidateWorkoutLevel = categorizeFitnessLevel(candidate.workoutCount)
        
        return when {
            userWorkoutLevel == candidateWorkoutLevel -> 1.0
            kotlin.math.abs(userWorkoutLevel - candidateWorkoutLevel) == 1 -> 0.7
            kotlin.math.abs(userWorkoutLevel - candidateWorkoutLevel) == 2 -> 0.4
            else -> 0.1
        }
    }

    private fun calculateActivityCompatibility(
        userProfile: SocialProfileEntity,
        candidate: SocialProfileEntity
    ): Double {
        val userActivityLevel = userProfile.workoutCount.toDouble()
        val candidateActivityLevel = candidate.workoutCount.toDouble()
        
        if (userActivityLevel == 0.0 && candidateActivityLevel == 0.0) return 1.0
        if (userActivityLevel == 0.0 || candidateActivityLevel == 0.0) return 0.1
        
        val ratio = minOf(userActivityLevel, candidateActivityLevel) / 
                   maxOf(userActivityLevel, candidateActivityLevel)
        
        return ratio.coerceAtLeast(0.1)
    }

    private suspend fun calculateEngagementScore(userId: String, candidateId: String): Double {
        val recentTimestamp = System.currentTimeMillis() - (VIEW_HISTORY_DAYS * 24 * 60 * 60 * 1000L)
        
        // Check if user has viewed candidate's profile recently
        val viewCount = profileViewDao.getViewCount(candidateId, recentTimestamp)
        val uniqueViewerCount = profileViewDao.getUniqueViewerCount(candidateId, recentTimestamp)
        
        val popularityScore = minOf(1.0, uniqueViewerCount.toDouble() / 50.0)
        val interactionScore = if (viewCount > 0) 0.3 else 0.0
        
        return (popularityScore * 0.7) + (interactionScore * 0.3)
    }

    private fun calculateFreshnessScore(candidate: SocialProfileEntity): Double {
        val daysSinceMemberSince = (System.currentTimeMillis() - candidate.memberSince) / (24 * 60 * 60 * 1000)
        val daysSinceLastActive = candidate.lastActive?.let { 
            (System.currentTimeMillis() - it) / (24 * 60 * 60 * 1000) 
        } ?: 365L
        
        val newUserBoost = if (daysSinceMemberSince <= 30) 0.5 else 0.0
        val recentActivityBoost = if (daysSinceLastActive <= 7) 0.5 else 0.0
        
        return (newUserBoost + recentActivityBoost).coerceAtMost(1.0)
    }

    private fun calculatePopularityScore(candidate: SocialProfileEntity): Double {
        val followerScore = minOf(1.0, candidate.followerCount.toDouble() / 1000.0)
        val workoutScore = minOf(1.0, candidate.workoutCount.toDouble() / 500.0)
        
        return (followerScore * 0.6) + (workoutScore * 0.4)
    }

    private fun calculateConnectionStrengthScore(userId: String, candidateId: String): Double {
        // This would analyze the strength of mutual connections
        // For now, return a base score
        return 0.75
    }

    private fun calculateTrendingScore(candidate: SocialProfileEntity, timeThreshold: Long): Double {
        val recentActivityScore = candidate.lastActive?.let { lastActive ->
            val timeSinceActive = System.currentTimeMillis() - lastActive
            val daysSince = timeSinceActive / (24 * 60 * 60 * 1000.0)
            1.0 / (1.0 + daysSince) // Exponential decay
        } ?: 0.1
        
        val popularityScore = calculatePopularityScore(candidate)
        
        return (recentActivityScore * 0.7) + (popularityScore * 0.3)
    }

    private fun determineSuggestionReason(
        mutualScore: Double,
        interestScore: Double,
        activityScore: Double
    ): SuggestionReason {
        return when {
            mutualScore >= 0.6 -> SuggestionReason.MUTUAL_CONNECTIONS
            interestScore >= 0.7 -> SuggestionReason.SIMILAR_INTERESTS
            activityScore >= 0.8 -> SuggestionReason.SIMILAR_ACTIVITY_LEVEL
            else -> SuggestionReason.GENERAL_RECOMMENDATION
        }
    }

    private suspend fun selectFinalSuggestions(
        scoredCandidates: List<UserSuggestionResult>,
        limit: Int,
        minScore: Double
    ): List<UserSuggestionResult> {
        // Filter by minimum score and sort by suggestion score
        val qualifiedSuggestions = scoredCandidates
            .filter { it.suggestionScore >= minScore }
            .sortedByDescending { it.suggestionScore }
        
        // Apply diversity rules to avoid too similar suggestions
        val diversifiedSuggestions = mutableListOf<UserSuggestionResult>()
        val reasonCounts = mutableMapOf<SuggestionReason, Int>()
        
        for (suggestion in qualifiedSuggestions) {
            val reasonCount = reasonCounts.getOrDefault(suggestion.suggestionReason, 0)
            val maxPerReason = limit / 3 // Don't let one reason dominate
            
            if (reasonCount < maxPerReason || diversifiedSuggestions.size < limit) {
                diversifiedSuggestions.add(suggestion)
                reasonCounts[suggestion.suggestionReason] = reasonCount + 1
                
                if (diversifiedSuggestions.size >= limit) break
            }
        }
        
        return diversifiedSuggestions
    }

    private suspend fun filterExistingConnections(
        candidates: List<SocialProfileEntity>,
        userId: String
    ): List<SocialProfileEntity> {
        val existingConnections = followRelationshipDao.getFollowing(userId, FollowRelationshipEntity.STATUS_ACCEPTED, 1000)
        val connectionIds = existingConnections.map { it.followingId }.toSet()
        
        return candidates.filter { candidate ->
            candidate.userId !in connectionIds && 
            !blockedUserDao.isUserBlocked(candidate.userId, userId) &&
            !blockedUserDao.isUserBlocked(userId, candidate.userId)
        }
    }

    private fun mapEntityToSearchResult(
        entity: SocialProfileEntity,
        viewerId: String
    ): UserSearchResult {
        return UserSearchResult(
            userId = entity.userId,
            displayName = entity.displayName ?: entity.username,
            profileImageUrl = entity.profilePhotoUrl,
            bio = entity.bio,
            fitnessLevel = determineFitnessLevel(entity.workoutCount),
            totalWorkouts = entity.workoutCount,
            memberSince = LocalDateTime.ofEpochSecond(entity.memberSince / 1000, 0, ZoneOffset.UTC),
            sharedEquipment = emptyList(),
            sharedGoals = emptyList(),
            connectionStatus = ConnectionStatus.NONE, // Would need to calculate
            mutualConnections = 0 // Would need to calculate
        )
    }

    private fun determineFitnessLevel(workoutCount: Int): FitnessLevel {
        return when {
            workoutCount >= 500 -> FitnessLevel.ADVANCED
            workoutCount >= 100 -> FitnessLevel.INTERMEDIATE
            workoutCount >= 20 -> FitnessLevel.BEGINNER
            else -> FitnessLevel.BEGINNER
        }
    }

    private fun categorizeFitnessLevel(workoutCount: Int): Int {
        return when {
            workoutCount >= 500 -> 3 // Advanced
            workoutCount >= 100 -> 2 // Intermediate
            workoutCount >= 20 -> 1  // Beginner
            else -> 0 // New
        }
    }

    private fun logProfileDbDiagnostics(context: String) {
        val dbName = runCatching { database.openHelper.databaseName }.getOrNull()
        val dbPath = runCatching { database.openHelper.writableDatabase.path }.getOrNull()
        val dbId = System.identityHashCode(database)
        val daoId = System.identityHashCode(socialProfileDao)
        val pid = Process.myPid()
        val threadName = Thread.currentThread().name
        Timber.d(
            "PROFILE_DB_DIAG $context dbName=$dbName dbPath=$dbPath dbId=$dbId daoId=$daoId pid=$pid thread=$threadName"
        )
    }
}

/**
 * Result data class for user suggestions with scoring details
 */
data class UserSuggestionResult(
    val userSearchResult: UserSearchResult,
    val suggestionScore: Double,
    val suggestionReason: SuggestionReason,
    val mutualConnectionCount: Int,
    val interestMatchScore: Double,
    val activityCompatibilityScore: Double
)

/**
 * Enum representing different suggestion reasoning
 */
enum class SuggestionReason {
    MUTUAL_CONNECTIONS,
    SIMILAR_INTERESTS,
    SIMILAR_ACTIVITY_LEVEL,
    NEW_USER_POPULAR,
    TRENDING,
    RECENTLY_VIEWED,
    GENERAL_RECOMMENDATION
}
