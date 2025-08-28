package com.example.liftrix.data.repository

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.liftrix.core.workmanager.WorkManagerProvider
import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.hilt.work.HiltWorker
import com.example.liftrix.data.local.dao.AchievementDao
import com.example.liftrix.data.mapper.AchievementMapper
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.AchievementRepository
import com.example.liftrix.sync.AchievementSyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AchievementRepository that manages user achievements
 * with local Room database storage and Firebase sync.
 */
@Singleton
class AchievementRepositoryImpl @Inject constructor(
    private val achievementDao: AchievementDao,
    private val achievementMapper: AchievementMapper,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : AchievementRepository {
    
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)

    override suspend fun getUserAchievements(userId: String): LiftrixResult<List<UserAchievement>> {
        return try {
            val entities = achievementDao.getUserAchievements(userId)
            val achievements = entities.map { achievementMapper.toDomain(it) }
            Result.success(achievements)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get achievements for user: $userId")
            Result.failure(e)
        }
    }

    override fun getUserAchievementsFlow(userId: String): Flow<List<UserAchievement>> {
        return achievementDao.getUserAchievementsFlow(userId).map { entities ->
            entities.map { achievementMapper.toDomain(it) }
        }
    }

    override suspend fun getDisplayedAchievements(userId: String): LiftrixResult<List<UserAchievement>> {
        return try {
            val entities = achievementDao.getDisplayedAchievements(userId)
            val achievements = entities.map { achievementMapper.toDomain(it) }
            Result.success(achievements)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get displayed achievements for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun saveAchievement(achievement: UserAchievement): LiftrixResult<Unit> {
        return try {
            require(achievement.userId.isNotBlank()) { "Achievement must have a valid user ID" }
            require(achievement.id.isNotBlank()) { "Achievement must have a valid ID" }

            // Check if achievement already exists
            val exists = achievementDao.findExistingAchievement(
                achievement.userId,
                achievement.achievementType.name,
                achievement.title
            ) != null

            if (exists) {
                return Result.success(Unit)
            }

            // Save to local database (offline-first)
            val entity = achievementMapper.toEntity(achievement, isSynced = false)
            achievementDao.insertAchievement(entity)

            // Queue sync in background
            queueAchievementSync(achievement.userId)

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save achievement: ${achievement.title}")
            Result.failure(e)
        }
    }

    override suspend fun saveAchievements(achievements: List<UserAchievement>): LiftrixResult<Unit> {
        return try {
            if (achievements.isEmpty()) {
                return Result.success(Unit)
            }

            val userId = achievements.first().userId
            require(achievements.all { it.userId == userId }) { "All achievements must belong to the same user" }

            // Filter out existing achievements
            val newAchievements = achievements.filter { achievement ->
                achievementDao.findExistingAchievement(
                    achievement.userId,
                    achievement.achievementType.name,
                    achievement.title
                ) == null
            }

            if (newAchievements.isEmpty()) {
                return Result.success(Unit)
            }

            // Save to local database (offline-first)
            val entities = newAchievements.map { achievementMapper.toEntity(it, isSynced = false) }
            achievementDao.insertAchievements(entities)

            // Queue sync in background
            queueAchievementSync(userId)

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save achievements batch")
            Result.failure(e)
        }
    }

    override suspend fun updateDisplayStatus(achievementId: String, userId: String, isDisplayed: Boolean): LiftrixResult<Unit> {
        return try {
            achievementDao.updateDisplayStatus(achievementId, userId, isDisplayed)
            queueAchievementSync(userId)

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to update achievement display status: $achievementId")
            Result.failure(e)
        }
    }

    override suspend fun deleteAchievement(achievementId: String, userId: String): LiftrixResult<Unit> {
        return try {
            achievementDao.deleteAchievement(achievementId, userId)

            // Also delete from Firestore
            firestore.collection("user_achievements")
                .document(achievementId)
                .delete()
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to delete achievement: $achievementId")
            Result.failure(e)
        }
    }

    override suspend fun getAchievementCount(userId: String): LiftrixResult<Int> {
        return try {
            val count = achievementDao.getAchievementCount(userId)
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get achievement count for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun achievementExists(userId: String, achievementType: String, title: String): LiftrixResult<Boolean> {
        return try {
            val exists = achievementDao.findExistingAchievement(userId, achievementType, title) != null
            Result.success(exists)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check achievement existence for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Queues achievement sync work for the specified user.
     */
    private suspend fun queueAchievementSync(userId: String): Result<Unit> {
        return try {
            val unsyncedAchievements = achievementDao.getUnsyncedAchievements(userId)
            if (unsyncedAchievements.isNotEmpty()) {
                val syncRequest = OneTimeWorkRequestBuilder<AchievementSyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniqueWork(
                    "achievement_sync_$userId",
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue achievement sync for user: $userId")
            Result.failure(e)
        }
    }
}