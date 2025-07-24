package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user achievement operations.
 * Handles CRUD operations for fitness achievements with proper user scoping.
 */
interface AchievementRepository {

    /**
     * Get all achievements for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with list of user's achievements
     */
    suspend fun getUserAchievements(userId: String): LiftrixResult<List<UserAchievement>>

    /**
     * Get achievements for a user as a Flow for real-time updates.
     * 
     * @param userId The user ID for data scoping
     * @return Flow of user's achievements
     */
    fun getUserAchievementsFlow(userId: String): Flow<List<UserAchievement>>

    /**
     * Get only displayed achievements for a user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with list of displayed achievements
     */
    suspend fun getDisplayedAchievements(userId: String): LiftrixResult<List<UserAchievement>>

    /**
     * Save a single achievement for a user.
     * 
     * @param achievement The achievement to save
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveAchievement(achievement: UserAchievement): LiftrixResult<Unit>

    /**
     * Save multiple achievements for a user.
     * 
     * @param achievements The achievements to save
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveAchievements(achievements: List<UserAchievement>): LiftrixResult<Unit>

    /**
     * Update achievement display status.
     * 
     * @param achievementId The achievement ID to update
     * @param userId The user ID for data scoping
     * @param isDisplayed Whether the achievement should be displayed
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateDisplayStatus(achievementId: String, userId: String, isDisplayed: Boolean): LiftrixResult<Unit>

    /**
     * Delete an achievement for a user.
     * 
     * @param achievementId The achievement ID to delete
     * @param userId The user ID for data scoping
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteAchievement(achievementId: String, userId: String): LiftrixResult<Unit>

    /**
     * Get total count of achievements for a user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with achievement count
     */
    suspend fun getAchievementCount(userId: String): LiftrixResult<Int>

    /**
     * Check if a specific achievement already exists for a user.
     * 
     * @param userId The user ID for data scoping
     * @param achievementType The type of achievement
     * @param title The achievement title
     * @return LiftrixResult with true if achievement exists, false otherwise
     */
    suspend fun achievementExists(userId: String, achievementType: String, title: String): LiftrixResult<Boolean>
}