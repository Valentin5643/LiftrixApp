package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.GuestSessionDao
import com.example.liftrix.data.mapper.GuestSessionMapper.toDomain
import com.example.liftrix.data.mapper.GuestSessionMapper.toEntity
import com.example.liftrix.domain.model.GuestSession
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.GuestSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of GuestSessionRepository using Room database
 */
@Singleton
class GuestSessionRepositoryImpl @Inject constructor(
    private val guestSessionDao: GuestSessionDao
) : GuestSessionRepository {

    override suspend fun getGuestSession(userId: String): LiftrixResult<GuestSession?> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = guestSessionDao.getGuestSession(userId)
                Result.success(entity?.toDomain())
            }
        } catch (e: Exception) {
            Result.failure(
                LiftrixError.DatabaseError(
                    "Failed to get guest session for user $userId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun saveGuestSession(guestSession: GuestSession): LiftrixResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                guestSessionDao.insertOrUpdate(guestSession.toEntity())
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(
                LiftrixError.DatabaseError(
                    "Failed to save guest session: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun clearGuestSession(userId: String): LiftrixResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                guestSessionDao.deleteByUserId(userId)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(
                LiftrixError.DatabaseError(
                    "Failed to clear guest session for user $userId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun getActiveGuestSessions(): LiftrixResult<List<GuestSession>> {
        return try {
            withContext(Dispatchers.IO) {
                val entities = guestSessionDao.getActiveGuestSessions()
                Result.success(entities.toDomain())
            }
        } catch (e: Exception) {
            Result.failure(
                LiftrixError.DatabaseError(
                    "Failed to get active guest sessions: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 2000L
                )
            )
        }
    }

    override suspend fun cleanupExpiredSessions(): LiftrixResult<Int> {
        return try {
            withContext(Dispatchers.IO) {
                // Consider sessions inactive if no activity for 7 days
                val cutoffTime = Instant.now().minusSeconds(7 * 24 * 60 * 60)
                val deletedCount = guestSessionDao.deleteInactiveSessions(cutoffTime)
                Result.success(deletedCount)
            }
        } catch (e: Exception) {
            Result.failure(
                LiftrixError.DatabaseError(
                    "Failed to cleanup expired sessions: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 5000L
                )
            )
        }
    }
}