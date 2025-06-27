package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity
import com.example.liftrix.domain.service.WeightMemoryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightMemoryServiceImpl @Inject constructor(
    private val exerciseUsageHistoryDao: ExerciseUsageHistoryDao
) : WeightMemoryService {
    
    override suspend fun getLastUsedWeight(userId: String, exerciseId: String): Result<Float?> {
        return withContext(Dispatchers.IO) {
            try {
                if (userId.isBlank() || exerciseId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("User ID and Exercise ID cannot be blank"))
                }
                
                val weight = exerciseUsageHistoryDao.getLastUsedWeight(userId, exerciseId)
                Timber.d("Retrieved last used weight for user $userId, exercise $exerciseId: $weight")
                Result.success(weight)
            } catch (e: Exception) {
                Timber.e(e, "Error retrieving last used weight for user $userId, exercise $exerciseId")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateExerciseWeight(
        userId: String,
        exerciseId: String,
        weight: Float,
        reps: Int,
        sets: Int,
        workoutId: String?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (userId.isBlank() || exerciseId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("User ID and Exercise ID cannot be blank"))
                }
                
                if (weight <= 0) {
                    return@withContext Result.failure(IllegalArgumentException("Weight must be positive"))
                }
                
                val usageHistory = ExerciseUsageHistoryEntity(
                    userId = userId,
                    exerciseId = exerciseId,
                    weightUsed = weight,
                    repsPerformed = reps,
                    setsPerformed = sets,
                    usedAt = LocalDateTime.now(),
                    workoutId = workoutId
                )
                
                val insertedId = exerciseUsageHistoryDao.insertUsage(usageHistory)
                Timber.d("Updated exercise weight for user $userId, exercise $exerciseId: $weight (ID: $insertedId)")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error updating exercise weight for user $userId, exercise $exerciseId")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getRecentExercises(userId: String, limit: Int): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                if (userId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
                }
                
                if (limit <= 0) {
                    return@withContext Result.failure(IllegalArgumentException("Limit must be positive"))
                }
                
                val recentExerciseIds = exerciseUsageHistoryDao.getRecentExerciseIds(userId, limit)
                Timber.d("Retrieved ${recentExerciseIds.size} recent exercises for user $userId")
                Result.success(recentExerciseIds)
            } catch (e: Exception) {
                Timber.e(e, "Error retrieving recent exercises for user $userId")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getAverageWeightLast30Days(userId: String, exerciseId: String): Result<Float?> {
        return withContext(Dispatchers.IO) {
            try {
                if (userId.isBlank() || exerciseId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("User ID and Exercise ID cannot be blank"))
                }
                
                val averageWeight = exerciseUsageHistoryDao.getAverageWeightLast30Days(userId, exerciseId)
                Timber.d("Retrieved average weight for user $userId, exercise $exerciseId: $averageWeight")
                Result.success(averageWeight)
            } catch (e: Exception) {
                Timber.e(e, "Error retrieving average weight for user $userId, exercise $exerciseId")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getExerciseUsageCount(userId: String, exerciseId: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (userId.isBlank() || exerciseId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("User ID and Exercise ID cannot be blank"))
                }
                
                val usageCount = exerciseUsageHistoryDao.getExerciseUsageCount(userId, exerciseId)
                Timber.d("Retrieved usage count for user $userId, exercise $exerciseId: $usageCount")
                Result.success(usageCount)
            } catch (e: Exception) {
                Timber.e(e, "Error retrieving usage count for user $userId, exercise $exerciseId")
                Result.failure(e)
            }
        }
    }
} 