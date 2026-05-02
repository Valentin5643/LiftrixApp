package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Enhanced repository interface for managing user profile data.
 * App-owned implementations keep persistence, sync, and media concerns out of core.
 */
interface ProfileRepository {
    fun getProfile(userId: String): Flow<UserProfile?>

    suspend fun saveProfile(profile: UserProfile): Result<Unit>

    suspend fun updatePartialProfile(userId: String, updates: Map<String, Any>): Result<Unit>

    suspend fun deleteProfile(userId: String): Result<Unit>

    suspend fun hasProfile(userId: String): Boolean

    suspend fun hasCompletedProfile(userId: String): Boolean

    suspend fun getUnsyncedCount(userId: String): Int

    suspend fun queueSync(userId: String): Result<Unit>

    suspend fun syncNow(userId: String): Result<Unit>

    suspend fun getUserProfile(userId: String): LiftrixResult<UserProfile?>

    suspend fun saveUserProfile(profile: UserProfile): LiftrixResult<Unit>

    suspend fun updateProfileCompletion(userId: String): LiftrixResult<Int>

    suspend fun calculateStreakData(userId: String): LiftrixResult<StreakData>

    suspend fun updatePrivacySettings(userId: String, isPublic: Boolean): LiftrixResult<Unit>

    suspend fun getPublicProfiles(limit: Int = 50): LiftrixResult<List<UserProfile>>

    suspend fun getPublicProfile(userId: String): LiftrixResult<UserProfile?>
}
