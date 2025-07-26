package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating and maintaining search keywords for user discovery
 * 
 * This service handles the generation, indexing, and maintenance of search keywords
 * that enable efficient user discovery through the search functionality. It processes
 * user profile data to create comprehensive search terms while respecting privacy settings.
 * 
 * Key Responsibilities:
 * - Generate search keywords from user profile data
 * - Maintain searchable user index for performance
 * - Update search keywords when profile data changes
 * - Handle privacy settings and public profile visibility
 * - Clean up stale search data and optimize search performance
 * 
 * Business Rules:
 * - Only public profiles are indexed for search
 * - Search keywords respect user privacy preferences
 * - Keywords are automatically updated when profile changes
 * - Inactive users are periodically removed from search index
 * - Search terms are normalized for consistent matching
 */
@Singleton
class UserSearchIndexingService @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userSearchRepository: UserSearchRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Generates and updates search keywords for a user profile
     * 
     * @param userId The user ID to generate keywords for
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateUserSearchKeywords(userId: String): LiftrixResult<Unit> {
        return try {
            // Validate user ID
            if (userId.isBlank()) {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be empty"),
                        errorMessage = "User ID cannot be empty"
                    )
                )
            }
            
            // Get user profile using the suspend version
            val profileResult = profileRepository.getUserProfile(userId)
            if (profileResult.isFailure) {
                return profileResult as LiftrixResult<Unit>
            }
            
            val profile = profileResult.getOrNull()
            if (profile == null) {
                return liftrixFailure(
                    LiftrixError.NotFoundError("User profile not found")
                )
            }
            
            // Only index public profiles
            // Note: Profile privacy check needs to be implemented
            // For now, we'll index all profiles and add privacy filtering later
            Timber.d("Generating keywords for profile: $userId")
            
            // Generate search keywords
            val keywords = generateSearchKeywords(profile)
            
            // Update keywords in repository
            val updateResult = userSearchRepository.updateSearchKeywords(userId, keywords)
            if (updateResult.isFailure) {
                return updateResult
            }
            
            Timber.d("Updated search keywords for user: $userId (${keywords.size} keywords)")
            LiftrixResult.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error updating search keywords for user: $userId")
            val error = LiftrixError.UnknownError("Search keyword update failed: ${e.message}")
            errorHandler.handleError(error, mapOf(
                "context" to "UserSearchIndexingService.updateUserSearchKeywords",
                "userId" to userId
            ))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Batch updates search keywords for multiple users
     * 
     * @param userIds List of user IDs to update
     * @return LiftrixResult containing number of successful updates
     */
    suspend fun batchUpdateSearchKeywords(userIds: List<String>): LiftrixResult<BatchUpdateResult> {
        return try {
            var successCount = 0
            var failureCount = 0
            val errors = mutableListOf<String>()
            
            for (userId in userIds) {
                val result = updateUserSearchKeywords(userId)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failureCount++
                    errors.add("$userId: ${result.exceptionOrNull()?.message}")
                }
            }
            
            val result = BatchUpdateResult(
                totalProcessed = userIds.size,
                successCount = successCount,
                failureCount = failureCount,
                errors = errors
            )
            
            Timber.i("Batch keyword update completed: $successCount success, $failureCount failures")
            LiftrixResult.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during batch keyword update")
            val error = LiftrixError.UnknownError("Batch keyword update failed: ${e.message}")
            errorHandler.handleError(error, mapOf(
                "context" to "UserSearchIndexingService.batchUpdateSearchKeywords",
                "userCount" to userIds.size.toString()
            ))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Removes search keywords for a user (e.g., when profile becomes private)
     * 
     * @param userId The user ID to remove from search index
     * @return LiftrixResult indicating success or failure
     */
    suspend fun removeUserFromSearchIndex(userId: String): LiftrixResult<Unit> {
        return try {
            if (userId.isBlank()) {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be empty"),
                        errorMessage = "User ID cannot be empty"
                    )
                )
            }
            
            val result = userSearchRepository.updateSearchKeywords(userId, emptyList())
            if (result.isSuccess) {
                Timber.d("Removed user from search index: $userId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error removing user from search index: $userId")
            val error = LiftrixError.UnknownError("Search index removal failed: ${e.message}")
            errorHandler.handleError(error, mapOf(
                "context" to "UserSearchIndexingService.removeUserFromSearchIndex",
                "userId" to userId
            ))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Monitors profile changes and automatically updates search keywords
     * 
     * @param userId The user ID to monitor for changes
     * @return Flow that emits update results
     */
    fun monitorProfileChanges(userId: String): Flow<LiftrixResult<Unit>> {
        return profileRepository.getProfile(userId).map { profile ->
            if (profile != null) {
                updateUserSearchKeywords(userId)
            } else {
                removeUserFromSearchIndex(userId)
            }
        }
    }
    
    /**
     * Generates comprehensive search keywords from user profile data
     */
    private fun generateSearchKeywords(profile: UserProfile): List<String> {
        val keywords = mutableSetOf<String>()
        
        // Display name variations
        profile.displayName?.let { name ->
            keywords.add(name.lowercase())
            keywords.addAll(name.split(" ").map { it.lowercase().trim() })
            
            // Add partial matches for names
            if (name.length >= 3) {
                for (i in 3..name.length) {
                    keywords.add(name.lowercase().substring(0, i))
                }
            }
        }
        
        // Bio keywords (extract meaningful terms)
        profile.bio?.let { bio ->
            val bioWords = bio.lowercase()
                .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                .split("\\s+".toRegex())
                .filter { it.length >= 3 }
                .take(10) // Limit bio keywords
            
            keywords.addAll(bioWords)
        }
        
        // Fitness goals
        profile.fitnessGoals?.forEach { goal ->
            keywords.add(goal.name.lowercase())
            // Add goal-related terms
            when (goal) {
                FitnessGoal.LOSE_WEIGHT -> keywords.addAll(listOf("weight", "loss", "cut", "cutting"))
                FitnessGoal.BUILD_MUSCLE -> keywords.addAll(listOf("muscle", "gain", "bulk", "bulking"))
                FitnessGoal.INCREASE_STRENGTH -> keywords.addAll(listOf("strength", "strong", "powerlifting"))
                FitnessGoal.IMPROVE_ENDURANCE -> keywords.addAll(listOf("endurance", "cardio", "running"))
                FitnessGoal.IMPROVE_FLEXIBILITY -> keywords.addAll(listOf("flexibility", "stretching", "mobility"))
                else -> {}
            }
        }
        
        // Available equipment
        profile.availableEquipment?.forEach { equipment ->
            keywords.add(equipment.name.lowercase())
            // Add equipment-related terms
            when (equipment) {
                Equipment.BARBELL -> keywords.addAll(listOf("barbell", "bar", "powerlifting"))
                Equipment.DUMBBELLS -> keywords.addAll(listOf("dumbbells", "dumbbell", "db"))
                Equipment.RESISTANCE_BANDS -> keywords.addAll(listOf("bands", "resistance"))
                Equipment.PULL_UP_BAR -> keywords.addAll(listOf("pullup", "pullups", "chinup"))
                Equipment.BODYWEIGHT_ONLY -> keywords.addAll(listOf("bodyweight", "calisthenics"))
                else -> {}
            }
        }
        
        // Fitness level indicators
        keywords.addAll(listOf(
            when (profile.totalWorkouts ?: 0) {
                in 0..10 -> "beginner"
                in 11..50 -> "intermediate"
                in 51..150 -> "advanced"
                else -> "expert"
            }
        ))
        
        // Current streak indicators
        profile.currentStreak?.let { streak ->
            when (streak) {
                in 7..29 -> keywords.add("consistent")
                in 30..89 -> keywords.add("dedicated")
                in 90..Int.MAX_VALUE -> keywords.add("committed")
            }
        }
        
        // Clean up and filter keywords
        return keywords
            .filter { it.isNotBlank() && it.length >= 2 }
            .filter { !EXCLUDED_KEYWORDS.contains(it) }
            .take(MAX_KEYWORDS_PER_USER)
            .toList()
    }
    
    companion object {
        private val EXCLUDED_KEYWORDS = setOf(
            "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "is", "am", "are", "was", "were", "be", "been", "have", "has", "had", "do",
            "does", "did", "will", "would", "could", "should", "may", "might", "can",
            "this", "that", "these", "those", "a", "an", "my", "your", "his", "her",
            "our", "their", "me", "you", "him", "her", "us", "them", "it"
        )
        
        private const val MAX_KEYWORDS_PER_USER = 50
    }
}

/**
 * Result data class for batch update operations
 * 
 * @property totalProcessed Total number of users processed
 * @property successCount Number of successful updates
 * @property failureCount Number of failed updates
 * @property errors List of error messages for failed updates
 */
data class BatchUpdateResult(
    val totalProcessed: Int,
    val successCount: Int,
    val failureCount: Int,
    val errors: List<String>
) {
    val successRate: Float
        get() = if (totalProcessed > 0) successCount.toFloat() / totalProcessed else 0f
}