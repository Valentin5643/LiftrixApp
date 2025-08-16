package com.example.liftrix.data.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.PRNotificationRepository
import com.example.liftrix.domain.repository.PRNotificationPreferences
import com.example.liftrix.domain.repository.PRReaction
import com.example.liftrix.domain.repository.UserReaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of PRNotificationRepository
 * TODO: Implement full functionality with DAO and Firebase integration
 */
@Singleton
class PRNotificationRepositoryImpl @Inject constructor() : PRNotificationRepository {

    override suspend fun saveReaction(
        userId: String,
        buddyUserId: String,
        prId: String,
        reactionType: String
    ): LiftrixResult<Unit> {
        // Stub implementation
        return Result.success(Unit)
    }

    override fun getReactionsForPR(prId: String): Flow<List<PRReaction>> {
        // Stub implementation
        return flowOf(emptyList())
    }

    override suspend fun getReactionCounts(prId: String): LiftrixResult<Map<String, Int>> {
        // Stub implementation
        return Result.success(emptyMap())
    }

    override suspend fun hasUserReacted(userId: String, prId: String): LiftrixResult<Boolean> {
        // Stub implementation
        return Result.success(false)
    }

    override suspend fun removeReaction(userId: String, prId: String): LiftrixResult<Unit> {
        // Stub implementation
        return Result.success(Unit)
    }

    override fun getUserReactions(userId: String, daysSince: Int): Flow<List<UserReaction>> {
        // Stub implementation
        return flowOf(emptyList())
    }

    override suspend fun markNotificationSent(
        fromUserId: String,
        toUserId: String,
        prId: String
    ): LiftrixResult<Unit> {
        // Stub implementation
        return Result.success(Unit)
    }

    override suspend fun wasNotificationSent(
        fromUserId: String,
        toUserId: String,
        prId: String
    ): LiftrixResult<Boolean> {
        // Stub implementation
        return Result.success(false)
    }

    override suspend fun getPRNotificationPreferences(userId: String): LiftrixResult<PRNotificationPreferences> {
        // Stub implementation
        return Result.success(PRNotificationPreferences())
    }

    override suspend fun updatePRNotificationPreferences(
        userId: String,
        preferences: PRNotificationPreferences
    ): LiftrixResult<Unit> {
        // Stub implementation
        return Result.success(Unit)
    }

    override suspend fun getBuddiesForPRNotification(userId: String): LiftrixResult<List<String>> {
        // Stub implementation
        return Result.success(emptyList())
    }
}